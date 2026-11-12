# R2 — Revisão Implementada (F1: BSON, índices canônicos, inventário, tokens, SSO, AppSwitcher e tema)

> Relatório da revisão **R2**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e
> `docs/REVISAO-IMPLEMENTADA-R1.md`.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `main` · base `c2f5640` (R1).

---

## 1. Decisões de escopo aplicadas

| Decisão | Efeito |
|---|---|
| **Contrato único de perfil** | `UsuarioResponse` passa a expor `papeis` (antes `roles`) + `preferencias` em login, cadastro, refresh, handoff, `/auth/me`, confirmação de e-mail e admin. `AlterarRolesRequest.roles` → `papeis`; rota admin `/{id}/roles` → `/{id}/papeis`. `Usuario` (record de domínio) e `SecurityFilter` não mudaram |
| **Inventário de 20 coleções** | `enum ColecaoTrilhara` (19 privadas + `editais`), 18 filhas privadas, fonte única do inicializador e da exclusão de conta |
| **Allowlist legada de R5** | `InventarioColecoesTest` aceita `operacoes_concluir_atrasados`, `planejamento_ciclo` e resíduo `handoff_tokens` até D-17/R5; remover junto com a fase dona |
| **Índices de R7/R11 antecipados** | `uk_series_recorrentes_usuarioId_origemOperacaoId` (D-24) e `uk_lembretes_push_tarefaId_versaoAgendamento` (D-36) já declarados, inertes em base vazia |
| **`idx_trilhas_usuario_cronogramaAtivo` removido** | Não consta da tabela canônica do §13 e nenhuma query filtra por `cronogramaAtivoId` |
| **Nomes `idx_`/`uk_`** | Únicos deixam de usar sufixo `_unique`; mapeamento antigo→novo na seção 6 |
| **Estudos sem namespace anônimo** | `getStudyTimerStorageKey(null) === null` (nada persistido); Pomodoro visitante em `trilhara:anon:pomodoro_timer` |
| **Tema `system`** | `ThemeMode = dark \| light \| system`, resolvido por `prefers-color-scheme`, cache `trilhara:u:{id}:tema` / `trilhara:anon:tema`, espelho no servidor |

---

## 2. Achados confirmados

| # | Achado | Evidência antes | Correção |
|---|---|---|---|
| 1 | Conflito de índice não derrubava readiness | `MongoIndexInitializer.onStart` lançava `IllegalStateException` (abortava o boot) | Reconciliação devolve `DiagnosticoIndices`; `@Startup` só registra; readiness decide |
| 2 | Comparação frouxa de índices | `garantirIndice` comparava apenas `key` + `unique` | Compara `key`, `unique`, `expireAfterSeconds`, `partialFilterExpression`, `default_language` |
| 3 | Nenhum TTL real | `grep expireAfterSeconds` → 0 ocorrências | TTL `expireAfterSeconds: 0` em `refresh_tokens.expiraEm`, `tokens_recuperacao_senha.expiraEm`, `tokens_confirmacao_email.expiraEm`, `tokens_handoff_sso.expiraEm` |
| 4 | Índices criados fora do inicializador | `HandoffService:56-57` criava 2 índices no construtor | Removidos; índices em `IndicesCanonicos` |
| 5 | Coleção de handoff fora do inventário | `HandoffService` usava `handoff_tokens` com `usuarioId` **string** | `tokens_handoff_sso` (`usuarioId` ObjectId, `codigoHash`, `origemDestino`, `expiraEm`, `consumidoEm`) |
| 6 | Consumo não atômico por contrato | flag `usado: false/true` + `destino` | `findOneAndUpdate({codigoHash, consumidoEm: null, expiraEm > agora})` |
| 7 | `MongoIndexInitializerTest` validava só nomes | `assertEquals` de nomes apenas | Teste compara nome + chaves + todas as opções |
| 8 | `scripts/mongo/reset-dev.sh` inexistente | `ls scripts/mongo` → ausente | Script criado (allowlist `trilhara`/`trilhara_test`, exige `--yes`) |
| 9 | Access token + usuário em `localStorage` | `authStorage.ts` gravava `trilhara_auth_access_token`/`trilhara_auth_user` | Estado em memória; zero escrita em `localStorage`/`sessionStorage` |
| 10 | Token e perfil na URL | `sso.ts` `gerarUrlComTokenHandoff`/`consumirTokenHandoffDaUrl`; `EstudosApp` usava nos dois fluxos de `returnUrl` | Funções removidas; `iniciarHandoffParaApp` (handoff opaco) nos dois fluxos |
| 11 | Tema só local e global | `useTheme` com chave global `trilhara_theme`, sem `system` | Preferência no servidor + cache por usuário/anon + `system` |
| 12 | Namespace anônimo misturado | `getStudyTimerStorageKey`/`getPomodoroTimerStorageKey` com fallback `'anon'` | Estudos sem chave anônima; Pomodoro em `trilhara:anon:` |
| 13 | `AppSwitcher` fora do APG e sem guarda | sem setas/Home/End/roving/Escape-foco/Tab; `unsavedChangesRegistry` inexistente | APG Menu Button completo + `ConfirmDialog` "Ficar"/"Sair mesmo assim" + flush |
| 14 | `BsonDates` tolerante | `toIsoString`, `toInstant(Document,campo)`, `toLocalDate(Document,campo)` | API canônica + `lerInstant`/`lerLocalDate` com `IllegalStateException` |
| 15 | `/auth/me` sem preferências e com `roles` | `UsuarioResponse` sem `preferencias` | `papeis` + `preferencias` + `PUT /api/usuarios/me/preferencias/tema` |
| 16 | Regra de pacote legado desativada | `@ArchIgnore(reason = "Habilitar em R13")` | Habilitada; `@ArchIgnore` restantes documentados para R3–R9 |

