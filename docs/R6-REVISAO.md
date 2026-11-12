# R6 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R6** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R6 — Revisão da F5: `GlobalSessionModal`, `sessaoGlobalService` e timers

**1. Referência e Diagnóstico**
- Namespace `anon` no timer de Estudos (RI-16/DC-14); retry do registro manual pode duplicar trilha/disciplina (RI-17); semântica do timer ao cancelar/salvar indefinida (D-22); rótulo divergente (UX-07); canais heterogêneos (UX-11); fechar com dados editados descarta sem aviso (UX-06); tratamento de offline/401 no modal não especificado.

**2. Especificação Técnica, Segurança e UX**
- `sessaoGlobalService` (único ponto de `POST .../sessoes`):
  ```ts
  registrarSessao(trilhaId: string, payload: RegistrarSessaoPayload, chave: string): Promise<SessaoRegistradaDTO>
  concluirBloco(blocoId: string): Promise<void>            // PATCH { concluido: true }
  executarFluxo(params): Promise<ResultadoFluxo>           // etapas com estado { sessaoSalva, sessaoId?, blocoConcluido }
  ```
- `GlobalSessionModal` conforme D-22. Estado interno serializável `{ modo, chaveIdempotencia, trilhaId, disciplinaId, criadosInline: { trilhaId?, disciplinaId? }, sessaoSalva, sessaoId?, erro?, sujo }`. Foco inicial no primeiro campo editável; `aria-labelledby` no título; Escape/overlay bloqueados durante `isSaving`; com `sujo` → confirmação de descarte.
- Duração em horas + minutos (inputs numéricos com `inputmode="numeric"`), exibindo também os segundos originais do timer como texto auxiliar quando houver ("Tempo medido: 25 min 37 s"); o valor enviado preserva os segundos se o usuário não editar os campos.
- `StudyTimerContext`: chave `trilhara:u:{usuarioId}:study_timer`; sem usuário → contexto inativo (botões desabilitados com "Entre para usar o cronômetro"). Troca de contexto com ≥ 60 s acumulados → `ConfirmDialog`.
- Pomodoro: `trilhara:u:{usuarioId}:pomodoro_timer` (autenticado) / `trilhara:anon:pomodoro_timer` (visitante), sem migração.
- Barramento único `BroadcastChannel("trilhara")` (D-21) em `shared/sync/barramento.ts`, com fallback por evento `storage` em uma chave `trilhara:bus`.

**3. Checklist Atômico de Execução**
1. Remover o fallback `'anon'` do `StudyTimerContext`; desabilitar o timer sem autenticação.
2. Separar o namespace anônimo do Pomodoro.
3. Criar `shared/sync/barramento.ts`; migrar `trilhara_study_timer`, `trilhara_pomodoro_timer`, `trilhara_agenda_hoje`, `trilhara:auth` para mensagens tipadas; descartar mensagens de outro `usuarioId`.
4. Unificar os eventos DOM (`trilhara:auth-required`, `trilhara:logout`, `trilhara:conta-excluida`) e remover os antigos.
5. Implementar no modal: pausa do timer ao abrir, reset apenas após sucesso, guarda de descarte, rótulos fixos, reutilização de IDs criados inline, tratamento de rede/401/409/400.
6. Integrar `useUnsavedChanges` no modal (sujo → registrado).
7. Emitir `AGENDA_INVALIDADA` após salvar/concluir.
8. Verificar que `EstudarView`, `PlanejamentoView`, `TarefasApp` e `PomodoroApp` só abrem o modal via `useGlobalSessionModal()`.

