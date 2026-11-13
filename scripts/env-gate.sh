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
#   MONGODB_URL    O container trilhara_mongo não publica porta, então
#                  localhost:27017 só funciona se alguém a publicar. Caso
#                  contrário usamos o IP da rede do compose — o primeiro que
#                  responder, pois o container pode estar em mais de uma rede.
#   RESEND_API_KEY trilhara.email.resend-api-key não possui default; sem valor
#                  o Quarkus aborta no startup (ConfigRecorder) e a suíte
#                  backend pulam 106 testes + 1 erro. Valor dummy só para teste.
#
# Descoberta do Docker:
#   No WSL podem coexistir dois daemons: o snap `docker` (onde vivem os
#   containers deste compose) e o Docker Desktop do Windows, cujo docker.exe
#   entra no PATH pelo interop e responde "could not be found in this WSL 2
#   distro". Por isso não basta `command -v docker`: validamos que o cliente
#   alcança de fato um daemon. Use DOCKER_BIN para forçar um binário.
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

# --- Docker utilizável ----------------------------------------------------
# Um `docker` no PATH pode ser o docker.exe do interop do WSL (Docker Desktop),
# que falha com "could not be found in this WSL 2 distro". Só aceitamos o
# cliente que responde a um `docker info` de verdade.
resolver_docker() {
  local candidato
  for candidato in "${DOCKER_BIN:-}" /snap/bin/docker /usr/bin/docker /usr/local/bin/docker; do
    [ -n "$candidato" ] || continue
    [ -x "$candidato" ] || continue
    if "$candidato" info >/dev/null 2>&1; then
      DOCKER_BIN="$candidato"
      return 0
    fi
  done
  candidato="$(command -v docker 2>/dev/null || true)"
  if [ -n "$candidato" ] && "$candidato" info >/dev/null 2>&1; then
    DOCKER_BIN="$candidato"
    return 0
  fi
  DOCKER_BIN=""
  return 1
}

# --- MONGODB_URL ----------------------------------------------------------
MONGODB_URL_EFETIVO="${MONGODB_URL:-}"
if [ -z "$MONGODB_URL_EFETIVO" ]; then
  # 1) porta publicada no host, se existir
  if (exec 3<>/dev/tcp/127.0.0.1/27017) 2>/dev/null; then
    MONGODB_URL_EFETIVO="mongodb://localhost:27017"
  # 2) IP do container trilhara_mongo na rede do compose. O container pode
  #    estar em várias redes (inclusive de stacks antigas já derrubadas), então
  #    testamos cada IP e usamos o primeiro que aceitar conexão na 27017.
  elif resolver_docker; then
    CONTAINER_MONGO="${MONGO_CONTAINER:-trilhara_mongo}"
    IP_MONGO="$(
      "$DOCKER_BIN" inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' \
        "$CONTAINER_MONGO" 2>/dev/null | tr ' ' '\n' | sed '/^$/d' || true
    )"
    if [ -z "$IP_MONGO" ]; then
      echo "env-gate: container '$CONTAINER_MONGO' não encontrado via '$DOCKER_BIN'." >&2
    fi
    for ip in $IP_MONGO; do
      if (exec 3<>"/dev/tcp/$ip/27017") 2>/dev/null; then
        MONGODB_URL_EFETIVO="mongodb://$ip:27017"
        break
      fi
    done
  else
    echo "env-gate: nenhum cliente docker funcional (tente DOCKER_BIN=/snap/bin/docker)." >&2
  fi
fi
if [ -z "$MONGODB_URL_EFETIVO" ]; then
  echo "env-gate: MongoDB inalcançável. Suba o container com" \
       "'${DOCKER_BIN:-docker} compose up -d mongo' ou exporte MONGODB_URL." >&2
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