## 3. Achados descartados

| # | Hipótese | Evidência de descarte |
|---|---|---|
| 1 | Duas classes `MongoIndexInitializer` (DC-02) | `rg -l "class MongoIndexInitializer" backend/src/main` → **1**; não existe pacote `config/` |
| 2 | Índices relatados ≠ plano | O **código** já seguia §13; a divergência estava no relato histórico da F1, não no código |
| 3 | `BsonIds.toObjectIdOrNotFound` ainda existente | Removido na R1; `BsonIds` expõe `parse`, `novoIdHex`, `toHex`, `isValid` |
| 4 | Regra ArchUnit "Resource não acessa Mongo" desativada | Já ativa desde a R0 (`recursosNaoAcessamMongoDiretamente`) |
---

## 4. Arquivos criados

**Backend**
- `shared/mongo/ColecaoTrilhara.java` — inventário de 20 coleções + `filhasPrivadas()`
- `shared/mongo/IndicesCanonicos.java` — tabela canônica declarativa (44 índices em 20 coleções)
- `shared/mongo/DiagnosticoIndices.java`, `shared/mongo/ConflitoIndice.java`
- `auth/UsuarioPreferenciasResource.java` — `PUT /api/usuarios/me/preferencias/tema`
- `auth/dto/PreferenciasDTO.java`, `auth/dto/AtualizarTemaRequest.java`
- `scripts/mongo/reset-dev.sh`
- Testes: `BsonDatesStrictTest`, `MongoIndexHealthCheckTest`, `HandoffConsumoAtomicoTest`, `TemaPreferenciaTest`, `InventarioColecoesTest`; `MongoIndexInitializerTest` reescrito

**Frontend**
- `shared/state/unsavedChangesRegistry.ts`
- `shared/hooks/useUnsavedChanges.ts`
- Testes: `authStorage.test.ts`, `bootRefresh.test.tsx`, `tema.test.ts`; `appSwitcher.test.tsx` reescrito

## 5. Arquivos alterados (principais)

**Backend:** `MongoIndexInitializer`, `MongoIndexHealthCheck`, `BsonDates` (reescritos), `HandoffService`, `AccountDeletionService`, `UserRepository`, `UserService`, `AuthService`, `AuthResource`, `UsuarioResponse`, `AlterarRolesRequest`, `application.properties`, `TenantIsolationArchTest`, `AuthResourceTest`, `AccountDeletionIntegrationTest`, `MongoExplainBenchmarkTest`, `BsonRoundTripTest` e os repositórios migrados de `toIsoString`/`toInstant(Doc)`/`toLocalDate(Doc)`.

**Frontend:** `authStorage.ts` (reescrito), `types.ts`, `authService.ts`, `sso.ts`, `AuthContext.tsx`, `useTheme.ts`, `main.tsx`, `AppSwitcher.tsx` (reescrito), `StudyTimerContext.tsx`, `PomodoroApp.tsx`, `EstudosApp.tsx`, `modules/estudos/types/trilhara.ts`, `shared/hooks/index.ts`, `appSwitcher.test.tsx`, `fase5GlobalSessionTimer.test.tsx`, `fase9ExclusaoConta.test.tsx`, `fase7IntegracaoDiaria.test.tsx`, `sso.test.ts`.

