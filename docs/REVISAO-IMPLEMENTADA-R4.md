# R4 — Revisão Implementada (F3: Sessões, histórico e estatísticas)

> Relatório da revisão **R4**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R3.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `r4-revisao-f3` · base `3d59f38` (R3).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-06** `editavel` por página/filtro | **Descartado** | `SessaoRepository.buscarPrimeiraSessaoDaTrilha` já consultava `{usuarioId, trilhaId, deletedAt:null}` ordenado `(dataSessao DESC, criadoEm DESC, _id DESC)` e `SessaoService.listarPaginado` calculava **uma vez por request** | Nada a corrigir no cálculo; faltava cobertura → `editavelEhGlobalMesmoComFiltroDeDisciplina`, `editavelFalsoNaPagina2` |
| **RI-07** testes obrigatórios ausentes | **Confirmado** | `SessaoNormalizadaIntegrationTest` tinha 9 testes agrupados; nenhum dos nomes exigidos existia | 14 testes novos com os nomes normativos (§5) |
| **RI-08** sem purga de tombstone | **Confirmado** | `grep SystemScope backend/src/main` → **0**; `grep PurgaTombstones` → só no guia | `SystemScope`, `SessaoPurgaService`, `PurgaTombstonesSessaoJob` + índice parcial |
| **D-15** tombstone retinha conteúdo | **Confirmado** | `marcarTombstone` fazia `$unset` de 7 campos e **mantinha** `disciplinaId`, `criadoEm`, `atualizadoEm` | `$unset` completado → sobram exatamente os 6 campos de D-15 |
| **RI-09** série sem granularidade | **Confirmado** | `calcularSerieTemporal` agrupava sempre por `$dataSessao`; DTO sem `granularidade` | `Granularidade` (DIA/SEMANA/MES) + rebucket em Java + campo na resposta |
| **D-14** streak sem definição | **Descartado (implementação)** | A regra (hoje → senão ontem → senão 0, sobre a trilha inteira) já estava correta em `calcularStreakDias` | Extraída para `StreakCalculator` (pura) para permitir teste 23h30 com relógio fixo |
| **DC-06** gravação fora do serviço global | **Confirmado** | `estudosService.ts:228-279` fazia `POST /sessoes` com payload legado (`minutos`,`tipo`,`erros`,`inicio`); sem call sites | Método removido; `POST` só existe em `shared/services/sessaoGlobalService.ts` |
| **META-03** contagem de testes | **Confirmado (positivo)** | R3: 190 backend / 177 frontend | R4: **221** backend / **183** frontend |
| E11000 não capturado | **Descartado** | `SessaoService:173-197` já capturava `MongoWriteException` código 11000 | Coberto agora por `deveTratarConcorrenciaDaMesmaChave` |
| Modelo persiste `minutos`/`erros`/`tipo` | **Descartado (backend)** | `Sessao.java` não tem esses campos | Dívida era só de contrato/tipos de frontend → removida |

### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | `disciplinaId` de outra trilha devolvia **404** (revela existência); guia exige **400** | `SessaoService:91-93` usava `NotFoundException` | `400 VALIDATION_ERROR` em `errors.disciplinaId` (D-06d) |
| **N-2** | Erros de sessão não preenchiam `errors.<campo>` | `badRequest` usava `ProblemDetailsFactory.criar` sem mapa | Helper `badRequestCampo` → `criarCampo` para `disciplinaId`/`dataSessao`/`duracaoSegundos`/`questoes`/`acertos` |
| **N-3** | `requestHash` era concatenação com `\|`, não JSON canônico | `SessaoService:436-437` | JSON canônico (`LinkedHashMap`, ordem fixa) + SHA-256 |
| **N-4** | `contarPorTrilha` contava tombstones, inflando `impacto-exclusao` | `SessaoRepository:226-231` (sem `deletedAt:null`) | Filtro adicionado |
| **N-5** | Dashboard não usava `Skeleton`/`SectionErrorCard`/`EmptyState` (D-37) | `DashboardView:574-582` | Componentes do design system |
| **N-6** | Badge "Disciplina removida" com cores hardcoded (`#92400e/#fef3c7`) | `DashboardView.module.css:558-609` | `Badge variant="warning"` + tokens `--yellow*` |
| **N-7** | Edição sem guarda de alterações; 409 caía em toast genérico e não recarregava | `DashboardView:433-464` (modal cru) | `FormModal` + `useUnsavedChanges` + tratamento de `LATEST_SESSION_ONLY` |
| **N-8** | Textos fora do Anexo B/D-38 | `DashboardView:471`, `:920` | "Esta ação não pode ser desfeita." / "Nenhuma sessão neste período" |
| **N-9** | `listarSessoes` e `obterResumoEstatisticas` mortos perpetuavam `minutos`/`totalMinutos` | `estudosService.ts:319-329`, `:385-399` | Removidos |
| **N-10** | `SerieTemporalDiaDTO` deixa de ser "dia" com SEMANA/MES | `dto/SerieTemporalDiaDTO.java` | Renomeado para `SerieTemporalPontoDTO` |

