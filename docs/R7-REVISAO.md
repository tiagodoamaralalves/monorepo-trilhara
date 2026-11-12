# R7 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R7** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

### Hierarquia normativa incorporada neste guia

Os requisitos de domínio e produto aplicáveis a esta revisão estão reproduzidos abaixo e são a referência normativa para comportamento. As decisões `D-xx` explicitamente listadas prevalecem sobre especificações históricas quando determinam uma regra diferente; mudanças que alterem comportamento devem atualizar os documentos normativos do produto no mesmo change. O contexto de execução original da fase aparece apenas para delimitar o escopo desta revisão. O relato da implementação é uma declaração a verificar no código e nos testes, nunca prova de conformidade. Qualquer trecho histórico incluído está marcado como não normativo. O agente deve trabalhar com o contexto deste guia e com o estado atual do repositório, sem precisar consultar os documentos de origem.

### Regras de execução aplicáveis

- Dependências desta revisão: R0→R1→R2; R6 exige R4+R5; R8 exige R5+R6+R7; R9 exige R3; R10 exige R3–R9; R11 exige R7+R10; R12 e R13 exigem todas as revisões anteriores. Sequência linear segura: R0→R1→R2→R3→R4→R5→R6→R7→R8→R9→R10→R11→R12→R13.
- Faça inventário de imports, usos e call sites antes de mudanças transversais. Não introduza compatibilidade legada nem mocks/callbacks temporários destinados a fases futuras.
- Preserve os limites de escopo desta revisão. Mudanças transversais indispensáveis para compilar/testar devem ser justificadas no resumo.
- A premissa é banco de desenvolvimento/teste vazio no novo esquema; não preservar compatibilidade com modelos, rotas ou dados legados.
- Contratos comuns: rotas privadas exigem autenticação (401); falta de papel administrativo é 403; recursos inexistentes ou de outro usuário retornam 404 sem revelar existência. IDs HTTP são hexadecimais de 24 caracteres: malformado é 400 `INVALID_IDENTIFIER`. Erros usam `application/problem+json`, RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) e códigos estáveis `code`/`errors`; falhas inesperadas não expõem detalhes internos ou dados pessoais. Paginação usa `pagina` 1-based, tamanho dentro do limite do endpoint (máximo 100), erro 400 `INVALID_PAGINATION`, resposta `{ itens, pagina, tamanho, totalItens, totalPaginas }` e ordenação estável com `_id` como desempate. Datas de calendário são `yyyy-MM-dd`; instantes são RFC 3339 na API e `Instant`/BSON Date no banco; o fuso vem de `X-Timezone`, com fallback `America/Sao_Paulo`, nunca do relógio/fuso implícito do servidor. Escritas idempotentes definem chave, escopo, hash funcional e política de retry; mesma chave com payload funcional diferente retorna 409 `IDEMPOTENCY_KEY_CONFLICT`. Operações compostas sem transação multi-documento documentam estados intermediários, recuperação, retry e efeitos parciais; não declarar atomicidade inexistente.
- Ao concluir, registre achados confirmados/descartados, arquivos alterados/removidos, testes completos por nome e resultado do gate em `docs/REVISAO-IMPLEMENTADA.md`; atualize `docs/RASTREABILIDADE-REESTRUTURACAO.md`.

**Gate comum de saída:** `backend/`: `./mvnw -q verify`; `frontend/`: `npm run typecheck && npm run lint -- --max-warnings 0 && npm test -- --run && npm run build && npx knip`; raiz: `bash scripts/verificar-legado.sh`. Se uma etapa ainda não estiver disponível por dependência anterior, registre claramente o bloqueio e não declare a revisão concluída.


---

## Escopo desta revisão

### R7 — Revisão da F6: Tarefas manuais, recorrência e lixeira

**1. Referência e Diagnóstico**
- `GET /api/tarefas/hoje` inclui concluídas (RI-18); `TODA_A_SERIE` anula `serieId` (RI-19); edição `ESTA_E_FUTURAS`/`operationId` ausentes (RI-20); "lista padrão" (RI-21); séries com lista na lixeira (RI-22); agrupamento divergente (RI-23); índice esparso sem `usuarioId` (RI-24); testes de calendário/concorrência ausentes (RI-25); modelo de lembrete ambíguo (RI-38); `tarefaStorage.ts` (DC-09); listagens sem paginação (SEC-16); `dataTermino` (DC-08).

**2. Especificação Técnica, Segurança e UX**
- Modelo: conforme o contrato de tarefas e recorrência estabelecido neste guia, com D-26 (`modoLembrete`) e `series_recorrentes.origemOperacaoId?` (D-24). `estado` é a fonte canônica; `concluida` é derivado e gravado junto (mesmo `$set`).
- Rotas (allowlist do Anexo A, seção Tarefas). Alterações de contrato:
  - `GET /api/tarefas?filtro=TODAS|FUTURO|CONCLUIDAS&listaId=&pagina=&tamanho=` → `Pagina<TarefaResponse>` (D-28); jamais contém blocos.
  - `GET /api/tarefas/hoje` → `{ hojeLocal, atrasadas, hoje, semData }`, apenas `PENDENTE`.
  - `PUT /api/tarefas/{id}?escopo=ESTA|ESTA_E_FUTURAS|TODA_A_SERIE` e `DELETE /api/tarefas/{id}?escopo=…`: `escopo` obrigatório quando `serieId != null` (ausente → 400 `VALIDATION_ERROR`) e proibido em tarefa avulsa; `ESTA_E_FUTURAS` exige o header `Idempotency-Key` (reutilizado como `operationId`; ausente → 400 `IDEMPOTENCY_KEY_REQUIRED`).
  - `POST /api/tarefas/lixeira/{tarefaId}/restaurar` → `{ tarefa, realocadaParaListaGeral }` (D-25).
  - `GET /api/listas-tarefas/{id}/impacto-exclusao` (D-33).
  - `GET /api/tarefas/lixeira` paginado; filtra `expiresAt > agora`.
