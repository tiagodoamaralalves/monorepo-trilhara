# R8 — Revisão Implementada (F7: Visão Hoje compartilhada, listas virtuais e Pomodoro)

> Relatório da revisão **R8**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R7.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-13 · branch `main` · base `24149c9` (R7-revisão).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **SEC-02 / D-46** singleton de agenda sem reset por conta | **Confirmado** | `shared/services/agendaHojeService.ts:118` tinha um único `cacheDados`, sem `usuarioId` e sem assinatura de `trilhara:logout`/`trilhara:conta-excluida`; `subscrever()` recarregava sempre e reemitia o cache de qualquer conta | Estado virou `Map<usuarioId, EstadoAgendaUsuario>` com **época** por conta; reset em `trilhara:logout`, `trilhara:conta-excluida` e troca de conta no hook; resultado de carga de época antiga é descartado. Testes `trocaDeContaZeraEstadoSemItensDaContaAnterior`, `novaAssinaturaNuncaRecebeDadosDeOutraConta`, `contaExcluidaZeraEstadoDaConta` |
| D-46 — detecção de virada de dia | **Confirmado ausente** | `hojeLocal` era só derivado (`:250`), nunca comparado | `executarCarga` compara `hojeLocal` com o anterior, descarta o estado do dia e recarrega **uma única vez** (`recargaPorVirada`). Teste `viradaDeHojeLocalDescartaEstadoDoDiaAnteriorERecarrega` |
| **RI-26** `SectionErrorCard` restrito a Hoje | **Parcial** | Componente usado em Hub/Dashboard/Planejamento/Tarefas/Pomodoro, mas **nenhum uso de `variant="inline"`** em produção (`grep` só achava `designSystemEstados.test.tsx:126`); erro de blocos ocupava a seção inteira | `AgendaHoje` usa `variant="inline"` dentro do subgrupo afetado. Testes `erroDeBlocosRenderizaSectionErrorCardInlineSomenteNoSubgrupoBlocos`, `erroDeTarefasRenderizaInlineEmAtrasadasTarefasDeHojeESemData` |
| **UX-01/02** loading/vazio indefinidos | **Confirmado** | `grep Skeleton` em `TarefasApp`/`PomodoroApp` → 0; Tarefas mostrava `<p>Nenhuma tarefa ou bloco de estudo com vencimento para hoje.</p>`, texto fora do Anexo B | `Skeleton variant="row" linhas={3}` por seção (200 ms/300 ms via `useDelayedFlag`) e `EmptyState` "Tudo em dia por hoje" com o texto normativo |
| **RI-27** contagem N da página | **Confirmado** | `TarefasApp:1045` usava `blocosTrilha.length` e `:1052-1060` derivava de `blocosAtrasadosTrilha.length` (página carregada). Não existia chamada `statusData=ATRASADO&tamanho=1` | `carregarBlocosTrilha` busca em paralelo `?concluido=false&statusData=ATRASADO&pagina=1&tamanho=1` e usa `totalItens`. Teste `contagemDeAtrasadasUsaTotalItensComTamanhoUm` (página com 1 item, total 7 → botão "(7)") |
| **DC-10** agrupamento/edição duplicados | **Parcial** | Lógica de agrupamento única, mas a **renderização** das 3 seções estava duplicada: `TarefasApp:1146-1371` (~225 linhas) × `PomodoroApp:1282-1606` (~325 linhas) | Extraído `shared/agenda/AgendaHoje.tsx` + `TarefaAgendaItem.tsx`; os apps só passam props. `grep -c "Atrasadas ("` → 0 / 0 / 1 |
| **DC-11** `ItemAgendaHojeDTO` órfão | **Confirmado** | Declarado em `domainContracts.ts:150`; uso só em `test/api.test.ts:10,168` | **Removido** + o bloco correspondente em `api.test.ts` |
| **D-48** lista virtual obsoleta | **Confirmado ausente** | `sessaoGlobalService.concluirBloco` só propagava `ApiError`; `GlobalSessionModal` classificava `nao-encontrado` mas não recarregava nem avisava; a string "O cronograma foi atualizado…" não existia no repo | `onBlocoObsoleto` em `GlobalSessionModalOpenParams`; `tratarBlocoObsoleto` fecha o modal, publica `AGENDA_INVALIDADA`, mostra o toast normativo e **não** reenvia `POST /sessoes` |
| **D-23** arquivos `shared/agenda/*` | **Confirmado ausente** | Diretório não existia; agrupador/ordenações em `shared/services/agendaHojeService.ts` | Criados `agruparAtrasadasPorSerie.ts` e `ordenarAgenda.ts`; `rg "agruparAtrasadas" -l` → só `shared/agenda` + teste |
| Pomodoro "Foco atual" persistido | **Confirmado ausente** | `PomodoroApp:246` era `useState<{tipo,id,titulo}>`; `pomodoro_foco` não existia | Persistência `trilhara:u:{id}:pomodoro_foco` com **apenas** `{tipo,id}`; título derivado; chave limpa quando o item some e em logout |
| Pomodoro visitante: card + `abrirAuthModal` | **Parcial** | Card com título "Organize seus estudos e tarefas" | Título normativo "Entre para ver suas tarefas de hoje" + botão `abrirAuthModal` |
| Concluir manual não pausa o timer | **Confirmado conforme**, sem teste | `handleConcluirTarefa` não tocava em `isRunning` | Comportamento preservado e agora coberto por `concluirTarefaNaoPausaOTimerNemAbreModal` |
| Sidebar "Trilhas de Estudo" só com cronograma ativo e sem editar/excluir | **Descartado** | `TarefasApp:170-180` filtra `cronogramaAtivoResumo?.cronogramaAtivoId`; `MinhasListasNav.tsx:197-235` não tem ações | Nada a corrigir; coberto por `listaVirtual.test.tsx` |
| Filtros gerais não renderizam blocos | **Confirmado conforme**, sem teste | `tarefasFiltradas` opera só sobre `tarefasManuais` | Coberto por `filtrosGeraisNaoRenderizamBlocos` |
| Backend da F7 (rotas/contratos/404) | **Confirmado conforme** | `BlocoResource`, `CronogramaService:441-448` (404 de versão não publicada), `listarBlocosPaginado` com `statusData`/`totalItens` | **Nenhuma alteração de backend** |

### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | `hojeISO()` duplicado em `modules/tarefas/utils/date.ts:1` e `shared/format/datas.ts:61`; `formatarDataRelativa` reimplementava `diasAte` | `grep "utils/date"` em 3 arquivos | `hojeISO` passou a vir de `@shared/format`; `descreverDataRelativa` criada em `shared/format`; `formatarDataRelativa` local **removida** |
| **N-2** | Visitante disparava `GET /tarefas/hoje` e `GET /blocos/hoje` (401) ao montar Tarefas/Pomodoro | `subscrever()` recarregava incondicionalmente | Sem `usuarioId` não há assinatura nem requisição |
| **N-3** | `fase7IntegracaoDiaria.test.tsx` alimentava `E3b-arquivos-por-fase` | `verificar-legado.sh` → 6 arquivos | Removido; casos portados para as 4 suítes normativas. Padrão caiu para **5** |

---

## 2. Arquivos criados / alterados / removidos

**Criados (12)**
```
frontend/src/shared/agenda/ordenarAgenda.ts
frontend/src/shared/agenda/agruparAtrasadasPorSerie.ts
frontend/src/shared/agenda/agendaHojeService.ts
frontend/src/shared/agenda/AgendaHoje.tsx
frontend/src/shared/agenda/AgendaHoje.module.css
frontend/src/shared/agenda/TarefaAgendaItem.tsx
frontend/src/shared/agenda/TarefaAgendaItem.module.css
frontend/src/shared/agenda/index.ts
frontend/src/test/agendaHojeService.test.ts
frontend/src/test/agendaHoje.test.tsx
frontend/src/test/listaVirtual.test.tsx
frontend/src/test/pomodoroAgenda.test.tsx
docs/REVISAO-IMPLEMENTADA-R8.md
```

**Removidos (4)**
```
frontend/src/shared/services/agendaHojeService.ts      (sem shim)
frontend/src/modules/tarefas/components/TaskItem/      (movido para shared/agenda/TarefaAgendaItem.tsx)
frontend/src/test/fase7IntegracaoDiaria.test.tsx       (E3b; casos portados)
ItemAgendaHojeDTO em frontend/src/shared/contracts/domainContracts.ts (DC-11)
```

