#!/usr/bin/env bash
# Runs the finsim/v1 participant conformance suite against a running bank.
# Usage: scripts/conformance.sh [BASE_URL]      (default http://localhost:9001)
set -euo pipefail

BASE="${1:-http://localhost:9001}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$(date +%s)"

if ! command -v hurl >/dev/null 2>&1; then
  echo "Hurl is not installed. Install it from https://hurl.dev/docs/installation.html" >&2
  echo "  (brew install hurl | cargo install hurl | scoop install hurl)" >&2
  exit 2
fi

if ! curl -fsS "$BASE/finsim/v1/institution/health" >/dev/null 2>&1; then
  echo "No bank answering at $BASE. Start your bank first, or pass its URL:" >&2
  echo "  scripts/conformance.sh http://localhost:9002" >&2
  exit 2
fi

echo "Running conformance suite against $BASE (run id $RUN)"
hurl --test --variable "base=$BASE" --variable "run=$RUN" "$ROOT"/contract/conformance/*.hurl
