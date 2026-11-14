#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups/mongo}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date -u +"%Y%m%d_%H%M%S")"
BACKUP_FILE="$BACKUP_DIR/trilhara_mongo_${TIMESTAMP}.archive.gz"

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

mkdir -p "$BACKUP_DIR"
umask 077

echo "[$(date -Is)] Iniciando backup consistente do MongoDB (trilhara)..."

"$DOCKER_BIN" compose -f "$ROOT_DIR/docker-compose.yml" exec -T mongo \
  mongodump --db=trilhara --archive --gzip > "$BACKUP_FILE"

# Valida a integridade do arquivo gzip gerado e garante que não está vazio
if [[ ! -s "$BACKUP_FILE" ]] || ! gzip -t "$BACKUP_FILE"; then
  echo "[ERRO] Arquivo de backup inválido ou corrompido: $BACKUP_FILE" >&2
  rm -f "$BACKUP_FILE"
  exit 1
fi

SIZE_HR="$(du -h "$BACKUP_FILE" | cut -f1)"
echo "[$(date -Is)] Backup concluído com sucesso: $BACKUP_FILE ($SIZE_HR)"

# Retenção de backups antigos locais
find "$BACKUP_DIR" -name "trilhara_mongo_*.archive.gz" -type f -mtime +"$RETENTION_DAYS" -delete
echo "[$(date -Is)] Retenção de ${RETENTION_DAYS} dias aplicada com sucesso."
