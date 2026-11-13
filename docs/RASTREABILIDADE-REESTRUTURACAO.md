# Rastreabilidade da Reestruturação Trilhara

> Matriz criada na **R0** (D-01). Regra: **nenhum critério de aceite é declarado
> "Atendido" sem um teste automatizado nomeado**. Status possíveis:
> `Coberto` (existe teste que falha se o comportamento regredir),
> `Parcial` (teste cobre parte do critério), `Sem teste` (lacuna a suprir na
> fase indicada). Atualizada a cada revisão.
>
> Baseline de referência: backend 144 testes / frontend 109 testes (R0).
> **R12:** backend 350+ testes / **frontend 57 arquivos e 438 testes**.

---

## A. Critérios transversais (documento §16)

| # | Critério de aceite | Teste(s) automatizado(s) | Status |
|---|---|---|---|
| A1 | Usuário A nunca acessa, altera ou exclui dados do usuário B por ID conhecido | `security/AuthorizationByOwnerTest` (13, anti-IDOR ⇒ 404); `auth/AccountDeletionIntegrationTest#excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital` (B intacto nas 18 filhas); `test/authExclusaoConta.test.ts` | Coberto |
| A2 | Datas de calendário não mudam entre navegador/backend; 21h–23h59 em `America/Sao_Paulo` usam a data local via `X-Timezone`; instantes em BSON Date | `shared/TimezoneContextTest` (6); `shared/mongo/BsonRoundTripTest` (7); `test/sessaoNormalizada.test.ts` | Coberto |
| A3 | Trilha grande não exige transferir disciplinas/tópicos ao listar Hub | `trilha/TrilhaNormalizadaIntegrationTest` (17, inclui `deveCriarTrilhaERetornarResumoSemArrayDeTopicosNoHub`) | Parcial — falta assert de payload/`explain` em trilha de 2.000 tópicos; `MongoExplainBenchmarkTest` é opt-in. **R3:** o resumo não tem mais `disciplinas`/`progresso` no contrato |
| A4 | Marcar/desmarcar tópico afeta somente aquele progresso e agregados | `trilha/TrilhaNormalizadaIntegrationTest#deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha`; `test/topicoOtimista.test.tsx` | **Coberto na R3** — inclui isolamento por trilha no path e rollback otimista |
| A5 | Regenerar cronograma substitui blocos via `versaoGeracao`/`versaoAtiva`, mantém sessões/progresso; concorrência nunca deixa dois ativos | `cronograma/CronogramaNormalizadoIntegrationTest` (8, inclui `deveCoordenarConcorrenciaComLockAtomicoNaTrilha`) + índice parcial único `idx_cronogramas_usuario_trilha_ativo_unique` | **Coberto na R5** — contador atômico `trilha.proximaVersaoCronograma`, fencing no ponteiro e `duplicateModalityNaoAlteraBlocos` |
| A6 | Tarefas/Pomodoro mostram as mesmas tarefas manuais Hoje + blocos do dia; nenhum bloco é gravado como tarefa | `test/agendaHojeService.test.ts` (19); `test/agendaHoje.test.tsx` (10); `tarefa/TarefaIntegrationTest` (16); `test/tarefasHoje.test.tsx` (5); `test/listaVirtual.test.tsx` (5) | **Coberto na R8** — renderização única em `shared/agenda/AgendaHoje.tsx`, ordens normativas em `ordenarAgenda.ts`, agrupamento único em `agruparAtrasadasPorSerie.ts` |
| A7 | Concluir manual nunca cria sessão; concluir bloco abre o modal global; cancelar não conclui | `test/globalSessionModal.test.tsx` (10); `test/sessaoGlobalService.test.ts` (5); `test/agendaHoje.test.tsx` (10); `test/pomodoroAgenda.test.tsx` (4) | **Coberto na R8** — `concluirTarefaManualNaoAbreModalEChamaAcaoDeTarefas`, `concluirBlocoChamaHandlerDeBlocoDireto` e `concluirTarefaNaoPausaOTimerNemAbreModal` |
| A8 | Retry após timeout não duplica sessão nem conclusão | `sessao/SessaoNormalizadaIntegrationTest` (9) + índice único `idx_sessoes_usuario_chaveIdempotencia_unique` | Parcial — falta retry concorrente com `operationId` repetido em `concluir-atrasados` |
| A9 | Sessão de 95s persiste `duracaoSegundos=95` e soma 95s; estatísticas conferem; editar/excluir recalcula | `sessao/SessaoNormalizadaIntegrationTest`; `test/sessaoNormalizada.test.ts` (6) | Coberto |
| A10 | Remover disciplina preserva histórico por padrão e permite apagar sessões/métricas explicitamente | `trilha/TrilhaNormalizadaIntegrationTest#deveRetornar409SeLeaseOcupadoAoExcluirDisciplina`; `sessao/SessaoNormalizadaIntegrationTest` | Coberto (R3 adicionou a guarda de lease na exclusão) |
| A11 | Alterar `dataVencimento` de ocorrência mantém `dataOriginalOcorrencia` e não recria na data original | `tarefa/Fase6RecorrenciaEngineTest` (5); `tarefa/RecorrenciaEngineTest` (8); `TarefaIntegrationTest#materializacaoConcorrenteNaoDuplicaNemRetorna500`; índice parcial `uk_tarefas_usuarioId_serieId_dataOriginalOcorrencia` | **Coberto na R7** — inclui `E11000` engolido na materialização concorrente |
| A12 | Autosave/busca/lixeira de Notas não transferem Markdown completo; rascunho local preservado em falha de rede | `nota/NotaResourceTest` (11); `nota/NotaSnippetExtractorTest` (2); `test/fase8Notas.test.tsx` (13) | Coberto |
| A13 | Tarefas/notas em lixeira restauradas no prazo/contexto originais; purgadas após o prazo | `TarefaIntegrationTest#restaurarTarefaComListaNaLixeiraRestauraSoContainer`; `#restaurarTarefaComListaPurgadaRetornaFlag`; `#purgaDeListaMoveSeriesParaListaGeral`; `test/lixeiraTarefas.test.tsx` (5) | **Coberto na R7** — restauração só do container, flag de realocação e purga de 30 dias |
| A14 | Logout/troca de conta sincronizam via `storage`/`BroadcastChannel`; entre subdomínios validação server-side ao focar; timers só na mesma origem | `test/sso.test.ts` (6); `fase5GlobalSessionTimer.test.tsx`; `appSwitcher.test.tsx` (6) | Parcial — sem teste de `BroadcastChannel` entre subdomínios |
| A15 | Falha de serviço aparece como erro recuperável (`SectionErrorCard`/`SyncStatusBadge`), nunca como "sem dados" | `test/fase7IntegracaoDiaria.test.tsx`; `fase8Notas.test.tsx` | Parcial — `SyncStatusBadge` sem teste dedicado |
| A16 | WCAG 2.2 AA (focus trap, `aria-live`, alvos mínimos, `scroll-padding-bottom`) nos 4 módulos | `test/a11y/modaisFoco.test.tsx` (6); `cronometrosLiveRegions.test.tsx` (2); `alvosToque.test.tsx` (8); `scrollPaddingBottomNav.test.tsx` (4); `reducedMotion.test.tsx` (7); `contrasteTokens.test.ts` (96) | **Coberto na R12** — a F11 provava a11y com mock de JSX e leitura de texto do CSS; a R12 passou a exercitar os componentes reais, tokens por componente e contraste calculado nos dois temas |
| A18 | Listas paginadas com ordenação estável (`_id` desempate) e filtro de tenant; erro de uma fonte não remove dados da outra | `AuthorizationByOwnerTest`; `CronogramaNormalizadoIntegrationTest`; `test/fase7IntegracaoDiaria.test.tsx` | **Coberto na R5** — `tamanho` máx. de blocos agora é 100 com `400 INVALID_PAGINATION` |
| A19 | Alternar cronograma ativo mostra versão anterior ou nova completa, nunca staging incompleto | `PublicacaoCronogramaFaultInjectionTest#falhaNaEtapa1..6_leitorVeVersaoAnteriorOuNovaCompleta` | **Coberto na R5** |
| A20 | Geração de cronograma com falha injetada em cada etapa; lease expirado não publica | `PublicacaoCronogramaFaultInjectionTest` (7: 6 etapas + `leaseExpiradoNaoPublica`) | **Coberto na R5** — `FaultInjector` só no classpath de teste |
| A21 | Materialização recorrente idempotente sob concorrência; casos diário/semanal/mensal em virada de ano/ISO/meses curtos | `tarefa/Fase6RecorrenciaEngineTest` (5) | Parcial — falta caso virada de ano/semana ISO |
| A22 | Exclusão/restauração de lista/caderno/seção respeitam lotes e descendentes já purgados | `tarefa/ListaTarefasResourceTest` (1); `nota/NotaResourceTest#lixeiraPaginadaRestauraAncestraisDoMesmoLote`; `#loteRespeitaItensJaExcluidosEDescendentesPurgados` | **Coberto na R12** — o novo caso prova os três cenários do critério: item já excluído isoladamente mantém o lote original, descendente já purgado não é ressuscitado e a página ativa volta com o caderno |
| A23 | Scheduler push não processa o mesmo item em paralelo; expirados não enviados; backoff | `push/PushDeliverySchedulerTest` (6: `doisWorkersNaoProcessamOMesmoItem`, `atrasoMaiorQue15MinExpiraSemEnviar`, `falhaTransitoriaRespeitaJanela`, `falhaSemTempoNaJanelaTerminaEmFalhaFinal`, `gone410RemoveInscricao`, `ttl900NoEnvio`); `push/LembretePushFilaFiltroTest` (3); `push/LembretePushLifecycleTest` (9); `push/LembretePushCalculatorTest` (5); `push/WebPushCriptografiaTest` (4); `push/PushPayloadFactoryTest` (6); `push/PushResourceTest` (7) | **Coberto na R11** — modelo versionado por D-36a, claim atômico com `leaseToken`/`leaseAte`, backoff derivado de `atualizadoEm`, payload cifrado RFC 8291 e `url` absoluta com allowlist. Execução dos 3 arquivos `@QuarkusTest` pendente de ambiente com Mongo (ver §0 do relatório R11) |
| A24 | Frontend com Vitest + RTL + jsdom; cada critério de UI tem teste executável por `npm test` + `npm run build` | 14 arquivos / 109 testes; `npm run build` verde | Coberto |
| A25 | Conta excluída retorna 202 e worker conclui as 18 coleções sem órfãos, mesmo com falha injetada | `auth/AccountDeletionIntegrationTest` (29: `excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital`, `falhaInjetadaEmCadaColecaoRetomaSemOrfaos` parametrizado por **18** filhas, `apos5FalhasEntraEmRetentandoLentoENaoConclui`, `retomadaAposRetentandoLentoConcluiSemOrfaos`, `logsNaoContemUsuarioId`, `semIdempotencyKeyRetorna400`, `senhaInvalidaRetorna401ESemEfeito`, `respostaNaoEhProblemJson`); `test/contaModal.test.tsx`; `test/authExclusaoConta.test.ts` | **Coberto na R10** — fixtures dirigidas por `ColecaoTrilhara.filhasPrivadas()` (faltar fixture falha o teste), hook de produção removido em favor de `FaultInjectorExclusaoConta`, estado terminal `FALHA` substituído por `RETENTANDO_LENTO` com retentativa horária, logs pseudonimizados |
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
| ~~`tamanho` máximo de paginação = 200 nos blocos~~ → **resolvido na R5** (`tamanho` ≤ 100 com `400 INVALID_PAGINATION`) | — | `BlocoCronogramaRepository.TAMANHO_MAXIMO_PAGINA` |
| ~~`toObjectIdOrNotFound` retornava 404 para ID malformado~~ → **resolvido na R1** | — | `shared/InvalidIdentifierTest`; `BsonIds.parse` |
| ~~3 coleções fora do inventário~~ → **resolvido na R5** (allowlist zerada: `operacoes_concluir_atrasados` removida e `planejamento_ciclo` não é mais escrita) | — | `InventarioColecoesTest.ALLOWLIST_LEGADO` vazio |
| ~~`HandoffService` cria índices fora do `MongoIndexInitializer`~~ → **resolvido na R2** | — | `IndicesCanonicos`; `grep createIndex backend/src/main` → 1 |
| ~~`MongoIndexInitializerTest` valida só nomes~~ → **resolvido na R2** | — | `#deveCriarExatamenteIndicesCanonicos` |
| ~~`scripts/mongo/reset-dev.sh` inexistente~~ → **criado na R2** | — | `scripts/mongo/reset-dev.sh` |
| ~~Resíduo `Ciclo`/`planejamento_ciclo` em `trilha/` + refs `ciclo` no frontend~~ → **resolvido na R5**: `termo-ciclo-backend` 0 e frontend só mantém "ciclo Pomodoro" (preservado pelo guia) | — | `verificar-legado.sh --quiet` |
| ~~`tarefaStorage.ts` referenciava campos inexistentes~~ → **resolvido na R1** (arquivo removido) | — | `knip` exit 0 |
| ~~Anexo C precisa de decisão sobre `usaPadraoGlobalLembrete`~~ → **resolvido na R7** (`ModoLembrete` explícito); `OperacaoConcluirAtrasados` segue em R8 | R8 | `verificar-legado.sh --quiet` → `nomes-nao-canonicos-d02` = 0 |
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
| Nenhuma coleção da aplicação fora do enum | `InventarioColecoesTest#naoDeveExistirColecaoForaDoInventario` | **Coberto na R5** — allowlist vazia |
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