- Referências: `listaId` do body precisa pertencer ao usuário e estar ativa (senão 400 `VALIDATION_ERROR`).
- Validação estrita da série: `SEMANAL` exige `diasDaSemana` não vazio; `MENSAL/DIA_DO_MES` exige `diaDoMes`; `ORDINAL_SEMANA` exige `ordinalSemana ∈ {1,2,3,4,-1}` e `diaSemanaOrdinal`; campos incompatíveis com `frequencia`/`tipoTermino` → 400.
- Materialização: idempotente, ignorando séries de listas na lixeira (D-27); captura de `E11000` (D-24.5); nunca além de `hojeLocal + 366` nem do término.
- Purga (`PurgaLixeiraTarefasJob`, `@SystemScope`, diário): antes de apagar uma lista, `listaId = null` nas séries sobreviventes; concluídas nunca purgadas por idade.
- UX (Tarefas):
  - Seções com skeleton `row`; `EmptyState` por filtro (Hoje: "Tudo em dia por hoje", ação "Nova tarefa"; Concluídas: "Nenhuma tarefa concluída ainda"; Lixeira: "A lixeira está vazia").
  - Concluir/reabrir/pular/subtarefa otimistas (D-39); excluir item → toast com **Desfazer** (D-38); excluir lista → `ConfirmDialog` com contagens; esvaziar lixeira → `danger`.
  - Escopo recorrente: diálogo de escolha (radio group acessível) com "Apenas esta", "Esta e as próximas", "Toda a série" e descrição do efeito de cada uma (texto do Anexo B).
  - `TaskModal` com guarda de alterações (D-41) e seletor de lembrete com as opções "Usar padrão (X min)", "Sem alerta", "No horário", "5/15/30/60 min antes"; o seletor fica desabilitado com dica quando não houver horário.

**3. Checklist Atômico de Execução**
1. Corrigir `GET /api/tarefas/hoje` para somente pendentes.
2. Remover o `$unset`/`set null` de `serieId` em concluídas no escopo `TODA_A_SERIE`.
3. Implementar `ESTA_E_FUTURAS` (exclusão via lixeira; edição com nova série, preservação das desvinculadas, `origemOperacaoId`).
4. Substituir o índice esparso pelo índice parcial com `usuarioId` (via R2 / `MongoIndexInitializer`).
5. Implementar a suspensão de séries com lista na lixeira e o ajuste na purga.
6. Corrigir a restauração (somente o container; lista purgada → `null` + flag); remover qualquer conceito de "lista padrão".
7. Migrar o lembrete para `modoLembrete` (backend, DTOs, frontend); remover `usaPadraoGlobalLembrete`.
8. Renomear `dataTermino` → `dataFim` (se existir).
9. Paginar `GET /api/tarefas` e a lixeira; atualizar o frontend com "Carregar mais".
10. Implementar `impacto-exclusao` de listas.
11. Verificar `tarefaStorage.ts`: se armazena tarefas/listas (estado de domínio), **apagar** e migrar os consumidores para a API; se armazena apenas preferências de UI, mover para `trilhara:u:{id}:tarefas_ui` e renomear para `tarefasPreferenciasUi.ts`.
12. Remover o agrupamento de atrasadas de `TarefasApp.tsx` (substituído pelo util compartilhado em R8).
13. Habilitar ArchUnit para o pacote `tarefa`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend `RecorrenciaEngineTest` (unitário, parametrizado): diário com intervalo 3 cruzando 31/12→01/01; semanal intervalo 2 com semana ISO 53 (ex.: 2026-12-28) e início em quarta; mensal dia 31 em fev/2027 e fev/2028 (bissexto); ordinal 5ª inexistente (ignorada) e `-1` (última sexta); `APOS_OCORRENCIAS` contando canceladas/movidas/desvinculadas; `EM_DATA` inclusiva.
- Backend `TarefaIntegrationTest`: `hojeNaoContemConcluidas`; `todaASeriePreservaSerieIdDasConcluidas`; `estaEFuturasEdicaoPreservaDesvinculadas`; `estaEFuturasRetryComMesmaChaveNaoDuplicaSerie`; `estaEFuturasSemChaveRetorna400`; `escopoAusenteEmTarefaRecorrenteRetorna400`; `materializacaoConcorrenteNaoDuplicaNemRetorna500` (10 GETs paralelos); `serieDeListaNaLixeiraNaoMaterializa`; `purgaDeListaMoveSeriesParaListaGeral`; `restaurarTarefaComListaNaLixeiraRestauraSoContainer`; `restaurarTarefaComListaPurgadaRetornaFlag`; `listaIdDeOutroUsuarioRetorna400`; `paginacaoTarefasOrdenacaoEstavel`; isolamento A×B em todas as rotas.
- Frontend: `tarefasHoje.test.tsx` (estados; concluir otimista com rollback); `escopoRecorrencia.test.tsx`; `lixeiraTarefas.test.tsx` (Desfazer; restaurada na Lista Geral → toast); `lembreteSeletor.test.tsx` ("Sem alerta" ≠ "Usar padrão" no payload).
- `rg "usaPadraoGlobalLembrete|dataTermino|lista padr" backend frontend` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-23 — Hoje somente pendentes e agrupamento único**
- *Decisão Cravada:* `GET /api/tarefas/hoje` retorna apenas `estado = PENDENTE` e `deletedAt == null`. Concluídas somem com animação curta (respeitando reduced-motion) e ficam em "Concluídas". O agrupamento de atrasadas por série existe **uma única vez** em `frontend/src/shared/agenda/agruparAtrasadasPorSerie.ts` (sob a ocorrência **mais recente**, expansor "+ N ocorrências anteriores atrasadas"), usado por Tarefas e Pomodoro.