**Alterados (9)**
```
frontend/src/modules/tarefas/TarefasApp.tsx        (imports, AgendaHoje, N exato, lote com retry, D-48)
frontend/src/modules/pomodoro/PomodoroApp.tsx      (AgendaHoje modo pomodoro, foco persistido, card de visitante, subtarefa)
frontend/src/modules/tarefas/utils/date.ts         (remove formatarDataRelativa duplicada)
frontend/src/shared/format/datas.ts                (+ descreverDataRelativa)
frontend/src/shared/contracts/domainContracts.ts   (+ onBlocoObsoleto; − ItemAgendaHojeDTO)
frontend/src/shared/design-system/GlobalSessionModal/GlobalSessionModal.tsx  (D-48 nos 3 caminhos)
frontend/src/test/api.test.ts                      (remove ItemAgendaHojeDTO)
frontend/src/test/tarefasHoje.test.tsx             (novo caminho do serviço + derivarAgenda)
docs/RASTREABILIDADE-REESTRUTURACAO.md             (A6/A7 + seção R8)
```

---

## 3. Decisões de implementação

1. **Estado por conta com época.** `AgendaHojeManager` mantém `estados`, `epocas`,
   `ultimoCarregamento` e `cargasEmAndamento` por `usuarioId`. Todo descarte incrementa a época;
   uma carga que retorna com época diferente é **descartada**, eliminando a corrida
   "logout durante a requisição".
2. **`subscrever(usuarioId, ouvinte)`** emite o estado existente **somente** da conta assinante e só
   dispara rede quando não há estado `sucesso` do dia — Tarefas e Pomodoro podem montar juntos sem rajada.
3. **Retry por fonte.** `recarregarTarefas`/`recarregarBlocos` trocam apenas a fatia correspondente,
   preservando a outra lista (exigência: "falha de blocos mantém tarefas").
4. **Ordens com desempate por `id`.** `ordenarAgenda.ts` implementa as quatro ordens normativas +
   desempate estável por `id`; `ordenar()` nunca muta o array recebido.
5. **Agrupamento único.** `agruparAtrasadasPorSerie` ordena asc e elege a última como principal;
   `anteriores` ficam ascendentes (preserva a leitura cronológica do expansor).
6. **Item único.** `TaskItem` movido para `shared/agenda/TarefaAgendaItem.tsx` com
   `densidade: 'confortavel' | 'compacta'`, tipado por `TarefaDTO` — evita acoplamento
   `shared → modules`.
7. **D-48 em três caminhos.** Aplicado em "Concluir sem salvar", no retry da etapa 2 e no fluxo
   principal `executarFluxo`; em todos a sessão já salva **não** é reenviada e o modal fecha.
8. **Foco do Pomodoro** persiste `{ tipo, id }` e deriva o título; `focoItem` é `useMemo` sobre a
   agenda e um efeito limpa a chave quando o item desaparece.

---

## 4. Testes completos por nome

**`frontend/src/test/agendaHojeService.test.ts` (19)**
`ordenaAtrasadasPorDataPrioridadeOrdemComIdComoDesempate` ·
`ordenaTarefasDeHojeComHorarioNuloPorUltimo` ·
`ordenaSemDataPorPrioridadeDescEOrdemAsc` ·
`ordenaBlocosDeHojePorTrilhaOrdemEBlocoId` ·
`agrupaSerieSobOcorrenciaMaisRecenteComAnterioresAscendentes` ·
`tarefaDesvinculadaDaSerieNaoEhAgrupada` ·
`derivaAtrasadasHojeESemDataNasOrdensNormativas` ·
`ignoraConcluidasEItensDeOutrosDias` ·
`tudoVazioComAsDuasFontesOkMarcaVazioTotal` ·
`visitanteSemUsuarioNaoTemAgendaDerivada` ·
`falhaDeBlocosMantemTarefasEErroBlocos` ·
`falhaDeTarefasMantemBlocosEErroTarefas` ·
`retryRecarregaSomenteAFonteComFalha` ·
`retryDeTarefasNaoRecarregaBlocos` ·
`throttleDeCincoSegundosEvitaRajadasDeRequisicoes` ·
`trocaDeContaZeraEstadoSemItensDaContaAnterior` ·
`novaAssinaturaNuncaRecebeDadosDeOutraConta` ·
`contaExcluidaZeraEstadoDaConta` ·
`viradaDeHojeLocalDescartaEstadoDoDiaAnteriorERecarrega`

