# Credits

## Upstream

- Project: ghidra-blackfin, <https://github.com/geepot/ghidra-blackfin>
- Commit imported: `a36cc8fbf9966e1a4c0e221e63ec04ff94c0aaa7` ("Prepare 1.1.0 release notes",
  2026-09-25)
- Licence: **GPL-3.0-or-later** (`LICENSE.txt`, `NOTICE.md`, unchanged). The module follows
  GPL-3.0-or-later references (binutils `bfin-dis.c`, gdb `bfin-sim.c`) closely, so it stays
  under that licence, and so do the Ghidra-Rare changes and additions in this fork.
- Author: geepot (@geepot, all commits in the imported history). `NOTICE.md` also credits
  `0bs3n/arch-blackfin` (GPL-2.0-or-later) for field masks consulted by its first version.

## Alternatives looked at

- sualk/ghidra-blackfin (594 constructors, 294 unimplemented; no licence): not used. Its
  issues (#2 BF70x/Blackfin+ support, #3 non-LDR firmware) are discussed in GHIDRA-RARE.md.
- hekar-lab/Ghidra-BlackFinPlus and NoahLouu/ghidra-blackfin-plus (Blackfin+): no licence;
  not used.

## Reference tools used for testing (not shipped)

- GNU binutils 2.45 (`objdump -m bfin`, `bfin-elf-as`, `bfin-elf-ld`) and the GNU Blackfin
  simulator from gdb 17.2 (`bfin-elf-run`), GPL-3.0: decode and semantic oracles.
- GCC 14.2 (`bfin-elf-gcc`, built locally) for the zlib DWARF/relocation check.

## What Ghidra-Rare changed

See `GHIDRA-RARE.md` for the test results behind each item.

1. The byte-video instructions `BYTEOP1P`, `BYTEOP2P`, `BYTEOP3P`, `BYTEOP16P`, `BYTEOP16M`
   and `SAA` were opaque user operations; they now have p-code (byte alignment through I0/I1,
   rounding/truncation, clamping, saturating accumulation), checked against the GNU simulator
   on every legal register-pair/option combination.
2. Circular DAG post-modify (`I += M`, `I -= M`, `I += 2/4`, `[I++]`, `[I ++ M]`, ...) now
   follows the hardware's carry-based wrap as the GNU simulator models it, so it also holds
   outside the configured-buffer assumptions (index outside B/L, |M| > L, buffers crossing
   0xffffffff). Upstream's model wrapped once relative to B.
3. New ELF relocation handler (`Blackfin_ElfRelocationHandler`): BYTE4/BYTE2/BYTE data,
   FDPIC `FUNCDESC_VALUE`, PC-relative jumps/calls and 16-bit immediates for relocatable
   objects. Before it, every relocation in a Blackfin shared object or object file was left
   unapplied with an error bookmark.
4. New `BlackfinFdpicGotAnalyzer`: in FDPIC programs P3 holds the module's GOT address at
   every function; recording it lets the decompiler resolve GOT loads, switch tables and data
   references (uClibc: code coverage 87% -> 94%).
5. `blackfin.dwarf` DWARF register mapping (GCC numbering), referenced from the ldefs.
6. cspec: arguments beyond R0-R2 on the stack at SP+12, 64-bit arguments and results in
   register pairs (R1:R0), RETS as the return address.
7. Test harness: Ghidra project directories moved to a temporary directory (Ghidra rejects
   project paths containing a `.`-prefixed component such as `~/.cache`); the semantic test
   no longer skips the byte-video operations and treats packets that modify one pointer or
   index register in two slots as undefined, like it already did for data registers.
