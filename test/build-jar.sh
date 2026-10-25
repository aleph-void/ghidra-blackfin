#!/usr/bin/env bash
# Compile src/main/java into lib/Blackfin.jar for a Ghidra install that has this module linked
# under Ghidra/Processors/C6000 (the release route is gradle buildExtension).
#   GHIDRA_INSTALL_DIR=... [JAVA_HOME=<JDK with javac>] test/build-jar.sh
set -euo pipefail
: "${GHIDRA_INSTALL_DIR:?set GHIDRA_INSTALL_DIR}"
MOD=$(cd "$(dirname "$0")/.." && pwd)
G=$GHIDRA_INSTALL_DIR/Ghidra
CP=$(find "$G/Framework" "$G/Features" -name '*.jar' ! -name '*-src*' | tr '\n' ':')
JAVAC=javac; JAR=jar
[[ -n ${JAVA_HOME:-} ]] && JAVAC=$JAVA_HOME/bin/javac && JAR=$JAVA_HOME/bin/jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/bfin-jar.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
"$JAVAC" -nowarn --release 21 -cp "$CP" -d "$OUT" $(find "$MOD/src/main/java" -name '*.java')
mkdir -p "$MOD/lib"
"$JAR" cf "$MOD/lib/Blackfin.jar" -C "$OUT" .
echo "built $MOD/lib/Blackfin.jar"