**4. Critérios de Aceite e Validação Automatizada**
- `sessaoGlobalService.test.ts`: header e body com a mesma chave; fluxo de 2 etapas; falha na etapa 2 → retry só do PATCH (fetch de POST chamado 1×); 401 → evento `trilhara:auth-required` e retry com a mesma chave após login simulado.
- `globalSessionModal.test.tsx`: os 4 modos; "Concluir sem salvar sessão" só com bloco; cancelar mantém o timer pausado com o tempo acumulado; salvar zera o timer; fechar sujo pede confirmação; offline mantém os campos preenchidos; manual com criação inline + falha + retry → `POST /api/trilhas` chamado 1×; `40 s` → erro de duração mínima; acertos > questões → erro inline.
- `studyTimer.test.tsx`: sem usuário → desabilitado e nenhuma chave gravada; A e B isolados; logout pausa e persiste; mensagem do barramento com `usuarioId` diferente é ignorada; alarme não abre modal (fake timers); drift ≤ 1 s após "suspender" 10 min (`vi.setSystemTime`).
- `pomodoro.test.tsx`: visitante usa `trilhara:anon:`; login não migra.
- `rg "trilhara_study_timer|trilhara_pomodoro_timer|trilhara_agenda_hoje|'trilhara:auth'|\|\| 'anon'" frontend/src` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-21 — Namespaces locais e barramento entre abas**
- *Dúvida/Lacuna:* O namespace `anon` está misturado com `trilhara:u:`, e os canais/eventos são heterogêneos.
- *Deliberação e Referência:* Isolamento por conta (regra normativa) e consistência de API interna.
- *Decisão Cravada:* (a) O timer de Estudos **exige autenticação**; não existe `trilhara:u:anon:*`. (b) O Pomodoro de visitante usa `trilhara:anon:pomodoro_timer`; ao autenticar, **não migra** automaticamente (o estado anônimo é preservado separado). (c) Um único `BroadcastChannel("trilhara")` com mensagens tipadas `{ v: 1, tipo: 'TIMER_ESTUDOS_ATUALIZADO' | 'TIMER_POMODORO_ATUALIZADO' | 'AGENDA_INVALIDADA' | 'SESSAO_ENCERRADA' | 'CONTA_EXCLUIDA', usuarioId: string | null, origemAbaId: string, payload }`. Mensagens com `usuarioId` diferente do usuário em memória são **descartadas**. (d) Eventos DOM unificados no prefixo `trilhara:` (`trilhara:auth-required`, `trilhara:logout`, `trilhara:conta-excluida`). Remover os nomes antigos.

**D-22 — `GlobalSessionModal`: semântica completa**
- *Dúvida/Lacuna:* O que acontece com o timer ao cancelar/salvar? Como evitar uma trilha duplicada no registro manual? Fechar com dados editados?
- *Deliberação e Referência:* Linear pede confirmação ao descartar um rascunho editado; Toggl mantém o timer intacto até o "Save" confirmar.
- *Decisão Cravada:*
  1. Ao abrir em `ESTUDOS_TIMER`/`BLOCO_TIMER`, o timer é **pausado** (não zerado). Cancelar/fechar mantém o timer pausado com o tempo acumulado. Somente após `201/200` da sessão o timer é zerado e a chave `study_timer` é removida.
  2. Fechar o modal com campos alterados pelo usuário exige `ConfirmDialog` "Descartar registro?" (**Continuar editando** [padrão] / **Descartar**). Sem alterações, fecha direto.
  3. Rótulos fixos: **Salvar sessão**, **Concluir sem salvar sessão** (só em modos com bloco), **Cancelar**, **Tentar concluir bloco novamente**.
  4. Modo `MANUAL` com criação inline: IDs de trilha/disciplina criadas ficam no estado do modal; um retry reutiliza esses IDs e nunca recria. A `chaveIdempotencia` é gerada na abertura e só muda ao abrir um novo modal.
  5. Erros: rede/offline (`TypeError` de fetch ou `navigator.onLine === false`) → banner inline "Sem conexão. Seus dados foram mantidos." + botão **Tentar novamente**; `401` → `AuthModal` por cima, com retry automático do mesmo payload/chave após login; `409 IDEMPOTENCY_KEY_CONFLICT` → mensagem de erro e nova chave **só** se o usuário confirmar novo envio; `400` → erros inline por campo a partir de `errors`.
  6. Após salvar: emitir `AGENDA_INVALIDADA` no barramento e invalidar as consultas de estatísticas/histórico.

