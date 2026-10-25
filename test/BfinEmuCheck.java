// P-code semantics check: emulates test/emu/emu_test.bin (raw, imported at ram:0) with Ghidra's
// EmulatorHelper from address 0 until PC reaches "done", then compares the result words the
// program stored at 0x1040.. (and a few registers) with values worked out by hand.
// Prints PASS/FAIL lines and a total.
// Script args: <done-address-hex> [outfile]
// SPDX-License-Identifier: GPL-3.0-or-later
//@category Testing

import java.io.PrintStream;

import ghidra.app.emulator.EmulatorHelper;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.lang.Register;
import ghidra.program.model.lang.RegisterValue;

public class BfinEmuCheck extends GhidraScript {

	private int pass, fail;
	private PrintStream out;

	private void check(String what, long got, long want) {
		boolean ok = (got & 0xffffffffL) == (want & 0xffffffffL);
		out.printf("%s %-40s = 0x%08x (expected 0x%08x)%n", ok ? "PASS" : "FAIL", what,
			got & 0xffffffffL, want & 0xffffffffL);
		if (ok) {
			pass++;
		}
		else {
			fail++;
		}
	}

	private long word(EmulatorHelper emu, long addr) {
		byte[] m = emu.readMemory(toAddr(addr), 4);
		return (m[0] & 0xff) | (m[1] & 0xff) << 8 | (m[2] & 0xff) << 16 | (long) (m[3] & 0xff) << 24;
	}

	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		long done = Long.parseLong(args[0], 16);
		out = args.length > 1 ? new PrintStream(args[1]) : System.out;
		disassemble(toAddr(0));   // flow disassembly applies LSETUP's loop-bottom context
		EmulatorHelper emu = new EmulatorHelper(currentProgram);
		emu.writeRegister(emu.getPCRegister(), 0);
		Register ctxReg = currentProgram.getProgramContext().getBaseContextRegister();
		int steps = 0;
		while (steps++ < 2000) {
			Address cur = emu.getExecutionAddress();
			if (cur.getOffset() == done) {
				break;
			}
			if (getInstructionAt(cur) == null) {
				disassemble(cur);
			}
			RegisterValue ctx = currentProgram.getProgramContext().getRegisterValue(ctxReg, cur);
			if (ctx != null) {
				emu.setContextRegister(ctx);
			}
			if (!emu.step(monitor)) {
				out.println("emulation error at " + cur + ": " + emu.getLastError());
				break;
			}
		}
		check("reached done", emu.getExecutionAddress().getOffset(), done);
		Object[][] mem = {
			{ 0x40, 93L, "ADD" }, { 0x44, 107L, "SUB" }, { 0x48, 0x78L, "AND" },
			{ 0x4c, 0x123456ffL, "OR" }, { 0x50, 0x12345687L, "XOR" }, { 0x54, 0x23456780L, "LSL" },
			{ 0x58, 0x00123456L, "LSR" }, { 0x5c, 0xfffffffcL, "ASR" },
			{ 0x60, 0L, "CC = signed <" }, { 0x64, 1L, "CC = unsigned < (IU)" },
			{ 0x70, 0x3fff0001L, "16x16 multiply (IS)" }, { 0x74, 0x7fffffffL, "ADD (S) saturates" },
			{ 0x78, 0xfffffff9L, "B[] (X) sign extends" }, { 0x7c, 0xf9L, "B[] (Z)" },
			{ 0x80, 0x64L, "W[] (Z), W[] store" }, { 0x84, 0x12345678L, "[P++] / [P] store" },
			{ 0x88, 0x006400f9L, "[P--] after post-increment" },
			{ 0x8c, 0x006400f9L, "[P ++ P] post-modify" }, { 0x90, 0x12345678L, "push / pop" },
			{ 0x94, 0x1004L, "DAG [I ++ M] post-modify" },
			{ 0x98, 3L, "IF CC JUMP taken and not taken" }, { 0x9c, 42L, "CALL / RTS" },
			{ 0xa0, 20L, "LSETUP hardware loop, 5 iterations" }, { 0xa4, 0L, "LC0 after the loop" },
			{ 0xa8, 0x1800L, "LINK / UNLINK restore SP" },
		};
		for (Object[] e : mem) {
			check((String) e[2] + " @0x" + Integer.toHexString(0x1000 + (Integer) e[0]),
				word(emu, 0x1000 + (Integer) e[0]), (Long) e[1]);
		}
		check("AC0 after carry out (ASTAT bit 12)", (word(emu, 0x1068) >> 12) & 1, 1);
		check("AZ after zero result (ASTAT bit 0)", word(emu, 0x106c) & 1, 1);
		out.printf("emulation: %d steps, %d passed, %d failed%n", steps, pass, fail);
		emu.dispose();
		if (out != System.out) {
			out.close();
		}
	}
}
