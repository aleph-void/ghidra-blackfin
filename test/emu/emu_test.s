/* P-code semantics test for the Blackfin module (GNU as syntax, bfin-elf-as).  Assembled by
   test/emu/build.sh; BfinEmuCheck.java runs it in Ghidra's emulator from address 0 until PC
   reaches "done" and checks the registers and memory listed there.  Expected values are
   worked out by hand from the Blackfin Processor Programming Reference. */
	.text
	.global start
start:
	P0.L = 0x1000; P0.H = 0x0;		/* data area */
	SP.L = 0x1800; SP.H = 0x0;		/* stack */
	/* ---------------------------------------------------------------- ALU */
	R0 = 100 (X);
	R1 = -7 (X);				/* R1 = 0xfffffff9 */
	R2 = R0 + R1;				/* R2 = 93 */
	R3 = R0 - R1;				/* R3 = 107 */
	R4.L = 0x5678; R4.H = 0x1234;		/* R4 = 0x12345678 */
	R5 = 0xff (Z);
	R6 = R4 & R5;				/* R6 = 0x78 */
	R7 = R4 | R5;				/* R7 = 0x123456ff */
	[P0 + 0x40] = R2;
	[P0 + 0x44] = R3;
	[P0 + 0x48] = R6;
	[P0 + 0x4c] = R7;
	R2 = R4 ^ R5;				/* 0x12345687 */
	R3 = R4 << 4;				/* 0x23456780 */
	R6 = R4 >> 8;				/* 0x00123456 */
	R7 = R1 >>> 1;				/* arithmetic: 0xfffffffc */
	[P0 + 0x50] = R2;
	[P0 + 0x54] = R3;
	[P0 + 0x58] = R6;
	[P0 + 0x5c] = R7;
	/* ------------------------------------------------- flags: CC, AC0, AZ */
	CC = R0 < R1;				/* signed 100 < -7: false */
	R2 = CC;				/* 0 */
	CC = R0 < R1 (IU);			/* unsigned 100 < 0xfffffff9: true */
	R3 = CC;				/* 1 */
	R6 = -1 (X);
	R7 = R6 + R0;				/* carry out: AC0 = 1 */
	R5 = ASTAT;
	[P0 + 0x60] = R2;
	[P0 + 0x64] = R3;
	[P0 + 0x68] = R5;			/* AC0 (bit 12) set */
	R6 = R0 - R0;				/* zero: AZ (bit 0) set */
	R5 = ASTAT;
	[P0 + 0x6c] = R5;
	/* ------------------------------------------------- 16x16 multiply, saturation */
	R2.L = 0x7fff;
	R3.L = 0x7fff;
	R6 = R2.L * R3.L (IS);			/* integer: 0x7fff * 0x7fff = 0x3fff0001 */
	[P0 + 0x70] = R6;
	R2.L = 0xffff; R2.H = 0x7fff;
	R3 = 5 (X);
	R6 = R2 + R3 (S);			/* saturates to 0x7fffffff */
	[P0 + 0x74] = R6;
	/* ------------------------------------------------- loads/stores, addressing modes */
	[P0] = R4;				/* [0x1000] = 0x12345678 */
	B[P0 + 4] = R1;				/* [0x1004] = 0xf9 */
	W[P0 + 6] = R0;				/* [0x1006] = 0x0064 */
	R2 = B[P0 + 4] (X);			/* sign extended: 0xfffffff9 */
	R3 = B[P0 + 4] (Z);			/* 0xf9 */
	R6 = W[P0 + 6] (Z);			/* 0x64 */
	[P0 + 0x78] = R2;
	[P0 + 0x7c] = R3;
	[P0 + 0x80] = R6;
	P1 = P0;
	R2 = [P1++];				/* 0x12345678, P1 = 0x1004 */
	R3 = [P1--];				/* word at 0x1004 = 0x006400f9, P1 = 0x1000 */
	[P0 + 0x84] = R2;
	[P0 + 0x88] = R3;
	P2 = 4;
	R6 = [P1 ++ P2];			/* post-modify by P2: 0x12345678, P1 = 0x1004 */
	R6 = [P1];				/* 0x006400f9 */
	[P0 + 0x8c] = R6;
	[--SP] = R4;				/* push */
	R7 = [SP++];				/* pop: 0x12345678 */
	[P0 + 0x90] = R7;
	I0 = P0;				/* DAG */
	M0 = 4;
	R2 = [I0 ++ M0];			/* 0x12345678, I0 = 0x1004 */
	R3 = I0;
	[P0 + 0x94] = R3;
	/* ------------------------------------------------- branches */
	R2 = 0;
	CC = R0 == R0;
	IF CC JUMP 1f;				/* taken */
	R2 = 99;
1:	R2 += 1;				/* R2 = 1 */
	CC = R0 == R1;
	IF CC JUMP 2f;				/* not taken */
	R2 += 2;				/* R2 = 3 */
2:	[P0 + 0x98] = R2;
	/* ------------------------------------------------- call / return */
	CALL func;
	[P0 + 0x9c] = R0;			/* func returns 42 in R0 */
	/* ------------------------------------------------- hardware loop */
	R2 = 0;
	P2 = 5;
	LSETUP (ltop, lbot) LC0 = P2;
ltop:	R2 += 3;
lbot:	R2 += 1;				/* body runs 5 times: R2 = 20 */
	[P0 + 0xa0] = R2;
	R3 = LC0;				/* 0 after the loop */
	[P0 + 0xa4] = R3;
	/* ------------------------------------------------- LINK / UNLINK */
	LINK 8;
	R3 = FP;
	UNLINK;
	R6 = SP;
	[P0 + 0xa8] = R6;			/* SP restored: 0x1800 */
	JUMP done;
func:
	R0 = 42 (X);
	RTS;
	.align 4
done:
	JUMP done;
