# R13 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R13** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

### Hierarquia normativa incorporada neste guia

Os requisitos de domínio e produto aplicáveis a esta revisão estão reproduzidos abaixo e são a referência normativa para comportamento. As decisões `D-xx` explicitamente listadas prevalecem sobre especificações históricas quando determinam uma regra diferente; mudanças que alterem comportamento devem atualizar os documentos normativos do produto no mesmo change. O contexto de execução original da fase aparece apenas para delimitar o escopo desta revisão. O relato da implementação é uma declaração a verificar no código e nos testes, nunca prova de conformidade. Qualquer trecho histórico incluído está marcado como não normativo. O agente deve trabalhar com o contexto deste guia e com o estado atual do repositório, sem precisar consultar os documentos de origem.

### Regras de execução aplicáveis

- Dependências desta revisão: R0→R1→R2; após R2, R3/R4/R5 e R7 podem avançar em branches separados; R6 exige R4+R5; R8 exige R5+R6+R7; R9 exige R3; R10 exige R3–R9; R11 exige R7+R10; R12 e R13 exigem todas as revisões anteriores. Sequência linear segura: R0→R1→R2→R3→R4→R5→R6→R7→R8→R9→R10→R11→R12→R13.
- Faça inventário de imports, usos e call sites antes de mudanças transversais. Não introduza compatibilidade legada nem mocks/callbacks temporários destinados a fases futuras.
- Preserve os limites de escopo desta revisão. Mudanças transversais indispensáveis para compilar/testar devem ser justificadas no resumo.
- A premissa é banco de desenvolvimento/teste vazio no novo esquema; não preservar compatibilidade com modelos, rotas ou dados legados.
- Contratos comuns: rotas privadas exigem autenticação (401); falta de papel administrativo é 403; recursos inexistentes ou de outro usuário retornam 404 sem revelar existência. IDs HTTP são hexadecimais de 24 caracteres: malformado é 400 `INVALID_IDENTIFIER`. Erros usam `application/problem+json`, RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) e códigos estáveis `code`/`errors`; falhas inesperadas não expõem detalhes internos ou dados pessoais. Paginação usa `pagina` 1-based, tamanho dentro do limite do endpoint (máximo 100), erro 400 `INVALID_PAGINATION`, resposta `{ itens, pagina, tamanho, totalItens, totalPaginas }` e ordenação estável com `_id` como desempate. Datas de calendário são `yyyy-MM-dd`; instantes são RFC 3339 na API e `Instant`/BSON Date no banco; o fuso vem de `X-Timezone`, com fallback `America/Sao_Paulo`, nunca do relógio/fuso implícito do servidor. Escritas idempotentes definem chave, escopo, hash funcional e política de retry; mesma chave com payload funcional diferente retorna 409 `IDEMPOTENCY_KEY_CONFLICT`. Operações compostas sem transação multi-documento documentam estados intermediários, recuperação, retry e efeitos parciais; não declarar atomicidade inexistente.
- Ao concluir, registre achados confirmados/descartados, arquivos alterados/removidos, testes completos por nome e resultado do gate em `docs/REVISAO-IMPLEMENTADA.md`; atualize `docs/RASTREABILIDADE-REESTRUTURACAO.md`.

**Gate comum de saída:** `backend/`: `./mvnw -q verify`; `frontend/`: `npm run typecheck && npm run lint -- --max-warnings 0 && npm test -- --run && npm run build && npx knip`; raiz: `bash scripts/verificar-legado.sh`. Se uma etapa ainda não estiver disponível por dependência anterior, registre claramente o bloqueio e não declare a revisão concluída.


---

## Escopo desta revisão

### R13 — Limpeza de Código Morto e Hardening de Segurança Transversal (gate final)

**1. Referência e Diagnóstico**
- A F11 declarou a limpeza sem evidência (META-05). Restam suspeitas DC-01..DC-18 e os riscos transversais SEC-04, SEC-07, SEC-08, SEC-14, além de pacotes duplicados (META-02) e testes nomeados por fase (DC-18).

**2. Especificação Técnica, Segurança e UX**
- **Caça ao legado — remoção obrigatória (confirmar com `rg` + build):**
  - Backend: pacotes `br.com.trilhara.estudos`, `br.com.trilhara.tarefas`, `br.com.trilhara.notas` (se ainda existirem); classes `AjusteQuestoes`, `TipoSessao`, `TipoRegistroSessao`, `NovaSessao`, `EstatisticaDisciplina` (não DTO), `PlanejamentoCiclo*`, `DisciplinaProgramada`, `AtualizacaoPlanejamentoCiclo`, `AtualizacaoConclusaoBloco`, `CicloAtivoResponse`, `OrigemTarefa`, `OperacaoConcluirAtrasadosRepository`; método `removerTarefasDeCicloLegadas`; constantes `USUARIO_LOCAL_PADRAO`/`strictMode`; DTOs sem referência; mappers/exception classes sem uso; propriedades de `application.properties` não lidas por nenhum `@ConfigProperty`.
  - Frontend: componentes/hooks/serviços sem import (`knip`); tipos legados (`minutos`, `TipoSessao`, `Ciclo*`, `Trilha` dupla, mapas de progresso); `tarefaStorage.ts` (se não migrado em R7); CSS modules sem uso; dependências npm não usadas; chaves de storage antigas.
  - Testes: renomear/mover os testes `fase*`/`Fase*` para nomes por domínio; remover testes que testam código removido.
