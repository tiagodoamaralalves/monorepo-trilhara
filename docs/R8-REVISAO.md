# R8 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R8** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R8 — Revisão da F7: Visão Hoje compartilhada, listas virtuais e Pomodoro

**1. Referência e Diagnóstico**
- Singleton de agenda sem reset por conta (SEC-02); `SectionErrorCard` restrito a Hoje (RI-26); contagem N da página (RI-27); agrupamento duplicado (DC-10); `ItemAgendaHojeDTO` possivelmente órfão (DC-11); lista virtual obsoleta após regeneração (D-48); estados de loading/vazio indefinidos (UX-01/02).

**2. Especificação Técnica, Segurança e UX**
- `shared/agenda/agendaHojeService.ts`: estado por `usuarioId` `{ status, tarefas: { status, dados?, erro? }, blocos: { status, dados?, erro? }, hojeLocal }`; `Promise.allSettled`; *throttle* de 5 s em `focus`/`visibilitychange`; invalidação por `AGENDA_INVALIDADA`; reset em logout/troca de conta (D-46). Mudança de dia: se `hojeLocal` retornado diferir do anterior, descartar o estado e recarregar.
- `shared/agenda/agruparAtrasadasPorSerie.ts` (D-23) e `ordenarAgenda.ts` (ordens normativas do regra normativa ).
- `ItemAgendaHojeDTO` (união discriminada) passa a ser **o** tipo de saída do agregador; se não for usado, removê-lo.
- Componente `AgendaHoje` compartilhado (`shared/agenda/AgendaHoje.tsx`) usado por `TarefasApp` e `PomodoroApp` — proibido duplicar a renderização:
  - Seções Atrasadas / Hoje (subgrupos "Blocos de estudo" e "Tarefas") / Sem data, cada uma com cabeçalho `h2`, contador e estados próprios.
  - Carregamento inicial: skeleton `row` ×3 por seção; erro de uma fonte: `SectionErrorCard variant="inline"` **dentro** da seção afetada (falha de blocos → subgrupo "Blocos de estudo"; falha de tarefas → Atrasadas, tarefas de Hoje e Sem data), mantendo os dados da outra fonte.
  - Tudo vazio (ambas as fontes OK): `EmptyState` "Tudo em dia por hoje".
  - Prop `modo: 'tarefas' | 'pomodoro'` controla apenas densidade e a ação "Definir como foco" (Pomodoro).
- Lista virtual por trilha (`TarefasApp`): consulta `Pagina<BlocoCronogramaItemDTO>` com `concluido=false`, "Carregar mais" (botão com `loading`); contagem N via `statusData=ATRASADO&tamanho=1`; resultado parcial do lote exibido num `Toast` com "X concluídos, Y falharam · Tentar novamente"; 404 ao concluir → D-48. A sidebar "Trilhas de Estudo" lista apenas trilhas com `cronogramaAtivoResumo != null`; o item é um link de navegação (sem ações de editar/excluir).
- Pomodoro: "Foco atual" persistido em `trilhara:u:{id}:pomodoro_foco` (somente ID e tipo; ignorado se o item sumir); concluir tarefa não pausa o timer; visitante vê o card "Entre para ver suas tarefas de hoje" com botão que abre o `AuthModal`.

**3. Checklist Atômico de Execução**
1. Refatorar `AgendaHojeManager` para estado por usuário + reset (D-46) e detecção de virada de dia.
2. Extrair `agruparAtrasadasPorSerie` e `ordenarAgenda` para `shared/agenda`; remover as implementações locais.
3. Criar o componente `AgendaHoje` compartilhado; substituir as renderizações em `TarefasApp` e `PomodoroApp`.
4. Aplicar os estados D-37 por seção.
5. Corrigir a contagem N e o resultado parcial do lote na lista virtual.
6. Tratar o 404 de bloco obsoleto (D-48).
7. Resolver `ItemAgendaHojeDTO` (usar ou remover).
8. Garantir que os filtros gerais de Tarefas não renderizam blocos (teste).

