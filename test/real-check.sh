#!/usr/bin/env bash
# Import one real Blackfin ELF headless (no -processor: the ELF opinion picks the language),
# auto-analyse, decompile every function and cross-check the listing against GNU objdump
# with tools/bfin_crosscheck.py over every executable section.
#
#   GHIDRA_INSTALL_DIR=... BFIN_OBJDUMP=<objdump with bfin> test/real-check.sh <file.elf> <outdir> [timeout]
set -euo pipefail
ELF=$(readlink -f "$1"); OUTD=$(readlink -m "$2"); TMO=${3:-30}
: "${GHIDRA_INSTALL_DIR:?set GHIDRA_INSTALL_DIR}"
: "${BFIN_OBJDUMP:?set BFIN_OBJDUMP to an objdump with Blackfin support}"
HERE=$(cd "$(dirname "$0")" && pwd)
N=$(basename "$ELF"); mkdir -p "$OUTD"; P="$OUTD/$N"
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/bfin-real.XXXXXX")
trap 'rm -rf "$PROJ"' EXIT
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$ELF" \
	-loader ElfLoader -loader-imagebase 0 \
	-scriptPath "$HERE" -postScript BfinRealCheck.java "$P" "$TMO" \
	-deleteProject >"$PROJ/log.txt" 2>&1 || { tail -40 "$PROJ/log.txt" >&2; exit 1; }
grep -E "^(ERROR|WARN)|Exception" "$PROJ/log.txt" | grep -v JAVA_HOME | head -50 > "$P.log" || true
# raw images of the executable sections at their link addresses
args=()
while read -r name addr; do
	"${BFIN_OBJCOPY:-objcopy}" -O binary -j "$name" "$ELF" "$PROJ/$name.bin" 2>/dev/null || continue
	args+=("$PROJ/$name.bin" "0x$addr")
done < <("${BFIN_READELF:-readelf}" -SW "$ELF" | awk '/ AX /{for(i=1;i<=NF;i++) if($i ~ /^\./) {print $i, $(i+2); break}}')
python3 "$HERE/../tools/bfin_crosscheck.py" "$P.dis" "${args[@]}" > "$P.cross" 2> "$P.cmp"
echo "#### $N"
grep -v "^bookmark \|^baddata \|^decompile failed" "$P.summary"
cat "$P.cmp"