**D-24 — Escopos de recorrência**
- *Dúvida/Lacuna:* `TODA_A_SERIE` anula `serieId` das concluídas. Em `ESTA_E_FUTURAS`, os requisitos dizem "remove definitivamente" pendentes; o relato envia à lixeira; edições individuais (`desvinculadaDaSerie`) poderiam ser perdidas.
- *Deliberação e Referência:* Princípio regra normativa ("destruição é explícita") e as lixeiras de 30 dias favorecem reversibilidade. O Google Calendar descarta exceções em "este e os seguintes", mas o Todoist/Things preservam itens editados manualmente.
- *Decisão Cravada:*
  1. `TODA_A_SERIE`: **nunca** alterar `serieId`/`dataOriginalOcorrencia` de concluídas. Pendentes vão à lixeira com o mesmo `loteExclusaoId` da série.
  2. `ESTA_E_FUTURAS` — **exclusão**: encerra a série em `alvo − 1` e move à lixeira (mesmo `loteExclusaoId`) as pendentes com `dataOriginalOcorrencia >= alvo`.
  3. `ESTA_E_FUTURAS` — **edição**: encerra a série original em `alvo − 1`; remove definitivamente pendentes **não desvinculadas** com data original `>= alvo` (serão regeneradas pela nova série); **preserva** as pendentes com `desvinculadaDaSerie = true`; cria a nova série com `dataInicio = alvo`.
  4. Idempotência de `ESTA_E_FUTURAS`: `operationId` (UUID, obrigatório) gravado na nova série como `origemOperacaoId`, com índice único parcial `{ usuarioId: 1, origemOperacaoId: 1 }` (`partialFilterExpression: { origemOperacaoId: { $exists: true } }`). Um retry encontra a série existente e devolve o mesmo resultado (200).
  5. Índice de ocorrência: único **parcial** `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }` (remover a variante esparsa sem `usuarioId`). Um `E11000` na materialização concorrente é **engolido** (ocorrência já existe), nunca 500.
  6. Atualizar no mesmo change a regra normativa de recorrência com os itens 2–3.

**D-25 — Restauração de tarefas**
- *Decisão Cravada:* Restaurar uma tarefa cuja lista está na lixeira restaura **apenas o container da lista** (sem irmãs do lote). Lista purgada → `listaId = null`; a resposta contém `{ tarefa, realocadaParaListaGeral: true }` e a UI exibe o toast "Tarefa restaurada na Lista Geral (a lista original foi excluída definitivamente)". Não existe entidade "lista padrão".

**D-26 — Modelo de lembrete explícito**
- *Dúvida/Lacuna:* `lembreteMinutosAntes == null` significando "herdar" impede "sem alerta".
- *Deliberação e Referência:* Regra aplicável: "sem alerta, no horário ou 5/15/30/60 min; herdeiras acompanham alteração do padrão".
- *Decisão Cravada:* Em `tarefas` e `series_recorrentes`: `modoLembrete ∈ {"HERDAR","SEM_ALERTA","PERSONALIZADO"}` (default `HERDAR`) + `lembreteMinutosAntes ∈ {0,5,15,30,60}` obrigatório só em `PERSONALIZADO`. Remover `usaPadraoGlobalLembrete`. `fusoAgendamento` = `TimezoneContext.zoneId()` capturado ao salvar. Remover o horário **mantém** `modoLembrete`/minutos e cancela o lembrete pendente (suspensão); redefinir o horário reagenda.

**D-27 — Séries com lista na lixeira**
- *Decisão Cravada:* A materialização ignora séries cuja `listaId` aponta para uma lista com `deletedAt != null`. Ao restaurar a lista, a próxima consulta materializa a janela. O job de purga de listas executa `updateMany({usuarioId, listaId}, {$set:{listaId:null}})` em `series_recorrentes` **antes** de apagar a lista.