**4. Critérios de Aceite e Validação Automatizada**
- `agendaHojeService.test.ts`: ordenação das 3 seções; agrupamento sob a mais recente; falha de blocos mantém tarefas e `erroBlocos`; falha de tarefas mantém blocos; throttle de 5 s (fake timers); **troca de conta**: A carrega → logout → B carrega → estado sem itens de A e nenhuma renderização intermediária com dados de A; virada de `hojeLocal` reseta.
- `agendaHoje.test.tsx`: skeleton atrasado 200 ms; `SectionErrorCard` só no subgrupo afetado; retry recarrega somente a fonte com falha; vazio total → `EmptyState`; concluir manual não abre modal; concluir bloco abre `BLOCO_DIRETO`.
- `listaVirtual.test.tsx`: N exato vindo de `totalItens`; "Carregar mais"; resultado parcial com retry; 404 → recarrega + toast; nenhuma ação de editar/excluir em bloco.
- `pomodoroAgenda.test.tsx`: concluir tarefa não pausa o timer; visitante → card + `abrirAuthModal`.
- `rg "agruparAtrasadas" frontend/src -l` → somente o arquivo em `shared/agenda` e seus testes.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-23 — Hoje somente pendentes e agrupamento único**
- *Decisão Cravada:* `GET /api/tarefas/hoje` retorna apenas `estado = PENDENTE` e `deletedAt == null`. Concluídas somem com animação curta (respeitando reduced-motion) e ficam em "Concluídas". O agrupamento de atrasadas por série existe **uma única vez** em `frontend/src/shared/agenda/agruparAtrasadasPorSerie.ts` (sob a ocorrência **mais recente**, expansor "+ N ocorrências anteriores atrasadas"), usado por Tarefas e Pomodoro.

**D-46 — Cache de dados em memória e troca de conta**
- *Decisão Cravada:* Todo cache/singleton de dados de domínio no frontend (agenda, trilhas, cadernos) é indexado por `usuarioId` e **zerado** em `trilhara:logout`, `trilhara:conta-excluida` e troca de usuário detectada no foco. Teste: logar A → carregar agenda → logout → logar B → nenhuma requisição/render usa dados de A.

**D-48 — Lista virtual obsoleta após regeneração**
- *Decisão Cravada:* Se `PATCH /api/blocos/{id}/conclusao` retornar 404 (bloco de versão substituída), a UI recarrega a lista/agenda e mostra o toast "O cronograma foi atualizado. A lista foi recarregada." A sessão eventualmente já salva **não** é reenviada.

---

## Contexto normativo de domínio pertinente

### 7.4 Blocos de cronograma em Tarefas

- Não persistir bloco como tarefa. Uma lista virtual por trilha renderiza todos os blocos pendentes de todo o período gerado, em ordem de data, com indicação de atrasado/hoje/futuro.
- Filtros gerais **Futuro**, **Todas** e **Concluídas** não incluem blocos. A lista da trilha e Hoje são as exceções definidas.
- Hoje inclui somente blocos do dia atual, não blocos futuros ou atrasados.
- Blocos não podem ser criados/editados como tarefas. Concluir/reabrir usa endpoint de Estudos.
- Concluir um bloco abre o modal de sessão compartilhado.
- A ação **Concluir todas as atrasadas sem salvar** mostra confirmação com quantidade; implementar item a item/idempotentemente, reportando sucessos/falhas parciais e permitindo retry sem sessão. Não exigir transação Mongo multi-documento.

## 8. Pomodoro e timers

- Pomodoro independente mantém timer/configuração local e não registra sessão ao terminar, parar ou concluir uma tarefa manual.
- Pomodoro consulta e espelha exatamente a visão Hoje de Tarefas: tarefas manuais de hoje, atrasadas e sem data, e blocos de ciclo apenas de hoje. Não cria cópia/importação persistida.
- Exibir pílula da trilha nos blocos. Concluir tarefa manual chama a mesma ação de Tarefas; concluir bloco chama o modal de sessão compartilhado.
- Selecionar tarefa no Pomodoro é apenas contexto visual; não associa sessão/timer à tarefa. Concluir tarefa não pausa o timer.
- Cronômetro de Estudos e Pomodoro persistem seu estado completo em `localStorage` sob chaves escopadas por usuário (`trilhara:u:{usuarioId}:study_timer` e `trilhara:u:{usuarioId}:pomodoro_timer`), calculando o tempo em execução por diferença de timestamp absoluto (`Date.now() - runningSince` / `targetEndAt - Date.now()`) para suportar refresh, fechamento acidental de aba e *throttling* de segun sem *drift*.
- Em Estudos, existe no máximo **um único timer ativo ou pausado por usuário/dispositivo**; iniciar o cronômetro em outra trilha, disciplina ou aba acumula o tempo decorrido e pausa/substitui o anterior após confirmação caso haja tempo não salvo (`>= 60s`).
- Sincronizar estado do timer entre abas de mesma origem via eventos `storage` e `BroadcastChannel`. Ao fazer logout (ou expirar sessão), se o timer estiver rodando, acumular o tempo até o instante do logout (`elapsedMs += now - runningSince`), mudar `runningSince = null` (pausado) na chave `trilhara:u:{usuarioId}:*` e limpar os dados de sessão em memória. Ao autenticar novamente com a mesma conta, restaurar o timer pausado; outra conta jamais lê chaves de outro `usuarioId`.
- Se o usuário estiver offline, o cronômetro continua funcionando localmente, mas ações de escrita (salvar sessão, concluir bloco/tarefa) exibem erro claro de conectividade mantendo os dados preenchidos no modal para retry explícito.

