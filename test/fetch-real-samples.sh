#!/usr/bin/env bash
# Fetch the third-party Blackfin samples used by test/real-binaries.sh into <dir> (default
# ./samples), by pinned URL and SHA-256. Nothing here is committed.
#
#   Analog Devices Blackfin toolchain 2014R1-RC2 (uClibc, i386 host): its prebuilt target
#   runtime (bfin-linux-uclibc FDPIC shared libraries): uClibc 0.9.33.2 (LGPL-2.1),
#   libgcc_s, libstdc++ (GPL-3.0 with the GCC runtime library exception).
set -euo pipefail
OUT=$(readlink -m "${1:-samples}")
mkdir -p "$OUT/dl"
URL=https://downloads.sourceforge.net/project/adi-toolchain/2014R1/2014R1-RC2/i386/blackfin-toolchain-uclibc-default-2014R1-RC2.i386.tar.bz2
SHA=56bc8df22e68a5a476f44fe284d50fbcb3316da0de8a54259f151c858d27b159
F=$OUT/dl/blackfin-toolchain-uclibc-default-2014R1-RC2.i386.tar.bz2
if ! { [[ -f $F ]] && echo "$SHA  $F" | sha256sum -c --quiet 2>/dev/null; }; then
	curl -fsSL -o "$F" "$URL"
	echo "$SHA  $F" | sha256sum -c --quiet
fi
L=./opt/uClinux/bfin-linux-uclibc/bfin-linux-uclibc/runtime/lib
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
tar -C "$W" -xjf "$F" --wildcards "$L/*.so*"
for f in libuClibc-0.9.33.2.so ld-uClibc-0.9.33.2.so libm-0.9.33.2.so libpthread-0.9.33.2.so \
	libgcc_s.so.1 libstdc++.so.6.0.10; do
	cp "$W/$L/$f" "$OUT/$f"
done
ls -la "$OUT"
