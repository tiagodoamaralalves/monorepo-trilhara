#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

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

resolver_docker

echo "=========================================================="
echo "  Iniciando Trilhara (Backend + Caddy + MongoDB)...       "
echo "=========================================================="

"$DOCKER_BIN" compose up -d --build

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
