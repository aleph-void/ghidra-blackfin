/* ###
 * Ghidra-Rare additions to ghidra-blackfin.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package blackfin;

import ghidra.app.services.AbstractAnalyzer;
import ghidra.app.services.AnalysisPriority;
import ghidra.app.services.AnalyzerType;
import ghidra.app.util.importer.MessageLog;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * Makes FDPIC PLT stubs ({@code P1 = [P3 + d]; P3 = [P3 + d + 4]; JUMP (P1);}) thunks of
 * the function their GOT function descriptor names, once the stubs are disassembled. See
 * {@link BlackfinFdpicGotAnalyzer}.
 */
public class BlackfinFdpicPltAnalyzer extends AbstractAnalyzer {

	public BlackfinFdpicPltAnalyzer() {
		super("Blackfin FDPIC PLT thunks",
			"Turns FDPIC PLT stubs into thunks of the functions their GOT descriptors name.",
			AnalyzerType.INSTRUCTION_ANALYZER);
		setDefaultEnablement(true);
		setPriority(AnalysisPriority.REFERENCE_ANALYSIS.after());
	}

	@Override
	public boolean canAnalyze(Program program) {
		return "Blackfin".equals(program.getLanguage().getProcessor().toString()) &&
			program.getMemory().getBlock(".plt") != null;
	}

	@Override
	public boolean added(Program program, AddressSetView set, TaskMonitor monitor,
			MessageLog log) throws CancelledException {
		Long got = BlackfinFdpicGotAnalyzer.gotAddress(program);
		if (got != null) {
			BlackfinFdpicGotAnalyzer.makePltThunks(program, got, set, monitor);
		}
		return true;
	}
}
