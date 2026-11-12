# R5 — Revisão Implementada (F4: Cronogramas, blocos, lease e publicação)

> Relatório da revisão **R5**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R4.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `main` · base `c749c08` (R5-preparacao).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-10/DC-08** campos não canônicos (`dataEstudo`, `ordemDia`, `status`, `duracaoRealSegundos`) | **Descartado no código** | `BlocoCronograma` já usava `data/ordem/concluido/concluidoEm`; grep em `backend/src`+`frontend/src` = 0 | Dívida era apenas documental (relato 2 da F4). Nada a renomear |
| **RI-11** lote com `<= hoje` | **Descartado** | `listarAtrasadosPendentes` já usava `lt("data", hojeLocal)` | Comportamento preservado e agora coberto por teste nomeado |
| **RI-12/DC-07** coleção de operações | **Confirmado** | `OperacaoConcluirAtrasadosRepository` (117 linhas) + coleção `operacoes_concluir_atrasados`; exclusão mútua não atômica | Repositório **removido**; D-17 implementado (lease + update condicional item a item) |
| **RI-13** sem heartbeat/reconciliador/testes de falha | **Confirmado** | grep de `FaultInjector`/`Reconciliador` = 0; `atualizarPonteiroCronogramaAtivo` **não filtrava token** | `LeaseHeartbeat` (30 s), `FaultInjector`/`FaultInjectorNoOp`, `CronogramaReconciliacaoService` (`@SystemScope`), `ReconciliadorCronogramasJob`; ponteiro com fencing |
| **RI-14** período padrão e rótulos | **Confirmado (parcial)** | `calcularDataFim` sem clamp de 366 dias; `ATE_PROVA` aceitava `dataProva == dataInicio`; UI com default fixo e duração `min=10` | Clamp `dataInicio + 365`; D-20 (`dataProva` estritamente posterior); UI com período padrão condicional, aviso `role="status"` e `min=30` |
| **RI-15** política de concluídos | **Confirmado (parcial)** | Reabrir já era direto; faltava o seletor | Seletor segmentado Pendentes (padrão) / Concluídos com ação Reabrir sem modal |
| **RI-27** contagem exata de atrasados | **Confirmado** | N vinha da página carregada; `statusData` não existia como filtro | Filtro `statusData` + `contarBlocosAtrasados` (`tamanho=1`, lê `totalItens`) |
| **DC-15** terminologia "ciclo" | **Confirmado** | gate: `termo-ciclo-backend` 4, `termo-ciclo-frontend` 86 | backend **0**, frontend **5** (todas "ciclo Pomodoro", preservadas) |
| Duplicidade `PlanejamentoCicloRepository` | **Descartado** | Nenhuma classe existia; restava a string `planejamento_ciclo` | String removida junto com a exclusão em cascata delegada |
| Sem ArchUnit no pacote `cronograma` | **Confirmado** | Regras existiam só para `..trilha..` e `..sessao..` | `repositoriosDeCronogramaExigemUsuarioId` ativa |
| `trilhas.proximaVersaoCronograma` | **Ausente** | Versão era `versaoAtiva + 1` | Contador atômico (`findOneAndUpdate` + `$inc`) |
| `PATCH` aceita só `concluido` | **Já garantido** | `fail-on-unknown-properties=true` + `UnrecognizedPropertyExceptionMapper` | Comportamento preservado e coberto |
| Bloco de versão não ativa → 404 | **Ausente** | Buscava só `{_id, usuarioId}` | `exigirBlocoPublicado` compara ponteiro + versão |
| `GET /blocos/hoje` respeita versão | **Ausente** | Filtrava só `cronogramaId in ativos` | Mapa `cronogramaId → versaoAtivaCronograma` |
| Snapshot só em cronogramas ativos | **Confirmado (errado)** | `atualizarSnapshotsDisciplina` atualizava todos | Filtro `cronogramaId in ativos`; `disciplinaId` intocado |
| Tamanho máximo 100 com 400 | **Confirmado** | `min(200)` sem validar `pagina` | `TAMANHO_MAXIMO_PAGINA = 100` + `400 INVALID_PAGINATION` |

### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | `PlanejamentoStorage` chamava `/trilhas/{id}/ciclos/{id}/planejamento`, rota **inexistente** | grep de `@Path.*ciclos` no backend = 0 | Arquivo removido; conclusão de bloco passa só pelo `GlobalSessionModal` |
| **N-2** | Exclusão em cascata usava `deleteMany(new Document(...))` | `TrilhaRepository:206-208` | Delegado a `CronogramaRepository`/`BlocoCronogramaRepository`; `E1` 3 → **0** |
| **N-3** | `filtroUsuario` aceitava `usuarioId` string (`compatibilidade`) | `TrilhaRepository:257-263` | `eq(usuarioId)` apenas; `donoId` removido da inserção |
| **N-4** | `validarDisciplinasParticipantes` aceitava subconjunto em silêncio | filtrava e só falhava se vazio | 400 em `errors.disciplinasParticipantesIds` quando algum ID é de outra trilha |
| **N-5** | `POST /tarefas/ciclo/disponiveis` no cliente, sem rota no backend | `tarefaService.ts:295` | Método e tipo `CicloAtivoInfo` removidos |
| **N-6** | Erros de disponibilidade usavam `BadRequestException` sem `errors[campo]` | `atualizarDisponibilidadeSemanal` | `ValidationException` com `errors.disponibilidadeSemanalMinutos` |

---

## 2. Decisões implementadas

| Decisão | Implementação | Teste nomeado |
|---|---|---|
| **D-16** bloco só tem `concluido`/`concluidoEm` | `BlocoCronograma` sem campo de sessão/status | `patchConclusaoAceitaSomenteConcluido` |
| **D-17** concluir atrasados sem coleção | lease da trilha + `concluirSePendente` (`concluido:false` no filtro); `operationId` só em log | `concluirAtrasadosRetryNaoDuplica`, `concluirAtrasadosParaleloRetorna409`, `concluirAtrasadosNaoIncluiHoje` |
| **D-18a** heartbeat 30 s | `LeaseHeartbeat` (daemon) + `verificarVivo()` antes de cada etapa | `falhaNaEtapa*`, `leaseExpiradoNaoPublica` |
| **D-18b** reconciliador | `CronogramaReconciliacaoService` + `ReconciliadorCronogramasJob` (startup + `@Scheduled`) | `falhaNaEtapa*` (assert de idempotência) |
| **D-18c** falha só no classpath de teste | `FaultInjector` (main) + `FaultInjectorNoOp` (produção) + `FaultInjectorControlavel` (`src/test`) | `PublicacaoCronogramaFaultInjectionTest` |
| **D-18d** ponteiro é a fonte de leitura | `listarHoje` e `exigirBlocoPublicado` usam `cronogramaAtivoId` + `versaoAtivaCronograma` | `blocosHojeRespeitamVersaoDoPonteiro`, `blocoDeVersaoAntigaRetorna404` |
| **D-19** Pendentes/Concluídos | Seletor segmentado + Reabrir sem modal; concluir abre `BLOCO_DIRETO` | `planejamento.test.tsx` |
| **D-20** período padrão | `dataProva > dataInicio`; aviso > 366 dias | `planejamento.test.tsx` |



---

## 3. Arquivos alterados

### Backend — criados

- `cronograma/EtapaPublicacao.java`, `cronograma/FaultInjector.java`,
  `cronograma/FaultInjectorNoOp.java`, `cronograma/CronogramaReconciliacaoService.java`,
  `cronograma/ReconciliadorCronogramasJob.java`, `trilha/LeaseHeartbeat.java`
- `src/test/.../cronograma/FaultInjectorControlavel.java`,
  `src/test/.../cronograma/PublicacaoCronogramaFaultInjectionTest.java`

### Backend — modificados

- `cronograma/CronogramaService.java`: heartbeat + 6 etapas instrumentadas; `statusData`;
  `INVALID_PAGINATION` (máx. 100); `concluirAtrasados` sem coleção de operações;
  `exigirBlocoPublicado`; `validarConfiguracao`; clamp de 366 dias; `proximaVersaoCronograma`;
  validação integral de disciplinas; `ValidationException` em disponibilidade.
- `cronograma/BlocoCronogramaRepository.java`: `TAMANHO_MAXIMO_PAGINA`; `listarHoje` por versão
  do ponteiro; `listarPaginado(statusData)`; `concluirSePendente`;
  `atualizarSnapshotsDisciplina(cronogramasAtivosIds)`.
- `cronograma/CronogramaRepository.java`: `listarPorEstadoPublicacao`, `atualizarEstadoPublicacao`.
- `cronograma/CronogramaResource.java`: query `statusData`.
- `trilha/LeaseTrilhaService.java`: `renovar`, `aindaDetem`, `exigirPosse`,
  `executarComLease(Consumer<String>)`, `INTERVALO_RENOVACAO_SEGUNDOS`.