## F. Critérios específicos da R3 (F2: trilhas, disciplinas, tópicos e progresso)

| Critério | Teste / comando | Status |
|---|---|---|
| Rota legada `PATCH /api/trilhas/{id}/progresso/{topicoId}` removida (D-11) | `TrilhaNormalizadaIntegrationTest#naoDeveExistirRotaLegadaDeProgresso` | Coberto |
| Limites 50/100/500 respondem `409 RESOURCE_LIMIT_EXCEEDED` | `#deveRetornar409ResourceLimitAoExcederLimites` | Coberto |
| `editalId` **xor** `disciplinasIniciais` → 400 `VALIDATION_ERROR` em `errors.editalId` | `#deveRejeitarEditalEDisciplinasIniciaisJuntos` | Coberto |
| `editalId` inexistente/não publicado → 400 no campo (SEC-09) | `#deveRejeitarEditalInexistenteCom400NoCampoEditalId` | Coberto |
| Tópico de outra trilha no path → 404 | `#deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha` | Coberto |
| ID malformado na reordenação → 400 `INVALID_IDENTIFIER` | `#deveRejeitarIdMalformadoComInvalidIdentifierNaReordenacao` | Coberto |
| Lease de 120 s nas exclusões de trilha e disciplina (409 se ocupado) | `#deveRetornar409SeLeaseOcupadoAoExcluirDisciplina`; `LeaseTrilhaService` | Coberto |
| `GET /impacto-exclusao` com as quatro contagens | `#impactoExclusaoTrilhaContaCorretamente` | Coberto |
| Isolamento A×B em todas as rotas de trilha/disciplina/progresso | `#deveIsolarTodasAsRotasEntreUsuarios`; `security/AuthorizationByOwnerTest` | Coberto |
| `Disciplina`/DTOs/UI sem `cadernoLink` (DC-04) | `grep -rE "adaptarTrilha\|progresso/\{topicoId\}\|cadernoId" frontend/src/modules/estudos backend/src/main/java/br/com/trilhara/trilha` → 0 | Coberto |
| Sem adaptadores nem tipo `Trilha` duplo (DC-05/D-11) | `verificar-legado.sh` → `adaptadores-legados` 8 → **0**; `npx knip` exit 0 | Coberto |
| Hub com `Skeleton`/`EmptyState`/`SectionErrorCard`+retry | `src/test/hub.test.tsx` (5) | Coberto |
| Marcar/desmarcar tópico otimista com rollback (D-39) | `src/test/topicoOtimista.test.tsx` (2) | Coberto |
| Exclusão de trilha com nome exato e impacto em números (D-38) | `src/test/excluirTrilha.test.tsx` (3) | Coberto |
| Regra ArchUnit de `usuarioId` habilitada para o pacote `trilha` | `TenantIsolationArchTest#repositoriosDeTrilhaExigemUsuarioId` | Coberto |
| Gate comum verde | `docs/REVISAO-IMPLEMENTADA-R3.md` §5 (190 backend / 177 frontend) | Coberto |

