# Rastreabilidade da Reestruturação Trilhara

> Matriz criada na **R0** (D-01). Regra: **nenhum critério de aceite é declarado
> "Atendido" sem um teste automatizado nomeado**. Status possíveis:
> `Coberto` (existe teste que falha se o comportamento regredir),
> `Parcial` (teste cobre parte do critério), `Sem teste` (lacuna a suprir na
> fase indicada). Atualizada a cada revisão.
>
> Baseline de referência: backend 144 testes / frontend 109 testes (R0).

---

## A. Critérios transversais (documento §16)

| # | Critério de aceite | Teste(s) automatizado(s) | Status |
|---|---|---|---|
| A1 | Usuário A nunca acessa, altera ou exclui dados do usuário B por ID conhecido | `security/AuthorizationByOwnerTest` (13, anti-IDOR ⇒ 404); `test/fase9ExclusaoConta.test.tsx` | Coberto |
| A2 | Datas de calendário não mudam entre navegador/backend; 21h–23h59 em `America/Sao_Paulo` usam a data local via `X-Timezone`; instantes em BSON Date | `shared/TimezoneContextTest` (6); `shared/mongo/BsonRoundTripTest` (7); `test/sessaoNormalizada.test.ts` | Coberto |
| A3 | Trilha grande não exige transferir disciplinas/tópicos ao listar Hub | `trilha/TrilhaNormalizadaIntegrationTest` (9) | Parcial — falta assert de payload/`explain` em trilha de 2.000 tópicos; `MongoExplainBenchmarkTest` é opt-in |
| A4 | Marcar/desmarcar tópico afeta somente aquele progresso e agregados | `trilha/TrilhaNormalizadaIntegrationTest` | Parcial — cobre progresso, não os agregados derivados |
| A5 | Regenerar cronograma substitui blocos via `versaoGeracao`/`versaoAtiva`, mantém sessões/progresso; concorrência nunca deixa dois ativos | `cronograma/CronogramaNormalizadoIntegrationTest` (8, inclui `deveCoordenarConcorrenciaComLockAtomicoNaTrilha`) + índice parcial único `idx_cronogramas_usuario_trilha_ativo_unique` | Coberto |
| A6 | Tarefas/Pomodoro mostram as mesmas tarefas manuais Hoje + blocos do dia; nenhum bloco é gravado como tarefa | `test/fase7IntegracaoDiaria.test.tsx` (9); `tarefa/Fase6TarefaResourceTest` (4) | Coberto |
| A7 | Concluir manual nunca cria sessão; concluir bloco abre o modal global; cancelar não conclui | `test/fase5GlobalSessionTimer.test.tsx` (13); `fase7IntegracaoDiaria.test.tsx` | Coberto |
| A8 | Retry após timeout não duplica sessão nem conclusão | `sessao/SessaoNormalizadaIntegrationTest` (9) + índice único `idx_sessoes_usuario_chaveIdempotencia_unique` | Parcial — falta retry concorrente com `operationId` repetido em `concluir-atrasados` |
| A9 | Sessão de 95s persiste `duracaoSegundos=95` e soma 95s; estatísticas conferem; editar/excluir recalcula | `sessao/SessaoNormalizadaIntegrationTest`; `test/sessaoNormalizada.test.ts` (6) | Coberto |
| A10 | Remover disciplina preserva histórico por padrão e permite apagar sessões/métricas explicitamente | `trilha/TrilhaNormalizadaIntegrationTest`; `sessao/SessaoNormalizadaIntegrationTest` | Coberto |
| A11 | Alterar `dataVencimento` de ocorrência mantém `dataOriginalOcorrencia` e não recria na data original | `tarefa/Fase6RecorrenciaEngineTest` (5); índice `idx_tarefas_usuario_serie_dataOriginal_unique` | Coberto |
| A12 | Autosave/busca/lixeira de Notas não transferem Markdown completo; rascunho local preservado em falha de rede | `nota/NotaResourceTest` (11); `nota/NotaSnippetExtractorTest` (2); `test/fase8Notas.test.tsx` (13) | Coberto |
| A13 | Tarefas/notas em lixeira restauradas no prazo/contexto originais; purgadas após o prazo | `tarefa/Fase6TarefaResourceTest`; `test/fase6Tarefas.test.ts` (8) | Parcial — falta teste de purga após prazo |
| A14 | Logout/troca de conta sincronizam via `storage`/`BroadcastChannel`; entre subdomínios validação server-side ao focar; timers só na mesma origem | `test/sso.test.ts` (6); `fase5GlobalSessionTimer.test.tsx`; `appSwitcher.test.tsx` (6) | Parcial — sem teste de `BroadcastChannel` entre subdomínios |
| A15 | Falha de serviço aparece como erro recuperável (`SectionErrorCard`/`SyncStatusBadge`), nunca como "sem dados" | `test/fase7IntegracaoDiaria.test.tsx`; `fase8Notas.test.tsx` | Parcial — `SyncStatusBadge` sem teste dedicado |
| A16 | WCAG 2.2 AA (focus trap, `aria-live`, alvos mínimos, `scroll-padding-bottom`) nos 4 módulos | `test/fase11Accessibility.test.tsx` (9) | Coberto |
| A18 | Listas paginadas com ordenação estável (`_id` desempate) e filtro de tenant; erro de uma fonte não remove dados da outra | `AuthorizationByOwnerTest`; `CronogramaNormalizadoIntegrationTest`; `test/fase7IntegracaoDiaria.test.tsx` | Parcial — `tamanho` máx. de blocos é **200** (norma: 100) |
| A19 | Alternar cronograma ativo mostra versão anterior ou nova completa, nunca staging incompleto | `CronogramaNormalizadoIntegrationTest` | Parcial — falta teste de leitura concorrente durante a troca |
| A20 | Geração de cronograma com falha injetada em cada etapa; lease expirado não publica | `CronogramaNormalizadoIntegrationTest` (lease/lock) | Parcial — falta injeção de falha por etapa |
| A21 | Materialização recorrente idempotente sob concorrência; casos diário/semanal/mensal em virada de ano/ISO/meses curtos | `tarefa/Fase6RecorrenciaEngineTest` (5) | Parcial — falta caso virada de ano/semana ISO |
| A22 | Exclusão/restauração de lista/caderno/seção respeitam lotes e descendentes já purgados | `tarefa/ListaTarefasResourceTest` (1) | **Sem teste** — R7 |
| A23 | Scheduler push não processa o mesmo item em paralelo; expirados não enviados; backoff | `push/PushDeliverySchedulerTest` (3); `push/LembretePushLifecycleTest` (4); `push/LembretePushCalculatorTest` (3) | Coberto |
| A24 | Frontend com Vitest + RTL + jsdom; cada critério de UI tem teste executável por `npm test` + `npm run build` | 14 arquivos / 109 testes; `npm run build` verde | Coberto |
| A25 | Conta excluída retorna 202 e worker conclui as 18 coleções sem órfãos, mesmo com falha injetada | `auth/AccountDeletionIntegrationTest` (4, inclui `deveRetomarExclusaoAposFalhasEmCadaColecaoSemDeixarOrfaos`) | **Parcial com risco** — as 3 coleções fora do inventário (`handoff_tokens`, `operacoes_concluir_atrasados`, `planejamento_ciclo`) **não** são purgadas (REVISAO §5 I-3) |
| A26 | IDs HTTP: malformado → 400 `INVALID_IDENTIFIER`; inexistente/outro tenant → 404 | `shared/InvalidIdentifierTest` (3: 400 + `errors[trilhaId]`, 404, 401); `shared/ProblemDetailsCatalogoTest` (4); `shared/mongo/BsonRoundTripTest` (2, `BsonIds.parse`) | **Coberto (R1)** — `toObjectIdOrNotFound` removido; `BsonIds.parse` é a única conversão |
| A27 | Erros RFC 9457 (`application/problem+json` + `type/title/status/detail/instance/code`) sem vazar detalhe interno | `shared/ProblemDetailsCatalogoTest` (4, varre os 22 códigos); `shared/ProblemDetailsMapperTest` (6); `shared/DuplicateKeyMapperTest` (3); `shared/CorrelationIdFilterTest` (3) | **Coberto (R1)** — `ProblemDetailsFactory` é o único construtor |
| A29 | Mass assignment: `usuarioId`/`criadoEm` no body → 400 `VALIDATION_ERROR` com `errors[prop]` | `shared/MassAssignmentTest` (3) | **Coberto (R1)** — `quarkus.jackson.fail-on-unknown-properties=true` |
| A28 | Autorização administrativa: sem papel ADMIN → 403; sem autenticação → 401 | `auth/AuthResourceTest` (15); `edital/EditalResourceTest` (6) | Coberto |

