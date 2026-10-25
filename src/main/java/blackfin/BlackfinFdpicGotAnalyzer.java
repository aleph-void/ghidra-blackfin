/* ###
 * Ghidra-Rare additions to ghidra-blackfin.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package blackfin;

import java.math.BigInteger;

import ghidra.app.services.AbstractAnalyzer;
import ghidra.app.services.AnalysisPriority;
import ghidra.app.services.AnalyzerType;
import ghidra.app.util.importer.MessageLog;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.symbol.Symbol;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * FDPIC (Blackfin uClinux shared libraries and executables): every function runs with P3
 * holding its module's GOT address (DT_PLTGOT, symbol {@code __GLOBAL_OFFSET_TABLE_}), and
 * code reaches data, jump tables and function descriptors through it. This analyzer records
 * that value for P3 over the executable blocks, so the decompiler resolves GOT loads, which
 * lets it recover switch tables and data references in position-independent code.
 */
public class BlackfinFdpicGotAnalyzer extends AbstractAnalyzer {

	private static final String NAME = "Blackfin FDPIC GOT pointer";
	private static final String DESCRIPTION =
		"Sets P3 to the module's GOT address (DT_PLTGOT) in FDPIC programs so that " +
			"GOT-relative loads, switch tables and data references resolve.";

	public BlackfinFdpicGotAnalyzer() {
		super(NAME, DESCRIPTION, AnalyzerType.BYTE_ANALYZER);
		setDefaultEnablement(true);
		setPriority(AnalysisPriority.FORMAT_ANALYSIS.after());
	}

	@Override
	public boolean canAnalyze(Program program) {
		return "Blackfin".equals(program.getLanguage().getProcessor().toString());
	}

	static Long gotAddress(Program program) {
		for (String name : new String[] { "__GLOBAL_OFFSET_TABLE_", "_GLOBAL_OFFSET_TABLE_" }) {
			for (Symbol s : program.getSymbolTable().getSymbols(name)) {
				return s.getAddress().getOffset();
			}
		}
		// DT_PLTGOT (tag 3) in the dynamic section
		Memory memory = program.getMemory();
		MemoryBlock dyn = memory.getBlock(".dynamic");
		if (dyn == null || !dyn.isInitialized()) {
			return null;
		}
		try {
			for (Address a = dyn.getStart(); a.add(7).compareTo(dyn.getEnd()) <= 0; a = a.add(8)) {
				int tag = memory.getInt(a);
				if (tag == 0) {
					break;
				}
				if (tag == 3) {
					return memory.getInt(a.add(4)) & 0xffffffffL;
				}
			}
		}
		catch (Exception e) {
			return null;
		}
		return null;
	}

	@Override
	public boolean added(Program program, AddressSetView set, TaskMonitor monitor,
			MessageLog log) throws CancelledException {
		// Only FDPIC objects have a GOT on Blackfin (bare-metal bfin-elf images do not;
		// Blackfin Linux ELF objects are always FDPIC).
		Long got = gotAddress(program);
		Register p3 = program.getRegister("P3");
		if (got == null || p3 == null) {
			return true;
		}
		for (MemoryBlock block : program.getMemory().getBlocks()) {
			if (!block.isExecute() || !block.isInitialized()) {
				continue;
			}
			try {
				program.getProgramContext().setValue(p3, block.getStart(), block.getEnd(),
					BigInteger.valueOf(got));
			}
			catch (Exception e) {
				log.appendException(e);
			}
		}
		log.appendMsg(NAME, "P3 = 0x" + Long.toHexString(got));
		return true;
	}

	/**
	 * FDPIC PLT stubs are {@code P1 = [P3 + d]; P3 = [P3 + d + 4]; JUMP (P1);}: they load the
	 * callee's function descriptor from the GOT. Turn each into a thunk of the function the
	 * descriptor's entry word names (the FUNCDESC_VALUE relocation filled it in), so calls
	 * through the PLT show the callee and the decompiler does not jump into the EXTERNAL block.
	 */
	static int makePltThunks(Program program, long got, AddressSetView set, TaskMonitor monitor) {
		MemoryBlock plt = program.getMemory().getBlock(".plt");
		if (plt == null || !plt.isInitialized()) {
			return 0;
		}
		ghidra.program.model.listing.Listing listing = program.getListing();
		int made = 0;
		for (ghidra.program.model.listing.Instruction i : listing.getInstructions(
			set.intersect(new ghidra.program.model.address.AddressSet(plt.getStart(), plt.getEnd())),
			true)) {
			String t = i.toString().replace(" ", "");
			java.util.regex.Matcher m =
				java.util.regex.Pattern.compile("^P1=\\[P3\\+(-?0x[0-9a-fA-F]+|-?\\d+)\\]$").matcher(t);
			if (!m.matches()) {
				continue;
			}
			ghidra.program.model.listing.Instruction second = i.getNext();
			ghidra.program.model.listing.Instruction third = second == null ? null : second.getNext();
			if (third == null || !third.toString().replace(" ", "").equals("JUMP(P1)")) {
				continue;
			}
			String d = m.group(1);
			long disp = d.startsWith("-") ? -Long.decode(d.substring(1)) : Long.decode(d);
			try {
				Address slot = plt.getStart().getNewAddress((got + disp) & 0xffffffffL);
				long entry = program.getMemory().getInt(slot) & 0xffffffffL;
				Address target = plt.getStart().getNewAddress(entry);
				ghidra.program.model.listing.Function callee =
					program.getFunctionManager().getFunctionAt(target);
				if (callee == null) {
					ghidra.program.model.symbol.Symbol sym =
						program.getSymbolTable().getPrimarySymbol(target);
					if (sym != null && sym.getObject() instanceof ghidra.program.model.listing.Function f) {
						callee = f;
					}
				}
				if (callee == null && program.getMemory().getBlock(target) != null &&
					program.getMemory().getBlock(target).isExecute()) {
					callee = new ghidra.app.cmd.function.CreateFunctionCmd(target)
							.applyTo(program, monitor) ? program.getFunctionManager().getFunctionAt(target)
									: null;
				}
				if (callee == null) {
					continue;
				}
				ghidra.program.model.listing.Function stub =
					program.getFunctionManager().getFunctionAt(i.getAddress());
				if (stub == null) {
					stub = program.getFunctionManager().createFunction(null, i.getAddress(),
						new ghidra.program.model.address.AddressSet(i.getAddress(),
							third.getMaxAddress()),
						ghidra.program.model.symbol.SourceType.ANALYSIS);
				}
				stub.setThunkedFunction(callee);
				made++;
			}
			catch (Exception e) {
				// not a stub we can resolve; leave it
			}
		}
		return made;
	}
}
