#!/usr/bin/env bash
#
# verificar-legado.sh — Gate anti-legado da reestruturação Trilhara (R0).
#
# Falha (exit 1) ao encontrar qualquer padrão proibido fora das ALLOWLISTS
# declaradas abaixo. Interface:
#
#   bash scripts/verificar-legado.sh            # exit 1 se houver achados
#   bash scripts/verificar-legado.sh --report   # imprime e retorna 0 (baseline R0)
#   bash scripts/verificar-legado.sh --quiet    # só o resumo por padrão
#
# Motor: usa `rg --pcre2` quando disponível; caso contrário cai para
# `grep -rInE` (GNU grep). Nenhum padrão depende de recurso exclusivo de PCRE.
#
set -uo pipefail

RAIZ="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$RAIZ" || exit 2

MODO="estrito"
case "${1:-}" in
  --report) MODO="report" ;;
  --quiet)  MODO="quiet" ;;
  "")       ;;
  -h|--help)
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    echo "verificar-legado: opção desconhecida '$1' (use --report ou --quiet)." >&2
    exit 2
    ;;
esac

if command -v rg >/dev/null 2>&1; then
  MOTOR="rg"
else
  MOTOR="grep"
fi

# ---------------------------------------------------------------------------
# ALLOWLISTS — caminhos permitidos, com justificativa por entrada.
# ---------------------------------------------------------------------------
ALLOWLIST=(
  # Fonte única e normativa do fuso: único lugar com America/Sao_Paulo literal.
  "backend/src/main/java/br/com/trilhara/shared/TimezoneContext.java"
  # Diagnóstico de conflito de índice usa BsonDocument.toJson() para formatar
  # chaves (esperado x encontrado); não serializa documento de domínio (D-07).
  "backend/src/main/java/br/com/trilhara/shared/mongo/MongoIndexInitializer.java"
  # Comentário de documentação citando a remoção do fallback "local".
  "backend/src/main/java/br/com/trilhara/shared/UsuarioAtual.java"
  # R12: catálogo público de editais é persistido como BSON explícito
  # (Document.parse/readValue sobre o JSON do próprio documento), não é
  # serialização de DTO de domínio por reflexão; o filtro usa o Clock local
  # injetado via TimezoneContext (ver EditalService).
  "backend/src/main/java/br/com/trilhara/edital/EditalRepository.java"
  # R12: payload do provedor de e-mail (Resend) é JSON de transporte externo e
  # não serializa entidade do domínio.
  "backend/src/main/java/br/com/trilhara/auth/EmailService.java"
  # R12: "Ciclo 1 de 4" do Pomodoro é o termo do produto (D-21), não o conceito
  # de "planejamento-ciclo" removido pela reestruturação.
  "frontend/src/modules/pomodoro/PomodoroApp.tsx"
)

# Código de teste: o Anexo C permite console.* em teste. A renomeação dos testes
# por fase é dívida de R12/R13 e não pode mascarar código de aplicação.
ALLOWLIST_TESTE=(
  "frontend/src/test/"
  "backend/src/test/"
)

TOTAL=0
FALHAS=0
declare -a RESUMO=()

imprimir_cabecalho() {
  printf '=== %s (escopo: %s) ===\n' "$1" "$2"
}

# buscar <rotulo> <escopo...> -- <regex>
# Acumula em RESUMO, TOTAL e FALHAS; imprime ocorrências quando MODO != quiet.
buscar() {
  local rotulo="$1"; shift
  local escopo=()
  while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do
    escopo+=("$1"); shift
  done
  [ "${1:-}" = "--" ] && shift
  local regex="$1"

  local existentes=() e
  for e in "${escopo[@]}"; do
    [ -e "$e" ] && existentes+=("$e")
  done
  if [ "${#existentes[@]}" -eq 0 ]; then
    RESUMO+=("$(printf '%-46s %6s  (escopo ausente)' "$rotulo" "0")")
    return 0
  fi

  local brutos
  # `grep -E` não suporta a flag inline (?i) usada por alguns padrões: extraí-la
  # para a opção -i, mantendo o comportamento consistente entre rg e grep.
  local flag_i=""
  case "$regex" in
    '(?i)'*) flag_i="i"; regex="${regex#'(?i)'}" ;;
  esac
  if [ "$MOTOR" = "rg" ]; then
    if [ -n "$flag_i" ]; then
      brutos="$(rg --pcre2 -i -n --no-heading --color never "$regex" "${existentes[@]}" 2>/dev/null || true)"
    else
      brutos="$(rg --pcre2 -n --no-heading --color never "$regex" "${existentes[@]}" 2>/dev/null || true)"
    fi
  else
    brutos="$(grep -rIn${flag_i}E "$regex" "${existentes[@]}" 2>/dev/null || true)"
  fi

  local filtrados="" a caminho ignorar permitido
  while IFS= read -r a; do
    [ -z "$a" ] && continue
    caminho="${a%%:*}"
    ignorar=0
    for permitido in "${ALLOWLIST[@]}"; do
      case "$caminho" in *"$permitido") ignorar=1; break ;; esac
    done
    if [ "$ignorar" -eq 0 ]; then
      for permitido in "${ALLOWLIST_TESTE[@]}"; do
        case "$caminho" in *"$permitido"*) ignorar=1; break ;; esac
      done
    fi
    [ "$ignorar" -eq 1 ] && continue
    filtrados+="$a"$'\n'
  done <<< "$brutos"

  local qtd=0
  if [ -n "$filtrados" ]; then
    qtd="$(printf '%s' "$filtrados" | grep -c . || true)"
  fi

  RESUMO+=("$(printf '%-46s %6s' "$rotulo" "$qtd")")
  TOTAL=$((TOTAL + qtd))
  if [ "$qtd" -gt 0 ]; then
    FALHAS=$((FALHAS + 1))
    if [ "$MODO" != "quiet" ]; then
      imprimir_cabecalho "$rotulo" "${existentes[*]}"
      printf '%s' "$filtrados"
      echo
    fi
  fi
  return 0
}