**D-28 — Matriz de paginação**
- *Decisão Cravada (padrão/máx.):* sessões 20/100; blocos de cronograma 50/100; `GET /api/tarefas` (filtros Todas/Futuro/Concluídas) 50/100 com ordenação estável (`dataVencimento ASC NULLS LAST, prioridade DESC, ordem ASC, _id ASC`; Concluídas: `concluidaEm DESC, _id DESC`); lixeira de tarefas 50/100 (`deletedAt DESC, _id DESC`); notas por seção 50/100 (`ordem ASC, _id ASC`); busca de notas 20/100; lixeira de notas 50/100. Não paginados por limite estrutural: `GET /api/trilhas` (≤ 50), cronogramas da trilha (≤ 3), `GET /api/listas-tarefas` (limite de 200 listas por usuário → `409 RESOURCE_LIMIT_EXCEEDED`), `GET /api/cadernos` (limite de 500 cadernos), `GET /api/cadernos/{id}/secoes` (limite de 500 seções por caderno). Os limites novos devem ser acrescentados aos requisitos.

---

## Contexto normativo de domínio pertinente

## 7. Tarefas

### 7.1 Tarefas manuais e visão Hoje

- `GET /api/tarefas/hoje` é a consulta de visão diária e retorna `{ hojeLocal, atrasadas, hoje, semData }`, somente tarefas manuais pendentes. Tarefas sem `dataVencimento` ficam em `semData`; atrasadas ordenam por `dataVencimento ASC, prioridade DESC, ordem ASC`; tarefas hoje por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`. DTO não contém blocos de Estudos.
- Tarefas manuais têm origem própria e são persistidas; campos de data e horário são locais/calendário, horário opcional.
- Hoje inclui tarefas manuais vencidas hoje, atrasadas e sem data. Tarefas concluídas somem de Hoje/Pomodoro e permanecem consultáveis em Concluídas/histórico, sem expiração automática.
- Tarefas manuais sem data não são lembrete/cronograma; continuam aparecendo em Hoje conforme decisão do produto.
- Subtarefas são checklist marcável vinculado à tarefa pai, sem data/prioridade/recorrência próprias; marcar todas não conclui automaticamente o pai.
- Concluir uma tarefa manual não abre modal de sessão.

### 7.2 Recorrência

- Modelar a regra em coleção própria (`series_recorrentes`) separada das ocorrências materializadas em `tarefas`. Materializar ocorrências em janela móvel de **90 dias** a partir de `hojeLocal` (ou até o fim do intervalo consultado no calendário, limitado a 366 dias), atualizando `geradoAte` na série.
- Cada ocorrência em `tarefas` guarda `serieId` (`ObjectId`), `dataOriginalOcorrencia` (`LocalDate`, imutável) e `estado` (`PENDENTE`, `CONCLUIDA`, `CANCELADA`). A idempotência de geração é garantida por índice único parcial `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }`. Assim, se o usuário alterar a `dataVencimento` de uma ocorrência individual, `dataOriginalOcorrencia` permanece intacta e a janela móvel jamais recria aquela data em duplicidade.
- Recorrência por calendário fixo ancorada em `dataInicio` inclusiva: `DIARIO` gera `dataInicio + k*intervalo dias`; `SEMANAL` divide semanas ISO segunda–domingo, considera semana de `dataInicio` índice zero e gera dias selecionados somente em semanas cujo índice é múltiplo de `intervalo`, nunca antes de `dataInicio`; `MENSAL` avança meses de calendário em `intervalo`, por dia do mês `1..31` ou ordinal semanal (`1..4` ou última `-1`). Dia do mês ausente vira último dia válido. Não incluir recorrência anual no MVP.
- Para regra MENSAL `ORDINAL_SEMANA`, ordinal `1..4` que não existe no mês é inválido para aquela data e não gera ocorrência; ordinal `-1` seleciona a última ocorrência do dia da semana daquele mês.
- Término da série: `NUNCA`, `EM_DATA` (`dataFim`, inclusiva) ou `APOS_OCORRENCIAS` (`maxOcorrencias`). `APOS_OCORRENCIAS` conta as posições de datas originalmente programadas desde `dataInicio`, incluindo as que foram concluídas, canceladas, movidas à lixeira ou editadas individualmente; reagendar vencimento não altera a posição/contagem. Campos incompatíveis com `tipoTermino` são rejeitados.
- `geradoAte` é cursor inclusivo da última data calendário examinada; avanço da janela é idempotente por índice único. Consultas que ampliem a janela não materializam além de `hojeLocal + 366 dias` nem do término.
- Concluir uma ocorrência muda seu `estado` para `CONCLUIDA` sem alterar as demais e sem abrir modal de sessão. Pular uma ocorrência muda seu `estado` para `CANCELADA` (omitida das listas ativas, preservada para impedir regeneração).
- Na seção **Atrasadas**, múltiplas ocorrências pendentes atrasadas da mesma `serieId` são agrupadas visualmente sob a ocorrência atrasada mais recente, exibindo um expansor `"+ N ocorrências anteriores atrasadas"`.
- A API `GET /api/tarefas/hoje` retorna `{ atrasadas, hoje, semData }` de tarefas manuais pendentes e metadados de `hojeLocal`; recorrências são materializadas antes de responder. Repetição do GET não altera estado funcional fora de `geradoAte`/criação idempotente de ocorrências.
- Escopos de edição e exclusão:
  1. **Apenas esta ocorrência:** edição altera apenas campos editáveis da ocorrência e define `desvinculadaDaSerie = true`; exclusão move apenas ocorrência para lixeira. A ocorrência não é recriada e a série continua.
  2. **Esta e futuras:** encerra a série original em `dataOriginalOcorrencia.minusDays(1)`, inativando-a se o corte preceder `dataInicio`; remove definitivamente apenas ocorrências pendentes não concluídas com data original `>= alvo`. Concluídas permanecem no histórico. Em edição, cria nova série com `dataInicio = alvo`, novos parâmetros e reinício de contagem de término relativo ao alvo. Exige `operationId` idempotente para retries; não há opção de exclusão sem edição que apague automaticamente ocorrências concluídas.
  3. **Toda a série:** encerra/move a regra em `series_recorrentes` para a lixeira, move todas as ocorrências pendentes (atrasadas, de hoje e futuras) para a lixeira com o mesmo `loteExclusaoId` e preserva ocorrências já concluídas. Restaurar uma ocorrência individual da lixeira não reativa a série encerrada.

### 7.3 Lixeiras e listas manuais

- Campos de tarefa manual: `_id`, `usuarioId`, `listaId?`, `titulo` (1..200), `descricao?`, `prioridade` (`BAIXA|MEDIA|ALTA`), `dataVencimento?` (`LocalDate`), `horario?` (`HH:mm`), `estado` (`PENDENTE|CONCLUIDA|CANCELADA`), `concluida` compatível com estado, `concluidaEm?`, `ordem`, `subtarefas` (ID/título/concluída/ordem), preferência de lembrete, campos de soft-delete e timestamps. Subtarefas não concluem automaticamente a tarefa pai.
- Coleções `listas_tarefas`, `series_recorrentes` e `tarefas` têm todos os campos definidos no plano; `deletedAt` ausente ou `null` significa ativo. Tarefa concluída não expira automaticamente.
- Excluir tarefa ou lista move o conteúdo à lixeira por 30 dias; usuário pode esvaziar antecipadamente. Excluir pai marca a subárvore ativa com mesmo `loteExclusaoId`; não muda itens que já estavam na lixeira por exclusão isolada.
- Excluir lista move lista e tarefas relacionadas juntas. Confirmar mostrando quantidade; restaurar hierarquia e ordem originais.
- Séries recorrentes vinculadas a lista excluída ficam suspensas (não materializam enquanto a lista estiver na lixeira); ao restaurar a lista, retomam a geração. Ao purgar a lista, séries que sobrevivem passam a `listaId = null` (Lista Geral).
- Se o usuário restaurar uma tarefa individual cuja `listaId` original esteja na lixeira, o sistema restaura a lista pai (apenas o container da lista) ou, caso a lista já tenha sido purgada definitivamente, restaura a tarefa na lista geral (`listaId = null`) informando o usuário.
- Depois de 30 dias, purgar definitivamente. Implementar purga em rotina controlada; índice TTL do MongoDB, se usado, é eventual e não deve ser tratado como relógio exato.
- Rotas públicas completas de CRUD, conclusão, pular, subtarefa, séries, Hoje e lixeira estão enumeradas na FASE 6 ; todas usam DTOs, checam tenant e retornam 404 para ID válido fora da posse.

### 7.4 Blocos de cronograma em Tarefas

- Não persistir bloco como tarefa. Uma lista virtual por trilha renderiza todos os blocos pendentes de todo o período gerado, em ordem de data, com indicação de atrasado/hoje/futuro.
- Filtros gerais **Futuro**, **Todas** e **Concluídas** não incluem blocos. A lista da trilha e Hoje são as exceções definidas.
- Hoje inclui somente blocos do dia atual, não blocos futuros ou atrasados.
- Blocos não podem ser criados/editados como tarefas. Concluir/reabrir usa endpoint de Estudos.
- Concluir um bloco abre o modal de sessão compartilhado.
- A ação **Concluir todas as atrasadas sem salvar** mostra confirmação com quantidade; implementar item a item/idempotentemente, reportando sucessos/falhas parciais e permitindo retry sem sessão. Não exigir transação Mongo multi-documento.

## 11. Notificações push de Tarefas

- Push somente para tarefas manuais com horário explícito; blocos de cronograma não têm lembretes próprios.
- Preferência global padrão: 15 minutos antes. Cada tarefa pode sobrescrever para sem alerta, no horário ou 5/15/30/60 minutos antes. Herdeiras acompanham alteração do padrão; override local permanece.
- Permitir push em todos os dispositivos/navegadores autorizados do usuário. Clique abre a tarefa no app Tarefas; não a conclui.
- Capturar fuso IANA quando o lembrete é configurado e materializar instante UTC. Se retirar o horário, suspender lembrete preservando escolha; quando voltar a definir horário, reagendar.
- Concluir/excluir cancela lembrete; alterar horário/data recalcula entrega. Ocorrências recorrentes herdam lembrete e permitem override.
- Permissão negada ou navegador sem suporte não bloqueia o uso de tarefas; exibir estado informativo claro. Em iOS/iPadOS (Safari 16.4+), onde a Web Push API exige aplicativo instalado na Tela de Início, servir `/manifest.webmanifest` com `"display": "standalone"` e exibir instrução passo a passo ("Adicionar à Tela de Início") quando o usuário tentar ativar notificações em aba comum do Safari.
- Push é *best-effort* com **janela útil de entrega de 15 minutos**: a rotina agendadora materializa entregas na coleção `lembretes_push` com `instanteEntregaUtc`. Se o agendador processar um registro com atraso superior a 15 minutos em relação a `instanteEntregaUtc`, marca-o como `EXPIRADO_FORA_DA_JANELA` sem disparar notificação atrasada. Nas chamadas aos provedores Web Push (RFC 8030), enviar cabeçalho `TTL: 900`.
- Um lembrete tem estado `PENDENTE`, `PROCESSANDO`, `ENVIADO`, `CANCELADO`, `EXPIRADO_FORA_DA_JANELA` ou `FALHA_FINAL`; cada agendamento tem `lembreteId`/versão imutável por alteração da tarefa, chave idempotente por `(tarefaId, versãoAgendamento)`, lease de processamento e `tentativas`. O scheduler adquire cada item atomicamente; apenas o dono do lease pode concluir. Falhas temporárias tentam novamente com backoff exponencial limitado ao prazo de 15 minutos; expirado não envia. Envio externo pode ter resultado ambíguo após timeout; Web Push não garante exactly-once, portanto payload inclui `lembreteId` e o service worker deduplica notificações recentes localmente.
- Se a data/hora local ocorrer no intervalo de horário inexistente por mudança de fuso (gap DST), rejeitar configuração com `400 INVALID_LOCAL_DATETIME`; se ocorrer duas vezes (overlap DST), escolher a ocorrência anterior (offset de verão) deterministicamente e retornar `instanteEntregaUtc` calculado no DTO.
- Requisito técnico: `/sw.js` servido na raiz sem cache imutável (`Cache-Control: no-cache, no-store, must-revalidate` no Caddy), autenticação VAPID (RFC 8292) configurada via variáveis de ambiente, coleção `push_subscriptions` por dispositivo e coleção `lembretes_push` com índice em `{ estado: 1, instanteEntregaUtc: 1 }`. Quando o serviço de push responder `410 Gone` ou `404 Not Found`, remover imediatamente a inscrição inválida de `push_subscriptions`. Incluir os alertas na política de privacidade.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

---

## Plano original da FASE 6 (referência de escopo)

## FASE 6 — Domínio Tarefas: Tarefas Manuais, Séries Recorrentes (Janela 90d) e Lixeira (30d)

### 6.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 e 1 concluídas. Pode ser implementada em branch independente de F2–F5; integração final precede F7.
- **Objetivo da fase:** Refatorar o pacote `br.com.trilhara.tarefa` para operar exclusivamente com listas manuais (`listas_tarefas`), séries recorrentes (`series_recorrentes`) e tarefas/ocorrências manuais (`tarefas`), implementar a recorrência por calendário fixo com janela móvel de 90 dias e `dataOriginalOcorrencia` imutável, implementar a lixeira de 30 dias com `loteExclusaoId` e atualizar o CRUD de tarefas manuais, recorrência e lixeira em `frontend/src/modules/tarefas`.
- **O que NÃO fazer nesta fase:**
  - Não implementar ainda a agregação com blocos de Estudos (`AgendaHojeService` e listas virtuais de trilha) nem tocar em `PomodoroApp.tsx` — isso pertence integralmente à **FASE 7**.

### 6.3 Critérios de Saída da FASE 6
- Testes unitários e de integração em `backend/src/test/java/br/com/trilhara/tarefa/` validando:
  - Geração idempotente na janela de 90 dias (incluindo dias 29/30/31 em fevereiro bissexto e não bissexto).
  - Reagendar `dataVencimento` de uma ocorrência não recria a ocorrência na `dataOriginalOcorrencia`.
  - Edição e exclusão nos escopos `ESTA`, `ESTA_E_FUTURAS` e `TODA_A_SERIE` (preservando concluídas).
  - Lixeira de tarefas/listas, restauração com lista pai em lixeira/purgada e esvaziamento manual.
- `mvn test` e `npm run build` passando sem erros.
- Testes frontend de recorrência, escopos e lixeira passam com o runner configurado em F0; `npm run build` não substitui esses testes.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 6                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 7                                 -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 6 (hipótese a verificar)

## FASE 6 — Tarefas Manuais, Recorrência em Janela Deslizante de 90 Dias e Lixeira com Expiração de 30 Dias

### 1. Resumo Executivo e Decisões de Arquitetura

1. **Normalização do Modelo de Dados de Tarefas e Listas**:
   - Reestruturadas as 3 coleções dedicadas: `listas_tarefas`, `series_recorrentes` e `tarefas`.
   - Removidos acoplamentos legados de origem mista (`OrigemTarefa`, `cicloId`, `blocoEstudoId`) dos modelos persistidos de tarefas, isolando estritamente o gerenciamento de afazeres manuais e séries recorrentes.
   - Suporte a subtarefas/checklist como subdocumentos embutidos (`id`, `titulo`, `concluida`, `ordem`). A marcação de conclusão de subtarefas opera de forma totalmente independente e **nunca altera o estado de conclusão da tarefa pai**.

2. **Motor de Recorrência com Janela Deslizante de 90 Dias (`RecorrenciaEngine`)**:
   - Geração dinâmica de ocorrências individuais materializadas na coleção `tarefas` para os próximos 90 dias a partir da data de referência (`hojeLocal`), parametrizado pelo cabeçalho `X-Timezone`.
   - **Idempotência estrita**: índice único esparso `(serieId, dataOriginalOcorrencia)`. A regeração em lote é idempotente e nunca duplica ocorrências já existentes.
   - **Imutabilidade da Data Original**: se o usuário alterar a `dataVencimento` de uma ocorrência, o campo `dataOriginalOcorrencia` permanece inalterado. Dessa forma, futuras execuções do motor não recriam uma ocorrência duplicada para a data original.
   - **Regras de Calendário Robustas**:
     - Frequências suportadas: `DIARIO`, `SEMANAL` (com seleção arbitrária de múltiplos dias da semana) e `MENSAL`.
     - Recorrência mensal com modos `DIA_DO_MES` (com ajuste automático para o último dia válido do mês, e.g. dia 31 vira dia 28/29 em fevereiro) e `ORDINAL_SEMANA` (e.g. 1ª segunda-feira, última sexta-feira).
     - Critérios de término por data final (`EM_DATA`), contagem de ocorrências (`APOS_OCORRENCIAS`) ou perpétua (`NUNCA`).

3. **Orquestração dos 3 Escopos de Alteração e Exclusão**:
   - **`ESTA`**: opera isoladamente sobre a tarefa pontual informada. Na edição, desvincula a ocorrência da série definindo `desvinculadaDaSerie = true`, preservando a série intacta. Na exclusão, move unicamente a tarefa para a lixeira.
   - **`ESTA_E_FUTURAS`**:
     - Se a ocorrência for a primeira da série, cancela/desativa a série inteira. Caso contrário, ajusta `dataTermino = dataOriginalOcorrencia - 1 dia` e desativa se não houver mais janelas ativas.
     - Envia para a lixeira todas as tarefas pendentes vinculadas à série cuja `dataOriginalOcorrencia >= dataOriginalOcorrencia` da tarefa alvo.
     - **Regra de Ouro**: ocorrências passadas ou futuras que já foram **concluídas** são preservadas intocadas.
   - **`TODA_A_SERIE`**:
     - Desativa e exclui a `SerieRecorrente`.
     - Move todas as ocorrências pendentes da série para a lixeira.
     - Ocorrências já concluídas têm sua chave `serieId` desvinculada (`null`) e são mantidas no histórico do usuário como tarefas avulsas completadas.

4. **Lixeira Inteligente de 30 Dias e Exclusão em Cascata**:
   - Mecanismo de soft delete unificado via `deletedAt`, `expiresAt` (`deletedAt + 30 dias`) e `loteExclusaoId`.
   - Ao excluir uma `ListaTarefas`, todas as tarefas pertencentes a ela são movidas para a lixeira em cascata compartilhando o mesmo `loteExclusaoId`.
   - **Restauração com Resolução Hierárquica**:
     - Ao restaurar uma tarefa pertencente a uma lista que ainda está na lixeira, a lista pai é restaurada automaticamente em conjunto e o `loteExclusaoId` é limpo.
     - Se a lista pai já tiver sido purgada permanentemente, a tarefa é restaurada desacoplada (vinculada à lista padrão do usuário ou sem lista).
   - Job agendado `PurgaLixeiraTarefasJob` executado periodicamente via Quarkus Scheduler expurgando documentos expirados (`expiresAt <= now`), acompanhado pelo endpoint manual `DELETE /api/tarefas/lixeira/esvaziar`.

5. **Visão Hoje (`GET /api/tarefas/hoje`) e Agrupamento no Frontend**:
   - Endpoint dedicado que entrega em payload estruturado:
     - `hojeLocal`: data de referência resolvida pelo fuso horário do usuário.
     - `atrasadas`: tarefas não concluídas com `dataVencimento < hojeLocal`.
     - `hoje`: tarefas não concluídas com `dataVencimento == hojeLocal` somadas a tarefas concluídas no dia de hoje.
     - `semData`: tarefas no backlog sem vencimento atribuído.
   - No frontend, tarefas atrasadas de uma mesma série recorrente são condensadas visualmente ("X ocorrências atrasadas"), permitindo foco imediato na mais antiga ou pular em lote as ocorrências acumuladas.

6. **Preservação e Delimitação Rígida de Escopo**:
   - A reestruturação de tarefas não alterou o `PomodoroApp.tsx` nem implementou o agregador de agenda de estudos (`AgendaHojeService`), que permanecem estritamente resguardados para a FASE 7.

---


### 3. Checklist de Validação da FASE 6

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **6.1** | Reestruturação das coleções `listas_tarefas`, `series_recorrentes` e `tarefas` | **Atendido** | Entidades normalizadas e repositórios BSON implementados com índices no MongoDB |
| **6.2** | Motor de recorrência com janela de 90 dias a partir de `hojeLocal` | **Atendido** | Validado em `Fase6RecorrenciaEngineTest` e `Fase6TarefaResourceTest#deveGerarRecorrenciasEmJanelaDe90DiasDeFormaIdempotente` |
| **6.3** | Idempotência estrita na geração de ocorrências via índice esparso `(serieId, dataOriginalOcorrencia)` | **Atendido** | Regeração de janela não duplica ocorrências já existentes no banco |
| **6.4** | Imutabilidade do campo `dataOriginalOcorrencia` mesmo com adiamento de `dataVencimento` | **Atendido** | Validado em `Fase6TarefaResourceTest#devePreservarDataOriginalOcorrenciaAoAdiarVencimento` |
| **6.5** | Implementação dos 3 escopos de recorrência (`ESTA`, `ESTA_E_FUTURAS`, `TODA_A_SERIE`) | **Atendido** | Validado em `Fase6TarefaResourceTest#deveExcluirNosTresEscoposPreservandoConcluidas` e no frontend em `fase6Tarefas.test.ts` |
| **6.6** | Preservação irrevogável de ocorrências já concluídas ao alterar ou excluir séries | **Atendido** | Testes comprovam que ocorrências com `concluida = true` permanecem intactas no histórico do usuário |
| **6.7** | Gestão de subtarefas/checklist independente sem auto-conclusão da tarefa pai | **Atendido** | Testado em `Fase6TarefaResourceTest` e no frontend em `fase6Tarefas.test.ts` |
| **6.8** | Lixeira com soft-delete, retenção de 30 dias (`expiresAt = deletedAt + 30d`) e `loteExclusaoId` | **Atendido** | Documentos recebem atributos temporais e lote de exclusão em cascata |
| **6.9** | Restauração inteligente com resolução de lista pai excluída ou purgada | **Atendido** | Validado em `Fase6TarefaResourceTest#deveGerenciarLixeiraERestauracaoComListaExcluidaEPurgada` |
| **6.10** | Job de purga automática de lixeira expirada e endpoint de esvaziamento manual | **Atendido** | `PurgaLixeiraTarefasJob` agendado e `DELETE /api/tarefas/lixeira/esvaziar` validado |
| **6.11** | Visão diária `GET /api/tarefas/hoje` segregando atrasadas, hoje e sem data | **Atendido** | `TarefasHojeResponse` implementado no backend e integrado à visualização principal de `TarefasApp.tsx` |
| **6.12** | Agrupamento de tarefas recorrentes atrasadas no frontend com ação de pular/expandir | **Atendido** | Componente e agrupador implementados em `TarefasApp.tsx` com `TaskItem.tsx` |
| **6.13** | Contratos de frontend alinhados (`domainContracts.ts`, `tarefa.ts`, `tarefaService.ts`) | **Atendido** | Tipos normalizados sem dependências residuais de ciclos de estudos |
| **6.14** | Não interferência em `PomodoroApp.tsx` nem blocos de estudo (reservados para Fase 7) | **Atendido** | Módulo Pomodoro e agregador `AgendaHojeService` mantidos estritamente para a Fase 7 |
| **6.15** | Sucesso completo nos testes de frontend Vitest (`npm test`) | **Atendido** | 9 arquivos de teste, 64/64 testes aprovados (incluindo `fase6Tarefas.test.ts`) |
| **6.16** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem advertências de tipagem |
| **6.17** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 90 testes executados com 0 falhas e 0 erros |