---

## G. Critérios específicos da R4 (F3: sessões, histórico e estatísticas)

| Critério | Teste / evidência | Status |
|---|---|---|
| `Idempotency-Key` obrigatória (ausente → 400) | `SessaoNormalizadaIntegrationTest#deveExigirIdempotencyKey` | Coberto |
| Concorrência da mesma chave (1 documento, 201+200) | `#deveTratarConcorrenciaDaMesmaChave` | Coberto |
| Retry após editar devolve sessão atual | `#retryAposEditarRetornaSessaoAtual` | Coberto |
| Retry após excluir → 409 `IDEMPOTENCY_RESULT_REMOVED` | `#retryAposExcluirRetorna409ResultRemoved` | Coberto |
| `editavel` global (D-13), independente de filtro | `#editavelEhGlobalMesmoComFiltroDeDisciplina` | Coberto |
| Página 2 nunca editável | `#editavelFalsoNaPagina2` | Coberto |
| Tombstone fora de listagem e agregação (D-15) | `#tombstoneNaoApareceEmListagemNemEstatistica` | Coberto |
| Período invertido → 400 | `#periodoInvertidoRetorna400` | Coberto |
| Estatística vazia → zeros e percentual 0 | `#estatisticaVaziaRetornaZerosEPercentual0` | Coberto |
| Granularidade DIA/SEMANA/MES preserva totais (RI-09) | `#granularidadeSemanalPreservaTotais`; `SessaoServiceHashTest#deveDerivarGranularidade`; `#deveCalcularChaveDoBucket` | Coberto |
| `disciplinaId` de outra trilha → 400 em `errors.disciplinaId` | `#disciplinaDeOutraTrilhaRetorna400` | Coberto |
| `dataSessao` futura → 400 em `errors.dataSessao` | `#dataSessaoFuturaRetorna400NoCampoDataSessao` | Coberto |
| `requestHash` = SHA-256 de JSON canônico ordenado | `SessaoServiceHashTest#deveSerDeterministicoParaMesmoPayload`; `#deveProduzirHashDoJsonCanonico`; `#naoDeveColidirEntreDuracaoEQuestoes` | Coberto |
| Streak D-14 considera ontem quando não há sessão hoje | `#streakConsideraOntemQuandoSemSessaoHoje`; `StreakCalculatorTest#deveTerminarOntemSemSessaoHoje` | Coberto |
| Streak independe do filtro de período | `#streakIndependeDoFiltroDePeriodo` | Coberto |
| Borda 23h30 em `America/Sao_Paulo` | `StreakCalculatorTest#deveContarDiaLocalAs23h30EmSaoPaulo`; `#deveManterStreakQuandoUltimaSessaoFoiAs23h30` | Coberto |
| `PurgaTombstonesSessaoJob` (`@SystemScope`, 24h) | `PurgaTombstonesSessaoJobTest` (4 testes) | Coberto |
| Nenhum `POST .../sessoes` fora de `sessaoGlobalService` | `rg "/sessoes" frontend/src --glob '!**/sessaoGlobalService.ts'` → apenas GET/PATCH/DELETE | Coberto |
| Tipos sem `minutos`/`erros`/`tipo` persistidos | `sessaoNormalizada.test.ts` (`not.toHaveProperty`); `verificar-legado.sh` `simbolos-legado` 31 → 29 | Coberto |
| Dashboard com D-37 (Skeleton/EmptyState/SectionErrorCard) | `src/test/dashboard.test.tsx` (5) | Coberto |
| Grupo removido + confirmação com contagem/tempo (D-38) | `dashboard.test.tsx` | Coberto |
| Edição com guarda e 409 `LATEST_SESSION_ONLY` → toast + refetch | `src/test/editarSessao.test.tsx` (2) | Coberto |
| Regra ArchUnit do pacote `sessao` habilitada | `TenantIsolationArchTest#repositoriosDeSessaoExigemUsuarioId` | Coberto |
| Contagem de testes ≥ baseline R0 (144 backend / 109 frontend) | 221 backend / 183 frontend | Coberto |
| Gate comum verde | `docs/REVISAO-IMPLEMENTADA-R4.md` §5 | Coberto |