---

## Contexto normativo de domínio pertinente

### 2.1 Estudos

É a fonte de verdade de trilhas, disciplinas, tópicos, cronogramas, progresso dos tópicos, sessões e estatísticas derivadas.

O cronômetro da aba **Estudar** pertence a Estudos. Exige uma trilha selecionada e uma disciplina; o registro manual pode criar uma trilha e disciplina se ainda não houver contexto adequado. Quando o cronômetro é iniciado a partir de um bloco planejado (**Estudar este bloco**), ele carrega a trilha, a disciplina e a referência da ocorrência do bloco; ao clicar em **Encerrar e salvar**, o modal global é aberto com o tempo real focado em segundos e, após salvar a sessão, conclui o bloco vinculado.

### 2.4 Pomodoro

O módulo em `pomodoro.localhost` (ou `/pomodoro`) tem cronômetro próprio, independente de Estudos e sem criação automática de sessões. Para usuários autenticados, exibe ao lado do cronômetro a visão de Hoje compartilhada com Tarefas (tarefas manuais de hoje, atrasadas e sem data, mais blocos do dia dos cronogramas ativos). Para visitantes não autenticados, o cronômetro funciona localmente e o painel de tarefas exibe chamada para autenticação. Concluir tarefa manual executa a mesma ação de Tarefas; concluir bloco de cronograma aciona o modal global de sessão.

### 6.4 Falhas ao concluir bloco

- Concluir bloco abre o modal global. O bloco só muda para concluído quando o usuário salva a sessão ou escolhe explicitamente **Concluir sem salvar**.
- Cancelar/voltar/fechar retorna ao contexto anterior e deixa o bloco pendente.
- Ao salvar: gravar sessão idempotentemente e então concluir bloco. Sessão e bloco não precisam de vínculo persistente.
- Se salvar sessão funcionar e concluir bloco falhar, informar “sessão salva, bloco ainda pendente” e permitir retry apenas da conclusão, sem criar outra sessão.
- Payload/chave de idempotência permanecem estáveis durante a abertura do modal. Se aba/reload interromper a segunda etapa, sessão já salva não é revertida nem reenviada automaticamente; ao voltar, bloco continua pendente e pode ser concluído sem sessão ou iniciar novo fluxo intencional.
- Desconcluir bloco não exclui sessões. Podem existir múltiplas sessões informativas no mesmo contexto/bloco.
- Falhas de conexão não enfileiram operações no servidor no MVP; preservar formulário/dados, mostrar erro e permitir retry.

## 8. Pomodoro e timers