---

## B. Critérios específicos da R0

| Critério | Teste / comando | Status |
|---|---|---|
| `scripts/verificar-legado.sh` existe, executável, reporta achados e `--report` retorna 0 | `bash scripts/verificar-legado.sh --report` → exit 0, 240 achados | Coberto |
| `--report` retorna 0 e modo estrito retorna 1 | `bash scripts/verificar-legado.sh` → exit 1 | Coberto |
| Inventário de pacotes sem duplicidade (META-02) | REVISAO §3; `find`/`uniq -d` → 0 | Coberto |
| Rotas JAX-RS inventariadas e estáveis | `RotasInventarioTest` (3 testes: 108 métodos, `@Path` obrigatório, rotas baseline) | Coberto |
| Índices reais comparados com a tabela canônica | `shared/mongo/MongoIndexInitializerTest` (4) + REVISAO §5 (40 declarados × 42 reais) | Parcial — teste valida **só nomes** (I-4), não chaves/opções |
| F4 reconciliada (relato 1) | REVISAO §6 com `arquivo:linha` por ponto | Coberto |
| `knip.json`, flags D-44 e regras ESLint configurados; achados contados | REVISAO §9 (163 ESLint, 73 knip) | Coberto |
| ArchUnit com dependência e esqueleto `TenantIsolationArchTest` | `TenantIsolationArchTest` (2 ativas + 3 `@ArchIgnore` com fase) | Coberto |
| JaCoCo report-only (sem `check`) | `pom.xml`: `prepare-agent` + `report`; ausência de `check` | Coberto |
| Baseline de testes registrado por suíte | REVISAO §2 (136→144 backend; 109 frontend) | Coberto |
| Build e testes verdes, nenhuma correção funcional | REVISAO §12; §10 lista as 55 remoções não comportamentais | Coberto |