**Removidos:** persistência de token/usuário em storage; `gerarUrlComTokenHandoff`; `consumirTokenHandoffDaUrl`; constantes `ACCESS_TOKEN_KEY`/`USER_STORAGE_KEY`; duplicata de `UsuarioAutenticado` em `modules/estudos/types`; chave global `trilhara_theme`; `idx_trilhas_usuario_cronogramaAtivo`.


---

## 6. Tabela canônica — mapeamento de nomes (antigo → novo)

| Coleção | Antes | Depois |
|---|---|---|
| `usuarios` | `idx_usuarios_email_unique` | `uk_usuarios_email` |
| `usuarios` | `idx_usuarios_criadoEm_desc` | `idx_usuarios_criadoEm` |
| `refresh_tokens` | `idx_refresh_tokens_tokenHash_unique` | `uk_refresh_tokens_tokenHash` |
| `refresh_tokens` | `idx_refresh_tokens_usuario_familia` | `idx_refresh_tokens_usuarioId_familiaId` |
| `refresh_tokens` | `idx_refresh_tokens_expiraEm` (sem TTL) | `idx_refresh_tokens_expiraEm` (**TTL 0**) |
| `tokens_recuperacao_senha` | `idx_tokens_recuperacao_*` | `uk_tokens_recuperacao_senha_tokenHash`, `idx_tokens_recuperacao_senha_usuarioId`, `idx_tokens_recuperacao_senha_expiraEm` (**TTL**) |
| `tokens_confirmacao_email` | `idx_tokens_confirmacao_*` | `uk_tokens_confirmacao_email_tokenHash`, `idx_tokens_confirmacao_email_usuarioId`, `idx_tokens_confirmacao_email_expiraEm` (**TTL**) |
| `tokens_handoff_sso` | `handoff_tokens` + 2 índices no construtor | `uk_tokens_handoff_sso_codigoHash`, `idx_tokens_handoff_sso_usuarioId`, `idx_tokens_handoff_sso_expiraEm` (**TTL**) |
| `trilhas` | `idx_trilhas_usuario_atualizadoEm`, `..._origemEdital`, `..._cronogramaAtivo` | `idx_trilhas_usuarioId_atualizadoEm`, `idx_trilhas_usuarioId_origemEditalId` (**terceiro removido**) |
| `disciplinas` | `idx_disciplinas_usuario_trilha_ordem` | `idx_disciplinas_usuarioId_trilhaId_ordem` |
| `progresso_topicos` | `idx_progresso_topicos_unico` | `uk_progresso_topicos_usuarioId_trilhaId_topicoId` |
| `cronogramas` | `idx_cronogramas_usuario_trilha_modalidade_unique`, `..._ativo_unique` | `uk_cronogramas_usuarioId_trilhaId_modalidade`, `uk_cronogramas_usuarioId_trilhaId_ativo` |
| `blocos_cronograma` | `idx_blocos_usuario_trilha_cronograma_versao_data_ordem_unique`, `idx_blocos_usuario_data_concluido` | `uk_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_data_ordem`, `idx_blocos_cronograma_usuarioId_data_concluido`, **+** `idx_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_concluido_data` |
| `sessoes` | `idx_sessoes_usuario_trilha_data_criado_id`, `..._disciplina_data`, `idx_sessoes_usuario_chaveIdempotencia_unique` | `idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id`, `idx_sessoes_usuarioId_trilhaId_disciplinaId_dataSessao`, `uk_sessoes_usuarioId_chaveIdempotencia` |
| `listas_tarefas` | `idx_listas_tarefas_usuario_deletedAt_ordem` | `idx_listas_tarefas_usuarioId_deletedAt_ordem` |
| `series_recorrentes` | `idx_series_recorrentes_usuario_deletedAt_ativa` | `idx_series_recorrentes_usuarioId_deletedAt_ativa`, **+** `uk_series_recorrentes_usuarioId_origemOperacaoId` |
| `tarefas` | `idx_tarefas_usuario_deleted_lista_concluida_ordem`, `..._concluida_vencimento`, `..._serie_dataOriginal_unique` | `idx_tarefas_usuarioId_deletedAt_listaId_concluida_ordem`, `idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento`, `uk_tarefas_usuarioId_serieId_dataOriginalOcorrencia` |
| `cadernos` | `idx_cadernos_usuario_deletedAt_atualizadoEm`, `..._origemTrilha_unique` | `idx_cadernos_usuarioId_deletedAt_atualizadoEm`, `uk_cadernos_usuarioId_origemTrilhaId` |
| `secoes` | `idx_secoes_usuario_caderno_deletedAt_ordem`, `..._origemDisciplina` | `idx_secoes_usuarioId_cadernoId_deletedAt_ordem`, `idx_secoes_usuarioId_cadernoId_origemDisciplinaId` |
| `notas` | `idx_notas_usuario_secao_deletedAt_ordem`, `..._caderno_deletedAt`, `..._deletedAt_text` | `idx_notas_usuarioId_secaoId_deletedAt_ordem`, `idx_notas_usuarioId_cadernoId_deletedAt`, `idx_notas_usuarioId_deletedAt_text` |
| `push_subscriptions` | `idx_push_subscriptions_usuario`, `idx_push_subscriptions_endpoint_unique` | `idx_push_subscriptions_usuarioId`, `uk_push_subscriptions_endpoint` |
| `lembretes_push` | `idx_lembretes_push_estado_instanteEntregaUtc`, `idx_lembretes_push_usuario_tarefa` | `uk_lembretes_push_tarefaId_versaoAgendamento`, `idx_lembretes_push_estado_instanteEntregaUtc`, `idx_lembretes_push_usuarioId_tarefaId` |

