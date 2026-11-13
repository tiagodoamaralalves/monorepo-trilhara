# R7 — Revisão Implementada (F6: Tarefas manuais, recorrência e lixeira)

> Relatório da revisão **R7**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R6.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `main` · base `2524abb` (R6-revisao).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-18** `GET /api/tarefas/hoje` inclui concluídas | **Descartado no backend** | `TarefaRepository.listarHoje/listarAtrasadas/listarSemData` já filtravam `eq("estado","PENDENTE")` + `deletedAt=null` | Resíduo real no frontend: `agendaHojeService.ts` derivava `tarefasConcluidasHoje` de `hoje` (sempre vazio). Campo **removido**; coberto por `tarefasHoje.test.tsx` |
| **RI-19** `TODA_A_SERIE` anula `serieId` | **Descartado** | `grep 'unset("serieId")\|set("serieId"' backend/src/main` → 0; `moverPendentesSerieParaLixeira` só toca `deletedAt/expiresAt/loteExclusaoId` | Nada a corrigir. Faltava o **teste**: `TarefaIntegrationTest#todaASeriePreservaSerieIdDasConcluidas` |
| **RI-20** `ESTA_E_FUTURAS`/`operationId` ausentes | **Confirmado (divergente)** | (a) gravava `operationId` enquanto o índice canônico usa `origemOperacaoId` → **índice único inoperante**; (b) `operationId` opcional, sem `Idempotency-Key`; (c) `EscopoRecorrencia.fromString(null) → ESTA` silencioso; (d) `removerPendentesFuturas` apagava sem respeitar `desvinculadaDaSerie`; (e) exclusão usava `deleteMany` definitivo | Renomeado para **`origemOperacaoId`**; `Idempotency-Key` **obrigatório** (400 `IDEMPOTENCY_KEY_REQUIRED`); `fromString` devolve `null` e o serviço responde 400; filtro `ne("desvinculadaDaSerie", true)`; exclusão move para a **lixeira** (`moverPendentesAPartirParaLixeira`) |
| **RI-21** "lista padrão" | **Descartado no código**; contrato incompleto | `grep -ri 'lista padr'` só em `docs/`; restauração já usava `listaId=null` | Contrato completo (D-25): `RestauracaoTarefaResponse { tarefa, realocadaParaListaGeral }` + toast na UI |
| **RI-22** séries com lista na lixeira | **Parcial** | Suspensão já existia; purga desvinculava antes de apagar; **porém** `PurgaLixeiraTarefasJob` não era `@SystemScope` e a varredura multi-tenant vivia em `TarefaService` (exposto por HTTP) | Criado **`TarefaPurgaService`** `@SystemScope`; job só agenda; `desvincularLista(usuarioId, listaId)` passou a exigir tenant |
| **RI-23** agrupamento divergente | **Parcial** — ver §7 | Comportamento já conforme D-23 em `shared/services/agendaHojeService.ts`, consumido por `TarefasApp` e `PomodoroApp` | **Adiado para R8** (o arquivo-alvo `shared/agenda/agruparAtrasadasPorSerie.ts` é da R8). Registrado como bloqueio herdado |
| **RI-24** índice esparso sem `usuarioId` | **Descartado** | `IndicesCanonicos` já declara índice **parcial** `{usuarioId, serieId, dataOriginalOcorrencia}` com `exists("serieId")`; `grep -rn sparse backend/` → 0 | Nada a corrigir; o defeito real era o nome divergente do campo |
| **RI-25** testes de calendário/concorrência ausentes | **Confirmado** | `Fase6RecorrenciaEngineTest`: 5 casos, sem virada de ano, semana ISO 53, fev/2027-2028, ordinal 5, `APOS_OCORRENCIAS` | **`RecorrenciaEngineTest`** (8 testes, 2 parametrizados) e **`TarefaIntegrationTest`** (16 testes, inclui 10 GETs paralelos) |
| **RI-38** modelo de lembrete ambíguo | **Confirmado** | `usaPadraoGlobalLembrete` em 2 entidades, 6 DTOs, 24 sítios de `TarefaService`, 5 de repositórios, 2 de `LembretePushService`, 5 do frontend | **`ModoLembrete {HERDAR, SEM_ALERTA, PERSONALIZADO}`** + validação {0,5,15,30,60}; campo antigo **removido de todo o código** |
| **DC-09** `tarefaStorage.ts` | **Descartado** | Arquivo inexistente; `grep localStorage\|sessionStorage` em `modules/tarefas` → 0 | Nada a fazer |
| **SEC-16** listagens sem paginação | **Confirmado** | `TarefaResource.listar` devolvia `List<TarefaResponse>`; lixeira sem metadados; ordenação `ordem, criadoEm` ≠ D-28 | `PaginaTarefasDTO` (50/100) com `INVALID_PAGINATION`; ordenação por agregação com `_id` de desempate e `NULLS LAST`; lixeira paginada |
| **DC-08** `dataTermino` | **Descartado** | Campo é `dataFim` desde a F6 | Nada a fazer; grep do critério = 0 |