---

## C. Lacunas prioritárias reveladas pela R0 (insumo para R1+)

> **Atualizado na R2 (2026-11-12).** Itens resolvidos marcados; os demais mantêm o dono original.

> **Atualizado na R1 (2026-11-12).** Itens resolvidos marcados; os demais mantêm o dono original.

| Lacuna | Fase dona | Evidência |
|---|---|---|
| `tamanho` máximo de paginação = 200 nos blocos (norma: 100, com 400) | R5 | REVISAO §6 |
| ~~`toObjectIdOrNotFound` retornava 404 para ID malformado~~ → **resolvido na R1** | — | `shared/InvalidIdentifierTest`; `BsonIds.parse` |
| ~~3 coleções fora do inventário~~ → **resolvido na R2** para `tokens_handoff_sso`; `operacoes_concluir_atrasados`/`planejamento_ciclo` seguem em allowlist de R5 | R5/R9 | `InventarioColecoesTest.ALLOWLIST_LEGADO_R5` |
| ~~`HandoffService` cria índices fora do `MongoIndexInitializer`~~ → **resolvido na R2** | — | `IndicesCanonicos`; `grep createIndex backend/src/main` → 1 |
| ~~`MongoIndexInitializerTest` valida só nomes~~ → **resolvido na R2** | — | `#deveCriarExatamenteIndicesCanonicos` |
| ~~`scripts/mongo/reset-dev.sh` inexistente~~ → **criado na R2** | — | `scripts/mongo/reset-dev.sh` |
| Resíduo `Ciclo`/`planejamento_ciclo` em `trilha/` + refs `ciclo` no frontend | R5/R7 | REVISAO §3 |
| ~~`tarefaStorage.ts` referenciava campos inexistentes~~ → **resolvido na R1** (arquivo removido) | — | `knip` exit 0 |
| Anexo C precisa de decisão sobre `usaPadraoGlobalLembrete` e `OperacaoConcluirAtrasados` | D-02/R8 | REVISAO §8 |
| ~~163 problemas ESLint / 73 achados knip~~ → **zerados na R1** | — | `npm run lint -- --max-warnings 0` + `npx knip` |
| `eslint-plugin-react` ausente nas dependências aprovadas (`react/no-danger` expressa via `no-restricted-syntax`) | R13 | REVISAO §9 |

