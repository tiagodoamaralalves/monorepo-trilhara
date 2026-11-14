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

echo "Reconstruindo serviços app (Quarkus) e caddy (Frontend React Unificado)..."
"$DOCKER_BIN" compose up -d --build --no-deps app caddy

echo ""
echo "Serviços atualizados com sucesso!"
echo "  -> Estudos:  http://estudos.localhost/  (ou http://localhost/estudos/)"
echo "  -> Tarefas:  http://tarefas.localhost/  (ou http://localhost/tarefas/)"
echo "  -> Notas:    http://notas.localhost/    (ou http://localhost/notas/)"
echo "  -> Pomodoro: http://pomodoro.localhost/ (ou http://localhost/pomodoro/)"