## 9. Visão compartilhada de Hoje e integração entre domínios

- Manter APIs backend separadas: Tarefas fornece tarefas manuais; Estudos fornece blocos do dia de todos os cronogramas ativos em todas as trilhas.
- Um serviço frontend compartilhado agrega os dois resultados para Tarefas e Pomodoro, sem mover lógica de domínio para uma coleção agregadora.
- Ordem das seções na Visão Hoje (Tarefas e Pomodoro):
  1. **Atrasadas** (somente tarefas manuais pendentes com `dataVencimento < hojeLocal`, agrupadas por série quando recorrentes): ordenadas por `dataVencimento ASC, prioridade DESC, ordem ASC`.
  2. **Hoje** (blocos pendentes de `hojeLocal` de todos os cronogramas ativos + tarefas manuais pendentes com `dataVencimento == hojeLocal`): blocos ordenados por `trilhaNome ASC, ordem ASC` e tarefas manuais ordenadas por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`.
  3. **Sem data** (somente tarefas manuais pendentes com `dataVencimento == null`): ordenadas por `prioridade DESC, ordem ASC`.
- Atualizar automaticamente após mutações locais, ao receber eventos `BroadcastChannel`/`storage` na mesma origem e nos eventos `visibilitychange` (quando `document.visibilityState === 'visible'`) e `focus` da janela (com *throttle* mínimo de 5 segundos para evitar rajadas de requisições).
- Se uma fonte falhar, renderizar a seção disponível e erro explícito/retry para a seção indisponível; não substituir falha por lista vazia.
- APIs de blocos devem oferecer consulta eficiente para todos os cronogramas ativos do usuário no dia e consultas paginadas para listas por trilha.
- API de blocos em Hoje retorna apenas blocos pendentes (`concluido == false`) no `hojeLocal`, com `trilhaId`, `cronogramaId`, `blocoId`, `disciplinaId`, nomes/cores snapshot, `data`, `ordem`, `duracaoMinutos`; a união de tela diferencia explicitamente item manual e bloco. Na seção Hoje, tarefas e blocos são grupos visuais separados, cada qual com sua ordenação própria.

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

---

## Plano original da FASE 7 (referência de escopo)

## FASE 7 — Integração Diária: `SectionErrorCard`, `AgendaHojeService`, Listas Virtuais em Tarefas e Espelho no Pomodoro

### 7.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1, 2, 3, 4, 5 e 6 concluídas.
- **Objetivo da fase:**
  1. Implementar o componente `SectionErrorCard` no Design System (`frontend/src/shared/design-system`).
  2. Implementar o agregador `AgendaHojeService` em `frontend/src/shared/services/agendaHojeService.ts`.
  3. Integrar em `TarefasApp.tsx` a Visão Hoje compartilhada e as **listas virtuais por trilha** (consumindo os blocos de Estudos da FASE 4 e abrindo o `GlobalSessionModal` da FASE 5).
  4. Integrar em `PomodoroApp.tsx` o espelho exato da Visão Hoje ao lado do cronômetro Pomodoro.
- **O que NÃO fazer nesta fase:**
  - Jamais gravar ou copiar blocos de cronograma na coleção `tarefas`.

### 7.3 Critérios de Saída da FASE 7
- Testes automatizados no frontend/integração validando:
  - `agendaHojeService` combina e ordena corretamente Atrasadas (com agrupamento de série), Hoje e Sem data.
  - Falha simulada em `GET /api/blocos/hoje` renderiza as tarefas manuais normalmente e exibe `SectionErrorCard` com botão de retry.
  - Concluir tarefa manual em Tarefas ou Pomodoro não abre `GlobalSessionModal`; concluir bloco abre `GlobalSessionModal`.
- `mvn test` e `npm run build` passando sem erros.
- Testes frontend executam sob Vitest; build sozinho não vale como comprovação dos critérios de UI.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 7                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 8                                 -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 7 (hipótese a verificar)

## FASE 7 — Integração Diária: `SectionErrorCard`, `AgendaHojeService`, Listas Virtuais em Tarefas e Espelho no Pomodoro

### 1. Resumo Executivo e Decisões de Arquitetura

1. **Componente de Resiliência do Design System (`SectionErrorCard`)**:
   - Implementado componente compacto de erro recuperável no Design System (`frontend/src/shared/design-system/SectionErrorCard`).
   - Apresenta ícone de alerta SVG, mensagem descritiva da falha de carregamento da seção específica e botão de ação com `aria-label="Tentar novamente"` que aciona a função de callback `onRetry`.
   - Exportado centralizadamente em `frontend/src/shared/design-system/index.ts`.

2. **Agregador Diário Reativo (`agendaHojeService.ts`)**:
   - Desenvolvido singleton gerenciador `AgendaHojeManager` e hook reativo `useAgendaHoje()`.
   - **Busca em Paralelo com Tolerância a Falhas**: orquestra requisições via `Promise.allSettled` para `GET /api/tarefas/hoje` e `GET /api/blocos/hoje`.
   - **Ordenação Determinística em 3 Seções**:
     - **Seção 1 — Atrasadas**: tarefas manuais não concluídas com `dataVencimento < hojeLocal`, ordenadas estritamente por `dataVencimento ASC, prioridade DESC, ordem ASC`. Quando houver múltiplas tarefas da mesma série recorrente (`serieId`), agrupa ocorrências anteriores sob a mais recente, permitindo expansão visual via toggle `"+ N ocorrência(s) anterior(es) atrasada(s)"`.
     - **Seção 2 — Hoje**: subdivisão sem intercalação artificial:
       - *Blocos de estudo de hoje*: ordenados por `trilhaNome ASC, ordem ASC`, com pílula de identificação da trilha e cor da disciplina.
       - *Tarefas manuais de hoje*: pendentes com `dataVencimento == hojeLocal`, ordenadas por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`.
     - **Seção 3 — Sem data**: tarefas manuais pendentes sem vencimento (`dataVencimento == null`), ordenadas por `prioridade DESC, ordem ASC`.
   - **Tratamento de Falha Parcial**: se a API de Estudos falhar mas a de Tarefas responder com sucesso (ou vice-versa), os dados disponíveis são entregues normalmente e o estado registra `erroEstudos` ou `erroTarefas`. A interface do usuário exibe o `SectionErrorCard` exclusivamente na seção indisponível, impedindo listas vazias silenciosas.
   - **Sincronização Reativa Multiabas e Visibilidade**: escuta o canal `BroadcastChannel('trilhara_agenda_hoje')`, ouvintes nos eventos de janela `focus` e `visibilitychange` com *throttle* de 5 segundos e notificação de mutações locais com despacho automático no canal.

