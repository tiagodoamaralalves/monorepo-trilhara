#!/usr/bin/env bash
#
# env-gate.sh — Exporta as variáveis de ambiente necessárias para executar o
# gate comum de revisão (R0..R13) de forma reprodutível nesta máquina/CI.
#
# Uso:
#   eval "$(bash scripts/env-gate.sh)"          # exporta no shell atual
#   bash scripts/env-gate.sh --exec -- ./mvnw verify   # executa um comando
#
# Variáveis:
#   JAVA_HOME      JDK 25 (o backend exige release 25). O VS Code aponta
#                  JAVA_HOME para um JRE sem javac, o que quebra o mvnw.
#   MONGODB_URL    O perfil %test de application.properties fixa 172.22.0.2,
#                  IP que não existe no host atual. O container trilhara_mongo
#                  não publica porta; usamos o IP da rede do compose.
#   RESEND_API_KEY trilhara.email.resend-api-key não possui default; sem valor
#                  o Quarkus aborta no startup (ConfigRecorder) e a suíte
#                  backend pulam 106 testes + 1 erro. Valor dummy só para teste.
#
set -uo pipefail

RAIZ="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"

# --- JAVA_HOME ------------------------------------------------------------
JAVA_HOME_EFETIVO="${JAVA_HOME:-}"
if [ -z "$JAVA_HOME_EFETIVO" ] || [ ! -x "$JAVA_HOME_EFETIVO/bin/javac" ]; then
  for candidato in \
    /usr/lib/jvm/java-25-openjdk-amd64 \
    /usr/lib/jvm/temurin-25-jdk-amd64 \
    /usr/lib/jvm/*25* \
    /usr/lib/jvm/*; do
    if [ -x "$candidato/bin/javac" ] && [ -x "$candidato/bin/java" ]; then
      JAVA_HOME_EFETIVO="$candidato"
      break
    fi
  done
fi
if [ -z "$JAVA_HOME_EFETIVO" ] || [ ! -x "$JAVA_HOME_EFETIVO/bin/javac" ]; then
  echo "env-gate: nenhum JDK com javac encontrado. Defina JAVA_HOME para um JDK 25." >&2
  exit 1
fi

# --- MONGODB_URL ----------------------------------------------------------
MONGODB_URL_EFETIVO="${MONGODB_URL:-}"
if [ -z "$MONGODB_URL_EFETIVO" ]; then
  # 1) porta publicada no host, se existir
  if (exec 3<>/dev/tcp/127.0.0.1/27017) 2>/dev/null; then
    MONGODB_URL_EFETIVO="mongodb://localhost:27017"
  # 2) IP do container trilhara_mongo na rede do compose
  elif command -v docker >/dev/null 2>&1; then
    IP_MONGO="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' trilhara_mongo 2>/dev/null | awk '{print $1}')"
    if [ -n "$IP_MONGO" ] && (exec 3<>"/dev/tcp/$IP_MONGO/27017") 2>/dev/null; then
      MONGODB_URL_EFETIVO="mongodb://$IP_MONGO:27017"
    fi
  fi
fi
if [ -z "$MONGODB_URL_EFETIVO" ]; then
  echo "env-gate: MongoDB inalcançável. Suba 'docker compose up -d mongo' ou exporte MONGODB_URL." >&2
  exit 1
fi

# --- RESEND_API_KEY -------------------------------------------------------
# Valor dummy: a suíte nunca deve fazer chamadas reais ao provedor de e-mail.
# Um teste que dependa do provedor real deve ser tratado como falha de teste.
RESEND_API_KEY_EFETIVO="${RESEND_API_KEY:-chave-dummy-para-gate-local}"

# --- saída ----------------------------------------------------------------
if [ "${1:-}" = "--exec" ]; then
  shift
  [ "${1:-}" = "--" ] && shift
  if [ "$#" -eq 0 ]; then
    echo "env-gate: --exec exige um comando após '--'." >&2
    exit 2
  fi
  JAVA_HOME="$JAVA_HOME_EFETIVO" \
  PATH="$JAVA_HOME_EFETIVO/bin:$PATH" \
  MONGODB_URL="$MONGODB_URL_EFETIVO" \
  RESEND_API_KEY="$RESEND_API_KEY_EFETIVO" \
  TRILHARA_RAIZ="$RAIZ" \
  exec "$@"
fi

printf 'export JAVA_HOME=%q\n' "$JAVA_HOME_EFETIVO"
printf 'export PATH=%q\n' "$JAVA_HOME_EFETIVO/bin:$PATH"
printf 'export MONGODB_URL=%q\n' "$MONGODB_URL_EFETIVO"
printf 'export RESEND_API_KEY=%q\n' "$RESEND_API_KEY_EFETIVO"
printf 'export TRILHARA_RAIZ=%q\n' "$RAIZ"
