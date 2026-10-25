#!/usr/bin/env bash
# Real-binary check over the fetched samples (test/fetch-real-samples.sh).
#   GHIDRA_INSTALL_DIR=... BFIN_OBJDUMP=... [BFIN_OBJCOPY=... BFIN_READELF=...] \
#   test/real-binaries.sh <samples dir> <outdir> [timeout]
set -euo pipefail
S=$(readlink -f "$1"); OUTD=$(readlink -m "$2"); TMO=${3:-30}
HERE=$(cd "$(dirname "$0")" && pwd)
for f in "$S"/*.so*; do
	"$HERE/real-check.sh" "$f" "$OUTD" "$TMO" | grep -v 'Low-level Error'
done