- Pomodoro independente mantém timer/configuração local e não registra sessão ao terminar, parar ou concluir uma tarefa manual.
- Pomodoro consulta e espelha exatamente a visão Hoje de Tarefas: tarefas manuais de hoje, atrasadas e sem data, e blocos de ciclo apenas de hoje. Não cria cópia/importação persistida.
- Exibir pílula da trilha nos blocos. Concluir tarefa manual chama a mesma ação de Tarefas; concluir bloco chama o modal de sessão compartilhado.
- Selecionar tarefa no Pomodoro é apenas contexto visual; não associa sessão/timer à tarefa. Concluir tarefa não pausa o timer.
- Cronômetro de Estudos e Pomodoro persistem seu estado completo em `localStorage` sob chaves escopadas por usuário (`trilhara:u:{usuarioId}:study_timer` e `trilhara:u:{usuarioId}:pomodoro_timer`), calculando o tempo em execução por diferença de timestamp absoluto (`Date.now() - runningSince` / `targetEndAt - Date.now()`) para suportar refresh, fechamento acidental de aba e *throttling* de segun sem *drift*.
- Em Estudos, existe no máximo **um único timer ativo ou pausado por usuário/dispositivo**; iniciar o cronômetro em outra trilha, disciplina ou aba acumula o tempo decorrido e pausa/substitui o anterior após confirmação caso haja tempo não salvo (`>= 60s`).
- Sincronizar estado do timer entre abas de mesma origem via eventos `storage` e `BroadcastChannel`. Ao fazer logout (ou expirar sessão), se o timer estiver rodando, acumular o tempo até o instante do logout (`elapsedMs += now - runningSince`), mudar `runningSince = null` (pausado) na chave `trilhara:u:{usuarioId}:*` e limpar os dados de sessão em memória. Ao autenticar novamente com a mesma conta, restaurar o timer pausado; outra conta jamais lê chaves de outro `usuarioId`.
- Se o usuário estiver offline, o cronômetro continua funcionando localmente, mas ações de escrita (salvar sessão, concluir bloco/tarefa) exibem erro claro de conectividade mantendo os dados preenchidos no modal para retry explícito.

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

## Plano original da FASE 5 (referência de escopo)

## FASE 5 — Frontend Shared & Timers: `GlobalSessionModal`, Serviço Único de Sessão e Timers Isolados por Conta

### 5.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1, 2, 3 e 4 concluídas.
- **Objetivo da fase:** Implementar em `frontend/src/shared/` o serviço único `sessaoGlobalService` e o componente compartilhado `GlobalSessionModal` (reutilizado por Estudos, Tarefas e Pomodoro), conectar a aba **Estudar** e a aba **Planejamento** de Estudos a esse modal, e refatorar os timers de Estudos e do Pomodoro para persistirem em `localStorage` sob chaves escopadas por `usuarioId` (`trilhara:u:{usuarioId}:*`) com sincronização entre abas.
- **O que NÃO fazer nesta fase:**
  - Não alterar ainda as listas de Tarefas (`TarefasApp.tsx`) nem a lista de tarefas dentro do Pomodoro (isso será feito na FASE 7). Na FASE 5, em `PomodoroApp.tsx`, ajuste apenas a persistência/sincronização do cronômetro Pomodoro em si.

### 5.3 Critérios de Saída da FASE 5
- Build completo (`npm run build`) passando sem erros.
- `npm test` passa; testes cobrem perda de foco, alarme sem autosave, clique explícito para salvar e isolamento de timer entre usuários/origens.
- Testes automatizados de unidade/integração para `sessaoGlobalService` e `StudyTimerContext` validando:
  - Fluxo em 2 etapas (sucesso total e falha parcial na conclusão do bloco mantendo `sessaoSalva = true` para retry apenas do bloco).
  - Isolamento das chaves `trilhara:u:{usuarioId}:*` entre Usuário A e Usuário B e pausa automática no logout.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 5                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 6                                 -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 5 (hipótese a verificar)

## FASE 5 — Frontend Shared & Timers: `GlobalSessionModal`, Serviço Único de Sessão e Timers Isolados por Conta

### 1. Resumo Executivo e Decisões de Arquitetura

1. **Serviço Centralizado `sessaoGlobalService`**:
   - Implementado em `frontend/src/shared/services/sessaoGlobalService.ts` como canal único para criação de sessão (`POST /api/trilhas/{trilhaId}/sessoes`) e conclusão de bloco (`PATCH /api/blocos/{blocoId}/conclusao`).
   - Idempotência ponta a ponta: recebe e repassa a chave de idempotência tanto no cabeçalho `Idempotency-Key` quanto no payload JSON.
   - **Fluxo em Duas Etapas**: orquestração de registro com bloco vinculado (Etapa 1: cria sessão; Etapa 2: conclui bloco). Em caso de falha de rede/servidor na Etapa 2, mantém `sessaoSalva = true` no estado em memória para retry exclusivo da Etapa 2 sem duplicar a sessão.