- `trilha/TrilhaRepository.java`: `renovarLease`, `leaseAtivo`, `proximaVersaoCronograma`,
  fencing no ponteiro (+ variante sem fencing só do reconciliador), filtro de tenant estrito e
  remoção dos `deleteMany(new Document(...))`.
- `trilha/TrilhaService.java`: exclusão em cascata delegada; snapshot só nos cronogramas ativos.
- `trilha/dto/TrilhaDetalheDTO.java`: campo `ciclos` removido.

### Backend — removido

- `cronograma/OperacaoConcluirAtrasadosRepository.java`

### Frontend — criado

- `src/test/planejamento.test.tsx` (7 testes)

### Frontend — modificados

- `views/PlanejamentoView.tsx` (reescrita: `Tabs` APG, badge Ativo, modalidade desabilitada com
  tooltip, período padrão D-20, aviso de horizonte, sobrecarga por dia em `role="status"`,
  `Criar cronograma`/`Salvar e gerar blocos` com `ConfirmDialog`, Pendentes/Concluídos, Reabrir
  sem modal, contagem exata de atrasados, `Skeleton`/`SectionErrorCard`).
- `services/estudosService.ts`: `statusData` e `contarBlocosAtrasados`; métodos de
  `planejamento` removidos.
- `types/trilhara.ts`: `Ciclo`/`PlanejamentoCiclo`/`DisciplinaProgramada` removidos;
  `RegistroPlanejamentoPendente` → `RegistroBlocoPendente`.
- `EstudosApp.tsx`, `views/EstudarView.tsx`, `views/DashboardView.tsx`: fluxo local de
  planejamento removido; meta do Dashboard derivada dos blocos reais do cronograma ativo.
- `modules/tarefas/*`: `CicloAtivoInfo` e props mortas removidas; badge "Bloco de cronograma".
- Textos de `LandingView`, `HubView`, `DisciplinasView`, `CatalogoView`, Navbars,
  `DisciplinaPesoSelector`, `AppSwitcher` e `alarmService`.

### Frontend — removido

- `services/planejamentoStorage.ts`


---

## 4. Testes normativos

### Backend — `CronogramaR5IntegrationTest` (15)

Nomes normativos do guia, todos com código real:

`geradoresDeterministicos` (sequencial e ponderado com mesma config → mesma sequência de
`data/ordem/disciplina`, ids novos), `manualRepeteSemanaModelo`, `ponderadoRespeitaDeficit`
(pesos 1:5 → proporção dentro de ±1 bloco), `limite366DiasE24PorDia` (clamp em +365 dias, 24
aceito, 25 → 400), `concorrenciaGeracao` (409 com lease ocupado, 201 após liberar, exatamente 1
ativo), `duplicateModalityNaoAlteraBlocos` (contagem e ids idênticos), `concluirAtrasadosNaoIncluiHoje`,
`concluirAtrasadosRetryNaoDuplica`, `concluirAtrasadosParaleloRetorna409`,
`statusDataAtrasadoTotalItensExato`, `patchConclusaoAceitaSomenteConcluido` (body com
`status` — campo removido por D-16 — → 400 e bloco continua pendente), `blocoDeVersaoAntigaRetorna404`,
`paginacaoBlocosLimite100Retorna400`, `blocosHojeRespeitamVersaoDoPonteiro` (staging órfão não
aparece), `snapshotAtualizaSomenteCronogramasAtivos`.

### Backend — `CronogramaNormalizadoIntegrationTest` (8)

Fluxo completo de cronograma da F4, ajustado para o contrato D-17 (retry recalcula em vez de
devolver resposta cacheada).

### Backend — `PublicacaoCronogramaFaultInjectionTest` (7)

`falhaNaEtapa1_leitorVeVersaoAnteriorOuNovaCompleta` … `falhaNaEtapa6_…` (parametrizado por
`EtapaPublicacao`; cada caso verifica que a versão lida pelo ponteiro está completa e que o
reconciliador converge de forma idempotente) e `leaseExpiradoNaoPublica` (token roubado não
altera o ponteiro nem renova).

### Backend — regras e inventário

