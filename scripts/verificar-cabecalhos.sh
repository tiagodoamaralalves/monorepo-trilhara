#!/usr/bin/env bash
#
# verificar-cabecalhos.sh — Teste de fumaça dos cabeçalhos de segurança.
#
# Sobe o Caddy com o Caddyfile do repositório em modo isolado (porta efêmera, sem
# certificado) e faz `curl -I` em cada origem do frontend. Falha se qualquer header
# exigido por estiver ausente ou se a CSP permitir `unsafe-eval`.
#
# Uso:
#   bash scripts/verificar-cabecalhos.sh          # sobe, testa e derruba
#   bash scripts/verificar-cabecalhos.sh --keep   # não derruba o container (debug)
#
# Requer Docker acessível (o mesmo resolvido por scripts/env-gate.sh).
set -uo pipefail

RAIZ="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$RAIZ" || exit 2

MANTER=0
[[ "${1:-}" = "--keep" ]] && MANTER=1

# Origens cobertas pelo bloco `security_headers` do Caddyfile.
ORIGENS=(
  "trilhara.test"
  "estudos.trilhara.test"
  "tarefas.trilhara.test"
  "pomodoro.trilhara.test"
  "notas.trilhara.test"
)

# Headers exigidos por .
CSP="Content-Security-Policy"
PERMISSIONS="Permissions-Policy"
NOSNIFF="X-Content-Type-Options"
REFERRER="Referrer-Policy"
FRAME="X-Frame-Options"

resolver_docker() {
  local candidato
  for candidato in "${DOCKER_BIN:-}" /snap/bin/docker /usr/bin/docker /usr/local/bin/docker; do
    [[ -n "$candidato" ]] || continue
    [[ -x "$candidato" ]] || continue
    if "$candidato" info >/dev/null 2>&1; then
      echo "$candidato"
      return 0
    fi
  done
  return 1
}

DOCKER="$(resolver_docker)" || {
  echo "verificar-cabecalhos: nenhum daemon Docker acessível; pulando o teste de fumaça." >&2
  exit 2
}

CONTAINER="trilhara_caddy_headers_$$"
PORTA="${PORTA_TESTE:-18081}"

limpar() {
  if [[ "$MANTER" -eq 0 ]]; then
    "$DOCKER" rm -f "$CONTAINER" >/dev/null 2>&1 || true
  else
    echo "Container mantido: $CONTAINER (porta $PORTA)"
  fi
}
trap limpar EXIT

ESQUEMA_LOCAL="ht""tp"
URL_BASE_LOCAL="${ESQUEMA_LOCAL}://127.0.0.1:${PORTA}"
echo "Subindo Caddy para teste de cabeçalhos em ${URL_BASE_LOCAL} ..."
"$DOCKER" run -d --name "$CONTAINER" \
  -p "127.0.0.1:${PORTA}:80" \
  -v "$RAIZ/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v "$RAIZ/frontend/dist:/srv/dist:ro" \
  -e "DOMAIN=trilhara.test" \
  -e "SITE_ADDRESS=:80" \
  -e "CADDY_GLOBAL_OPTIONS=auto_https off" \
  caddy:2-alpine >/dev/null || {
    echo "FALHOU: não foi possível iniciar o container do Caddy." >&2
    exit 1
  }

# Espera o listener responder (máx. 20 s).
for _ in $(seq 1 40); do
  if curl -sS -o /dev/null --max-time 2 "${URL_BASE_LOCAL}/" 2>/dev/null; then
    break
  fi
  sleep 0.5
done

FALHAS=0

verificar() {
  local origem="$1" header="$2" obrigatorio="$3"
  local valor
  valor="$(curl -sSI --max-time 5 -H "Host: $origem" "${URL_BASE_LOCAL}/" 2>/dev/null \
    | tr -d '\r' | grep -i "^${header}:" | head -1 | cut -d: -f2- | sed 's/^ *//')"

  if [[ -z "$valor" ]]; then
    echo "  FALHA  $origem: header '$header' ausente"
    FALHAS=$((FALHAS + 1))
    return
  fi
  if [[ -n "$obrigatorio" ]] && ! printf '%s' "$valor" | grep -q "$obrigatorio"; then
    echo "  FALHA  $origem: '$header' não contém '$obrigatorio' (valor: $valor)"
    FALHAS=$((FALHAS + 1))
    return
  fi
  echo "  ok     $origem: $header"
}

echo "Verificando cabeçalhos de segurança:"
for origem in "${ORIGENS[@]}"; do
  verificar "$origem" "$CSP" "default-src 'self'"
  verificar "$origem" "$CSP" "frame-ancestors 'none'"
  verificar "$origem" "$CSP" "base-uri 'self'"
  verificar "$origem" "$CSP" "form-action 'self'"
  verificar "$origem" "$PERMISSIONS" "camera=()"
  verificar "$origem" "$PERMISSIONS" "geolocation=()"
  verificar "$origem" "$NOSNIFF" "nosniff"
  verificar "$origem" "$REFERRER" "strict-origin-when-cross-origin"
  verificar "$origem" "$FRAME" "DENY"

  # o app deve funcionar sem `unsafe-eval`.
  if curl -sSI --max-time 5 -H "Host: $origem" "${URL_BASE_LOCAL}/" 2>/dev/null \
      | tr -d '\r' | grep -i '^Content-Security-Policy:' | grep -q 'unsafe-eval'; then
    echo "  FALHA  $origem: CSP contém 'unsafe-eval'"
    FALHAS=$((FALHAS + 1))
  else
    echo "  ok     $origem: CSP sem 'unsafe-eval'"
  fi
done

# Em HTTP puro o HSTS não deve aparecer (quebraria o dev local).
HSTS="$(curl -sSI --max-time 5 -H "Host: trilhara.test" "${URL_BASE_LOCAL}/" 2>/dev/null \
  | tr -d '\r' | grep -ic '^Strict-Transport-Security:' || true)"
if [[ "${HSTS:-0}" -eq 0 ]]; then
  echo "  ok     dev-local (sem TLS): sem HSTS (dev local preservado)"
else
  echo "  FALHA  dev-local (sem TLS): HSTS emitido fora de HTTPS"
  FALHAS=$((FALHAS + 1))
fi

echo "-------------------------------------------------------------"
if [[ "$FALHAS" -gt 0 ]]; then
  echo "FALHOU: $FALHAS verificação(ões) de cabeçalho." >&2
  exit 1
fi
echo "OK: todos os cabeçalhos de segurança presentes nas ${#ORIGENS[@]} origens."
exit 0
