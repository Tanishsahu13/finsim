#!/usr/bin/env bash
# Local verification for FinSim contributors.
#
# Runs the same three checks CI enforces on every pull request:
#   1. Spotless formatting check (./gradlew spotlessCheck)
#   2. Gradle tests (./gradlew test)
#   3. DCO sign-off on the latest commit (git commit -s)
#
# Usage (from anywhere inside the repository):
#   bash scripts/verify.sh
#
# The script stops at the first failing check and exits non-zero.
# Gradle output is shown as-is so failures can be investigated directly.

set -u

# Always run from the repository root, wherever the script is invoked from.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "== [1/3] Formatting check (./gradlew spotlessCheck) =="
if ! ./gradlew spotlessCheck; then
  echo "Formatting check failed." >&2
  echo "Run ./gradlew spotlessApply to fix formatting issues." >&2
  exit 1
fi
echo "Formatting check passed."

echo "== [2/3] Tests (./gradlew test) =="
if ! ./gradlew test; then
  echo "Tests failed." >&2
  echo "Inspect the Gradle test output above to find the failing test." >&2
  exit 1
fi
echo "Tests passed."

echo "== [3/3] DCO sign-off check =="
if ! git log -1 --format=%B | grep -q '^Signed-off-by: '; then
  echo "DCO sign-off is missing." >&2
  echo "Use git commit --amend -s to add a sign-off to your latest commit." >&2
  exit 1
fi
echo "DCO sign-off present."

echo "All local verification checks passed."
