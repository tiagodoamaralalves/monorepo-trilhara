#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
PID_FILE="$ROOT_DIR/scripts/.quarkus-dev.pid"

if [ -n "${JAVA_HOME-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  :
elif command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  JAVA_HOME=$(CDPATH= cd -- "$(dirname "$(command -v java)")/.." && pwd)
else
  JAVA_HOME=""
  for candidate in /usr/lib/jvm/* /home/wsl/.vscode-server/extensions/redhat.java-*/jre/*; do
    if [ -x "$candidate/bin/java" ] && [ -x "$candidate/bin/javac" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JDK não encontrado. Instale o Java 25 ou configure JAVA_HOME apontando para um JDK." >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
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