### Achados novos

| # | Achado | Correção |
|---|---|---|
| **N-1** | `origemOperacaoId` no índice × `operationId` no código | Unificado em `origemOperacaoId` |
| **N-2** | Purga multi-tenant fora de `@SystemScope` | `TarefaPurgaService` + regra ArchUnit |
| **N-3** | Restauração não checava `expiresAt` | `404 TRASH_ITEM_EXPIRED` |
| **N-4** | `desvincularLista(listaId)` sem `usuarioId` (viola D-06a) | Assinatura exige tenant; coberto por ArchUnit |
| **N-5** | `impacto-exclusao` de listas ausente | `ListaImpactoExclusaoResponse` + rota |
| **N-6** | Sem textos do Anexo B e seletor de lembrete fora do padrão | `EmptyState` por filtro; seletor com opções canônicas e dica sem horário |
| **N-7** | `APOS_OCORRENCIAS` não considerava `totalGeradas` (gerava até o alvo) | `aplicarLimiteTermino` trunca por posições consumidas |
| **N-8** | Retry de `ESTA_E_FUTURAS` devolvia 404 (âncora removida) | `resolverResultadoIdempotente` devolve 200 com a sucessora |

---

## 2. Arquivos alterados

**Backend — novos**
`tarefa/ModoLembrete.java` · `tarefa/TarefaPurgaService.java` ·
`tarefa/dto/PaginaTarefasDTO.java` · `tarefa/dto/RestauracaoTarefaResponse.java` ·
`tarefa/dto/ListaImpactoExclusaoResponse.java` ·
`test/tarefa/RecorrenciaEngineTest.java` · `test/tarefa/TarefaIntegrationTest.java`

**Backend — alterados**
`Tarefa.java` · `SerieRecorrente.java` · `EscopoRecorrencia.java` ·
`TarefaRepository.java` · `SerieRecorrenteRepository.java` · `ListaTarefasRepository.java` ·
`RecorrenciaEngine.java` · `TarefaService.java` · `TarefaResource.java` ·
`ListaTarefasResource.java` · `PurgaLixeiraTarefasJob.java` · `dto/TarefaResponse.java` ·
`dto/SerieRecorrenteResponse.java` · `dto/CriarTarefaRequest.java` ·
`dto/AtualizarTarefaRequest.java` · `dto/CriarSerieRecorrenteRequest.java` ·
`dto/AtualizarSerieRecorrenteRequest.java` · `dto/LixeiraTarefasResponse.java` ·
`push/LembretePushService.java` · `test/arquitetura/TenantIsolationArchTest.java` ·
`test/tarefa/Fase6TarefaResourceTest.java` · `test/tarefa/Fase6RecorrenciaEngineTest.java` ·
`test/push/LembretePushLifecycleTest.java`

**Frontend — novos**
`test/tarefasHoje.test.tsx` · `test/escopoRecorrencia.test.tsx` ·
`test/lixeiraTarefas.test.tsx` · `test/lembreteSeletor.test.tsx`

**Frontend — alterados**
`shared/contracts/domainContracts.ts` · `shared/services/agendaHojeService.ts` ·
`modules/tarefas/types/tarefa.ts` · `modules/tarefas/services/tarefaService.ts` ·
`modules/tarefas/components/TaskModal/TaskModal.tsx` · `modules/tarefas/TarefasApp.tsx` ·
`test/fase6Tarefas.test.ts`

**Nenhum arquivo removido.** Mudanças de nome de campo permitidas pela premissa de banco
vazio no novo esquema.

---

## 3. Contratos resultantes