- **Hardening transversal:**
  - CSP e cabeçalhos (D-47) no `Caddyfile`, com teste de fumaça (`curl -I` em CI ou teste de integração do Caddy se existir) e validação de que o app funciona sem `unsafe-eval`.
  - Rate limiting (D-45).
  - ArchUnit `TenantIsolationArchTest` com **todas** as regras habilitadas (sem `@ArchIgnore`).
  - Teste de mass assignment parametrizado: para cada DTO de request, enviar `usuarioId` extra → 400.
  - Revisão de logs: nenhum log com e-mail, nome, token, senha, conteúdo de nota ou `usuarioId` em nível `INFO`+ (teste com appender em memória durante a suíte de integração; padrão de e-mail e ObjectId de usuários fixture não podem aparecer).
  - JaCoCo e Vitest coverage em modo *enforce* (D-43).
  - `scripts/verificar-legado.sh` sem modo `--report`: falha o CI.
  - Documentação: README (Java 25, comandos reais, gate), `.env.example`, requisitos/plano atualizados (D-49), `docs/CONTRATO-ERROS.md`, Anexo A sincronizado com as rotas reais.

**3. Checklist Atômico de Execução**
1. Rodar `npx knip` e remover tudo o que for apontado (ou justificar exceções em `knip.json` com comentário).
2. Rodar `npm run lint -- --max-warnings 0` e `npm run typecheck` com as flags D-44; corrigir.
3. Rodar `scripts/verificar-legado.sh`; zerar ocorrências.
4. Remover os pacotes/classes legados listados; rodar `./mvnw verify`.
5. `./mvnw dependency:analyze` sem *unused declared* / *used undeclared*.
6. Renomear os testes por domínio; atualizar a matriz de rastreabilidade.
7. Aplicar CSP/cabeçalhos/rate limiting; criar os testes.
8. Habilitar todas as regras ArchUnit e os thresholds de cobertura.
9. Atualizar README, `.env.example`, requisitos, plano e o Anexo A.
10. Executar o gate completo duas vezes seguidas em ambiente limpo (`reset-dev.sh`) para detectar testes não determinísticos.

**4. Critérios de Aceite e Validação Automatizada**
- `npx knip` → 0 achados; ESLint → 0 warnings; `tsc` → 0 erros; `scripts/verificar-legado.sh` → exit 0.
- `TenantIsolationArchTest`, `LegacyPackagesArchTest`, `RotasPublicasContratoTest`, `MassAssignmentParametrizadoTest`, `LogSemPiiTest`, `InventarioColecoesTest`, `AuthorizationByOwnerTest` verdes.
- Cobertura: `shared/` ≥ 80%, módulos ≥ 70% (Vitest); domínio backend ≥ 75% (JaCoCo).
- Contagem final de testes ≥ baseline de R0 em ambas as suítes.
- Gate comum verde duas vezes consecutivas.
- `docs/REVISAO-IMPLEMENTADA.md` completo; nenhum item "Atendido" sem teste.

---

---

## Decisões arquiteturais aplicáveis

**D-44 — Ferramentas de detecção de código morto**
- *Decisão Cravada:* Frontend: `tsconfig` com `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`; ESLint com `@typescript-eslint/no-unused-vars: error`, `react-hooks/*`, `jsx-a11y/recommended`, `react/no-danger: error`, `no-console: error`; `knip` (arquivos, exports e dependências não usados) com zero achados. Backend: ArchUnit (pacotes legados proibidos, D-06), `maven-dependency-plugin:analyze` sem dependências não declaradas/não usadas, e o script `scripts/verificar-legado.sh` com padrões `rg` que falham o build (Anexo C).

**D-45 — Rate limiting**
- *Decisão Cravada:* Aplicar o `RateLimit` existente (resposta `429 RATE_LIMITED` + `Retry-After`) por usuário e por IP: login/refresh/handoff (10/min/IP), `handoff/consume` (20/min/IP), busca de notas (60/min/usuário), `DELETE /api/auth/conta` e verificação de senha (5/15 min/usuário), criação de trilha/cronograma (30/min/usuário).

**D-47 — Cabeçalhos de segurança**
- *Decisão Cravada:* No `Caddyfile`, aplicar a todas as origens do frontend: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' <api-origins>; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` (handoff via fragmento não vaza), `Permissions-Policy: camera=(), microphone=(), geolocation=()` e HSTS em produção.

**D-49 — Documentação**
- *Decisão Cravada:* README com a versão correta do Java (25) e os comandos reais; `.env.example` sem segredos reais; requisitos/plano atualizados com as decisões que alteram comportamento (D-08, D-17, D-24, D-26, D-28, D-29, D-30, D-33, D-36), no mesmo PR da fase correspondente.

---

---

## Contexto normativo de domínio pertinente

## 3. Identidade, tenancy e tipos BSON

- Padronizar o campo de proprietário de todas as coleções privadas como `usuarioId` gravado como `ObjectId` BSON nativo. Eliminar o fallback legado `"local"` em string; em teste usar autenticação simulada/helper ou, somente se indispensável, ObjectId fixo no profile `%test`, nunca fora dele.
- Usar `ObjectId` BSON nativo para `_id` e referências entre coleções. A API HTTP expõe IDs como strings hexadecimais de 24 caracteres; ID malformado é sempre `400 INVALID_IDENTIFIER`, ID bem formado mas inexistente ou de outro tenant é `404 NOT_FOUND`.
- Tópicos embutidos no documento da disciplina usam `id` string estável gerado com `new ObjectId().toHexString()`. IDs de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações e nunca são reutilizados após remoção.
- Datas que representam instantes (`criadoEm`, `atualizadoEm`, `concluidoEm`, `deletedAt`, `expiresAt`, `instanteEntregaUtc`) são `BSON Date` no MongoDB e `Instant` no Java.
- Datas de calendário (`dataVencimento`, `dataOriginalOcorrencia`, `dataSessao`, `dataBloco`, `dataInicio`, `dataFim`, `dataProva`) são armazenadas como string ISO `yyyy-MM-dd` (mapeadas para `LocalDate` no Java via codec BSON direto), garantindo ordenação lexicográfica e ausência de deslocamento por fuso horário.
- O fuso de calendário segue o fuso atual do navegador: o cliente HTTP compartilhado envia em todas as requisições o cabeçalho `X-Timezone` com o identificador IANA (ex.: `America/Sao_Paulo`, obtido via `Intl.DateTimeFormat().resolvedOptions().timeZone`). O backend valida o `X-Timezone` (com fallback seguro para `America/Sao_Paulo` se ausente/inválido) e calcula `hojeLocal = LocalDate.now(zoneId)` para visão Hoje, atrasadas, corte de blocos futuros, trava de data futura em sessão manual e cálculo de *streak*, proibindo o uso de `LocalDate.now()` no fuso UTC da JVM.
- Converter valores para BSON com `CodecRegistry` tipado ou mapeadores BSON diretos. É proibido serializar/desserializar via `ObjectMapper.writeValueAsString` + `Document.parse` + `documento.toJson()`.