## H. Critérios específicos da R6 (F5: `GlobalSessionModal`, `sessaoGlobalService` e timers)

| Critério | Teste / comando | Status |
|---|---|---|
| `registrarSessao` envia `Idempotency-Key` no header e `chaveIdempotencia` no corpo com o mesmo valor | `sessaoGlobalService.test.ts#envia header Idempotency-Key e body com a mesma chave` | Coberto |
| Fluxo em 2 etapas conclui sessão e bloco | `#executa o fluxo de duas etapas concluindo sessão e bloco` | Coberto |
| Falha na etapa 2 → retry só do PATCH (POST chamado 1×) | `#falha na etapa 2 permite retry apenas do PATCH sem reenviar o POST` | Coberto |
| `401` → evento `trilhara:auth-required` e retry com a mesma chave | `#401 dispara trilhara:auth-required e o retry reutiliza a mesma chave após login` | Coberto |
| Desconcluir bloco não exclui sessões e usa `PATCH {concluido:false}` | `#concluirBloco envia PATCH concluido true e reabrirBloco envia false` | Coberto |
| 4 modos abrem com título e campos corretos; rótulo fixo **Salvar sessão** | `globalSessionModal.test.tsx#abre em ESTUDOS_TIMER com título, tempo medido e rótulo fixo`; `#abre em BLOCO_DIRETO...`; `#abre em BLOCO_TIMER e MANUAL...` | Coberto |
| **Concluir sem salvar sessão** só em modos com bloco | `#"Concluir sem salvar sessão" aparece apenas em modos com bloco` | Coberto |
| Cancelar mantém o timer pausado com o tempo acumulado (D-22.1) | `#cancelar mantém o cronômetro pausado com o tempo acumulado` | Coberto |
| Fechar sujo pede confirmação de descarte (D-22.2) | `#fechar com campos alterados pede confirmação de descarte` | Coberto |
| Duração mínima de 60 s com `40 s` → erro inline | `#40 s exibe erro inline de duração mínima` | Coberto |
| Acertos > questões → erro inline por campo | `#acertos maiores que questões exibem erro inline` | Coberto |
| Offline mantém os campos e mostra "Sem conexão. Seus dados foram mantidos." | `#offline mantém os campos preenchidos e exibe banner de conexão` | Coberto |
| Salvar zera o timer e **remove** a chave `study_timer` (D-22.1) | `#salvar com sucesso zera o timer e remove a chave`; `studyTimer.test.tsx#reset remove a chave do usuário (D-22.1)` | Coberto |
| Sem usuário, o cronômetro de Estudos fica inativo e não grava nenhuma chave | `studyTimer.test.tsx#sem usuário fica inativo, ignora o início e não grava nenhuma chave` | Coberto |
| Chaves de timer isoladas entre Usuário A e Usuário B | `#isola as chaves de Usuário A e Usuário B` | Coberto |
| Logout pausa, acumula e persiste sob a conta | `#logout pausa, acumula o tempo e persiste na chave da conta` | Coberto |
| Mensagem do barramento de outro `usuarioId` é descartada | `#ignora mensagem do barramento com usuarioId diferente`; `barramento.test.ts#descarta mensagem de outro usuarioId` | Coberto |
| Alarme não abre modal nem grava sessão | `#o alarme não abre modal de sessão` | Coberto |
| Drift ≤ 1 s após 10 min suspenso (timestamp absoluto) | `#mantém drift ≤ 1 s após 10 min "suspenso" (timestamp absoluto)` | Coberto |
| Visitante do Pomodoro usa `trilhara:anon:pomodoro_timer` e o login não migra | `pomodoro.test.tsx#visitante usa trilhara:anon:pomodoro_timer`; `#login não migra o estado anônimo para a chave da conta` | Coberto |
| Configuração do Pomodoro escopada por conta (sem chaves globais) | `#não há chaves globais de configuração do Pomodoro`; `#configuração pertence ao objeto persistido da conta` | Coberto |
| Barramento único `v:1`, filtro por `usuarioId` e fallback `storage` | `barramento.test.ts` (8) | Coberto |
| Os 4 apps abrem o modal só via `useGlobalSessionModal()` | `rg "abrirModal\\(" frontend/src/modules` → apenas chamadas do hook | Coberto |
| Grep de canais antigos zerado | `rg "trilhara_study_timer|trilhara_pomodoro_timer|trilhara_agenda_hoje|'trilhara:auth'|\|\| 'anon'" frontend/src` → 0 | Coberto |
| Gate comum verde | `docs/REVISAO-IMPLEMENTADA-R6.md` §5 | Coberto |

## I. Critérios específicos da R7 (F6: tarefas manuais, recorrência e lixeira)

