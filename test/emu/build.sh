#!/usr/bin/env bash
# Rebuild emu_test.bin from emu_test.s:  AS=bfin-elf-as OBJCOPY=bfin-elf-objcopy NM=bfin-elf-nm build.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
AS=${AS:-bfin-elf-as}; OBJCOPY=${OBJCOPY:-bfin-elf-objcopy}; NM=${NM:-bfin-elf-nm}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
"$AS" "$HERE/emu_test.s" -o "$T/t.o"
"$OBJCOPY" -O binary -j .text "$T/t.o" "$HERE/emu_test.bin"
truncate -s 0x1900 "$HERE/emu_test.bin"   # zeroed data (0x1000) and stack (to 0x1800)
"$NM" "$T/t.o" | awk '$3=="done"{print $3, $1}' > "$HERE/emu_test.syms"
cat "$HERE/emu_test.syms"