## 12. Autenticação, exclusão e confiabilidade

- SSO unificado para os quatro módulos, tratando separadamente **Produção** e **Ambiente Local (`localhost` / `*.localhost`)**:
  1. **Em Produção (ex.: `*.trilhara.com.br`):** o backend configura o atributo `Domain` compartilhado, validando host e allowlist de proxy; `trilhara_rt` é `HttpOnly; Secure; Path` mínimo e `SameSite` explícito. Emite também cookie `trilhara_session_state` não-HttpOnly com valor opaco (sem `usuarioId`/PII), `Secure`, `Path=/` e mesmos atributos Domain/SameSite compatíveis.
  2. **Em Ambiente Local (`localhost` / `*.localhost`):** nunca enviar atributo `Domain`; cookies são host-only. Transição entre origens locais usa endpoints `POST /api/auth/handoff` e `/api/auth/handoff/consume`: código aleatório opaco, persistido como hash, TTL de 60s, uso único e vinculado a allowlist exata de origem/destino. URL nunca contém access/refresh token ou PII; código é removido do fragmento após consumo e não aparece em logs/Referer.
  3. **Sincronização ao focar a aba (ambos os ambientes):** `storage`/`BroadcastChannel` sincronizam apenas abas da mesma origem. Em produção, o marcador de sessão é opaco e serve apenas como sinal; cada aba valida sessão no servidor em `visibilitychange`/`focus` (throttle 5s, refresh single-flight). Em `*.localhost`, sem cookie cross-origin, validar servidor ao foco. Não há promessa de sincronização imediata entre dispositivos nem de timer compartilhado cross-origin; expiração/revogação é detectada pela validação.
- Em caso de expiração de sessão (`401 Unauthorized` após tentativa de refresh), abrir o modal de reautenticação (`AuthModal`) sobre a tela atual **sem desmontar a árvore de componentes nem recarregar a página**, preservando formulários abertos, rascunhos de notas e estado de conclusão de bloco para retomar a ação após o login.
- Isolar estado local de cada conta sob chaves `trilhara:u:{usuarioId}:*`. Logout limpa tokens e estado sensível em memória, mas preserva timers pausados e rascunhos de notas associados à conta no dispositivo, salvo exclusão de conta, quando são apagados. Conta diferente nunca lê essas chaves. Dados locais não são sincronizados entre origens/dispositivos.
- Exclusão de conta exige senha atual ou reautenticação recente server-side; texto `EXCLUIR MINHA CONTA` é confirmação de intenção adicional, nunca autenticação suficiente.
- Toda consulta por recurso filho valida `usuarioId` no backend, inclusive refs de trilha, caderno, seção, sessão e bloco.
- Exclusão de conta é cascata completa de dados pessoais nas **18 coleções privadas** (`usuarios`, `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email`, `trilhas`, `disciplinas`, `progresso_topicos`, `cronogramas`, `blocos_cronograma`, `sessoes`, `listas_tarefas`, `series_recorrentes`, `tarefas`, `cadernos`, `secoes`, `notas`, `push_subscriptions`, `lembretes_push`), sem deixar dados órfãos identificáveis. Exige reautenticação server-side; confirmação textual é adicional e não autentica. API responde `202` com `operationId`, revoga sessão e worker processa a limpeza idempotentemente, removendo `usuarios` por último. Logs não contêm identificadores pessoais.
- Não exigir transação Mongo multi-documento para métricas: elas são agregadas da coleção de sessões, não atualizadas em contador duplicado.
- Sessão e conclusão do bloco podem falhar parcialmente; chave idempotente, estado explícito e retry só da operação pendente evitam duplicidade e silêncio.
- Operação em lote de blocos atrasados é item a item com resultado parcial explícito; não exigir replica set apenas para atomicidade de lote.
- Exclusão de conta é retry-safe e assíncrona: solicitação confirmada retorna `202`; job idempotente completa etapas e registra erro operacional sem PII para retries.

### 12.1 UX, Acessibilidade (WCAG 2.2 AA) e Design System

- **Tokens e Temas:** Manter como fonte única de estilo os tokens CSS globais em `frontend/src/shared/styles/index.css` para os temas `dark` (*Obsidian Dark UI*) e `light` (*Daylight Editorial UI*), garantindo contraste mínimo WCAG AA (4.5:1 para texto normal, 3:1 para componentes de UI e gráficos) e tipografia numérica tabular (`font-variant-numeric: tabular-nums`) em todos os cronômetros, contadores e tabelas.
- **Componentes compartilhados obrigatórios em `frontend/src/shared/design-system` (contratos congelados em F0; implementação junto da fase que consome cada componente):**
  1. `AppSwitcher` (implementado em **F1**): seletor rápido entre Estudos, Tarefas, Notas e Pomodoro integrado à `AppSidebar` (desktop) e ao header/navegação (mobile).
  2. `GlobalSessionModal` (implementado em **F5**): modal único de registro de sessão e conclusão de bloco.
  3. `SectionErrorCard` (implementado em **F7**): bloco de erro recuperável com botão **"Tentar novamente"** para falha parcial na Visão Hoje e em listagens paginadas.
  4. `SyncStatusBadge` (implementado em **F8**): indicador visual padronizado para estados `salvo`, `salvando`, `alteracoes-locais` e `erro` (com ação de retry).