| Critério | Teste / comando | Status |
|---|---|---|
| `GET /api/tarefas/hoje` devolve apenas `PENDENTE` | `tarefa/TarefaIntegrationTest#hojeNaoContemConcluidas`; `test/tarefasHoje.test.tsx` (5) | Coberto |
| `TODA_A_SERIE` preserva `serieId`/`dataOriginalOcorrencia` das concluídas | `#todaASeriePreservaSerieIdDasConcluidas` | Coberto |
| `ESTA_E_FUTURAS` (edição) preserva as ocorrências desvinculadas | `#estaEFuturasEdicaoPreservaDesvinculadas` | Coberto |
| Retry de `ESTA_E_FUTURAS` com a mesma `Idempotency-Key` não duplica a série sucessora (200) | `#estaEFuturasRetryComMesmaChaveNaoDuplicaSerie` | Coberto |
| `ESTA_E_FUTURAS` sem `Idempotency-Key` → 400 `IDEMPOTENCY_KEY_REQUIRED` | `#estaEFuturasSemChaveRetorna400` | Coberto |
| `escopo` ausente em ocorrência de série → 400 `VALIDATION_ERROR` | `#escopoAusenteEmTarefaRecorrenteRetorna400` | Coberto |
| Materialização concorrente não duplica nem devolve 500 (10 GETs paralelos) | `#materializacaoConcorrenteNaoDuplicaNemRetorna500` | Coberto |
| Série com lista na lixeira não materializa | `#serieDeListaNaLixeiraNaoMaterializa` | Coberto |
| Purga da lista move séries sobreviventes para a Lista Geral | `#purgaDeListaMoveSeriesParaListaGeral` | Coberto |
| Restaurar tarefa com lista na lixeira restaura **só o container** | `#restaurarTarefaComListaNaLixeiraRestauraSoContainer` | Coberto |
| Restaurar tarefa com lista purgada devolve `realocadaParaListaGeral` | `#restaurarTarefaComListaPurgadaRetornaFlag`; `test/lixeiraTarefas.test.tsx` | Coberto |
| `listaId` de outro usuário no body → 400 `VALIDATION_ERROR` | `#listaIdDeOutroUsuarioRetorna400` | Coberto |
| `GET /api/tarefas` paginado 50/100 com ordenação estável e `INVALID_PAGINATION` | `#paginacaoTarefasOrdenacaoEstavel` | Coberto |
| Lixeira paginada com filtro `expiresAt > agora` | `#lixeiraPaginadaApenasItensVigentes` | Coberto |
| `impacto-exclusao` de listas (D-33) | `#impactoExclusaoListaContaTarefasESeries` | Coberto |
| Isolamento A×B em todas as rotas de tarefas (404) e 401 sem autenticação | `#isolamentoEntreUsuariosEmTodasAsRotas` | Coberto |
| Calendário: virada de ano, semana ISO 53, fev/2027 e fev/2028, ordinal 5 e `-1` | `tarefa/RecorrenciaEngineTest` (8, 2 parametrizados) | Coberto |
| `APOS_OCORRENCIAS` conta canceladas/movidas/desvinculadas; `EM_DATA` inclusiva | `#terminoAposOcorrenciasConsideraPosicoesConsumidas`; `#terminoEmDataEhInclusivo` | Coberto |
| `modoLembrete` explícito: **SEM_ALERTA ≠ HERDAR** no payload | `test/lembreteSeletor.test.tsx` (4) | Coberto |
| Escopos de recorrência no cliente com `Idempotency-Key` | `test/escopoRecorrencia.test.tsx` (5) | Coberto |
| Repositórios de `tarefa` exigem `usuarioId` (D-06a) | `arquitetura/TenantIsolationArchTest#repositoriosDeTarefaExigemUsuarioId` | Coberto |
| Grep do critério R7 §4 zerado | `grep -rn "usaPadraoGlobalLembrete\|dataTermino\|lista padr" backend frontend` → 0 | Coberto |
| Gate comum verde | `docs/REVISAO-IMPLEMENTADA-R7.md` §5 | Coberto |

## J. Critérios específicos da R8 (F7: Visão Hoje compartilhada, listas virtuais e Pomodoro)

| Critério | Teste / comando | Status |
|---|---|---|
| Estado por `usuarioId` com reset em logout, exclusão e troca de conta (D-46) | `test/agendaHojeService.test.ts#trocaDeContaZeraEstadoSemItensDaContaAnterior`; `#novaAssinaturaNuncaRecebeDadosDeOutraConta`; `#contaExcluidaZeraEstadoDaConta` | Coberto |
| Visitante não dispara requisição na Visão Hoje | `#visitanteSemUsuarioNaoTemAgendaDerivada`; `pomodoroAgenda.test.tsx#visitanteVeCardEEntrarNaContaAbreAuthModal`; `listaVirtual.test.tsx` | Coberto |
| Virada de `hojeLocal` descarta o dia anterior e recarrega uma vez | `#viradaDeHojeLocalDescartaEstadoDoDiaAnteriorERecarrega` | Coberto |
| Throttle de 5 s em `focus`/`visibilitychange` | `#throttleDeCincoSegundosEvitaRajadasDeRequisicoes` (fake timers) | Coberto |
| Ordens normativas das três seções com desempate por `id` | `#ordenaAtrasadasPorDataPrioridadeOrdemComIdComoDesempate`; `#ordenaTarefasDeHojeComHorarioNuloPorUltimo`; `#ordenaSemDataPorPrioridadeDescEOrdemAsc`; `#ordenaBlocosDeHojePorTrilhaOrdemEBlocoId`; `#derivaAtrasadasHojeESemDataNasOrdensNormativas` | Coberto |
| Agrupamento único de atrasadas por série (D-23) sob a mais recente | `#agrupaSerieSobOcorrenciaMaisRecenteComAnterioresAscendentes`; `#tarefaDesvinculadaDaSerieNaoEhAgrupada`; `agendaHoje.test.tsx#atrasadasAgrupadasExibemExpansorDeOcorrenciasAnteriores` | Coberto |
| Falha parcial preserva a fonte sadia e o retry é isolado | `#falhaDeBlocosMantemTarefasEErroBlocos`; `#falhaDeTarefasMantemBlocosEErroTarefas`; `#retryRecarregaSomenteAFonteComFalha`; `#retryDeTarefasNaoRecarregaBlocos` | Coberto |
| `SectionErrorCard variant="inline"` apenas no subgrupo afetado | `agendaHoje.test.tsx#erroDeBlocosRenderizaSectionErrorCardInlineSomenteNoSubgrupoBlocos`; `#erroDeTarefasRenderizaInlineEmAtrasadasTarefasDeHojeESemData` | Coberto |
| Skeleton `row` ×3 por seção após 200 ms | `agendaHoje.test.tsx#exibe skeleton row após 200 ms por seção no carregamento inicial` | Coberto |
| Vazio total → `EmptyState` do Anexo B | `agendaHoje.test.tsx#tudoVazioExibeEmptyStateTudoEmDiaPorHoje`; `tarefasHoje.test.tsx` | Coberto |
| Concluir manual não abre modal; concluir bloco abre `BLOCO_DIRETO` | `agendaHoje.test.tsx#concluirTarefaManualNaoAbreModalEChamaAcaoDeTarefas`; `#concluirBlocoChamaHandlerDeBlocoDireto`; `pomodoroAgenda.test.tsx#concluirTarefaNaoPausaOTimerNemAbreModal` | Coberto |
| Contagem N da lista virtual vem de `totalItens` (`statusData=ATRASADO&tamanho=1`) | `listaVirtual.test.tsx#contagemDeAtrasadasUsaTotalItensComTamanhoUm` | Coberto |
| "Carregar mais" e resultado parcial do lote com retry | `listaVirtual.test.tsx#carregarMaisAcrescentaAProximaPaginaDeBlocos`; `#resultadoParcialDoLoteExibeToastComTentarNovamente` | Coberto |
| Blocos não expõem editar/excluir e filtros gerais não renderizam blocos | `listaVirtual.test.tsx#blocosDaListaVirtualNaoExpoemEditarNemExcluir`; `#filtrosGeraisNaoRenderizamBlocos` | Coberto |
| Foco do Pomodoro persiste só `{tipo,id}` e é ignorado se o item sumir | `pomodoroAgenda.test.tsx#focoAtualPersisteSomenteIdETipoNaChaveDaConta`; `#focoEhDescartadoQuandoOItemSomeDaAgenda` | Coberto |
| `ItemAgendaHojeDTO` resolvido (removido) | `grep -rn "ItemAgendaHojeDTO" frontend/src` → 0 | Coberto |
| Agrupamento existe uma única vez em `shared/agenda` | `rg "agruparAtrasadas" frontend/src -l` → 4 arquivos, todos em `shared/agenda`/teste | Coberto |
| Gate comum | `docs/REVISAO-IMPLEMENTADA-R8.md` §5 | Coberto (backend em §6) |

