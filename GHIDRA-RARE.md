# Ghidra-Rare notes: Analog Devices Blackfin

This fork of [geepot/ghidra-blackfin](https://github.com/geepot/ghidra-blackfin) (1.1.0, commit
`a36cc8f`, **GPL-3.0-or-later**, and so are all changes here) is part of
[Ghidra-Rare](https://github.com/aleph-void/Ghidra-Rare). It was checked to the same standard as
the project's own modules. `CREDITS.md` lists the authors and every change; this page has the
evidence. Prebuilt zips for Ghidra 12.1.3, 12.1.4 and 12.2 are on the fork's Releases page.

## Coverage

The classic Blackfin ISA (ADSP-BF53x/BF54x/BF561 and relatives), little endian,
`Blackfin:LE:32:default`. Not Blackfin+ (ADSP-BF70x): its extensions are not decoded (see
gaps).

## Results

Upstream already verified decoding against GNU objdump and semantics against the GNU
simulator; those harnesses (`tools/`) were rerun here with binutils 2.45 and gdb 17.2's
`bfin-elf-run`, and extended.

| Check | Result |
|---|---|
| Every 16-bit word (`tools/bfin_isatest.py 16`) | 65,488 of 65,536 identical to objdump; 48 LSETUP offset differences (objdump bug, below) |
| 387,072 sampled 32/64-bit encodings with random parallel slots (`bfin_isatest.py 32`) | 386,816 identical; 256 LSETUP offset differences |
| Semantics vs the GNU simulator, every 16-bit encoding (`bfin_semtest.py 16`) | 59,914 match, 0 mismatch (252 simulator-undefined) |
| Semantics vs the simulator, sampled DSP packets (`bfin_semtest.py dsp`) | 18,649 match, 0 mismatch (704 undefined, 22,688 the simulator rejects) |
| Byte-video instructions (BYTEOP1P/2P/3P/16P/16M, SAA), every legal pair/option, 3 states each | 456 of 456 match the simulator |
| Circular DAG, directed (`bfin_dagtest.py`) and random outside the buffer assumptions (`bfin_dagfuzz.py`) | 13 of 13 and 3,000 of 3,000 match the simulator |
| Emulator, hand-computed (`test/emu-test.sh`) | 28 of 28 (ALU, shifts, CC signed/unsigned, AC0/AZ, 16x16 multiply, saturating add, byte/half/word loads and stores, post-increment/decrement, P-register post-modify, push/pop, DAG post-modify, taken/not-taken branch, CALL/RTS, LSETUP hardware loop, LINK/UNLINK) |

Real binaries (fetched by `test/fetch-real-samples.sh`, never committed): the prebuilt FDPIC
target libraries of Analog Devices' Blackfin toolchain 2014R1-RC2 (uClibc 0.9.33.2, LGPL;
libgcc_s and libstdc++, GPL with the runtime exception), plus zlib 1.3.1 compiled locally with
GCC 14.2 (`bfin-elf-gcc -O2 -g`, a relocatable object with DWARF). Imported headless without
`-processor`; every function decompiled (30 s limit); the listing cross-checked with objdump
(`tools/bfin_crosscheck.py`).

| Binary | Functions | Decompile failures | Instructions agreeing with objdump | Code coverage | Error bookmarks |
|---|---|---|---|---|---|
| libuClibc-0.9.33.2.so | 1,263 | 0 (1 function ends in a deliberate illegal opcode: `abort`) | 72,255 of 72,256 | 94.4% | 2 (that illegal opcode) |
| libstdc++.so.6.0.10 | 1,686 | 0 | 88,792 of 88,792 | 97.4% | 0 |
| libm-0.9.33.2.so | 148 | 0 | 20,895 of 20,895 | 99.0% | 0 |
| libgcc_s.so.1 | 156 | 0 | 19,217 of 19,217 | 93.7% | 0 |
| libpthread-0.9.33.2.so | 192 | 0 | 9,420 of 9,420 | 96.6% | 0 |
| ld-uClibc-0.9.33.2.so | 86 | 0 | 7,664 of 7,664 | 98.4% | 0 |
| zlib 1.3.1, GCC 14.2 `-O2 -g` object | 79 | 0 | 13,942 + 204 at relocation sites (objdump shows the unrelocated bytes) | 99.8% | 0 |

