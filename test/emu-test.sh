#!/usr/bin/env bash
# Run the p-code emulation test.   test/emu-test.sh [out.txt]
set -euo pipefail
: "${GHIDRA_INSTALL_DIR:?set GHIDRA_INSTALL_DIR}"
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(readlink -m "${1:-${TMPDIR:-/tmp}/bfin-emu.txt}")
read -r _ DONE < <(grep '^done ' "$HERE/emu/emu_test.syms")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/bfin-emu.XXXXXX")
trap 'rm -rf "$PROJ"' EXIT
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$HERE/emu/emu_test.bin" \
	-loader BinaryLoader -processor Blackfin:LE:32:default -noanalysis -scriptPath "$HERE" \
	-postScript BfinEmuCheck.java "$DONE" "$OUT" -deleteProject >"$PROJ/log.txt" 2>&1 \
	|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
[[ -s $OUT ]] || { grep -E "ERROR|Exception" "$PROJ/log.txt" | head >&2; exit 1; }
cat "$OUT"
grep -q " 0 failed" "$OUT"