## K. Critérios específicos da R9 (F8: Notas)

| Critério | Teste / comando | Status |
|---|---|---|
| Importar trilha já vinculada sem `reimportar=true` → `409 TRAIL_ALREADY_IMPORTED` com `errors.cadernoId` | `nota/NotaResourceTest#importarSemReimportarComVinculoExistenteRetorna409` | Coberto |
| Reimportação mantém o caderno antigo, desvincula e cria novo com `titulo = trilha.nome` | `#reimportarDesvinculaAntigoECriaNovoComNomeDaTrilha` | Coberto |
| Trilha de outro usuário → `404` (sem revelar existência) | `#importarTrilhaDeOutroUsuarioRetorna404` | Coberto |
| Sincronização não cria nem altera páginas de seções existentes (RI-29) | `#sincronizarNaoCriaNotasEmSecoesExistentes` | Coberto |
| Sincronização cria seção + páginas apenas para disciplina nova | `#sincronizarCriaSecaoParaDisciplinaNovaComPaginas` | Coberto |
| Caderno sem origem → `400 VALIDATION_ERROR` com `errors.cadernoId` | `#sincronizarCadernoSemOrigemRetorna400ComCampo` | Coberto |
| `impacto-exclusao` conta somente itens ativos | `#impactoExclusaoContaSoAtivos` | Coberto |
| Lixeira paginada com `breadcrumb` e restauração de ancestrais do lote | `#lixeiraPaginadaRestauraAncestraisDoMesmoLote` | Coberto |
| Restauração fora do prazo → `404 TRASH_ITEM_EXPIRED`; item nunca excluído → `404 NOT_FOUND` | `#restaurarForaDoPrazoRetorna404TrashItemExpired` | Coberto |
| Ancestral purgado → "Restauradas" com `realocadaPara` | `#restaurarComCadernoPurgadoCriaCadernoESecaoRestauradas` | Coberto |
| Snippet em texto puro ≤ 180 com `destaques` UTF-16 corretos sob acento | `#buscaRetornaSnippetTextoPuroComDestaques`; `NotaSnippetExtractorTest#deveExtrairSnippetComDestaqueDeTermoAcentuado`; `#snippetNuncaExcedeOLimite` | Coberto |
| Busca ignora lixeira e outro usuário; query fraca → `400 INVALID_SEARCH_QUERY` | `#buscaNaoRetornaLixeiraNemOutroUsuario`; `NotaSnippetExtractorTest#deveRejeitarApenasStopWords` | Coberto |
| Endpoint canônico de página; rota raiz de criação e leitura aninhada ausentes | `#endpointAninhadoDeNotaNaoExiste`; `#rotaDuplicadaDeCriacaoNaoExiste` | Coberto |
| Seção de outro caderno no path → `404` | `#notaComSecaoDeOutroCadernoRetorna404` | Coberto |
| Listagem de páginas paginada e sem `conteudoMarkdown` | `#listagemNaoContemConteudoMarkdown`; `#paginacaoInvalidaRetorna400` | Coberto |
| Markdown > 512 KB ou com caractere de controle → `400 VALIDATION_ERROR` | `#markdownAcimaDe512KBRetorna400ValidationError`; `#markdownComCaractereDeControleRetorna400` | Coberto |
| Teto de 500 cadernos → `409 RESOURCE_LIMIT_EXCEEDED` | `#limiteDe500CadernosRetorna409` | Coberto |
| Filtro `?origemTrilhaId=` e `400 INVALID_IDENTIFIER` | `#filtroOrigemTrilhaId` | Coberto |
| Caderno com zero seções ativas é estado válido | `#deveGerenciarCicloCompletoEPermitirCadernoComZeroSecoes`; `cadernoVazio.test.tsx` | Coberto |
| Esvaziar lixeira e purga `@SystemScope` | `#esvaziarLixeiraEPurgar`; `nota/NotaPurgaService` | Coberto |
| Autosave: debounce 800 ms, `seq`, `AbortController`, flush e rascunho só após 200 igual | `autosave.test.tsx` (11) | Coberto |
| Recuperação de rascunho por diferença de conteúdo + aviso de versão do servidor | `recuperacaoRascunho.test.tsx` (6) | Coberto |
| Busca no cliente: 2 alfanuméricos, 300 ms, `<mark>` por offsets, XSS como texto, 429 | `buscaModal.test.tsx` (8) | Coberto |
| Preferência `trilhara:u:{id}:notas_ocultar_modal_importacao` + reimportação confirmada | `importacaoAutomatica.test.tsx` (4) | Coberto |
| `SyncStatusBadge` com quatro estados e retry | `syncStatusBadge.test.tsx` (5) | Coberto |
| Allowlist de protocolos de link no editor | `linkSeguro.test.ts` (6) | Coberto |
| Repositórios de `nota` exigem `usuarioId` e não buscam sem ele | `arquitetura/TenantIsolationArchTest#repositoriosDeNotaExigemUsuarioId`; `#repositoriosDeNotaNaoBuscamSemUsuario` | Coberto |
| Gate comum | `docs/REVISAO-IMPLEMENTADA-R9.md` §5 e §6 | Coberto; ressalva em §7 (achados herdados de R12/R13) |


## L. Critérios específicos da R10 (F9: exclusão de conta e isolamento ponta a ponta)

