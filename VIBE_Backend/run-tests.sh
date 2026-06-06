#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# VIBE backend test + coverage runner.
#
#   ./run-tests.sh                 # run tests for every service that has tests
#   ./run-tests.sh auth-service    # run tests for one service
#
# Produces a JaCoCo HTML report per service at:
#   <service>/target/site/jacoco/index.html
# and prints a coverage summary table.
# ─────────────────────────────────────────────────────────────────────────────
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-17.0.19+10/Contents/Home}"
export PATH="$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH"

# Services that currently have a src/test directory (extend as you add tests).
if [ $# -ge 1 ]; then
  SERVICES="$*"
else
  SERVICES=""
  for d in "$ROOT"/*/; do
    s=$(basename "$d")
    [ -d "$d/src/test" ] && SERVICES="$SERVICES $s"
  done
fi
[ -z "${SERVICES// }" ] && { echo "No services with tests found."; exit 0; }

echo "==> Running tests for:${SERVICES}"
for s in $SERVICES; do
  echo ""
  echo "════════════════════════════════════════════════════════════"
  echo "  $s"
  echo "════════════════════════════════════════════════════════════"
  ( cd "$ROOT/$s" && mvn -o -q test ) || echo "  ⚠️  tests failed in $s"
done

echo ""
echo "==> Coverage summary (instruction %):"
for s in $SERVICES; do
  CSV="$ROOT/$s/target/site/jacoco/jacoco.csv"
  [ -f "$CSV" ] && python3 - "$CSV" "$s" <<'PY'
import csv,sys
c=m=0
for r in csv.DictReader(open(sys.argv[1])):
    c+=int(r['INSTRUCTION_COVERED']); m+=int(r['INSTRUCTION_MISSED'])
t=c+m
print(f"   {sys.argv[2]:<22} {100*c/t:5.1f}%   ({c}/{t})  →  {sys.argv[1].replace('jacoco.csv','index.html')}")
PY
done