# ---------------------------------------------------------------------------
# Anexo C (padrões do guia R0)
# ---------------------------------------------------------------------------
buscar "serializacao-json" backend/src/main -- \
  'writeValueAsString|Document\.parse|\.toJson\(\)'
buscar "LocalDate.now" backend/src/main -- \
  'LocalDate\.now\(\)'
buscar "ZoneId-America/Sao_Paulo" backend/src/main -- \
  'ZoneId\.of\("America/Sao_Paulo"\)'
buscar "deleteMany-generico" backend/src/main -- \
  'deleteMany\(new Document\(\)\)'
buscar "fallback-usuario-local" backend/src/main -- \
  'USUARIO_LOCAL_PADRAO|"local"'
buscar "simbolos-legado" "backend/src" "frontend/src" -- \
  'AjusteQuestoes|TipoSessao|TipoRegistroSessao|AJUSTE_MANUAL|NovaSessao|PlanejamentoCiclo|DisciplinaProgramada|CicloAtivoResponse|OrigemTarefa|OperacaoConcluirAtrasados|removerTarefasDeCicloLegadas'
buscar "nomes-nao-canonicos-d02" "backend/src" "frontend/src" -- \
  'dataEstudo|ordemDia|duracaoRealSegundos|horarioDisparoUtc|dataTermino|usaPadraoGlobalLembrete'
buscar "pacotes-legados" backend/src -- \
  'package br\.com\.trilhara\.(estudos|tarefas|notas)\b'
buscar "storage-inseguro" frontend/src -- \
  'trilhara_auth_token|trilhara_user|trilhara:u:anon'
buscar "canais-antigos" frontend/src -- \
  'trilhara_study_timer|trilhara_pomodoro_timer|trilhara_agenda_hoje'
buscar "xss-e-reload" frontend/src -- \
  'dangerouslySetInnerHTML|window\.location\.reload\('
buscar "console-em-app" frontend/src -- \
  'console\.(log|debug|info)'
buscar "adaptadores-legados" frontend/src -- \
  'adaptarTrilhaResumo|adaptarTrilhaDetalhe'
buscar "termo-ciclo-backend" backend/src/main/java/br/com/trilhara -- \
  '(?i)\bciclos?\b'
buscar "termo-ciclo-frontend" frontend/src/modules -- \
  '(?i)\bciclos?\b'

# ---------------------------------------------------------------------------
# EXTENSÕES (lacunas comprovadas do Anexo C — ver docs/REVISAO-IMPLEMENTADA.md)
# ---------------------------------------------------------------------------
# E1: a regex do Anexo C só casa deleteMany(new Document()) sem argumentos, mas
# o repositório chama deleteMany(new Document(campo, valor)...) — igualmente
# destrutivo e fora do padrão normativo de exclusão explícita.
buscar "E1-deleteMany-com-filtro" backend/src/main -- \
  'deleteMany\(new Document\('
# E2: o Anexo C cita `trilhara_auth_token`, mas as chaves reais no código são
# `trilhara_auth_access_token`, `trilhara_auth_refresh_token` e `trilhara_auth_user`.
buscar "E2-storage-auth-chaves" frontend/src -- \
  'trilhara_auth_(token|access_token|refresh_token|user)'
# E3: nomenclatura por fase (D-43) em blocos de teste.
buscar "E3-nomenclatura-fase" "backend/src/test" "frontend/src/test" -- \
  "describe\(['\"]fase|class Fase[0-9]"

# E3b: arquivos de teste nomeados por fase.
ARQUIVOS_FASE="$(find backend/src/test frontend/src/test -type f 2>/dev/null | grep -E '(^|/)fase[0-9].*\.test\.' || true)"
QTD_ARQUIVOS_FASE=0
if [ -n "$ARQUIVOS_FASE" ]; then
  QTD_ARQUIVOS_FASE="$(printf '%s\n' "$ARQUIVOS_FASE" | grep -c . || true)"
fi
RESUMO+=("$(printf '%-46s %6s' "E3b-arquivos-por-fase" "$QTD_ARQUIVOS_FASE")")
TOTAL=$((TOTAL + QTD_ARQUIVOS_FASE))
if [ "$QTD_ARQUIVOS_FASE" -gt 0 ]; then
  FALHAS=$((FALHAS + 1))
  if [ "$MODO" != "quiet" ]; then
    imprimir_cabecalho "E3b-arquivos-por-fase" "backend/src/test frontend/src/test"
    printf '%s\n\n' "$ARQUIVOS_FASE"
  fi
fi

# ---------------------------------------------------------------------------
# Resumo
# ---------------------------------------------------------------------------
echo "-------------------------------------------------------------"
echo "Motor: $MOTOR | Modo: $MODO"
echo "Padrao                                          Achados"
echo "-------------------------------------------------------------"
printf '%s\n' "${RESUMO[@]}"
echo "-------------------------------------------------------------"
echo "Total de achados: $TOTAL (padroes com ocorrencia: $FALHAS)"

if [ "$MODO" = "report" ]; then
  echo "Modo --report: retornando 0 (baseline R0; correcoes nas fases de dominio)."
  exit 0
fi

if [ "$TOTAL" -gt 0 ]; then
  echo "FALHOU: padroes de legado encontrados." >&2
  exit 1
fi

echo "OK: nenhum padrao de legado encontrado."
exit 0