---

---

## Anexos pertinentes  de revisão

## Anexo B — Textos padronizados de interface (pt-BR)

| Contexto | Texto |
|---|---|
| Carregando (leitor de tela) | "Carregando…" |
| Erro de seção (rede) | Título "Não foi possível carregar" · Descrição "Verifique sua conexão e tente novamente." · Botão "Tentar novamente" |
| Erro de seção (servidor) | Título "Algo deu errado" · Descrição "Tente novamente em instantes. Código: {correlationId curto}" |
| Hoje vazio | "Tudo em dia por hoje" · "Nenhuma tarefa ou bloco pendente para hoje." |
| Hub vazio | "Crie sua primeira trilha" · ação "Nova trilha" |
| Histórico vazio | "Nenhuma sessão neste período" · ação "Registrar sessão manual" |
| Lixeira vazia | "A lixeira está vazia" · "Itens excluídos ficam aqui por 30 dias." |
| Caderno sem seções | "Este caderno não tem seções" · ações "Nova seção" / "Abrir lixeira" |
| Busca sem resultado | "Nenhuma página encontrada para “{q}”" · "A busca encontra palavras inteiras." |
| Toast lixeira | "{Tarefa\|Página} movida para a lixeira" · ação "Desfazer" |
| SyncStatusBadge | "Salvo" · "Salvando…" · "Alterações locais pendentes" · "Erro ao salvar" + "Tentar novamente" |
| Descartar formulário | "Descartar alterações?" · "As alterações feitas não serão salvas." · "Continuar editando" / "Descartar" |
| Sair com alterações | "Você tem alterações não salvas" · "Ficar" / "Sair mesmo assim" |
| Falha parcial do modal | "A sessão de estudo foi salva, mas não foi possível marcar o bloco como concluído." · "Tentar concluir bloco novamente" |
| Offline | "Sem conexão. Seus dados foram mantidos." · "Tentar novamente" |
| Escopo recorrente | "Apenas esta" — "Altera só esta ocorrência." · "Esta e as próximas" — "Encerra a série atual nesta data e aplica a mudança daqui em diante. Ocorrências concluídas não mudam." · "Toda a série" — "Aplica a todas as ocorrências pendentes. Concluídas não mudam." |
| Regenerar cronograma | "Gerar blocos novamente?" · "Os blocos atuais e suas conclusões serão substituídos. Sessões e progresso de tópicos são mantidos." |
| Push sem suporte | "Este navegador não suporta notificações. Suas tarefas funcionam normalmente." |
| Push negado | "As notificações estão bloqueadas nas configurações do navegador." |
| iOS não instalado | "Para receber notificações no iPhone/iPad, toque em Compartilhar → Adicionar à Tela de Início e abra o Trilhara por lá." |

---

---

