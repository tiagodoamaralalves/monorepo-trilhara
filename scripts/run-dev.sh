#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
PID_FILE="$ROOT_DIR/scripts/.quarkus-dev.pid"

eval "$(bash "$ROOT_DIR/scripts/env-gate.sh")"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Quarkus já está em execução."
  exit 0
fi

echo "=========================================================="
echo "  Iniciando Backend Quarkus em modo DEV (Hot-reload)      "
echo "  -> API: http://localhost:8080/api                       "
echo "  -> Swagger: http://localhost:8080/q/swagger-ui          "
echo ""
echo "  Para rodar o frontend unificado React em modo dev (Vite):"
echo "    cd frontend && npm run dev (Porta 5173)                "
echo "=========================================================="

rm -f "$PID_FILE"
cd "$ROOT_DIR/backend"
./mvnw quarkus:dev &
QUARKUS_PID=$!
echo "$QUARKUS_PID" > "$PID_FILE"

cleanup() {
  rm -f "$PID_FILE"
}

stop_quarkus() {
  kill "$QUARKUS_PID" 2>/dev/null || true
  wait "$QUARKUS_PID" 2>/dev/null || true
  exit 143
}

trap cleanup EXIT
trap stop_quarkus INT TERM
wait "$QUARKUS_PID"