- **Acessibilidade e Responsividade (WCAG 2.2 Level AA):**
  1. Modais (`Modal`, `FormModal`, `ConfirmDialog`) implementam *focus trap*, fechamento por `Escape` (desabilitado enquanto `isSaving == true`) e devolução de foco ao elemento acionador.
  2. Cronômetros usam `role="timer"` com `aria-live="off"` nos dígitos que mudam a cada segundo, anunciando apenas mudanças de estado (início, pausa, término de foco/descanso e erros) em região `aria-live="polite"`.
  3. Alvos de toque e clique têm dimensão mínima de `24x24px` no desktop (WCAG 2.2 SC 2.5.8) e `40x40px` em telas touch (`@media (pointer: coarse)`).
  4. Listas com barra de navegação inferior fixa no mobile aplicam `scroll-padding-bottom` e espaçamento inferior seguro para que o foco de teclado e o último item da lista nunca fiquem encobertos (WCAG 2.2 SC 2.4.11).
  5. Respeitar `@media (prefers-reduced-motion: reduce)` desativando animações contínuas de pulso e alternância rápida do título da aba.

## 13. Modelo de coleções recomendado

Nomes e responsabilidades das **19 coleções** do sistema (**18 coleções privadas** de usuário + **1 coleção de catálogo público** `editais`):

