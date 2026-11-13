#!/usr/bin/env bash
#
# reset-dev.sh — Recria do zero o banco de desenvolvimento/teste do Trilhara (D-07).
#
# Premissa normativa: os dados de desenvolvimento e teste são DESCARTÁVEIS. Este script
# existe para garantir que a tabela canônica de índices (IndicesCanonicos) seja aplicada
# sobre um banco vazio, sem resíduos de esquemas anteriores.
#
# Uso:
#   bash scripts/mongo/reset-dev.sh --db trilhara --yes
#   bash scripts/mongo/reset-dev.sh --db trilhara_test --yes
#
# Após executar, reinicie a aplicação: o MongoIndexInitializer recria as 20 coleções e
# todos os índices no startup (readiness fica DOWN enquanto houver divergência).
set -euo pipefail

RAIZ="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
BANCO="trilhara"
CONFIRMADO="nao"
CONTAINER="${MONGO_CONTAINER:-trilhara_mongo}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --db) BANCO="${2:-}"; shift 2 ;;
    --db=*) BANCO="${1#*=}"; shift ;;
    --yes) CONFIRMADO="sim"; shift ;;
    --container) CONTAINER="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "reset-dev: opção desconhecida '$1'" >&2; exit 2 ;;
  esac
done

case "$BANCO" in
  trilhara|trilhara_test) ;;
  *) echo "reset-dev: banco '$BANCO' não é permitido (use trilhara ou trilhara_test)." >&2; exit 2 ;;
esac

if [ "$CONFIRMADO" != "sim" ]; then
  echo "reset-dev: operação destrutiva (drop do banco '$BANCO'). Reexecute com --yes." >&2
  exit 1
fi

executar_mongosh() {
  local script="$1"
  if command -v mongosh >/dev/null 2>&1 && [ -z "${MONGODB_URL:-}" -o "${MONGODB_FORCAR_DOCKER:-false}" != "true" ] \
      && mongosh "${MONGODB_URL:-mongodb://localhost:27017}" --quiet --eval "$script" >/dev/null 2>&1; then
    return 0
  fi
  # No WSL o `docker` do PATH pode ser o docker.exe do Docker Desktop, que não
  # enxerga os containers deste compose. Preferimos um daemon que responda de fato.
  local candidato
  for candidato in "${DOCKER_BIN:-}" /snap/bin/docker /usr/bin/docker /usr/local/bin/docker; do
    [ -n "$candidato" ] || continue
    [ -x "$candidato" ] || continue
    "$candidato" info >/dev/null 2>&1 || continue
    "$candidato" exec "$CONTAINER" mongosh "$BANCO" --quiet --eval "$script" && return 0
  done
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    docker exec "$CONTAINER" mongosh "$BANCO" --quiet --eval "$script" && return 0
  fi
  return 1
}


echo "reset-dev: removendo o banco '$BANCO' (container '$CONTAINER')..."
if ! executar_mongosh 'db.dropDatabase()'; then
  echo "reset-dev: não foi possível conectar ao MongoDB (docker exec $CONTAINER ou mongosh local)." >&2
  exit 1
fi

echo "reset-dev: banco '$BANCO' removido. Reinicie a aplicação para recriar as 20 coleções e os índices canônicos."
echo "reset-dev: gate sugerido: bash scripts/verificar-legado.sh"
