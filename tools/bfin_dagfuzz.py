#!/usr/bin/env python3
"""Random circular-DAG updates outside the configured-buffer assumptions (Ghidra-Rare).

    python3 tools/bfin_dagfuzz.py [n]

bfin_dagtest.py checks directed cases inside a configured buffer.  This draws I, B, L and M
at random -- I outside [B, B+L), |M| larger than L, L not a multiple of 4, wraps across
0xffffffff -- and compares the I register Ghidra's p-code produces with the GNU Blackfin
simulator's for the modify forms (I += M, I -= M, I += 4, I -= 4, I += 2, I -= 2).  Needs
BFIN_BINUTILS and BFIN_RUN like bfin_semtest.py.
"""
import os
import random
import sys

from bfin_semtest import B, M32, REGION, run_ghidra, run_sim, state

# (word, index register, description); the DAG modify encodings of bfin-dis.c dagMODim/dagMODik
FORMS = []
for i in range(4):
    for m in range(4):
        FORMS.append((0x9e60 | (m << 2) | i, i, f"I{i} += M{m}", m))
        FORMS.append((0x9e70 | (m << 2) | i, i, f"I{i} -= M{m}", m))
    FORMS.append((0x9f60 | i, i, f"I{i} += 2", None))
    FORMS.append((0x9f64 | i, i, f"I{i} -= 2", None))
    FORMS.append((0x9f68 | i, i, f"I{i} += 4", None))
    FORMS.append((0x9f6c | i, i, f"I{i} -= 4", None))


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 2000
    os.makedirs(B, exist_ok=True)
    rng = random.Random(41)
    cases, meta = [], []
    for k in range(n):
        word, idx, text, m = rng.choice(FORMS)
        win = REGION + k * 0x100
        regs = state(rng, (word,), win)
        base = rng.getrandbits(32) & ~1
        length = rng.choice([0, 2, 6, 16, 18, 64, 0x100, rng.getrandbits(16) & ~1])
        regs[f"B{idx}"] = base
        regs[f"L{idx}"] = length
        regs[f"I{idx}"] = rng.choice([base + rng.randrange(0, max(length, 1)),
                                      base - rng.randrange(1, 64), base + length + rng.randrange(0, 64),
                                      rng.getrandbits(32)]) & M32 & ~1
        for j in range(4):
            regs[f"M{j}"] = rng.choice([2, -2, 4, -4, length, -length, 3 * length + 2,
                                        rng.getrandbits(32)]) & M32 & ~1
        cases.append(((word,), text, win, regs))
        meta.append((f"I{idx}", text))
    region = os.path.join(B, "dagfuzz-region.bin")
    with open(region, "wb") as f:
        f.write(bytes(0x100 * n))
    got = run_ghidra(cases, region)
    sim = run_sim(cases, 0, len(cases), region)
    ok = bad = rejected = 0
    for k, (reg, text) in enumerate(meta):
        g, s = got.get(k) if isinstance(got, dict) else got[k], sim.get(k)
        if s is None:
            rejected += 1
            continue
        if isinstance(g, str) or g[reg] != s[reg]:
            bad += 1
            print(f"{text}\tB={cases[k][3]['B' + reg[1]]:#x} L={cases[k][3]['L' + reg[1]]:#x} "
                  f"I={cases[k][3][reg]:#x}\tghidra={g if isinstance(g, str) else hex(g[reg])} sim={s[reg]:#x}")
        else:
            ok += 1
    print(f"match={ok} mismatch={bad} sim-rejected={rejected}", file=sys.stderr)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