| Coleção | Unidade de documento | Índices principais |
|---|---|---|
| `usuarios` | Conta + subdocumento `preferencias` (disponibilidade semanal e padrão de push) | `email` único normalizado; `criadoEm DESC` |
| `refresh_tokens` | Sessão/dispositivo autenticado (família de rotação) | `tokenHash` único; `usuarioId + familiaId`; `expiraEm` (TTL/limpeza) |
| `tokens_recuperacao_senha` | Token de uso único para redefinição de senha | `tokenHash` único; `usuarioId`; `expiraEm` |
| `tokens_confirmacao_email` | Token de verificação de e-mail da conta | `tokenHash` único; `usuarioId`; `expiraEm` |
| `editais` | Catálogo público de editais (gerido por ADMIN) | `status + esfera + atualizadoEm DESC` |
| `trilhas` | Metadados da trilha do usuário | `usuarioId + atualizadoEm DESC`; `usuarioId + origemEditalId` |
| `disciplinas` | Disciplina da trilha + lista limitada de tópicos (máx. 500) | `usuarioId + trilhaId + ordem` |
| `progresso_topicos` | Um tópico concluído | único `usuarioId + trilhaId + topicoId` |
| `cronogramas` | Configuração, modalidade, período, `ativo` e `versaoAtiva` | único `usuarioId + trilhaId + modalidade`; único parcial `usuarioId + trilhaId` onde `ativo == true` |
| `blocos_cronograma` | Uma ocorrência gerada vinculada a `versaoGeracao` | `usuarioId + trilhaId + cronogramaId + versaoGeracao + data + ordem`; `usuarioId + data + concluido` (para visão Hoje) |
| `sessoes` | Um registro de sessão (`duracaoSegundos`, `questoes`, `acertos`) | `usuarioId + trilhaId + dataSessao DESC + criadoEm DESC`; `usuarioId + trilhaId + disciplinaId + dataSessao DESC`; único parcial `usuarioId + chaveIdempotencia` |
| `listas_tarefas` | Lista manual de tarefas (com suporte a lixeira) | `usuarioId + deletedAt + ordem` |
| `series_recorrentes` | Regra de recorrência e cursor `geradoAte` | `usuarioId + deletedAt + ativa` |
| `tarefas` | Tarefa manual ou ocorrência materializada de série | `usuarioId + deletedAt + listaId + concluida + ordem`; `usuarioId + deletedAt + concluida + dataVencimento`; único parcial `usuarioId + serieId + dataOriginalOcorrencia` |
| `cadernos` | Caderno de notas | `usuarioId + deletedAt + atualizadoEm DESC`; único parcial `usuarioId + origemTrilhaId` quando vinculado e ativo |
| `secoes` | Seção de caderno | `usuarioId + cadernoId + deletedAt + ordem`; `usuarioId + cadernoId + origemDisciplinaId` |
| `notas` | Página com `conteudoMarkdown` | `usuarioId + secaoId + deletedAt + ordem`; `usuarioId + cadernoId + deletedAt`; índice de texto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` (`default_language: "portuguese"`) |
| `push_subscriptions` | Inscrição Web Push por navegador/dispositivo | `usuarioId`; `endpoint` único |
| `lembretes_push` | Entrega de notificação agendada (`instanteEntregaUtc`) | `estado + instanteEntregaUtc`; `usuarioId + tarefaId` |

Use soft delete com `deletedAt`, `expiresAt`, `loteExclusaoId`/rotina de purga e estado hierárquico para lixeiras. TTL Mongo pode auxiliar, mas sua execução é assíncrona; restauração e prazo devem ser validados pela aplicação.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

## 16. Critérios de aceite transversais

- Usuário A nunca acessa, altera ou exclui dados do usuário B por ID conhecido.
- Datas de calendário não mudam ao converter entre navegador/backend; operações entre 21h00 e 23h59 no fuso `America/Sao_Paulo` usam a data local correta enviada via `X-Timezone`; instantes usam BSON Date.
- Trilha grande não exige transferir disciplinas/tópicos ao listar Hub.
- Marcar/desmarcar tópico afeta somente aquele progresso e seus agregados.
- Regenerar cronograma substitui integralmente blocos/conclusões via `versaoGeracao`/`versaoAtiva`, mantendo sessões e progresso; requisições simultâneas nunca deixam dois cronogramas ativos na mesma trilha.
- Tarefas/Pomodoro mostram o mesmo conjunto de tarefas manuais Hoje e os blocos do dia; nenhum bloco é gravado como tarefa.
- Concluir manual nunca cria sessão; concluir bloco abre o modal global; cancelar não conclui.
- Retry após timeout não duplica sessão nem conclusão.
- Uma sessão salva com `95` segundos persiste `duracaoSegundos = 95` e soma exatamente `95` segundos nas agregações; estatísticas por sessão conferem com somas do histórico; editar a sessão mais recente `(dataSessao DESC, criadoEm DESC, _id DESC)` ou excluir uma sessão individual recalcula naturalmente.
- Remover disciplina preserva histórico por padrão (sem prompt destrutivo na exclusão da disciplina) e permite apagar sessões/métricas da disciplina removida de forma explícita no Dashboard/Histórico.
- Alterar a `dataVencimento` de uma ocorrência recorrente mantém `dataOriginalOcorrencia` e jamais recria a ocorrência na data original ao expandir a janela de 90 dias.
- Autosave/busca/lixeira de Notas não transferem Markdown completo em listagens e preservam rascunho local (`trilhara:u:{usuarioId}:nota_draft:{notaId}`) em falha de rede.
- Tarefas/notas em lixeira podem ser restauradas no prazo e no contexto originais; após prazo, são purgadas.
- Logout ou troca de conta sincroniza na mesma origem via `storage`/`BroadcastChannel`; entre subdomínios a validação é server-side ao focar e o cookie marcador de produção é apenas um sinal opaco. Timers são sincronizados apenas na mesma origem/dispositivo; não há promessa de sincronização entre dispositivos.
- Falha de serviço aparece como erro recuperável (`SectionErrorCard` / `SyncStatusBadge`); nunca é apresentada silenciosamente como “sem dados”.
- Requisitos de acessibilidade WCAG 2.2 AA (*focus trap*, `aria-live`, alvos mínimos de toque/clique e `scroll-padding-bottom` para barras fixas) são atendidos nos quatro módulos.
- Resultados de performance são medidos em ambientes representativos; estimativas de redução de payload/latência não são aceites como garantias sem benchmark.
- Em todas as listas paginadas, ordenação inclui chave estável e filtros de tenant; erro de uma fonte na Visão Hoje não remove os dados da fonte disponível.
- Ao alternar cronograma ativo dentro da mesma trilha, leitor vê versão anterior ou versão nova completa; não vê staging incompleto nem dois ativos. Um período de recuperação sem cronograma ativo só é permitido em falha operacional e precisa ser sinalizado como seção indisponível/retry.
- Geração de cronograma é testada com falha injetada em cada etapa: nenhum leitor vê staging incompleto; estado anterior ou novo é recuperável, e lease expirado/obsoleto não permite publicação concorrente.
- Materialização recorrente é idempotente sob requisições simultâneas e os casos diários/semanais/mensais são testados em mudanças de ano, semanas ISO e meses curtos.
- Exclusão e restauração de lista/caderno/seção respeitam lotes de exclusão, itens previamente excluídos isoladamente e descendentes já purgados.
- Scheduler push não processa o mesmo item em paralelo; expirados não são enviados; falhas transitórias respeitam janela e backoff, reconhecendo que o provedor não garante exactly-once.
- Frontend instala runner automatizado Vitest + React Testing Library + jsdom em F0; cada critério de UI deste documento/plano tem teste de componente ou serviço executável por `npm test`, além de `npm run build`.
- Conta excluída retorna 202 depois de aceitar operação autenticada; worker conclui exclusão das 18 coleções sem deixar órfãos, mesmo com falhas injetadas em qualquer etapa.

---

## Plano original da FASE 11 (referência de escopo)

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 11.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 a 10 concluídas.
- **Objetivo da fase:** Auditar segurança por proprietário (`usuarioId`), acessibilidade WCAG 2.2 AA nos quatro módulos, executar benchmark sintético verificando `IXSCAN` com `explain("executionStats")` no MongoDB 7 e remover qualquer classe, rota ou arquivo morto remanescente.

### 11.3 Critérios de Saída da FASE 11 (Encerramento da Reestruturação)
- Suíte completa do backend (`mvn test`), build frontend (`npm run build`) e testes frontend (`npm test`) verdes. Benchmark opt-in executado em ambiente MongoDB 7 especificado e relatório arquivado.
- Todos os critérios de aceite da Seção 16 de requisitos de aceite transversais reproduzidos neste guia comprovados por testes automatizados.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 11                                               -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 11 (hipótese a verificar)

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 1. Relatório Técnico Resumido da Fase 11

A **FASE 11** conclui integralmente a reestruturação arquitetural do ecossistema Trilhara, realizando a auditoria transversal de segurança e autorização anti-IDOR, adequação estrita aos padrões de acessibilidade WCAG 2.2 Level AA, validação sintética  de índices do MongoDB 7 com `explain("executionStats")` e fechamento da documentação de execução e variáveis de ambiente.

#### 1.1 Auditoria de Autorização por Proprietário (Anti-IDOR / 404 Sem Vazar Existência)
1. **Isolamento Transversal de Dados (`AuthorizationByOwnerTest.java`):**
   - Criação de suíte de testes de integração com REST Assured simulando dois usuários distintos (`usuarioA` e `usuarioB`).
   - População de instâncias nas 13 entidades e coleções do domínio pertencentes exclusivamente a `usuarioA`: `trilhas`, `disciplinas`, `progresso_topicos`, `cronogramas`, `blocos_cronograma`, `sessoes`, `listas_tarefas`, `series_recorrentes`, `tarefas`, `cadernos`, `secoes`, `notas` e `push`.
   - Validação exaustiva de que qualquer tentativa de leitura, escrita, atualização ou exclusão disparada por `usuarioB` referenciando identificadores conhecidos de `usuarioA` resulta rigorosamente em status HTTP `404 Not Found` (sem vazar a existência prévia do recurso, sem responder 403 Forbidden e sem erro 500).
2. **Hardening de Desinscrição Web Push (`PushResource.java`):**
   - Ajuste na rota `DELETE /api/push/subscriptions`: quando a tentativa de remoção não encontrar inscrição pertencente ao usuário autenticado, a API responde HTTP `404 Not Found`, impedindo que um usuário terceirizado sonde ou tente remover endpoints de outros dispositivos.

#### 1.2 Acessibilidade WCAG 2.2 Level AA nos Quatro Módulos
1. **Focus Trap, Tecla Escape e Restauração de Foco (`Modal`, `FormModal`, `ConfirmDialog`, `GlobalSessionModal`):**
   - Inclusão da propriedade `isSaving` no componente `Modal` do Design System Trilhara, repassada automaticamente pelo `FormModal` via `isSaving={isLoading}`.
   - **Bloqueio de Escape durante Salvamento:** Quando `isSaving === true`, tanto a tecla `Escape` quanto o clique no overlay e o botão fechar do cabeçalho são desabilitados, prevenindo fechamento acidental e perda de dados em operações em andamento.
   - **Restauração de Foco:** Ao fechar qualquer modal ou diálogo de confirmação (`ConfirmDialog`, `GlobalSessionModal`), o foco do teclado retorna deterministicamente para o elemento disparador (`triggerElement` / `previousActiveElement`).
   - **Focus Trap:** Navegação com tecla `Tab` e `Shift+Tab` mantém o foco ciclando exclusivamente dentro da caixa de diálogo ativa.
2. **Semântica dos Cronômetros e Transições de Fase:**
   - Nos cronômetros de foco dos módulos Estudos (`EstudarView.tsx`) e Pomodoro (`PomodoroApp.tsx`), os dígitos numéricos utilizam `role="timer"` e `aria-live="off"`, garantindo que leitores de tela não leiam a contagem a cada segundo.
   - As transições de ciclo/fase (`statusText`) e os banners de pausa curta/longa (`breakBanner`) utilizam `role="status"` e `aria-live="polite"`.
   - O componente `SyncStatusBadge` opera com `role="status"` e `aria-live="polite"`, reportando transições de salvamento e erros para tecnologias assistivas.
3. **Ergonomia e Alvos de Toque / Clique (SC 2.5.8):**
   - Alvos mínimos de interação interativa (`button`, `a`, `input`, `select`, `textarea`, `[role="button"]`, etc.) configurados para `24x24px` no desktop.
   - Em dispositivos touch (`@media (pointer: coarse)`), os alvos mínimos expandem para `40x40px`.
   - Prevenção de oclusão por barra inferior fixa mobile: `scroll-padding-bottom: 88px` aplicado globalmente em containers e listas no `@media (max-width: 900px)`.
   - Suporte nativo a `@media (prefers-reduced-motion: reduce)` anulando durações de animação e transições CSS para usuários com sensibilidade vestibular.

#### 1.3 Benchmark de Índices e `explain("executionStats")` no MongoDB 7 (`MongoExplainBenchmarkTest.java`)
1. **Perfil Opt-in:**
   - Configurado com `@Tag("benchmark")` e `@EnabledIfSystemProperty(named = "benchmark", matches = "true")`.
   - Não sobrecarrega a suíte unitária padrão (`./mvnw test` pula os 5 testes de benchmark em 0.01s).
   - Executado sob demanda em ambiente com MongoDB 7 via `./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true`.
2. **Carga Sintética Representativa Injetada:**
   - 2.190 sessões de estudo / ano (6 sessões diárias ao longo de 365 dias).
   - Trilha com 20 disciplinas e 2.000 tópicos (100 tópicos por disciplina).
   - Cronograma de 366 dias com 24 blocos/dia = 8.784 blocos.
   - Caderno com 300 páginas de notas em Markdown (10 seções * 30 páginas).
   - Séries recorrentes com ocorrências de tarefas.
3. **Evidências de `explain("executionStats")` nas Consultas Críticas:**
   - **Blocos do Dia / Atrasados:** Ativação de `IXSCAN` no índice composto `idx_blocos_usuario_data_concluido`. Alta seletividade comprovada (`totalDocsExamined <= 24` para um volume de 8.784 blocos).
   - **Paginação Estável de Sessões:** Ativação de `IXSCAN` em `idx_sessoes_usuario_trilha_data_criado_id`. A ordenação composta `(dataSessao DESC, criadoEm DESC, _id DESC)` cobre a consulta, resultando em `totalDocsExamined == 20` (exatamente o tamanho da página solicitada em 2.190 registros).
   - **Disciplinas da Trilha:** Ativação de `IXSCAN` em `idx_disciplinas_usuario_trilha_ordem` retornando 20 registros com ordenação indexada.
   - **Busca Textual em Notas (`$text`):** Ativação do estágio de índice de texto do MongoDB 7 com prefixos de tenant `usuarioId` e `deletedAt`.
   - **Tarefas de Hoje:** Ativação de `IXSCAN` no índice composto `idx_tarefas_usuario_deleted_concluida_vencimento`.

#### 1.4 Limpeza de Código e Documentação Final
1. **Auditoria de Código Morto:**
   - Verificação de que nenhuma classe legada ou rota substituída permanece ativa na aplicação.
2. **Variáveis de Ambiente (`.env.example`):**
   - Documentação consolidada contemplando segredos JWT, pepper PBKDF2, CORS, cookies de sessão, WiredTiger cache, Resend API e chaves VAPID RFC 8030 / RFC 8292 de Web Push.
3. **Documentação Geral (`README.md`):**
   - Criação de documentação completa cobrindo arquitetura dos 4 módulos, requisitos de ambiente (Java 21+, Node 20+, Docker/MongoDB 7), guia de execução local/Docker e comandos para testes e benchmark opt-in.

---


### 3. Checklist de Validação da FASE 11

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **11.1** | Auditoria Anti-IDOR: Usuário B recebe 404 sem vazar existência ao tentar ler, alterar ou excluir dados de Usuário A | **Atendido** | Validado em `AuthorizationByOwnerTest` cobrindo 13 coleções/entidades |
| **11.2** | Hardening em Push: desinscrição de endpoint inexistente ou de terceiro retorna 404 Not Found | **Atendido** | Validado em `PushResource.java` e `AuthorizationByOwnerTest#auditarPush` |
| **11.3** | Modal e FormModal: Focus trap funcional e fechamento com Escape prevenido durante salvamento (`isSaving == true`) | **Atendido** | Validado em `fase11Accessibility.test.tsx` (seções 1 e 2) |
| **11.4** | ConfirmDialog e GlobalSessionModal: Fechamento com Escape e restauração automática de foco ao disparador original | **Atendido** | Validado em `fase11Accessibility.test.tsx` (seções 3 e 4) |
| **11.5** | Semântica de cronômetros: `role="timer"` e `aria-live="off"` nos dígitos em Estudos e Pomodoro | **Atendido** | Validado em `EstudarView.tsx`, `PomodoroApp.tsx` e `fase11Accessibility.test.tsx` |
| **11.6** | Transições de ciclo e badges: `role="status"` e `aria-live="polite"` nas mudanças de estado e no SyncStatusBadge | **Atendido** | Validado em `SyncStatusBadge.tsx` e `fase11Accessibility.test.tsx` |
| **11.7** | Alvos mínimos de toque e clique: 24x24px no desktop e 40x40px em `@media (pointer: coarse)` (SC 2.5.8) | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.8** | Evitar sobreposição de barra inferior móvel: `scroll-padding-bottom: 88px` em `@media (max-width: 900px)` | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.9** | Suporte a acessibilidade motora e sensorial: `@media (prefers-reduced-motion: reduce)` | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.10** | Benchmark opt-in com carga representativa (2.190 sessões, 2.000 tópicos, 8.784 blocos, 300 páginas notas) | **Atendido** | Implementado em `MongoExplainBenchmarkTest.java` com profile opt-in |
| **11.11** | Execução de `explain("executionStats")` confirmando uso de `IXSCAN` e plano de índice de texto MongoDB 7 | **Atendido** | Validado com sucesso via `./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true` |
| **11.12** | Isolamento do benchmark: suíte padrão (`mvn test`) ignora o benchmark (tempo de execução: 10ms, 5 skipped) | **Atendido** | Validado na execução padrão do Surefire com `@EnabledIfSystemProperty` |
| **11.13** | Remoção de código legado e documentação completa de ambiente (`.env.example` e `README.md`) | **Atendido** | Atualizado `.env.example` com chaves VAPID/Resend e redigido `README.md` abrangente |
| **11.14** | Sucesso em 100% dos testes de frontend Vitest (`npm test -- --run`) | **Atendido** | 14 suítes de teste, 109/109 testes aprovados (9 testes exclusivos da Fase 11) |
| **11.15** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem erros de tipagem |
| **11.16** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 134 testes executados com 0 falhas e 0 erros (13 testes exclusivos da Fase 11) |

