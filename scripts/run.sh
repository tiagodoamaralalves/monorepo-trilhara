#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

echo "=========================================================="
echo "  Iniciando Trilhara (Backend + Caddy + MongoDB)...       "
echo "=========================================================="

docker compose up -d --build

echo ""
echo "=========================================================="
echo "  Trilhara no ar! Acesse os módulos pelas URLs abaixo:    "
echo "=========================================================="
echo ""
echo "  [Módulo de Estudos]:"
echo "    -> http://estudos.localhost/  (ou http://localhost/estudos/)"
echo ""
echo "  [Módulo de Tarefas]:"
echo "    -> http://tarefas.localhost/  (ou http://localhost/tarefas/)"
echo ""
echo "  [Módulo de Notas]:"
echo "    -> http://notas.localhost/    (ou http://localhost/notas/)"
echo ""
echo "  [Módulo de Pomodoro]:"
echo "    -> http://pomodoro.localhost/ (ou http://localhost/pomodoro/)"
echo ""
echo "  [API & Documentação Swagger]:"
echo "    -> http://localhost/q/swagger-ui"
echo "    -> http://localhost/api/trilhas"
echo "=========================================================="