> R12/R13 citam nomes antigos nos relatos de `explain`; usar esta tabela ao reproduzir os benchmarks.
---

## 7. Testes automatizados (nomes e resultado)

### Backend — novos

| Teste | Asserções |
|---|---|
| `MongoIndexInitializerTest#deveCriarExatamenteIndicesCanonicos` | nome + chaves + `unique` + TTL + `partialFilterExpression` + `default_language`/`weights` de cada um dos 44 índices |
| `MongoIndexInitializerTest#deveSerIdempotente` | 2ª execução sem duplicidade, sem conflito e sem drift |
| `MongoIndexInitializerTest#deveFicarDownComConflitoDeOpcoes` | índice prévio sem TTL → diagnóstico com `expireAfterSeconds` divergente |
| `MongoIndexInitializerTest#deveReportarDriftSemRemover` | `idx_usuarios_nome_legado` listado como drift e **preservado** |
| `MongoIndexInitializerTest#deveCobrirInventarioCompleto` | 20 coleções, 18 filhas, todas com índice, nomes únicos |
| `MongoIndexHealthCheckTest#deveFicarDownComConflito` | `DOWN` + dados "esperado × encontrado" |
| `MongoIndexHealthCheckTest#deveFicarUpComDrift` | `UP` + dado `drift` |
| `MongoIndexHealthCheckTest#deveFicarUpSemDivergencia` | `UP` e nome do check |
| `BsonDatesStrictTest` (5 testes) | round-trip canônico; `IllegalStateException` em tipo inesperado (`String`/`Date`) |
| `HandoffConsumoAtomicoTest` (4) | índices canônicos + TTL 0; dois consumos paralelos → 1 sucesso; expirado; replay |
| `InventarioColecoesTest` (5) | coleções fora do enum; 20/18; `handoff_tokens` ausente; cobertura da exclusão; sem duplicidade |
| `TemaPreferenciaTest` (3) | `PUT` 200 com preferências; tema inválido → 400 `VALIDATION_ERROR`; sem auth → 401 |

### Backend — atualizados
`AuthResourceTest` (`usuario.papeis`, `papeis`), `AccountDeletionIntegrationTest` (popula `tokens_handoff_sso`, 18 filhas), `MongoExplainBenchmarkTest` (nomes de índice novos), `BsonRoundTripTest` (`toIso`), `TenantIsolationArchTest` (regra de pacotes legados habilitada).

### Frontend — novos