| Rota | Contrato |
|---|---|
| `GET /api/tarefas?filtro=TODAS\|FUTURO\|CONCLUIDAS&listaId=&pagina=&tamanho=` | `PaginaTarefasDTO { itens, pagina, tamanho, totalItens, totalPaginas }`; 50/100; filtro inválido → 400 `VALIDATION_ERROR`; fora do limite → 400 `INVALID_PAGINATION`; nunca contém blocos |
| `GET /api/tarefas/hoje` | `{ hojeLocal, atrasadas, hoje, semData }` somente `PENDENTE` |
| `PUT /api/tarefas/{id}?escopo=` | `escopo` obrigatório em ocorrência de série (ausente → 400 `VALIDATION_ERROR`); proibido em avulsa; `ESTA_E_FUTURAS` exige `Idempotency-Key` (400 `IDEMPOTENCY_KEY_REQUIRED`) |
| `DELETE /api/tarefas/{id}?escopo=` | `ESTA_E_FUTURAS` → lixeira com `loteExclusaoId`; `TODA_A_SERIE` preserva concluídas intactas |
| `POST /api/tarefas/lixeira/{id}/restaurar` | `RestauracaoTarefaResponse { tarefa, realocadaParaListaGeral }`; expirado → 404 `TRASH_ITEM_EXPIRED` |
| `GET /api/tarefas/lixeira?pagina=&tamanho=` | `LixeiraTarefasResponse` com `tarefas` paginadas + `listas`, `series` e metadados |
| `GET /api/listas-tarefas/{id}/impacto-exclusao` | `ListaImpactoExclusaoResponse { id, nome, tarefasAtivas, tarefasConcluidas, seriesVinculadas }` |

Modelo: `modoLembrete ∈ {HERDAR, SEM_ALERTA, PERSONALIZADO}` (default `HERDAR`);
`lembreteMinutosAntes ∈ {0,5,15,30,60}` só em `PERSONALIZADO`;
`series_recorrentes.origemOperacaoId?` com índice único parcial.

---

## 4. Testes completos por nome

**Backend — `RecorrenciaEngineTest` (8)**
`diarioComIntervaloCruzaFimDeAno` (params: intervalo 3 em 2026-12-28 · intervalo 1 em
2028-02-27) · `semanalComSemanaIso53EInicioNaQuarta` · `mensalDia31EmFevereiro` (params:
2027-02-28 · 2028-02-29) · `mensalOrdinalInexistenteEUltimaOcorrencia` ·
`terminoEmDataEhInclusivo` · `terminoAposOcorrenciasConsideraPosicoesConsumidas`

**Backend — `TarefaIntegrationTest` (16)**
`hojeNaoContemConcluidas` · `todaASeriePreservaSerieIdDasConcluidas` ·
`estaEFuturasEdicaoPreservaDesvinculadas` · `estaEFuturasRetryComMesmaChaveNaoDuplicaSerie` ·
`estaEFuturasSemChaveRetorna400` · `escopoAusenteEmTarefaRecorrenteRetorna400` ·
`materializacaoConcorrenteNaoDuplicaNemRetorna500` · `serieDeListaNaLixeiraNaoMaterializa` ·
`purgaDeListaMoveSeriesParaListaGeral` · `restaurarTarefaComListaNaLixeiraRestauraSoContainer` ·
`restaurarTarefaComListaPurgadaRetornaFlag` · `listaIdDeOutroUsuarioRetorna400` ·
`paginacaoTarefasOrdenacaoEstavel` · `lixeiraPaginadaApenasItensVigentes` ·
`impactoExclusaoListaContaTarefasESeries` · `isolamentoEntreUsuariosEmTodasAsRotas`

**Backend — regra arquitetural**
`TenantIsolationArchTest#repositoriosDeTarefaExigemUsuarioId` (ativa, verde)

**Frontend — novos (19)**
`tarefasHoje.test.tsx` (5) · `escopoRecorrencia.test.tsx` (5) ·
`lixeiraTarefas.test.tsx` (5) · `lembreteSeletor.test.tsx` (4 — inclui
**SEM_ALERTA ≠ HERDAR** no payload)

---

## 5. Gate de saída

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `bash scripts/env-gate.sh --exec -- bash -c 'cd backend && ./mvnw -o verify'` | **BUILD SUCCESS — 269 testes**, 0 falhas, 0 erros, 7 skipped (R6: 244) |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** (exit 0) |
| Frontend testes | `npm test -- --run` | **36 arquivos / 232 testes** (R6: 32/213) |
| Frontend build | `npm run build` | **SUCCESS** (`built in 5.57s`) |
| Frontend dead code | `npx knip` | **SUCCESS** (exit 0) |
| Gate anti-legado | `bash scripts/verificar-legado.sh --quiet` | **17 achados** (R6: 63) |