3. **Listas Virtuais por Trilha no Módulo Tarefas (`TarefasApp.tsx`)**:
   - A sidebar de navegação (`MinhasListasNav.tsx` e `Navbar.tsx`) renderiza a seção **"Trilhas de Estudo"** com as trilhas ativas do usuário que possuem cronograma ativo (`GET /api/trilhas`).
   - Ao selecionar uma trilha de estudo, exibe a **Lista Virtual da Trilha** consumindo diretamente `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos?concluido=false&pagina=1&tamanho=50`.
   - **Isolamento BSON**: blocos de estudo **jamais são gravados ou copiados na coleção `tarefas`**. As visualizações gerais de Tarefas (Futuro, Todas, Concluídas) continuam exibindo unicamente tarefas manuais.
   - Os blocos virtuais exibem badges contextuais (`Atrasado`, `Hoje`, `Futuro`) e botão paginado "Carregar mais". É desabilitada qualquer criação, alteração textual ou exclusão de bloco como se fosse tarefa.
   - **Conclusão em Lote de Atrasados**: botão de topo *"Concluir todas as atrasadas sem salvar (N)"* aciona diálogo de confirmação seguro (`ConfirmDialog`) com contagem exata e executa `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados` com suporte a retentativas idempotentes e tratamento de falhas pontuais.
   - Ao concluir um bloco individual na lista virtual ou na visão diária, abre o `GlobalSessionModal` configurado no modo `BLOCO_DIRETO` com opções de salvar sessão de estudo ou concluir sem salvar.

