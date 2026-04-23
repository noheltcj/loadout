#!/bin/sh

if [ "$1" = "__printenv__" ]; then
  shift
  for key in "$@"; do
    case "$key" in
      HOME) value="${HOME-}" ;;
      XDG_CONFIG_HOME) value="${XDG_CONFIG_HOME-}" ;;
      XDG_DATA_HOME) value="${XDG_DATA_HOME-}" ;;
      XDG_STATE_HOME) value="${XDG_STATE_HOME-}" ;;
      XDG_CACHE_HOME) value="${XDG_CACHE_HOME-}" ;;
      PATH) value="${PATH-}" ;;
      GIT_DIR) value="${GIT_DIR-}" ;;
      GIT_WORK_TREE) value="${GIT_WORK_TREE-}" ;;
      *) value="" ;;
    esac
    printf '%s=%s\n' "$key" "$value"
  done
  exit 0
fi

if [ -z "${LOADOUT_E2E_BINARY_PATH:-}" ]; then
  echo "Loadout E2E helper requires LOADOUT_E2E_BINARY_PATH" >&2
  exit 1
fi

exec "$LOADOUT_E2E_BINARY_PATH" "$@"