| Teste | Asserções |
|---|---|
| `authStorage.test.ts` (4) | login não grava token/usuário em `localStorage`/`sessionStorage`; `Object.keys` sem chaves `trilhara_auth*`; `limparSessao` limpa memória; limpeza por usuário preserva outra conta |
| `bootRefresh.test.tsx` (3) | boot = `POST /auth/refresh` → `GET /auth/me`; duas chamadas simultâneas → 1 refresh; sem cookie permanece anônimo |
| `appSwitcher.test.tsx` (6) | `aria-haspopup`/`aria-expanded`; Enter abre focando o item atual; ↓/↑ circulam; Home/End; Escape devolve foco; Tab fecha; `aria-current="page"` |
| `appSwitcher.test.tsx` (guarda, 4) | "Ficar" não navega e mantém o registro; "Sair mesmo assim" executa flush e navega; sem alterações navega direto; `beforeunload` ativo só com item sujo |
| `tema.test.ts` (5) | chave por usuário / `trilhara:anon:tema`; nunca `trilhara_theme`; `system` via `prefers-color-scheme`; `temaInicial`; fallback sem `matchMedia` |

### Frontend — atualizados
`sso.test.ts`, `fase5GlobalSessionTimer.test.tsx` (chave anônima do Pomodoro), `fase9ExclusaoConta.test.tsx` (sessão em memória), `fase7IntegracaoDiaria.test.tsx` (`papeis`/`preferencias`).

---

## 8. Gate de saída (executado)

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -q verify` | **BUILD SUCCESS** — 181 testes, 0 falhas, 0 erros, 7 skipped |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** — 0 erros |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** — 0 erros, 0 avisos |
| Frontend testes | `npm test -- --run` | **SUCCESS** — 22 arquivos, 167 testes (R1: 19/151) |
| Frontend build | `npm run build` | **SUCCESS** (chunk `NotasApp` 545 kB — aviso pré-existente) |
| Frontend dead code | `npx knip` | **SUCCESS** — exit 0 |
| Raiz | `bash scripts/verificar-legado.sh` | **exit 1 (213 achados)** — resíduos de ciclo/nomenclatura/fases, donos R5/R7/R12 |
| Raiz (relatório) | `bash scripts/verificar-legado.sh --report` | **exit 0** — 213 achados (R1: 216); `storage-inseguro` 0 e `E2-storage-auth-chaves` 0 |

### Critérios de aceite da R2

| Critério | Evidência | Status |
|---|---|---|
| `rg "trilhara_auth_token\|trilhara_user\|trilhara:u:anon" frontend/src` → 0 | `grep -rInE` → 0 linhas | Coberto |
| `rg -l "class MongoIndexInitializer" backend/src/main` → 1 | retorna 1 arquivo | Coberto |
| Health `DOWN` em conflito de opções | `MongoIndexInitializerTest#deveFicarDownComConflitoDeOpcoes` + `MongoIndexHealthCheckTest#deveFicarDownComConflito` | Coberto |
| TTL canônico em `refresh_tokens`/`tokens_*` | `MongoIndexInitializerTest#deveCriarExatamenteIndicesCanonicos` | Coberto |
| `tokens_handoff_sso` no inventário com consumo atômico | `HandoffConsumoAtomicoTest` | Coberto |
| `BsonDates` estrito | `BsonDatesStrictTest` | Coberto |
| Nada de token em storage; boot por refresh single-flight | `authStorage.test.ts`, `bootRefresh.test.tsx` | Coberto |
| `AppSwitcher` APG + guarda de alterações | `appSwitcher.test.tsx` (10 testes) | Coberto |
| Tema persistido e aplicado antes do paint | `tema.test.ts`, `TemaPreferenciaTest` | Coberto |
| Regras ArchUnit de pacote habilitadas | `TenantIsolationArchTest` (regra ativa) | Coberto |

---

## 9. Bloqueios / pendências registradas (não pertencem à R2)

| Item | Dono | Evidência |
|---|---|---|
| `operacoes_concluir_atrasados` e `planejamento_ciclo` ainda criadas | R5/R9 | `InventarioColecoesTest.ALLOWLIST_LEGADO_R5` |
| Resíduo `handoff_tokens` em bancos antigos | operacional | `scripts/mongo/reset-dev.sh`; allowlist do teste |
| `BsonDates.lerInstant` aninhado em `LembretePushRepository`/`PushSubscriptionRepository` (conversão local `Date`) | R11 | `grep "date.toInstant()"` |
| Regras ArchUnit de assinatura `usuarioId` e `@SystemScope` | R3–R9 | `@ArchIgnore` com razão atualizada |
| `verificar-legado.sh` estrito ainda retorna 1 (213 achados: ciclo, nomenclatura, arquivos por fase) | R5/R7/R12 | saída do script |

