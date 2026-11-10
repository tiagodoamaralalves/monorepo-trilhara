#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
PID_FILE="$ROOT_DIR/scripts/.quarkus-dev.pid"

if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    echo "Quarkus encerrado."
  fi
  rm -f "$PID_FILE"
fi

cd "$ROOT_DIR"
docker compose down
echo "Docker encerrado."