---

## Anexos pertinentes  de revisão

## Anexo C — Padrões proibidos verificados por `scripts/verificar-legado.sh`

| Padrão (`rg`) | Escopo | Motivo |
|---|---|---|
| `writeValueAsString\|Document\.parse\|\.toJson\(\)` | `backend/src/main` | Serialização via JSON proibida (regra normativa) |
| `LocalDate\.now\(\)` | `backend/src/main` | Usar `TimezoneContext.hojeLocal()` |
| `ZoneId\.of\("America/Sao_Paulo"\)` | `backend/src/main` exceto `TimezoneContext.java` | Fuso implícito |
| `deleteMany\(new Document\(\)\)` | `backend/src/main` | Destrutivo |
| `USUARIO_LOCAL_PADRAO\|"local"` (em `UsuarioAtual`/auth) | `backend/src/main` | Fallback inseguro |
| `AjusteQuestoes\|TipoSessao\|TipoRegistroSessao\|AJUSTE_MANUAL\|NovaSessao\|PlanejamentoCiclo\|DisciplinaProgramada\|CicloAtivoResponse\|OrigemTarefa\|OperacaoConcluirAtrasados\|removerTarefasDeCicloLegadas` | `backend`, `frontend` | Legado |
| `dataEstudo\|ordemDia\|duracaoRealSegundos\|horarioDisparoUtc\|dataTermino\|usaPadraoGlobalLembrete\|toObjectIdOrNotFound\|PAYLOAD_TOO_LARGE\|testFailOnCollectionHook` | `backend`, `frontend` | Nomes não canônicos (D-02) |
| `package br\.com\.trilhara\.(estudos\|tarefas\|notas)\b` | `backend/src` | Pacotes legados |
| `trilhara_auth_token\|trilhara_user\|trilhara:u:anon\|\|\| 'anon'` | `frontend/src` | Armazenamento inseguro (D-09/D-21) |
| `trilhara_study_timer\|trilhara_pomodoro_timer\|trilhara_agenda_hoje` | `frontend/src` | Canais antigos (D-21) |
| `dangerouslySetInnerHTML\|window\.location\.reload\(` | `frontend/src` | XSS / perda de estado |
| `console\.(log\|debug\|info)` | `frontend/src` (exceto testes) | Higiene |
| `adaptarTrilhaResumo\|adaptarTrilhaDetalhe` | `frontend/src` | Adaptadores legados |
| `\bciclos?\b` (case-insensitive) | `frontend/src/modules/{estudos,tarefas}`, `backend/src/main/java/br/com/trilhara/{trilha,cronograma,tarefa}` | Terminologia (regra normativa) |
| `describe\(['"]fase\|class Fase[0-9]` e arquivos `fase*.test.*` | testes | Nomenclatura por fase (D-43) |

