#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

echo "Reconstruindo serviços app (Quarkus) e caddy (Frontend React Unificado)..."
docker compose up -d --build --no-deps app caddy

echo ""
echo "Serviços atualizados com sucesso!"
echo "  -> Estudos:  http://estudos.localhost/  (ou http://localhost/estudos/)"
echo "  -> Tarefas:  http://tarefas.localhost/  (ou http://localhost/tarefas/)"
echo "  -> Notas:    http://notas.localhost/    (ou http://localhost/notas/)"
echo "  -> Pomodoro: http://pomodoro.localhost/ (ou http://localhost/pomodoro/)"