2. **Componente Compartilhado `GlobalSessionModal`**:
   - Desenvolvido no Design System (`frontend/src/shared/design-system/GlobalSessionModal/`) com Provider e hook `useGlobalSessionModal()`.
   - Suporte nativo aos 4 modos de abertura contratuais:
     - `ESTUDOS_TIMER`: acionado ao encerrar o cronômetro de Estudos; duração inicial em segundos, data somente leitura (`hojeLocal`).
     - `BLOCO_DIRETO`: acionado ao concluir bloco no Planejamento; duração inicial sugerida baseada no bloco, botão secundário *"Concluir sem salvar sessão"* chamando diretamente a conclusão do bloco.
     - `BLOCO_TIMER`: acionado ao encerrar cronômetro iniciado a partir de bloco ("Estudar este bloco"); executa o fluxo em duas etapas.
     - `MANUAL`: formulário completo com seleção/criação inline de trilhas e disciplinas, data editável (`<= hojeLocal`) e inputs de duração em horas/minutos.
   - Métricas derivadas em tempo real: cálculo instantâneo de **Erros** (`questoes - acertos`) e **Aproveitamento (%)** (`(acertos / questoes) * 100`).
   - Acessibilidade WCAG 2.2 AA: *focus trap* ativo, foco inicial no primeiro campo editável, suporte a `Escape` (desabilitado durante `isSaving`) e devolução do foco ao elemento acionador ao fechar.

3. **Cronômetro de Estudos (`StudyTimerContext`) Isolado por Conta**:
   - Persistência desacoplada em `localStorage` sob chave unívoca escopada por usuário: `trilhara:u:${usuarioId || 'anon'}:study_timer`.
   - Contagem baseada em diferença de timestamp absoluto (`accumulatedElapsedMs + (runningSince ? now - runningSince : 0)`), imune a *throttling* de abas em segun, fechamento acidental e recarregamento de página.
   - **Regra Estrita do Alarme**: o atingimento da meta/zero aciona alarme sonoro e notificação, mas **nunca abre o modal nem grava sessão automaticamente** — exigindo clique explícito em "Encerrar e salvar".
   - **Exclusividade e Sincronização entre Abas**: no máximo 1 timer ativo por usuário na mesma origem, sincronizado instantaneamente via `BroadcastChannel('trilhara_study_timer')` e evento `storage`. Troca de contexto com mais de 60s acumulados exige confirmação.
   - **Logout / Troca de Conta**: ao interceptar `trilhara:auth:logout` ou alteração de conta, acumula o tempo até aquele instante, define `runningSince: null` na chave do usuário e limpa o estado em memória. Ao logar novamente na mesma conta, o timer pausado é restaurado integralmente.

4. **Cronômetro Pomodoro (`PomodoroApp`) Isolado e Sincronizado**:
   - Persistência e configurações sob chave escopada: `trilhara:u:${usuarioId || 'anon'}:pomodoro_timer`.
   - Sincronização entre abas da mesma conta via `BroadcastChannel('trilhara_pomodoro_timer')` e evento `storage`.
   - Pausa automática e persistência do tempo decorrido ao realizar logout.
   - Término de ciclo Pomodoro toca alerta sonoro/notificação, mas **jamais abre modal de sessão nem registra sessões no módulo de Estudos**.
   - Listas de tarefas do Pomodoro e do TarefasApp preservadas intocadas (reservadas para a Fase 7).

---