## D. Critérios específicos da R1

| Critério | Teste / comando | Status |
|---|---|---|
| Catálogo fechado com `type = urn:trilhara:problem:<kebab>` para todos os códigos | `shared/ProblemDetailsCatalogoTest` (4) | Coberto |
| ID malformado → 400 `INVALID_IDENTIFIER` com `errors[campo]`; inexistente → 404 | `shared/InvalidIdentifierTest` (3) | Coberto |
| Mass assignment rejeitado (`usuarioId`, `criadoEm`) | `shared/MassAssignmentTest` (3) | Coberto |
| `E11000` → 409 `DUPLICATE_RESOURCE`; outras falhas → 503 | `shared/DuplicateKeyMapperTest` (3) | Coberto |
| `X-Correlation-ID` em toda resposta (gera/ecoa) | `shared/CorrelationIdFilterTest` (3) | Coberto |
| `ApiError.code` sempre presente (`NETWORK_ERROR`/`UNKNOWN`) | `src/test/erros.test.ts` (11) | Coberto |
| `classificarErro` nas 7 categorias de UI | `src/test/erros.test.ts` | Coberto |
| `Skeleton` só após 200 ms, mínimo 300 ms, `aria-busy` | `src/test/designSystemEstados.test.tsx` (11) | Coberto |
| `SectionErrorCard` com retry e `aria-describedby` | `designSystemEstados.test.tsx` | Coberto |
| `Toast` máx. 3, 5 s, ação "Desfazer", região `polite` | `src/test/toastConfirm.test.tsx` (7) | Coberto |
| `ConfirmDialog` foco no botão seguro + Escape bloqueado em `isConfirming` | `toastConfirm.test.tsx` | Coberto |
| `useRecurso` nunca expõe lista vazia antes da 1ª resposta | `src/test/useRecurso.test.tsx` (3) | Coberto |
| `formatarDuracao` (45 s / 12 min / 1 h 05 min) e datas sem deslocamento de fuso | `src/test/format.test.ts` (10) | Coberto |
| Gate comum verde, sem etapas "esperadas falhando" | `docs/REVISAO-IMPLEMENTADA-R1.md` §7 | Coberto |


| A17 | Performance medida em ambiente representativo; estimativas não são garantias | `benchmark/MongoExplainBenchmarkTest` (5) | Parcial — opt-in (`-Dbenchmark=true`), skipped no gate padrão |

## E. Critérios específicos da R2 (F1: BSON, índices, inventário, SSO, AppSwitcher, tema)

> Inventário canônico a partir da R2: **20 coleções = 19 privadas + `editais`** (18 filhas privadas
> + `usuarios`). As tabelas de R10/R12/R13 devem usar esta contagem nas revisões delas.