4. **Espelho da Visão Hoje no Módulo Pomodoro (`PomodoroApp.tsx`)**:
   - Layout responsivo em duas colunas ("cockpit" de foco à esquerda e agenda diária à direita).
   - Para usuários autenticados, renderiza o espelho fiel da Visão Hoje alimentado por `useAgendaHoje()`.
   - **Seleção de Foco Visual**: o usuário pode clicar em qualquer tarefa manual ou bloco de estudo para definir o foco atual ("Foco Atual: [Título]"), de maneira puramente visual e sem acoplamento rígido ao cronômetro.
   - **Conclusão de Tarefa Manual**: aciona `tarefaService.atualizarConclusao`, remove a tarefa da listagem e **não pausa o timer nem abre o modal de sessão**.
   - **Conclusão de Bloco de Estudo**: abre o `GlobalSessionModal` em modo `BLOCO_DIRETO` para que o usuário decida se deseja computar o tempo no ciclo e nas métricas de estudo da trilha.
   - **Experiência do Visitante Não Autenticado**: caso o usuário não esteja logado, o cronômetro Pomodoro funciona normalmente e o painel lateral exibe um card convidativo para login/cadastro com botão direto para o modal de autenticação (`abrirAuthModal`).

---


### 3. Checklist de Validação da FASE 7

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **7.1** | Componente `SectionErrorCard` no Design System com ícone de alerta, mensagem e botão de retry | **Atendido** | Criado em `frontend/src/shared/design-system/SectionErrorCard/` e exportado em `index.ts` |
| **7.2** | Consulta paralela resiliente em `agendaHojeService.ts` via `Promise.allSettled` | **Atendido** | `AgendaHojeManager.carregar()` consome `GET /api/tarefas/hoje` e `GET /api/blocos/hoje` em paralelo |
| **7.3** | Ordenação determinística de Atrasadas (`dataVencimento ASC, prioridade DESC, ordem ASC`) | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` ("deve combinar e ordenar corretamente Atrasadas...") |
| **7.4** | Agrupamento de tarefas recorrentes atrasadas da mesma série sob a mais recente | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` com `"+ 2 ocorrência(s) anterior(es) atrasada(s)"` |
| **7.5** | Subdivisão da seção Hoje sem intercalação artificial (blocos por `trilhaNome, ordem` e tarefas por `horario, prioridade, ordem`) | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` com blocos da Trilha A e Trilha B antecedendo tarefas manuais |
| **7.6** | Ordenação determinística da seção Sem Data (`prioridade DESC, ordem ASC`) | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` |
| **7.7** | Resiliência a falhas parciais (`erroEstudos` / `erroTarefas`) sem lista vazia silenciosa | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` com fallback e renderização de `SectionErrorCard` |
| **7.8** | Sincronização via `BroadcastChannel('trilhara_agenda_hoje')` e throttle de 5s em `visibilitychange`/`focus` | **Atendido** | Implementado em `AgendaHojeManager` com desinscrição e controle de timestamp |
| **7.9** | Sidebar de Tarefas com seção "Trilhas de Estudo" e listas virtuais | **Atendido** | `MinhasListasNav.tsx` e `TarefasApp.tsx` integram trilhas com cronograma ativo |
| **7.10** | Isolamento BSON estrito: nenhum bloco é gravado ou copiado na coleção `tarefas` | **Atendido** | Listas virtuais consultam `/api/trilhas/{id}/cronogramas/{id}/blocos`; filtros padrão mantêm apenas tarefas |
| **7.11** | Paginação e badges contextuais (`Atrasado`, `Hoje`, `Futuro`) na lista virtual de estudos | **Atendido** | Renderizado em `TarefasApp.tsx` com botão "Carregar mais" e badges estilizados |
| **7.12** | Conclusão em lote de atrasados sem salvar sessão via `ConfirmDialog` e endpoint dedicado | **Atendido** | Integrado em `TarefasApp.tsx` chamando `/concluir-atrasados` com feedback visual de conclusão |
| **7.13** | Conclusão de bloco de estudo abre `GlobalSessionModal` em modo `BLOCO_DIRETO` | **Atendido** | Validado em `TarefasApp.tsx` e `PomodoroApp.tsx`, coberto por teste unitário no Vitest |
| **7.14** | Espelho da Visão Hoje no Pomodoro ao lado do cockpit com suporte a foco visual | **Atendido** | Layout em 2 colunas implementado em `PomodoroApp.tsx` com seleção não bloqueante de foco |
| **7.15** | Conclusão de tarefa manual no Pomodoro não pausa timer nem abre modal de sessão | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` |
| **7.16** | Estado convidativo para visitantes não autenticados no Pomodoro com link para login | **Atendido** | Validado em `fase7IntegracaoDiaria.test.tsx` verificando renderização do card e acionamento de `abrirAuthModal` |
| **7.17** | Sucesso em 100% dos testes automatizados de frontend (`npm test -- --run`) | **Atendido** | 10 arquivos de teste, 73/73 testes aprovados |
| **7.18** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | Compilação `tsc -b && vite build` concluída sem nenhum erro |
| **7.19** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 90 testes executados com 0 falhas e 0 erros |


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