| Critério | Teste / comando | Status |
|---|---|---|
| `DELETE /api/auth/conta` exige `Idempotency-Key` no cabeçalho | `auth/AccountDeletionIntegrationTest#semIdempotencyKeyRetorna400` (`400 IDEMPOTENCY_KEY_REQUIRED`) | Coberto |
| Chave no corpo não existe; header é o único canal | `test/authExclusaoConta.test.ts#envia Idempotency-Key no cabeçalho e não duplica a chave no corpo` | Coberto |
| Frase de confirmação exata (sensível a caixa) | `#confirmacaoEmMinusculasRetorna400` (`VALIDATION_ERROR` + `errors.confirmacao`) | Coberto |
| Senha inválida → `401` sem efeito colateral | `#senhaInvalidaRetorna401ESemEfeito` (nenhum documento removido, `ativo=true`, sem `exclusaoConta`) | Coberto |
| Rate limit D-45 de 5/15 min por usuário | `RateLimitService.verificarLimiteExclusaoConta` + `RateLimitExceptionMapper` (`429` + `Retry-After`) | Coberto por implementação; `429` fim-a-fim exercitado no cliente por `test/contaModal.test.tsx#exibe o tempo de espera quando o servidor responde 429` |
| `202` é `application/json` com `{operationId, estado}`, não Problem Details | `#respostaNaoEhProblemJson` | Coberto |
| Repetição com a mesma chave devolve o mesmo `operationId` | `#retryComMesmaChaveRetorna202ComMesmoOperationId` | Coberto |
| Mesma chave com payload diferente / outra chave → `409 IDEMPOTENCY_KEY_CONFLICT` | `#outraChaveRetorna409`; `AccountDeletionService.calcularHashPayload` | Coberto |
| Cookies `trilhara_rt` e `trilhara_session_state` expirados (`Max-Age=0`) | `#excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital` | Coberto |
| As 18 filhas privadas (incluindo `tokens_handoff_sso`) são purgadas | `#excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital` (laço em `nomesFilhasPrivadas()`) | Coberto |
| Fixture ausente para qualquer filha falha o teste (guarda do D-08) | `FixturesConta.validarCobertura()` invocado por `popularColecoesFilhas` | Coberto |
| Outro usuário e o catálogo `editais` permanecem intactos | `#excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital` | Coberto |
| `usuarios` é removido por último, após contagem zero nas filhas | `AccountDeletionService.executarLimpezaUsuario` (verificação antes do `deleteOne`) | Coberto |
| Falha injetada em cada uma das 18 filhas retoma sem órfãos | `#falhaInjetadaEmCadaColecaoRetomaSemOrfaos` (`@EnumSource` com as 18) | Coberto |
| Hook de falha não existe em produção | `grep -rn "testFailOnCollectionHook\|setHookFalha\|HookFalhaExclusao" backend/src` → 0; `FaultInjectorExclusaoContaNoOp` é o bean padrão | Coberto |
| Após 5 tentativas → `RETENTANDO_LENTO`, sem concluir, retentativa horária | `#apos5FalhasEntraEmRetentandoLentoENaoConclui` (`estado`, `tentativas=5`, `proximaTentativa > +50min`) | Coberto |
| Retomada a partir de `RETENTANDO_LENTO` conclui sem órfãos | `#retomadaAposRetentandoLentoConcluiSemOrfaos` | Coberto |
| Métrica `trilhara_account_deletion_stuck` incrementada ao esgotar as tentativas rápidas | `AccountDeletionService.registrarFalha` (`Counter.increment`) + `LOG.errorf(event=ACCOUNT_DELETION_STUCK)` | Coberto por implementação; registry Prometheus não exposto (ver §7.2 do relatório R10) |
| Logs da exclusão sem `usuarioId`, e-mail ou nome | `#logsNaoContemUsuarioId` (`CapturadorDeLogs`) | Coberto |
| Nenhum log do backend contém `usuarioId` | `grep -rn "usuarioId" backend/src/main --include=*.java \| grep -i "log\."` → 0 | Coberto |
| Conta em exclusão → `401` em qualquer rota; retry do `DELETE` com chave → `202` | `#pos202QualquerRotaRetorna401`; `SecurityFilter.autenticarSeNecessario` + `UserRepository.estaEmExclusao` | Coberto |
| Frontend: botão habilitado só com senha e a frase exata | `test/contaModal.test.tsx` (4 casos) | Coberto |
| Frontend: 401 → erro inline; 429 → tempo de espera | `test/contaModal.test.tsx#exibe "Senha atual incorreta."…`; `#exibe o tempo de espera…` | Coberto |
| Frontend: 202 → limpeza de `trilhara:u:{id}:*`, broadcast `CONTA_EXCLUIDA` e redirecionamento | `test/authExclusaoConta.test.ts` (8 casos) | Coberto |
| Frontend: entrada de Conta centralizada nos 4 módulos (inclui Pomodoro) | `test/appSidebarConta.test.tsx` (7 casos); `ContaMenuButton` no `AppSidebar` e no header do Pomodoro | Coberto |
| Entrada de Conta não é mais propagada por prop entre apps | `grep -rn "onAbrirConta\|onOpenAccount" frontend/src --include=*.tsx` → só `ContaMenuButton` | Coberto |
| Mensagem normativa "Sua conta está sendo excluída…" na tela pública | `test/authExclusaoConta.test.ts` + `AvisoContaExcluida` nos 4 módulos | Coberto |
| Gate comum | `docs/REVISAO-IMPLEMENTADA-R10.md` §5 e §6 | Coberto; ressalva em §7 (14 achados herdados de R12/R13) |

## M. Criterios especificos da R11 (F10: Web Push de Tarefas)