- `TenantIsolationArchTest#repositoriosDeCronogramaExigemUsuarioId` (nova regra ativa).
- `InventarioColecoesTest`: `ALLOWLIST_LEGADO` vazio — nenhuma coleção de operações existe.

### Frontend — `planejamento.test.tsx` (7)

`modalidade já usada fica desabilitada com tooltip da trilha`,
`período padrão é "Até a data da prova" apenas com dataProva futura`,
`sem dataProva futura a opção fica desabilitada e o padrão é 1 mês`,
`exibe aviso de horizonte quando a prova está a mais de 366 dias`,
`seletor Pendentes/Concluídos alterna o filtro enviado ao backend`,
`mostra a aba do cronograma com badge Ativo`,
`contagem de atrasados usa totalItens de statusData=ATRASADO&tamanho=1`.

---

## 5. Gate de saída

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -o verify` (via `scripts/env-gate.sh`) | **BUILD SUCCESS** — **244 testes**, 0 falhas, 0 erros, 7 skipped (R4: 221) |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** |
| Frontend lint | `npx eslint src --max-warnings 0` | **SUCCESS** (exit 0) |
| Frontend testes | `npm test -- --run` | **28 arquivos / 190 testes** (R4: 27/183) |
| Frontend build | `npm run build` | **SUCCESS** (`built in 5.43s`) |
| Frontend dead code | `npx knip` | **SUCCESS** (exit 0) |
| Gate anti-legado | `bash scripts/verificar-legado.sh --quiet` | **184 → 67 achados** |

### Movimento do gate anti-legado

| Padrão | Antes | Depois |
|---|---|---|
| `simbolos-legado` | 29 | **0** |
| `termo-ciclo-backend` | 4 | **0** |
| `termo-ciclo-frontend` | 86 | **5** (todas "ciclo Pomodoro", explicitamente preservadas) |
| `E1-deleteMany-com-filtro` | 3 | **0** |
| `nomes-nao-canonicos-d02` | 46 | 46 (campos de tarefas → R8) |
| `E3b-arquivos-por-fase` | 7 | 7 (→ R12/R13) |
| `serializacao-json` | 4 | 4 (editais/diagnóstico de índice) |
| `canais-antigos` | 3 | 3 |

## 6. Desvios de escopo justificados (mudanças transversais)

| Mudança | Por que foi necessária | Arquivos |
|---|---|---|
| Remoção do fluxo local de planejamento | `PlanejamentoStorage` chamava `/trilhas/{id}/ciclos/{id}/planejamento`, rota **inexistente**; sem remover, "concluir bloco" continuaria falhando em silêncio e o grep de "ciclo" nunca zeraria | `planejamentoStorage.ts` (removido), `EstudosApp.tsx`, `EstudarView.tsx`, `DashboardView.tsx` |
| `DashboardView` com meta vinda dos blocos reais | O cálculo anterior dependia de `trilha.ciclos` (campo removido) e de `DisciplinaProgramada` | `DashboardView.tsx` |
| `CicloAtivoInfo`/props de ciclo removidas em Tarefas | Tipo e props alimentavam uma lista virtual duplicada e chamavam rota inexistente (`/tarefas/ciclo/disponiveis`) | `modules/tarefas/**` |
| Exportações de auth não usadas (`AUTH_LOGOUT_EVENT`, `isAmbienteLocal`, `RoleUsuario`) | `knip` passou a acusá-las como dead code depois das remoções acima; viraram símbolos internos, sem perda de comportamento | `shared/auth/{authStorage,sso,types}.ts` |
| Filtro de tenant estrito em `TrilhaRepository` | O filtro antigo aceitava `usuarioId` como **string** (compatibilidade), violando D-06; o fencing do ponteiro exige filtro determinístico | `TrilhaRepository.java` |

## 7. Bloqueios herdados (não pertencem à R5)

| Item | Dono | Evidência |
|---|---|---|
| `nomes-nao-canonicos-d02` (46): `usaPadraoGlobalLembrete`, `dataTermino` | **R8** | `grep -rInE` em `tarefa/*` |
| `E3b-arquivos-por-fase` (7) e `E3-nomenclatura-fase` | **R12/R13** | D-43 exige zero ao final |
| `serializacao-json` (4) em `EditalRepository` / `MongoIndexInitializer` | R9/R13 | allowlist documentada na R0 |
| `canais-antigos` (3) | R13 | `frontend/src` |
| Gate estrito `verificar-legado.sh` → exit 0 | **R13** | D-43 |