## 2. Arquivos

**Criados (backend)**
- `shared/SystemScope.java` — anotação de escopo de sistema (D-06b), primeira materialização.
- `sessao/Granularidade.java` — `DIA|SEMANA|MES`, `para(inicio,fim)` e `chave(LocalDate)`.
- `sessao/StreakCalculator.java` — cálculo puro de D-14.
- `sessao/SessaoPurgaService.java` — `@SystemScope`, `purgarTombstones(Instant)`.
- `sessao/PurgaTombstonesSessaoJob.java` — `@Scheduled(every=1h, SKIP)`.
- `sessao/dto/SerieTemporalPontoDTO.java`.

**Criados (testes)**
- `backend/.../sessao/SessaoServiceHashTest.java` (6 testes)
- `backend/.../sessao/StreakCalculatorTest.java` (6 testes, inclui 23h30 SP)
- `backend/.../sessao/PurgaTombstonesSessaoJobTest.java` (4 testes)
- `frontend/src/test/dashboard.test.tsx` (5 testes)
- `frontend/src/test/editarSessao.test.tsx` (2 testes)

**Alterados**
- `sessao/SessaoRepository.java` — tombstone D-15, `purgarTombstonesAnteriores`, `contarPorTrilha` com `deletedAt:null`, série com granularidade, `diasComEstudo`.
- `sessao/SessaoService.java` — hash canônico, `badRequestCampo`, disciplina 400.
- `sessao/dto/EstatisticasTrilhaDTO.java` — campo `granularidade`.
- `shared/mongo/IndicesCanonicos.java` — helper `parcial` + `idx_sessoes_deletedAt`.
- `arquitetura/TenantIsolationArchTest.java` — `repositoriosDeSessaoExigemUsuarioId`.
- `frontend/.../estudosService.ts`, `types/trilhara.ts`, `shared/contracts/domainContracts.ts`.
- `frontend/.../DashboardView.tsx` + `DashboardView.module.css`.
- `frontend/src/test/sessaoNormalizada.test.ts` — alinhado ao novo contrato.

**Removidos**
- `sessao/dto/SerieTemporalDiaDTO.java` (substituído por `SerieTemporalPontoDTO`).
- `EstudosService.registrarSessao`, `listarSessoes`, `obterResumoEstatisticas`, `ResumoEstatisticas`.
- Campos `Sessao.minutos`, `Sessao.inicio`, `Sessao.tipo`.

## 3. Efeito colateral documentado (D-15)

O tombstone não retém mais `disciplinaId` (D-15 permite apenas `_id, usuarioId, trilhaId,
chaveIdempotencia, requestHash, deletedAt`). Consequência: `DELETE
/disciplinas/{id}/sessoes` não alcança tombstones daquela disciplina — eles expiram pela
`PurgaTombstonesSessaoJob` em ≤ 24 h. Nenhuma contagem, listagem ou agregação é afetada, porque
todas filtram `deletedAt: null`.

---

## 4. Testes completos por nome

### `SessaoNormalizadaIntegrationTest` (novos, nomes normativos)

| Teste | Critério |
|---|---|
| `deveExigirIdempotencyKey` | header ausente → 400 `IDEMPOTENCY_KEY_REQUIRED` e nenhuma sessão criada |
| `deveTratarConcorrenciaDaMesmaChave` | 2 threads, mesmo payload → 1 documento, respostas 201+200 |
| `retryAposEditarRetornaSessaoAtual` | replay pós-PATCH devolve estado atual (200), não 409 |
| `retryAposExcluirRetorna409ResultRemoved` | replay pós-DELETE → 409 `IDEMPOTENCY_RESULT_REMOVED` |
| `editavelEhGlobalMesmoComFiltroDeDisciplina` | `editavel` é global, não por filtro (D-13) |
| `editavelFalsoNaPagina2` | nenhuma sessão da página 2 é editável |
| `tombstoneNaoApareceEmListagemNemEstatistica` | tombstone fora de listagem e agregação |
| `periodoInvertidoRetorna400` | `dataInicio > dataFim` no histórico e nas estatísticas |
| `estatisticaVaziaRetornaZerosEPercentual0` | zeros, percentual 0, `granularidade=DIA` |
| `granularidadeSemanalPreservaTotais` | SEMANA/DIA/MES com totais coerentes (RI-09) |
| `disciplinaDeOutraTrilhaRetorna400` | 400 em `errors.disciplinaId` (N-1) |
| `dataSessaoFuturaRetorna400NoCampoDataSessao` | 400 em `errors.dataSessao` (N-2) |
| `streakConsideraOntemQuandoSemSessaoHoje` | D-14 com sessão apenas ontem |
| `streakIndependeDoFiltroDePeriodo` | streak global da trilha |