---

## Anexo A — Allowlist de rotas HTTP após a revisão

> Fonte para `RotasPublicasContratoTest` e `AuthorizationByOwnerTest`. Rotas ausentes desta lista devem ser removidas ou acrescentadas aqui com justificativa (no mesmo PR). Rotas de autenticação já existentes não relacionadas à reestruturação (login, cadastro, confirmação de e-mail, recuperação/alteração de senha, logout, logout-all, refresh, me) permanecem e devem ser listadas na primeira execução de R0.

**Auth/Usuário:** `POST /api/auth/handoff`, `POST /api/auth/handoff/consume`, `DELETE /api/auth/conta`, `GET /api/auth/me`, `PUT /api/usuarios/me/preferencias/tema`, `GET|PUT /api/usuarios/me/disponibilidade-semanal`.

**Estudos — trilhas/disciplinas/progresso:** `GET|POST /api/trilhas`, `GET|PUT|DELETE /api/trilhas/{trilhaId}`, `GET /api/trilhas/{trilhaId}/impacto-exclusao`, `POST /api/trilhas/{trilhaId}/disciplinas`, `PUT /api/trilhas/{trilhaId}/disciplinas/reordenar`, `PUT|DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}`, `PUT|DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso`.

**Estudos — sessões/estatísticas:** `GET|POST /api/trilhas/{trilhaId}/sessoes`, `PATCH|DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`, `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`, `GET /api/trilhas/{trilhaId}/estatisticas`.