| Criterio | Teste / comando | Status |
|---|---|---|
| Modelo `lembretes_push` exatamente conforme D-36a (`long versaoAgendamento`, `leaseToken`/`leaseAte`, sem campos extras) | `push/LembretePush`; `rg "horarioDisparoUtc|usaPadraoGlobalLembrete"` -> 0 | Coberto |
| Uma versao por `(tarefaId, versaoAgendamento)`; anteriores `PENDENTE` canceladas | `LembretePushRepository.inserirNovaVersao`/`cancelarPendentesAnteriores` | Coberto (codigo) |
| `SEM_ALERTA` nunca agenda | `LembretePushLifecycleTest#semAlertaNaoAgenda` | Escrito (execucao pendente de Mongo) |
| `HERDAR` acompanha o padrao; `PERSONALIZADO` nao | `#herdarAcompanhaPadrao`; `#personalizadoNaoAcompanhaPadrao`; `PushResourceTest#putPreferenciasRecalculaSoHerdar` | Escrito (execucao pendente de Mongo) |
| Remover horario cancela e preserva modo; redefinir reagenda em nova versao | `#removerHorarioCancelaEPreservaModo`; `#redefinirHorarioReagendaNovaVersao` | Escrito (execucao pendente de Mongo) |
| Concluir/lixeira cancelam; restaurar reagenda se futuro | `#concluirCancela`; `#lixeiraCancela`; `#restaurarReagendaSeFuturo` | Escrito (execucao pendente de Mongo) |
| Ocorrencia recorrente herda o modo da serie e agenda | `#ocorrenciaRecorrenteHerdaModoDaSerie` | Escrito (execucao pendente de Mongo) |
| Gap DST -> `400 INVALID_LOCAL_DATETIME`; overlap -> offset anterior; SP e Lisboa | `LembretePushCalculatorTest` (5) | **Executado e verde** |
| Dois workers nao processam o mesmo item | `PushDeliverySchedulerTest#doisWorkersNaoProcessamOMesmoItem` | Escrito (execucao pendente de Mongo) |
| Atraso > 15 min -> `EXPIRADO_FORA_DA_JANELA` sem envio | `#atrasoMaiorQue15MinExpiraSemEnviar` | Escrito (execucao pendente de Mongo) |
| Backoff exponencial limitado a janela, sem deslocar `instanteEntregaUtc` | `#falhaTransitoriaRespeitaJanela`; `#falhaSemTempoNaJanelaTerminaEmFalhaFinal`; `LembretePushFilaFiltroTest` (3) | **Filtro executado e verde**; integracao pendente de Mongo |
| `410`/`404` removem a inscricao | `#gone410RemoveInscricao` | Escrito (execucao pendente de Mongo) |
| `TTL: 900` no envio | `#ttl900NoEnvio` | Escrito (execucao pendente de Mongo) |
| Payload cifrado RFC 8291 (`aes128gcm`) com round-trip | `WebPushCriptografiaTest` (4) | **Executado e verde** |
| Payload `{lembreteId, tarefaId, titulo<=80, url absoluta}` + allowlist | `PushPayloadFactoryTest` (6) | **Executado e verde** |
| `PUT /preferencias` com conjunto `{0,5,15,30,60}` | `PushResourceTest#putPreferenciasComValorForaDoConjuntoRetorna400`; `#putPreferenciasRecalculaSoHerdar` | Escrito (execucao pendente de Mongo) |
| `DELETE` so por body; endpoint alheio -> 404 | `#deleteSemEndpointRetorna400`; `#deleteEndpointAlheioRetorna404` | Escrito (execucao pendente de Mongo) |
| `POST` reatribui `endpoint` e devolve o `_id` persistido; exige HTTPS | `#upsertReatribuiEndpoint`; `#endpointSemHttpsRetorna400` | Escrito (execucao pendente de Mongo) |
| SW registrado so no app Tarefas, com `scope: '/'` | `pushService.registrarServiceWorker`; `TarefasApp` (unico call site); `pushService.test.ts` | **Executado e verde** |
| Dedupe de 100 `lembreteId`; clique nao conclui a tarefa | `sw.test.ts` (4) | **Executado e verde** |
| Logout chama `DELETE` best-effort com timeout de 2 s | `logoutPush.test.ts` (3) | **Executado e verde** |
| Deep link `?tarefaId=` abre o modal; 404 -> toast + limpeza | `deepLinkTarefa.test.tsx` (3) | **Executado e verde** |
| Estados `unsupported`/`denied`/`default`/`granted`/`ios-nao-instalado` com textos do Anexo B | `lembretesUi.test.tsx` (4); `pushService.test.ts` | **Executado e verde** |
| Manifest PWA com `display: standalone` e vinculo no `index.html` | `manifestPwa.test.ts` (3) | **Executado e verde** |
| ArchUnit: repositorios `..push..` exigem `usuarioId`; scheduler e `@SystemScope` | `TenantIsolationArchTest#repositoriosDePushExigemUsuarioId`; `#schedulerDePushExigeSystemScope` | Escrito (execucao pendente de Mongo) |
| Gate comum | `docs/REVISAO-IMPLEMENTADA-R11.md` secao 0 | Frontend verde; backend bloqueado por Mongo |


---

## N. Critérios específicos da R12 (F11: UX/Design System, acessibilidade e rastreabilidade)

| Critério | Teste / comando | Status |
|---|---|---|
| `scripts/verificar-legado.sh` verde (11 achados → 0) | `bash scripts/verificar-legado.sh` → exit 0 | **Coberto na R12** |
| Testes sem prefixo de fase (D-43) | `src/test/a11y/modaisFoco.test.tsx`, `cronometrosLiveRegions.test.tsx`, `tarefasDominio.test.ts`; gate `E3b-arquivos-por-fase` = 0 | **Coberto na R12** |
| Fuso no catálogo de editais (nunca `LocalDate.now()` implícito) | `edital/EditalResourceTest`; `EditalService` injeta `TimezoneContext` | **Coberto na R12** |
| Alvos de toque por token, sem seletor global | `alvosToque.test.tsx` (8) — tokens `--target-min`/`--target-min-touch`, ausência do seletor global, `Button`/`IconButton`/`Checkbox` | **Coberto na R12** |
| `scroll-padding-bottom` só com a barra inferior presente | `scrollPaddingBottomNav.test.tsx` (4) — `body[data-bottom-nav='true']`, breakpoint 960px, limpeza no unmount | **Coberto na R12** |
| `useReducedMotion` desliga a alternância do título | `reducedMotion.test.tsx` (7) — `matchMedia` true/false/ausente, título estático, restauração no foco | **Coberto na R12** |
| Contraste de tokens WCAG AA nos dois temas | `contrasteTokens.test.ts` (96) — 4.5:1 texto, 3:1 UI, `--text-soft`/`--primary`/`--red-text`/`--on-primary-solid`/`--border-control` corrigidos | **Coberto na R12** |
| Anti-IDOR por allowlist, com guard de classificação total | `security/RotasPrivadasClassificacaoTest` (3) + `AuthorizationByOwnerTest#auditarRotaDaAllowlist` (56 casos) + `#auditarListagemSemId` (4) | **Coberto na R12** — rota nova sem caso quebra o build |
| Benchmark lê o plano **vencedor** e arquiva relatório | `benchmark/MongoExplainBenchmarkTest` (10); `docs/benchmarks/explain-2026-11-13.md` | **Coberto na R12** — 10/10 IXSCAN no índice canônico |
| Matriz sem linhas "Sem teste" | `grep -c "Sem teste" docs/RASTREABILIDADE-REESTRUTURACAO.md` → 0 | **Coberto na R12** |

## O. Decisões com efeito comportamental → teste nomeado

| Decisão | Regra | Teste(s) |
|---|---|---|
| D-37 | Estados de interface (skeleton 200 ms, vazio só com 2xx, erro com retry) | `designSystemEstados.test.tsx`; `useRecurso.test.tsx`; `hub.test.tsx`; `dashboard.test.tsx`; `cadernoVazio.test.tsx`; `agendaHoje.test.tsx` |
| D-38 | Ação destrutiva com confirmação, foco no botão seguro | `toastConfirm.test.tsx`; `excluirTrilha.test.tsx`; `modaisFoco.test.tsx` |
| D-40 | Teclado e ARIA (Dialog, Menu Button, Tabs, Listbox) | `modaisFoco.test.tsx`; `appSwitcher.test.tsx`; `a11y/*.axe` |
| D-43 | Nenhum teste removido sem equivalente; sem prefixo de fase | Gate `E3b-arquivos-por-fase` = 0; contagem 324 → 438 no frontend |
| D-06 | Toda leitura/escrita com `usuarioId`; outro tenant → 404 | `AuthorizationByOwnerTest` (70 casos); `TenantIsolationArchTest` |
| D-08 | 18 coleções filhas privadas + `usuarios` por último | `AccountDeletionIntegrationTest` (29) |
| D-36 | Push versionado por `(tarefaId, versaoAgendamento)` | `LembretePushLifecycleTest` (9); `PushResourceTest` (7) |