**`frontend/src/test/agendaHoje.test.tsx` (10)**
`exibe os cabeçalhos h2 das três seções e os subgrupos de Hoje` ·
`tudoVazioExibeEmptyStateTudoEmDiaPorHoje` ·
`exibe skeleton row após 200 ms por seção no carregamento inicial` ·
`erroDeBlocosRenderizaSectionErrorCardInlineSomenteNoSubgrupoBlocos` ·
`erroDeTarefasRenderizaInlineEmAtrasadasTarefasDeHojeESemData` ·
`retry recarrega somente a fonte com falha` ·
`concluirTarefaManualNaoAbreModalEChamaAcaoDeTarefas` ·
`concluirBlocoChamaHandlerDeBlocoDireto` ·
`atrasadasAgrupadasExibemExpansorDeOcorrenciasAnteriores` ·
`modoPomodoroExpoeDefinirComoFocoSemAcoesDeEdicao`

**`frontend/src/test/listaVirtual.test.tsx` (5)**
`blocosDaListaVirtualNaoExpoemEditarNemExcluir` ·
`filtrosGeraisNaoRenderizamBlocos` ·
`contagemDeAtrasadasUsaTotalItensComTamanhoUm` ·
`carregarMaisAcrescentaAProximaPaginaDeBlocos` ·
`resultadoParcialDoLoteExibeToastComTentarNovamente`

**`frontend/src/test/pomodoroAgenda.test.tsx` (4)**
`concluirTarefaNaoPausaOTimerNemAbreModal` ·
`visitanteVeCardEEntrarNaContaAbreAuthModal` ·
`focoAtualPersisteSomenteIdETipoNaChaveDaConta` ·
`focoEhDescartadoQuandoOItemSomeDaAgenda`

**Testes existentes ajustados**
`tarefasHoje.test.tsx` (5, agora via `derivarAgenda`) · `api.test.ts` (5, sem `ItemAgendaHojeDTO`)

---

## 5. Gate de saída

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `bash scripts/env-gate.sh --exec -- bash -c 'cd backend && ./mvnw -o verify'` | **BUILD SUCCESS — 269 testes**, 0 falhas, 0 erros, 7 skipped (R7: 269) |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** (exit 0) |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** (exit 0) |
| Frontend testes | `npx vitest run` | **39 arquivos / 261 testes** (R7: 36/232) |
| Frontend build | `npm run build` | **SUCCESS** (`built in 5.20s`) |
| Frontend dead code | `npx --no-install knip` | **SUCCESS** (exit 0) |
| Gate anti-legado | `bash scripts/verificar-legado.sh --quiet` | **16 achados** (R7: 17) |

### Movimento do gate anti-legado

| Padrão | Antes (R7) | Depois (R8) |
|---|---|---|
| `E3b-arquivos-por-fase` | 6 | **5** |
| `serializacao-json` | 4 | 4 (R9/R13) |
| `LocalDate.now` | 1 | 1 (R9/R13) |
| `ZoneId-America/Sao_Paulo` | 1 | 1 (allowlist `TimezoneContext`) |
| `termo-ciclo-frontend` | 5 | 5 ("ciclo Pomodoro", preservado) |
| **Total** | 17 | **16** |

### Critérios de grep da R8 §4
```bash
rg "agruparAtrasadas" frontend/src -l
# shared/agenda/agruparAtrasadasPorSerie.ts · shared/agenda/agendaHojeService.ts
# shared/agenda/index.ts · test/agendaHojeService.test.ts
grep -c "Atrasadas (" TarefasApp.tsx PomodoroApp.tsx AgendaHoje.tsx   # → 0 / 0 / 1
grep -rn "pomodoro_foco" frontend/src                                 # → só PomodoroApp.tsx
```

---

## 6. Backend

O backend **não foi alterado nesta revisão** (nenhuma rota, DTO ou serviço Java mudou).

```bash
bash scripts/env-gate.sh --exec -- bash -c 'cd backend && ./mvnw -o verify'
# [INFO] Tests run: 269, Failures: 0, Errors: 0, Skipped: 7
# [INFO] BUILD SUCCESS   (exit 0)
```

Os `ERROR` de `EmailService`/Resend nos logs são esperados: o `env-gate.sh` injeta uma chave
dummy (`RESEND_API_KEY`) e o envio real retorna 401, sem afetar nenhum teste.

---

## 7. Bloqueios herdados (não pertencem à R8)

| Item | Dono | Evidência |
|---|---|---|
| `serializacao-json` (4), `LocalDate.now` (1), `ZoneId.of("America/Sao_Paulo")` (1) | R9/R13 | allowlist da R0 |
| `E3b-arquivos-por-fase` (5) e gate estrito → exit 0 | R12/R13 | D-43 |
| `termo-ciclo-frontend` (5) — "ciclo Pomodoro" | preservado pelo guia | `PomodoroApp.tsx` |