Function counts exclude PLT thunks. The one text difference in libuClibc is the LSETUP quirk.
Warning bookmarks are R_BFIN_FUNCDESC relocations (below) and, in libstdc++, DWARF line
entries for crtreloc code at address 0.

## What testing found and fixed

Before this fork, every relocation in a Blackfin shared library or object file was left
unapplied (no relocation handler), so pointers in data and GOT entries were raw offsets;
PLT stubs decompiled into jumps into the EXTERNAL block (22 "bad data" functions in
libpthread alone); switch tables in position-independent code were not found (P3, the GOT
pointer, was unknown), leaving 13% of libuClibc's code undisassembled (now 6%); arguments beyond R2 and
64-bit values had no storage in the calling convention; DWARF had no register map; the
byte-video instructions were opaque; and circular addressing was only right inside a
configured buffer. The full list is in `CREDITS.md`.

## Remaining gaps, and why

- **Opaque operations**: `DISALGNEXCPT`, `IDLE`, `RAISE`, `EXCPT`, `EMUEXCPT`, cache control
  (`PREFETCH`, `FLUSH`, `FLUSHINV`, `IFLUSH`) and the simulator's pseudo-instructions (`DBG`,
  `OUTC`, `HLT`, `ABORT`, `DBGA`). They act on state Ghidra does not model: the exception and
  interrupt machinery (vectoring `EXCPT` through EVT3 would also turn every Linux system call
  into a computed jump), the caches, and the simulator's console. Their register side effects
  (post-increment of the cache pointer) are modelled.
- **R_BFIN_FUNCDESC** relocations (the address of a canonical function descriptor) are left
  with a warning: the FDPIC dynamic linker allocates the descriptor at load time, so there is
  no static value. `FUNCDESC_VALUE` descriptors are filled.
- **Switch tables whose GOT pointer was spilled to the stack** in very large functions (for
  example uClibc's regex compiler) are still not recovered: the decompiler does not track the
  reload: 72 indirect jumps in libuClibc stay unresolved.
- **Hardware itself**: the circular-addressing and byte-video semantics match the GNU
  simulator; no Blackfin board was available to check the simulator.
- **Blackfin+ (ADSP-BF70x)**, requested in issue #2 of sualk/ghidra-blackfin: binutils does not
  decode its extensions, the only implementations (hekar-lab, NoahLouu) have no licence, and no
  free BF70x binaries were found, so there is no oracle and nothing to test.
- Raw (non-LDR) firmware, issue #3 there: supported as a raw import with
  `Blackfin:LE:32:default` and the load address, as upstream's README says.

## Reference-tool disagreements

- objdump sign-extends the LSETUP start and end offsets, which are unsigned (pcrel5m2,
  lppcrel11m2); upstream documented this and the simulator agrees with Ghidra.
- The simulator writes the same register from two parallel slots in an order the hardware
  leaves undefined; the semantic test now treats a pointer or index register modified by two
  slots (for example `I2 += M2 (BREV) || W[I2++] = R1.L`) as undefined, as it already did for
  data registers.

## How to test

```sh
GHIDRA_INSTALL_DIR=/opt/ghidra_12.1.4_PUBLIC BFIN_OBJDUMP=<objdump with bfin> \
BFIN_BINUTILS=<dir with bfin-elf-as/ld> BFIN_RUN=<bfin-elf-run> \
SAMPLES=<dir from test/fetch-real-samples.sh> test/run-tests.sh /tmp/bfin-test
```

`BFIN_BINUTILS`/`BFIN_RUN` (semantic tests) and `SAMPLES` are optional. The module directory
is linked into the Ghidra install as `Ghidra/Processors/Blackfin` if nothing is there.