### 3. Checklist de Validação da FASE 5

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **5.1** | Criação do serviço centralizado `sessaoGlobalService` | **Atendido** | `frontend/src/shared/services/sessaoGlobalService.ts` com métodos `registrarSessao`, `concluirBloco` e `executarFluxoDuasEtapas` |
| **5.2** | Propagação de `Idempotency-Key` no header HTTP e no body da sessão | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveRegistrarSessaoEnviandoChaveIdempotencia` |
| **5.3** | Fluxo em duas etapas com suporte a retry isolado da Etapa 2 em falha parcial | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveSuportarRetryExclusivoDaEtapa2` |
| **5.4** | Criação do `GlobalSessionModal` e `useGlobalSessionModal` no Design System | **Atendido** | Exportado em `frontend/src/shared/design-system/index.ts` e disponibilizado via `GlobalSessionModalProvider` no `App.tsx` |
| **5.5** | Suporte integral aos 4 modos de abertura (`ESTUDOS_TIMER`, `BLOCO_DIRETO`, `BLOCO_TIMER`, `MANUAL`) | **Atendido** | Validado nos testes de UI e renderizado no `GlobalSessionModal.tsx` |
| **5.6** | Botão secundário "Concluir sem salvar sessão" exclusivamente em `BLOCO_DIRETO` | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveExibirBotaoConcluirSemSalvarSessao` |
| **5.7** | Validação de duração mínima de 60 segundos e campos de questões/acertos | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveValidarDuracaoMinimaDe60Segundos` |
| **5.8** | Métricas derivadas em tempo real (Erros e Aproveitamento %) | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveCalcularErrosEAproveitamentoEmTempoReal` |
| **5.9** | Acessibilidade WCAG 2.2 AA (focus trap, Escape, devolução de foco ao fechar) | **Atendido** | Implementado no container dialog e testado contra interrupções de digitação |
| **5.10** | Integração da aba Estudar ao `GlobalSessionModal` | **Atendido** | `EstudarView.tsx` refatorado para delegar encerramento e registro manual ao modal global |
| **5.11** | Integração da aba Planejamento ao `GlobalSessionModal` | **Atendido** | `PlanejamentoView.tsx` aciona `BLOCO_DIRETO` ao concluir blocos pendentes |
| **5.12** | Isolamento de storage do cronômetro de Estudos (`trilhara:u:{usuarioId}:study_timer`) | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveIsolarChavesEmLocalStorage` |
| **5.13** | Cálculo de tempo por timestamp absoluto sem drift em segun | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveCalcularTempoDecorridoPorDiferencaDeTimestampAbsoluto` |
| **5.14** | Regra estrita do alarme de Estudos (sem autosave e sem abertura automática de modal) | **Atendido** | Dispara exclusivamente alarme sonoro/notificação; modal exige clique explícito |
| **5.15** | Exclusividade de no máximo 1 timer ativo e sincronização multi-aba via BroadcastChannel | **Atendido** | BroadcastChannel `trilhara_study_timer` e canal de sincronização entre abas |
| **5.16** | Pausa automática no logout e restauração de timer pausado no login | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#devePausarAutomaticamenteNoLogout` e `deveRestaurarOTimerPausadoAoFazerLogin` |
| **5.17** | Isolamento e sincronização do Pomodoro (`trilhara:u:{usuarioId}:pomodoro_timer`) | **Atendido** | Validado em `PomodoroApp.tsx` com BroadcastChannel `trilhara_pomodoro_timer` |
| **5.18** | Término de Pomodoro nunca cria sessão nem abre modal de Estudos | **Atendido** | Pomodoro mantém ciclo estritamente local sem disparar chamadas de API de estudo |
| **5.19** | Não interferência em listas de Tarefas (preservadas para a Fase 7) | **Atendido** | `TarefasApp.tsx` e lista de tarefas de `PomodoroApp.tsx` mantidos intocados |
| **5.20** | Sucesso completo nos testes de unidade e integração frontend (`npm test`) | **Atendido** | 56 testes executados e aprovados com 100% de sucesso |
| **5.21** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` concluído com sucesso em 5.47s |
| **5.22** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 82 testes executados com 0 falhas e 0 erros |

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

