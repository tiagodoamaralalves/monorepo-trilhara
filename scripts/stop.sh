#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
PID_FILE="$ROOT_DIR/scripts/.quarkus-dev.pid"

if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    echo "Quarkus encerrado."
  fi
  rm -f "$PID_FILE"
fi

resolver_docker() {
  local candidato
  for candidato in "${DOCKER_BIN:-}" /snap/bin/docker /usr/bin/docker /usr/local/bin/docker; do
    [[ -n "$candidato" ]] || continue
    [[ -x "$candidato" ]] || continue
    if "$candidato" info >/dev/null 2>&1; then
      DOCKER_BIN="$candidato"
      return 0
    fi
  done
  candidato="$(command -v docker 2>/dev/null || true)"
  if [[ -n "$candidato" ]] && "$candidato" info >/dev/null 2>&1; then
    DOCKER_BIN="$candidato"
    return 0
  fi
  echo "Erro: nenhum daemon Docker ativo foi encontrado." >&2
  return 1
}

if resolver_docker 2>/dev/null; then
  cd "$ROOT_DIR"
  "$DOCKER_BIN" compose down
  echo "Docker encerrado."
fi
