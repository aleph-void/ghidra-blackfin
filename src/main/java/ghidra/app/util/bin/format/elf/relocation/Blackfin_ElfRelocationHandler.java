/* ###
 * Ghidra-Rare additions to ghidra-blackfin.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ghidra.app.util.bin.format.elf.relocation;

import ghidra.app.util.bin.format.elf.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.reloc.Relocation.Status;
import ghidra.program.model.reloc.RelocationResult;

/**
 * ELF relocation handler for Analog Devices Blackfin (EM_BLACKFIN = 106), following
 * binutils bfd/elf32-bfin.c and the uClibc FDPIC dynamic linker.
 * <ul>
 * <li>Data: BYTE4_DATA/VAR_EQ_SYMB = S + A (32 bits), BYTE2_DATA, BYTE_DATA.</li>
 * <li>FDPIC FUNCDESC_VALUE: the descriptor's entry word = S + A; its GOT word is the
 * defining module's, assigned at load time, and is left alone.</li>
 * <li>Code (relocatable objects): PC-relative jumps and calls in half-words; 16-bit
 * immediates (RIMM16, LUIMM16, HUIMM16), whose relocation points at the immediate.</li>
 * </ul>
 * FUNCDESC (address of a canonical descriptor the loader allocates) and the GOT-relative
 * types need loader- or linker-built tables and are reported, not applied.
 */
public class Blackfin_ElfRelocationHandler
		extends AbstractElfRelocationHandler<Blackfin_ElfRelocationType, ElfRelocationContext<?>> {

	public static final int EM_BLACKFIN = 106;

	public Blackfin_ElfRelocationHandler() {
		super(Blackfin_ElfRelocationType.class);
	}

	@Override
	public boolean canRelocate(ElfHeader elf) {
		return elf.e_machine() == EM_BLACKFIN;
	}

	/** Write {@code value} into a 32-bit instruction made of two little-endian half-words. */
	private static void putSplit(Memory mem, Address a, int hiBits, long value)
			throws MemoryAccessException {
		short h0 = mem.getShort(a);
		int mask = (1 << hiBits) - 1;
		mem.setShort(a, (short) ((h0 & ~mask) | ((value >> 16) & mask)));
		mem.setShort(a.add(2), (short) value);
	}

	private static void putLow(Memory mem, Address a, int bits, long value)
			throws MemoryAccessException {
		short h = mem.getShort(a);
		int mask = (1 << bits) - 1;
		mem.setShort(a, (short) ((h & ~mask) | (value & mask)));
	}

	@Override
	protected RelocationResult relocate(ElfRelocationContext<?> ctx, ElfRelocation relocation,
			Blackfin_ElfRelocationType type, Address relocationAddress, ElfSymbol elfSymbol,
			Address symbolAddr, long symbolValue, String symbolName)
			throws MemoryAccessException {
		Program program = ctx.getProgram();
		Memory mem = program.getMemory();
		long P = relocationAddress.getOffset();

		switch (type) {
			case R_BFIN_UNUSED0:
			case R_BFIN_GNU_VTINHERIT:
			case R_BFIN_GNU_VTENTRY:
				return RelocationResult.SKIPPED;
			default:
				break;
		}
		if (handleUnresolvedSymbol(ctx, relocation, relocationAddress)) {
			return RelocationResult.FAILURE;
		}
		long S = symbolValue;
		// REL sections keep the addend in the relocated field (data words)
		long A = relocation.hasAddend() ? relocation.getAddend() : 0;
		switch (type) {
			case R_BFIN_BYTE4_DATA:
			case R_BFIN_VAR_EQ_SYMB: {
				if (!relocation.hasAddend()) {
					A = mem.getInt(relocationAddress) & 0xffffffffL;
				}
				mem.setInt(relocationAddress, (int) (S + A));
				return new RelocationResult(Status.APPLIED, 4);
			}
			case R_BFIN_BYTE2_DATA:
				if (!relocation.hasAddend()) {
					A = mem.getShort(relocationAddress) & 0xffffL;
				}
				mem.setShort(relocationAddress, (short) (S + A));
				return new RelocationResult(Status.APPLIED, 2);
			case R_BFIN_BYTE_DATA:
				if (!relocation.hasAddend()) {
					A = mem.getByte(relocationAddress) & 0xffL;
				}
				mem.setByte(relocationAddress, (byte) (S + A));
				return new RelocationResult(Status.APPLIED, 1);
			case R_BFIN_FUNCDESC_VALUE: {
				// Against a section symbol (a local function): entry = S + the addend stored in
				// the word. Against a named function (the PLT slots in .rel.plt): the stored
				// word is the address of its lazy-binding stub, which the dynamic linker
				// replaces with the function's entry; statically that is S.
				boolean section = elfSymbol != null && elfSymbol.isSection();
				if (!relocation.hasAddend() && section) {
					A = mem.getInt(relocationAddress) & 0xffffffffL;
				}
				mem.setInt(relocationAddress, (int) (section ? S + A : S + (relocation.hasAddend() ? A : 0)));
				return new RelocationResult(Status.APPLIED, 4);
			}
			case R_BFIN_PCREL24:
			case R_BFIN_PCREL24_JUMP_L:
			case R_BFIN_PCREL24_JUMP_X:
			case R_BFIN_PCREL24_CALL_X: {
				// the relocation points at the second half-word; the displacement counts
				// from the instruction start (bfd bfin_pcrel24_reloc)
				Address insn = relocationAddress.subtract(2);
				putSplit(mem, insn, 8, (S + A - (P - 2)) >> 1);
				return new RelocationResult(Status.APPLIED, 4);
			}
			case R_BFIN_PCREL12_JUMP:
			case R_BFIN_PCREL12_JUMP_S:
				putLow(mem, relocationAddress, 12, (S + A - P) >> 1);
				return new RelocationResult(Status.APPLIED, 2);
			case R_BFIN_PCREL10:
				putLow(mem, relocationAddress, 10, (S + A - P) >> 1);
				return new RelocationResult(Status.APPLIED, 2);
			case R_BFIN_RIMM16:
			case R_BFIN_LUIMM16:
				mem.setShort(relocationAddress, (short) (S + A));   // the immediate half-word
				return new RelocationResult(Status.APPLIED, 2);
			case R_BFIN_HUIMM16:
				mem.setShort(relocationAddress, (short) ((S + A) >> 16));
				return new RelocationResult(Status.APPLIED, 2);
			case R_BFIN_FUNCDESC:
				// The address of the function's canonical descriptor, which the FDPIC dynamic
				// linker allocates at load time: there is no static value to write.
				markAsWarning(program, relocationAddress, type, symbolName,
					relocation.getSymbolIndex(),
					"function descriptor allocated by the dynamic linker", ctx.getLog());
				return RelocationResult.SKIPPED;
			default:
				markAsUnhandled(program, relocationAddress, type, relocation.getSymbolIndex(),
					symbolName, ctx.getLog());
				return RelocationResult.UNSUPPORTED;
		}
	}
}