### Novas classes de teste

- `SessaoServiceHashTest`: `deveSerDeterministicoParaMesmoPayload`,
  `deveMudarQuandoCampoFuncionalMudar`, `deveProduzirHashDoJsonCanonico`,
  `naoDeveColidirEntreDuracaoEQuestoes`, `deveDerivarGranularidade`, `deveCalcularChaveDoBucket`.
- `StreakCalculatorTest`: `deveContarTerminandoHoje`, `deveTerminarOntemSemSessaoHoje`,
  `deveZerarSemHojeNemOntem`, `deveZerarComEntradasAusentes`,
  `deveContarDiaLocalAs23h30EmSaoPaulo`, `deveManterStreakQuandoUltimaSessaoFoiAs23h30`.
- `PurgaTombstonesSessaoJobTest`: `deveRemoverApenasTombstonesExpirados`,
  `naoDeveRemoverSessoesVivas`, `deveUsarJanelaDeRetencaoDe24h`, `deveSerIdempotenteSemExpirados`.
- `TenantIsolationArchTest#repositoriosDeSessaoExigemUsuarioId` (nova regra ativa).

### Frontend

- `dashboard.test.tsx`: skeleton chart+row sem `EmptyState` precoce; `SectionErrorCard` + retry;
  `EmptyState` "Nenhuma sessão neste período" com ação "Registrar sessão manual"; grupo de
  disciplina removida com badge de aviso e confirmação contendo "3 sessões" e "1 h 30 min";
  confirmação de exclusão com "Esta ação não pode ser desfeita.".
- `editarSessao.test.tsx`: edição só com `editavel=true`; 409 `LATEST_SESSION_ONLY` avisa e
  recarrega estatísticas e histórico.
- `sessaoNormalizada.test.ts` (ajustado): sem `registrarSessao`; `minutos`/`inicio`/`tipo`
  ausentes do contrato; `granularidade` presente.

---

## 5. Gate de saída (executado)

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -o verify` | **BUILD SUCCESS** — 221 testes, 0 falhas, 0 erros, 7 skipped (R3: 190) |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** — 0 erros |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** — exit 0 |
| Frontend testes | `npm test -- --run` | **SUCCESS** — 27 arquivos, **183 testes** (R3: 177) |
| Frontend build | `npm run build` | **SUCCESS** — `built in 5.79s` |
| Frontend dead code | `npx knip` | **SUCCESS** — exit 0 |
| Raiz (relatório) | `bash scripts/verificar-legado.sh --quiet` | **184 achados** (R3: 186) — `simbolos-legado` **31 → 29** |
| Raiz (estrito) | `bash scripts/verificar-legado.sh` | **exit 1** — bloqueios herdados (ver §6) |

**Atribuição da queda 186 → 184:** os 2 `AJUSTE_MANUAL` removidos de
`types/trilhara.ts:110` e `estudosService.ts:239` (item 7 do checklist da R4). Nenhum outro
padrão aumentou; `E1-deleteMany-com-filtro` ficou em 3 (a purga usa `deleteMany(and(...))`).

## 6. Bloqueios herdados (não pertencem à R4)

> **Nota de processo (consolidação pós-R4):** os branches por fase foram eliminados do
> fluxo. R3 e R4 foram levadas à `main` por fast-forward e os branches `r3-revisao-f2` e
> `r4-revisao-f3` foram removidos. A instrução "após R2, R3/R4/R5 e R7 podem avançar em
> branches separados" foi retirada de todos os guias `docs/R0..R13-REVISAO.md`; o grafo de
> dependências e a sequência linear permanecem inalterados. Execução passa a ser linear
> direto na `main`.

| Item | Dono | Evidência |
|---|---|---|
| `termo-ciclo-backend` (4) / `termo-ciclo-frontend` (86) | **R5 / R7** | `verificar-legado.sh --quiet` |
| `E3b-arquivos-por-fase` (7) e `E3-nomenclatura-fase` (0) | **R12 / R13** (D-43) | idem |
| `nomes-nao-canonicos-d02` (46) e `simbolos-legado` (29) remanescentes | R5/R7/R9 | campos de tarefas/cronograma (`usaPadraoGlobalLembrete`, `PlanejamentoCiclo`) |
| Gate estrito `verificar-legado.sh` → exit 0 | **R13** | D-43 exige zero ao final |
| `RESEND_API_KEY` obrigatória e Node 20 × 22 | **R13** | `docs/REVISAO-IMPLEMENTADA.md` §1 |