**Estudos — cronogramas/blocos:** `GET|POST /api/trilhas/{trilhaId}/cronogramas`, `PUT|DELETE /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}`, `PATCH /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/ativar`, `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`, `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`, `GET /api/blocos/hoje`, `PATCH /api/blocos/{blocoId}/conclusao`.

**Catálogo:** rotas existentes de `editais` (leitura pública/autenticada e administração com `403` para não-ADMIN), a listar em R0.

**Tarefas:** `GET|POST /api/listas-tarefas`, `GET|PUT|DELETE /api/listas-tarefas/{listaId}`, `GET /api/listas-tarefas/{listaId}/impacto-exclusao`, `POST /api/listas-tarefas/{listaId}/restaurar`, `GET|POST /api/tarefas`, `GET /api/tarefas/hoje`, `GET|PUT|DELETE /api/tarefas/{tarefaId}`, `PATCH /api/tarefas/{tarefaId}/conclusao`, `PATCH /api/tarefas/{tarefaId}/subtarefas/{subtarefaId}`, `POST /api/tarefas/{tarefaId}/pular`, `GET|POST /api/series-recorrentes`, `GET|PUT|DELETE /api/series-recorrentes/{serieId}`, `GET /api/tarefas/lixeira`, `POST /api/tarefas/lixeira/{tarefaId}/restaurar`, `DELETE /api/tarefas/lixeira/esvaziar`.

**Notas:** `GET|POST /api/cadernos`, `PUT|DELETE /api/cadernos/{cadernoId}`, `GET /api/cadernos/{cadernoId}/impacto-exclusao`, `GET|POST /api/cadernos/{cadernoId}/secoes`, `PUT|DELETE /api/secoes/{secaoId}`, `GET /api/secoes/{secaoId}/impacto-exclusao`, `GET|POST /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`, `GET|PUT|DELETE /api/notas/{notaId}`, `GET /api/notas/busca`, `POST /api/cadernos/importar-trilha/{trilhaId}`, `POST /api/cadernos/{cadernoId}/sincronizar-trilha`, `GET /api/notas/lixeira`, `POST /api/notas/lixeira/{tipo}/{id}/restaurar`, `DELETE /api/notas/lixeira/esvaziar`.

**Push:** `GET /api/push/config`, `POST|DELETE /api/push/subscriptions`, `PUT /api/push/preferencias`.

**Removidas explicitamente:** `PATCH /api/trilhas/{id}/progresso/{topicoId}`, `GET /api/cadernos/{c}/secoes/{s}/notas/{n}`, `PATCH /api/push/preferencias`, qualquer rota de "ciclo"/"planejamento-ciclo".

---

## Anexo D — Resumo de rastreabilidade Achado → Decisão → Fase

| Achados | Decisões | Fase |
|---|---|---|
| META-01..05 | D-01, D-02 | R0 |
| RI-02, SEC-05, SEC-07, SEC-10, UX-01..05, UX-12, DC-11 | D-03, D-04, D-05, D-37, D-42 | R1 |
| SEC-01, SEC-06, SEC-08, SEC-13, UX-06, UX-08, DC-02, DC-14, DC-17 | D-06, D-07, D-08, D-09, D-10, D-12, D-41 | R2 |
| RI-01..05, SEC-09, DC-03..05 | D-06, D-11, D-38, D-39 | R3 |
| RI-06..09, DC-06 | D-13, D-14, D-15 | R4 |
| RI-10..15, RI-27, DC-07, DC-08, DC-15 | D-16, D-17, D-18, D-19, D-20 | R5 |
| RI-16, RI-17, UX-07, UX-11 | D-21, D-22 | R6 |
| RI-18..25, RI-38, SEC-16, DC-09 | D-23..D-28 | R7 |
| RI-26, RI-27, SEC-02, DC-10, DC-11 | D-23, D-46, D-48 | R8 |
| RI-28..33, SEC-03, SEC-04, DC-12, DC-13 | D-29..D-34 | R9 |
| RI-34..36, SEC-11, SEC-15 | D-08, D-35 | R10 |
| RI-37, SEC-12 | D-26, D-36 | R11 |
| RI-39, RI-40, UX-09, UX-10 | D-37, D-40, D-43 | R12 |
| DC-01..18, SEC-04, SEC-07, SEC-08, SEC-14 | D-44, D-45, D-47, D-49 | R13 |

---