| Critério | Teste / comando | Status |
|---|---|---|
| Tabela canônica única com nome + chaves + todas as opções | `shared/mongo/MongoIndexInitializerTest#deveCriarExatamenteIndicesCanonicos` | Coberto |
| Inicializador idempotente, sem duplicidade e sem drift | `#deveSerIdempotente` | Coberto |
| Conflito de opções de índice ⇒ readiness `DOWN` | `#deveFicarDownComConflitoDeOpcoes`; `MongoIndexHealthCheckTest#deveFicarDownComConflito` | Coberto |
| Drift `idx_`/`uk_` reportado em `WARN` e no health sem remoção | `#deveReportarDriftSemRemover`; `#deveFicarUpComDrift` | Coberto |
| TTL real sobre `expiraEm` em `refresh_tokens` e `tokens_*` | `#deveCriarExatamenteIndicesCanonicos`; `HandoffConsumoAtomicoTest#deveTerIndicesCanonicos` | Coberto |
| Uma única `MongoIndexInitializer` com todos os índices | `rg -l "class MongoIndexInitializer" backend/src/main` → 1; `IndicesCanonicos` | Coberto |
| `tokens_handoff_sso` no inventário, `usuarioId` ObjectId, consumo atômico | `HandoffConsumoAtomicoTest` (4) | Coberto |
| 20 coleções no enum; exclusão cobre 18 filhas + `usuarios` | `InventarioColecoesTest` (5); `AccountDeletionIntegrationTest` | Coberto |
| Nenhuma coleção da aplicação fora do enum (allowlist R5 documentada) | `InventarioColecoesTest#naoDeveExistirColecaoForaDoInventario` | Parcial — allowlist de `operacoes_concluir_atrasados`/`planejamento_ciclo` (R5) |
| `BsonDates` estrito (tipo inesperado ⇒ `IllegalStateException`) | `BsonDatesStrictTest` (5) | Coberto |
| `scripts/mongo/reset-dev.sh` recria o banco dev/test | script + `--help`; execução manual | Coberto |
| Access token/perfil só em memória; nada em storage | `src/test/authStorage.test.ts` (4) | Coberto |
| Boot restaura sessão por refresh single-flight + `/auth/me` | `src/test/bootRefresh.test.tsx` (3) | Coberto |
| Token nunca viaja na URL | `sso.ts` sem `gerarUrlComTokenHandoff`; `rg` = 0 | Coberto |
| `GET /api/auth/me` com `papeis` + `preferencias` | `TemaPreferenciaTest`; `AuthResourceTest` (`papeis`) | Coberto |
| Tema persistido no servidor e aplicado antes do paint | `TemaPreferenciaTest` (3); `src/test/tema.test.ts` (5) | Coberto |
| Cache de tema por usuário e namespace anônimo separado | `src/test/tema.test.ts` | Coberto |
| `AppSwitcher` APG (setas, Home/End, Escape devolve foco, Tab, `aria-current`) | `src/test/appSwitcher.test.tsx` (6) | Coberto |
| Guarda de alterações não salvas + `beforeunload` + flush antes de navegar | `src/test/appSwitcher.test.tsx` (4); `unsavedChangesRegistry` | Coberto |
| Estudos sem namespace anônimo; Pomodoro visitante em `trilhara:anon:` | `src/test/fase5GlobalSessionTimer.test.tsx` | Coberto |
| Regras ArchUnit de pacotes legados habilitadas | `TenantIsolationArchTest.pacotesLegadosProibidos` | Coberto |
| `rg "trilhara_auth_token|trilhara_user|trilhara:u:anon" frontend/src` → 0 | comando executado no gate | Coberto |
| Gate comum verde (backend/frontend/raiz) | `docs/REVISAO-IMPLEMENTADA-R2.md` §8 | Coberto |