### Movimento do gate anti-legado

| Padrão | Antes (R6) | Depois (R7) |
|---|---|---|
| `nomes-nao-canonicos-d02` | 46 | **0** |
| `serializacao-json` | 4 | 4 (R9/R13) |
| `LocalDate.now` | 1 | 1 (R9/R13) |
| `ZoneId-America/Sao_Paulo` | 1 | 1 (allowlist `TimezoneContext`) |
| `termo-ciclo-frontend` | 5 | 5 ("ciclo Pomodoro", preservado) |
| `E3b-arquivos-por-fase` | 6 | 6 (R12/R13) |
| **Total** | 63 | **17** |

### Critério de grep da R7 §4
```bash
grep -rn "usaPadraoGlobalLembrete|dataTermino|lista padr" backend frontend
```
→ **0 ocorrências**.

---

## 6. Desvios de escopo justificados

| Mudança | Por que foi necessária | Arquivos |
|---|---|---|
| `aplicarLimiteTermino` público no `RecorrenciaEngine` | `APOS_OCORRENCIAS` ignorava posições já consumidas (N-7); o teste unitário precisa exercitar só a regra de término | `RecorrenciaEngine`, `RecorrenciaEngineTest` |
| `TarefaRepository.PaginaTarefas` (record de entidades) | O repositório não pode depender de DTO de API (camadas); a conversão fica no serviço | `TarefaRepository`, `TarefaService` |
| `listaRepository.listarExpiradas` (substitui `listarIdsExpiradas`) | A purga precisa do `usuarioId` de cada lista (N-2/N-4) | `ListaTarefasRepository`, `TarefaPurgaService` |
| Ordenação por agregação (`$addFields` + `$switch`) | `prioridade` é texto e `dataVencimento` é opcional; `sort` simples não expressa `prioridade DESC` nem `NULLS LAST` | `TarefaRepository` |
| Testes `Fase6*` ajustados | Contratos mudaram (paginação, `Idempotency-Key`, `ModoLembrete`) | testes citados |

---

## 7. Bloqueios herdados (não pertencem à R7)

| Item | Dono | Evidência |
|---|---|---|
| `shared/agenda/agruparAtrasadasPorSerie.ts` + `ordenarAgenda.ts` (D-23) | **R8** | `R8-REVISAO.md:36,48,61` fixa o arquivo e manda remover a implementação local. Na R7 o agrupamento já é único (`agendaHojeService.agruparAtrasadas`), usado por Tarefas e Pomodoro — **sem duplicata em `TarefasApp`** (checklist 12 satisfeita no essencial) |
| `serializacao-json` (4) | R9/R13 | allowlist da R0 |
| `LocalDate.now` (1), `ZoneId.of("America/Sao_Paulo")` (1) | R9/R13 | allowlist da R0 |
| `E3b-arquivos-por-fase` (6) e gate estrito → exit 0 | R12/R13 | D-43 |
| `termo-ciclo-frontend` (5) — "ciclo Pomodoro" | preservado pelo guia | `PomodoroApp.tsx` |

---

## 8. Observações de implementação

- **`ESTA_E_FUTURAS` é operação composta sem transação**: cortar → remover pendentes não
  desvinculadas → criar a sucessora. Falha entre etapas deixa a série cortada sem sucessora;
  repetir com a mesma `Idempotency-Key` converge (o corte é idempotente e a sucessora é
  encontrada por `origemOperacaoId`). `E11000` no índice único parcial significa que outra
  execução venceu a corrida: a série é relida e a resposta é 200, nunca 500.
- **`TODA_A_SERIE` nunca toca concluídas**: `moverPendentesSerieParaLixeira` filtra
  `estado = PENDENTE`; `serieId` e `dataOriginalOcorrencia` permanecem intactos.
- **Purga e concluídas**: `purgarExpiradas` só considera `expiresAt` vencido; tarefa concluída
  não recebe `expiresAt`, logo nunca é purgada por idade.
- **`SEM_ALERTA` cancela o push**: `resolverMinutosAntes` devolve `null`; retirar o horário
  mantém `modoLembrete` e cancela (suspensão), conforme D-26.
