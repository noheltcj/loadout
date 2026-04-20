#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/qodana.sh [qodana args...]

Examples:
  scripts/qodana.sh scan
  scripts/qodana.sh scan --cleanup

Notes:
  - Requires the 1Password CLI (`op`) to be installed and authenticated.
  - Requires the Qodana CLI (`qodana`) to be installed.
  - If no arguments are provided, the script runs `qodana scan`.
EOF
}

if [[ "${1-}" == "-h" || "${1-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v op >/dev/null 2>&1; then
  echo "error: 1Password CLI ('op') is not installed or not on PATH" >&2
  exit 1
fi

if ! command -v qodana >/dev/null 2>&1; then
  echo "error: Qodana CLI ('qodana') is not installed or not on PATH" >&2
  exit 1
fi

qodana_token_op_ref="${QODANA_TOKEN_OP_REF:-op://Project - Loadout/Loadout/qodana_token}"

qodana_token="$(op read "${qodana_token_op_ref}")"

if [[ -z "${qodana_token}" ]]; then
  echo "error: 1Password returned an empty QODANA token" >&2
  exit 1
fi

if [[ "$#" -eq 0 ]]; then
  set -- scan
fi

QODANA_TOKEN="${qodana_token}" exec qodana "$@"
