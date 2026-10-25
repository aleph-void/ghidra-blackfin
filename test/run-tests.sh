#!/usr/bin/env bash
# Ghidra-Rare test harness for the Blackfin module (see GHIDRA-RARE.md). Runs upstream's
# harnesses (tools/) plus the Ghidra-Rare additions.
#
#   GHIDRA_INSTALL_DIR=<Ghidra 12.x> BFIN_OBJDUMP=<objdump with bfin> \
#   [BFIN_BINUTILS=<dir with bfin-elf-as/ld> BFIN_RUN=<bfin-elf-run>] [SAMPLES=<dir>] \
#   [JAVA_HOME=<JDK>] test/run-tests.sh [workdir]
#
# 1. compiles the SLEIGH spec and the Java (relocation handler, FDPIC GOT analyzer)
# 2. decode: every 16-bit word and the sampled 32/64-bit corpus against objdump
#    (tools/bfin_isatest.py 16 / 32)
# 3. with the GNU simulator: semantics (tools/bfin_semtest.py 16 / dsp), directed and random
#    circular-DAG checks (tools/bfin_dagtest.py, tools/bfin_dagfuzz.py)
# 4. hand-computed emulator test (test/emu-test.sh, 28 checks)
# 5. with SAMPLES: real binaries (test/real-binaries.sh)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(dirname "$HERE")
WORK=$(readlink -m "${1:-${TMPDIR:-/tmp}/bfin-test}")
: "${GHIDRA_INSTALL_DIR:?set GHIDRA_INSTALL_DIR}"
: "${BFIN_OBJDUMP:?set BFIN_OBJDUMP}"
export GHIDRA=$GHIDRA_INSTALL_DIR
mkdir -p "$WORK"; R=$WORK/report.txt; : > "$R"
"$GHIDRA_INSTALL_DIR/support/sleigh" "$MOD/data/languages/blackfin.slaspec" > "$WORK/sleigh.log" 2>&1 \
	|| { cat "$WORK/sleigh.log"; exit 1; }
[[ -e $GHIDRA_INSTALL_DIR/Ghidra/Processors/Blackfin ]] || ln -sfn "$MOD" "$GHIDRA_INSTALL_DIR/Ghidra/Processors/Blackfin"
"$HERE/build-jar.sh"
for m in 16 32; do
	python3 "$MOD/tools/bfin_isatest.py" $m 2>&1 | grep -E '^[a-z-]+=' | sed "s/^/isatest $m: /" | tee -a "$R"
done
if [[ -n ${BFIN_RUN:-} ]]; then
	for m in 16 dsp; do
		python3 "$MOD/tools/bfin_semtest.py" $m 2>&1 | grep -E '^[a-z-]+=' | sed "s/^/semtest $m: /" | tee -a "$R"
	done
	(cd "$MOD/tools" && python3 bfin_dagtest.py && python3 bfin_dagfuzz.py 3000) 2>&1 | tail -2 | tee -a "$R"
fi
"$HERE/emu-test.sh" "$WORK/emu.txt" | tail -1 | tee -a "$R"
[[ -n ${SAMPLES:-} ]] && "$HERE/real-binaries.sh" "$SAMPLES" "$WORK/real" 30 | tee -a "$R"
echo "report: $R"
