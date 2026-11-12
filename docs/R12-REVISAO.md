# R12 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R12** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

### Hierarquia normativa incorporada neste guia

Os requisitos de domínio e produto aplicáveis a esta revisão estão reproduzidos abaixo e são a referência normativa para comportamento. As decisões `D-xx` explicitamente listadas prevalecem sobre especificações históricas quando determinam uma regra diferente; mudanças que alterem comportamento devem atualizar os documentos normativos do produto no mesmo change. O contexto de execução original da fase aparece apenas para delimitar o escopo desta revisão. O relato da implementação é uma declaração a verificar no código e nos testes, nunca prova de conformidade. Qualquer trecho histórico incluído está marcado como não normativo. O agente deve trabalhar com o contexto deste guia e com o estado atual do repositório, sem precisar consultar os documentos de origem.

### Regras de execução aplicáveis

- Dependências desta revisão: R0→R1→R2; após R2, R3/R4/R5 e R7 podem avançar em branches separados; R6 exige R4+R5; R8 exige R5+R6+R7; R9 exige R3; R10 exige R3–R9; R11 exige R7+R10; R12 e R13 exigem todas as revisões anteriores. Sequência linear segura: R0→R1→R2→R3→R4→R5→R6→R7→R8→R9→R10→R11→R12→R13.
- Faça inventário de imports, usos e call sites antes de mudanças transversais. Não introduza compatibilidade legada nem mocks/callbacks temporários destinados a fases futuras.
- Preserve os limites de escopo desta revisão. Mudanças transversais indispensáveis para compilar/testar devem ser justificadas no resumo.
- A premissa é banco de desenvolvimento/teste vazio no novo esquema; não preservar compatibilidade com modelos, rotas ou dados legados.
- Contratos comuns: rotas privadas exigem autenticação (401); falta de papel administrativo é 403; recursos inexistentes ou de outro usuário retornam 404 sem revelar existência. IDs HTTP são hexadecimais de 24 caracteres: malformado é 400 `INVALID_IDENTIFIER`. Erros usam `application/problem+json`, RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) e códigos estáveis `code`/`errors`; falhas inesperadas não expõem detalhes internos ou dados pessoais. Paginação usa `pagina` 1-based, tamanho dentro do limite do endpoint (máximo 100), erro 400 `INVALID_PAGINATION`, resposta `{ itens, pagina, tamanho, totalItens, totalPaginas }` e ordenação estável com `_id` como desempate. Datas de calendário são `yyyy-MM-dd`; instantes são RFC 3339 na API e `Instant`/BSON Date no banco; o fuso vem de `X-Timezone`, com fallback `America/Sao_Paulo`, nunca do relógio/fuso implícito do servidor. Escritas idempotentes definem chave, escopo, hash funcional e política de retry; mesma chave com payload funcional diferente retorna 409 `IDEMPOTENCY_KEY_CONFLICT`. Operações compostas sem transação multi-documento documentam estados intermediários, recuperação, retry e efeitos parciais; não declarar atomicidade inexistente.
- Ao concluir, registre achados confirmados/descartados, arquivos alterados/removidos, testes completos por nome e resultado do gate em `docs/REVISAO-IMPLEMENTADA.md`; atualize `docs/RASTREABILIDADE-REESTRUTURACAO.md`.

**Gate comum de saída:** `backend/`: `./mvnw -q verify`; `frontend/`: `npm run typecheck && npm run lint -- --max-warnings 0 && npm test -- --run && npm run build && npx knip`; raiz: `bash scripts/verificar-legado.sh`. Se uma etapa ainda não estiver disponível por dependência anterior, registre claramente o bloqueio e não declare a revisão concluída.


---

## Escopo desta revisão

### R12 — Revisão da F11: Padronização UX/Design System, acessibilidade e rastreabilidade dos critérios de aceite

**1. Referência e Diagnóstico**
- A F11 validou acessibilidade com testes de CSS em jsdom (UX-09); `prefers-reduced-motion` só em CSS (UX-10); regras globais de alvo de toque e `scroll-padding` aplicadas indiscriminadamente; anti-IDOR incompleto (RI-39); critérios transversais sem rastreabilidade (RI-40); `explain` baseado em índices suspeitos (META-04); padronização de estados aplicada só nos componentes novos (UX-01..05, UX-12).

**2. Especificação Técnica, Segurança e UX**
- **Auditoria de estados por view (obrigatória):** para cada view listada abaixo, verificar e corrigir os 5 estados — carregando, sucesso com dados, vazio, erro, recarregando — e as ações destrutivas (D-37/D-38):

| Módulo | Views/componentes |
|---|---|
| Estudos | Hub, Disciplinas, Edital verticalizado, Planejamento, Estudar (timer), Dashboard/Histórico |
| Tarefas | Hoje, Todas, Futuro, Concluídas, Lista manual, Lista virtual de trilha, Lixeira, TaskModal |
| Notas | Lista de cadernos, Seções, Páginas, Editor, Busca, Lixeira, Importação |
| Pomodoro | Timer, Agenda Hoje (autenticado/visitante) |
| Shared | AppSidebar, AppSwitcher, AuthModal, ContaModal, GlobalSessionModal, ConfirmDialog, FormModal |

- **Acessibilidade verificável:**
  - `vitest-axe` (ou `jest-axe` compatível com Vitest) em testes de componente das views acima: zero violações das regras WCAG 2.x A/AA aplicáveis em jsdom.
  - Alvos de toque: substituir o seletor global por tokens/classes do Design System (`--target-min: 24px`; `@media (pointer: coarse) { --target-min: 40px }`) aplicados a `Button`, `IconButton`, `Checkbox`, itens de lista e menu. Remover a regra global sobre `input`/`a` genéricos.
  - `scroll-padding-bottom` ligado à presença da barra inferior: `body[data-bottom-nav="true"] { scroll-padding-bottom: calc(var(--bottom-nav-height) + 16px) }`, com o atributo definido pela `AppSidebar` mobile.
  - `useReducedMotion()` (hook com `matchMedia`) desliga a alternância do título da aba e as animações JS (timer, conclusão).
  - Cronômetros: `role="timer"` + `aria-live="off"`; região irmã `role="status"` anuncia início, pausa, término e erros.
  - Contraste: tokens dos temas `dark`/`light` verificados por teste unitário que calcula a razão de contraste dos pares de tokens declarados (texto/fundo ≥ 4.5:1; UI/gráfico ≥ 3:1).
- **Anti-IDOR completo:** `AuthorizationByOwnerTest` passa a ser **gerado pela allowlist de rotas** (Anexo A): para cada rota privada com path param de ID, o usuário B usa os IDs de A → espera 404 (ou 400 em referência de body), nunca 200/204/403/500. Rotas sem ID (listagens, `hoje`, busca, estatísticas, `usuarios/me/*`) verificam que B não recebe nenhum item de A.
- **Benchmark:** reexecutar `MongoExplainBenchmarkTest` após R2/R5 com os índices canônicos; relatório arquivado em `docs/benchmarks/explain-AAAA-MM-DD.md` com `totalKeysExamined`, `totalDocsExamined`, `nReturned`, estágio vencedor e latência p50/p95 por consulta crítica (Hoje de blocos, lista virtual, histórico de sessões, estatísticas, Hoje de tarefas, listagem de notas, busca `$text`, lixeiras).
- **Rastreabilidade:** cada critério transversal de aceite e cada decisão `D-xx` com efeito comportamental tem pelo menos um teste nomeado na matriz.

**3. Checklist Atômico de Execução**
1. Percorrer a tabela de views e aplicar `useRecurso` + primitivas D-37; remover os estados ad hoc (spinners locais, `"Carregando..."` em texto solto, listas inicializadas com `[]` exibindo vazio).
2. Aplicar a política D-38 em todas as ações destrutivas; padronizar textos (Anexo B).
3. Instalar `vitest-axe` e criar testes axe por view.
4. Refatorar alvos de toque, `scroll-padding` e reduced motion conforme o item 2.
5. Criar o teste de contraste dos tokens.
6. Reescrever `AuthorizationByOwnerTest` como teste parametrizado pela allowlist.
7. Reexecutar o benchmark e arquivar o relatório; criar/ajustar índices apenas com justificativa de `explain`.
8. Completar a matriz de rastreabilidade; nenhum critério sem teste.

**4. Critérios de Aceite e Validação Automatizada**
- `estadosViews.test.tsx` (parametrizado): para cada view, mocks de API produzem carregando (skeleton após 200 ms), vazio (`EmptyState`), erro (`SectionErrorCard` com retry funcional) e sucesso.
- `a11y/*.axe.test.tsx`: zero violações.
- `reducedMotion.test.tsx`: com `matchMedia('(prefers-reduced-motion: reduce)')` = true, o título da aba não alterna (fake timers).
- `contrasteTokens.test.ts` verde para os dois temas.
- `AuthorizationByOwnerTest` cobre 100% das rotas privadas da allowlist (o teste falha se uma rota privada não tiver caso).
- `docs/RASTREABILIDADE-REESTRUTURACAO.md` sem linhas com status "sem teste".
- Relatório de benchmark arquivado.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-37 — Taxonomia de estados de interface (obrigatória em todas as views)**
- *Dúvida/Lacuna:* Os documentos só especificaram `SectionErrorCard` e `SyncStatusBadge`; loading e vazio ficaram livres.
- *Deliberação e Referência:* Vercel Geist e Linear usam skeleton com o formato do conteúdo para carregamento inicial e spinner apenas para ações pontuais; atraso de exibição evita *flicker* (Geist ~ 200 ms); Shadcn/Radix fornecem primitivas acessíveis (Dialog, Toast).
- *Decisão Cravada — componentes em `frontend/src/shared/design-system/` e regras:*

| Estado | Componente | Regra |
|---|---|---|
| Carregamento inicial de seção/lista | `Skeleton` (`variant: 'line' \| 'row' \| 'card' \| 'chart'`, `linhas?`) | Exibido somente após **200 ms** (`useDelayedFlag`); mínimo de 300 ms visível quando exibido; `aria-busy="true"` no container + texto oculto "Carregando…" |
| Recarregamento com dados já exibidos | Dados atuais + `Spinner` pequeno no cabeçalho da seção | Nunca substituir conteúdo por skeleton em refetch |
| Ação de botão | `Button` com `loading` (spinner interno, largura preservada, `aria-busy`, desabilitado) | Spinner só se a ação durar > 200 ms |
| Lista vazia (sucesso) | `EmptyState` (`icone`, `titulo`, `descricao`, `acaoPrimaria?`) | Só após resposta `2xx` com zero itens; textos por contexto (Anexo B) |
| Falha de seção/lista | `SectionErrorCard` (`titulo`, `descricao`, `onRetry`, `variant: 'section' \| 'inline'`) | Rede/5xx/503/timeout; nunca para 401 (→ `AuthModal`) |
| Falha de campo | `FieldError` ligado por `aria-describedby` | A partir de `ProblemDetails.errors` |
| Feedback transitório | `Toast` (região `aria-live="polite"`, máx. 3 empilhados, 5 s, ação opcional "Desfazer") | Sucesso de ações reversíveis e erros de ações otimistas |
| Destruição | `ConfirmDialog` (`variant: 'danger'`, foco inicial no botão **seguro**) | Ver D-38 |
| Persistência contínua | `SyncStatusBadge` | Rótulos: "Salvo", "Salvando…", "Alterações locais pendentes", "Erro ao salvar" + **Tentar novamente** |
| Conflito 409 | `Toast` de erro com `detail` + recarregar dados da seção | Ex.: `SCHEDULE_GENERATION_IN_PROGRESS` → "Outra atualização deste cronograma está em andamento. Tente novamente em instantes." |

Helper único `classificarErro(e: unknown): 'rede' | 'autenticacao' | 'nao-encontrado' | 'conflito' | 'validacao' | 'limite' | 'servidor'`, em `shared/api/erros.ts`, usado por todos os componentes. Hook `useRecurso` (ou equivalente) retorna `{ status: 'carregando'|'sucesso'|'erro', dados, erro, recarregar, recarregando }` — **proibido** inicializar listas com `[]` e renderizar `EmptyState` antes da primeira resposta.

**D-40 — Teclado e ARIA**
- *Decisão Cravada:* Seguir o WAI-ARIA APG para Dialog, Menu Button (AppSwitcher), Tabs (abas de cronograma), Listbox/Checkbox. Não introduzir atalhos globais de uma tecla (conflito com leitores de tela, WCAG 2.1.4). Todo controle é alcançável por Tab com foco visível (`:focus-visible`, contraste ≥ 3:1). Itens de lista com ação primária (concluir) usam `<button>` com `aria-label` contextual ("Concluir tarefa: {título}").

**D-43 — Testes**
- *Decisão Cravada:* Nenhum teste é removido sem um teste equivalente nomeado na matriz. Testes organizados por domínio (`backend/src/test/java/br/com/trilhara/{dominio}/…`, `frontend/src/**/__tests__/*.test.tsx` ou `src/test/{dominio}/…`); o prefixo "faseN" é proibido. Cobertura mínima de linhas (Vitest `coverage.thresholds`): `shared/` ≥ 80%, módulos ≥ 70%; backend (JaCoCo) ≥ 75% nos pacotes de domínio. A contagem total de testes por suíte não pode diminuir entre fases de revisão.

---

## Contexto normativo de domínio pertinente

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

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

## 16. Critérios de aceite transversais

- Usuário A nunca acessa, altera ou exclui dados do usuário B por ID conhecido.
- Datas de calendário não mudam ao converter entre navegador/backend; operações entre 21h00 e 23h59 no fuso `America/Sao_Paulo` usam a data local correta enviada via `X-Timezone`; instantes usam BSON Date.
- Trilha grande não exige transferir disciplinas/tópicos ao listar Hub.
- Marcar/desmarcar tópico afeta somente aquele progresso e seus agregados.
- Regenerar cronograma substitui integralmente blocos/conclusões via `versaoGeracao`/`versaoAtiva`, mantendo sessões e progresso; requisições simultâneas nunca deixam dois cronogramas ativos na mesma trilha.
- Tarefas/Pomodoro mostram o mesmo conjunto de tarefas manuais Hoje e os blocos do dia; nenhum bloco é gravado como tarefa.
- Concluir manual nunca cria sessão; concluir bloco abre o modal global; cancelar não conclui.
- Retry após timeout não duplica sessão nem conclusão.
- Uma sessão salva com `95` segundos persiste `duracaoSegundos = 95` e soma exatamente `95` segundos nas agregações; estatísticas por sessão conferem com somas do histórico; editar a sessão mais recente `(dataSessao DESC, criadoEm DESC, _id DESC)` ou excluir uma sessão individual recalcula naturalmente.
- Remover disciplina preserva histórico por padrão (sem prompt destrutivo na exclusão da disciplina) e permite apagar sessões/métricas da disciplina removida de forma explícita no Dashboard/Histórico.
- Alterar a `dataVencimento` de uma ocorrência recorrente mantém `dataOriginalOcorrencia` e jamais recria a ocorrência na data original ao expandir a janela de 90 dias.
- Autosave/busca/lixeira de Notas não transferem Markdown completo em listagens e preservam rascunho local (`trilhara:u:{usuarioId}:nota_draft:{notaId}`) em falha de rede.
- Tarefas/notas em lixeira podem ser restauradas no prazo e no contexto originais; após prazo, são purgadas.
- Logout ou troca de conta sincroniza na mesma origem via `storage`/`BroadcastChannel`; entre subdomínios a validação é server-side ao focar e o cookie marcador de produção é apenas um sinal opaco. Timers são sincronizados apenas na mesma origem/dispositivo; não há promessa de sincronização entre dispositivos.
- Falha de serviço aparece como erro recuperável (`SectionErrorCard` / `SyncStatusBadge`); nunca é apresentada silenciosamente como “sem dados”.
- Requisitos de acessibilidade WCAG 2.2 AA (*focus trap*, `aria-live`, alvos mínimos de toque/clique e `scroll-padding-bottom` para barras fixas) são atendidos nos quatro módulos.
- Resultados de performance são medidos em ambientes representativos; estimativas de redução de payload/latência não são aceites como garantias sem benchmark.
- Em todas as listas paginadas, ordenação inclui chave estável e filtros de tenant; erro de uma fonte na Visão Hoje não remove os dados da fonte disponível.
- Ao alternar cronograma ativo dentro da mesma trilha, leitor vê versão anterior ou versão nova completa; não vê staging incompleto nem dois ativos. Um período de recuperação sem cronograma ativo só é permitido em falha operacional e precisa ser sinalizado como seção indisponível/retry.
- Geração de cronograma é testada com falha injetada em cada etapa: nenhum leitor vê staging incompleto; estado anterior ou novo é recuperável, e lease expirado/obsoleto não permite publicação concorrente.
- Materialização recorrente é idempotente sob requisições simultâneas e os casos diários/semanais/mensais são testados em mudanças de ano, semanas ISO e meses curtos.
- Exclusão e restauração de lista/caderno/seção respeitam lotes de exclusão, itens previamente excluídos isoladamente e descendentes já purgados.
- Scheduler push não processa o mesmo item em paralelo; expirados não são enviados; falhas transitórias respeitam janela e backoff, reconhecendo que o provedor não garante exactly-once.
- Frontend instala runner automatizado Vitest + React Testing Library + jsdom em F0; cada critério de UI deste documento/plano tem teste de componente ou serviço executável por `npm test`, além de `npm run build`.
- Conta excluída retorna 202 depois de aceitar operação autenticada; worker conclui exclusão das 18 coleções sem deixar órfãos, mesmo com falhas injetadas em qualquer etapa.

---

## Plano original da FASE 11 (referência de escopo)

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 11.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 a 10 concluídas.
- **Objetivo da fase:** Auditar segurança por proprietário (`usuarioId`), acessibilidade WCAG 2.2 AA nos quatro módulos, executar benchmark sintético verificando `IXSCAN` com `explain("executionStats")` no MongoDB 7 e remover qualquer classe, rota ou arquivo morto remanescente.

### 11.3 Critérios de Saída da FASE 11 (Encerramento da Reestruturação)
- Suíte completa do backend (`mvn test`), build frontend (`npm run build`) e testes frontend (`npm test`) verdes. Benchmark opt-in executado em ambiente MongoDB 7 especificado e relatório arquivado.
- Todos os critérios de aceite da Seção 16 de requisitos de aceite transversais reproduzidos neste guia comprovados por testes automatizados.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 11                                               -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 11 (hipótese a verificar)

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 1. Relatório Técnico Resumido da Fase 11

A **FASE 11** conclui integralmente a reestruturação arquitetural do ecossistema Trilhara, realizando a auditoria transversal de segurança e autorização anti-IDOR, adequação estrita aos padrões de acessibilidade WCAG 2.2 Level AA, validação sintética  de índices do MongoDB 7 com `explain("executionStats")` e fechamento da documentação de execução e variáveis de ambiente.

#### 1.1 Auditoria de Autorização por Proprietário (Anti-IDOR / 404 Sem Vazar Existência)
1. **Isolamento Transversal de Dados (`AuthorizationByOwnerTest.java`):**
   - Criação de suíte de testes de integração com REST Assured simulando dois usuários distintos (`usuarioA` e `usuarioB`).
   - População de instâncias nas 13 entidades e coleções do domínio pertencentes exclusivamente a `usuarioA`: `trilhas`, `disciplinas`, `progresso_topicos`, `cronogramas`, `blocos_cronograma`, `sessoes`, `listas_tarefas`, `series_recorrentes`, `tarefas`, `cadernos`, `secoes`, `notas` e `push`.
   - Validação exaustiva de que qualquer tentativa de leitura, escrita, atualização ou exclusão disparada por `usuarioB` referenciando identificadores conhecidos de `usuarioA` resulta rigorosamente em status HTTP `404 Not Found` (sem vazar a existência prévia do recurso, sem responder 403 Forbidden e sem erro 500).
2. **Hardening de Desinscrição Web Push (`PushResource.java`):**
   - Ajuste na rota `DELETE /api/push/subscriptions`: quando a tentativa de remoção não encontrar inscrição pertencente ao usuário autenticado, a API responde HTTP `404 Not Found`, impedindo que um usuário terceirizado sonde ou tente remover endpoints de outros dispositivos.

#### 1.2 Acessibilidade WCAG 2.2 Level AA nos Quatro Módulos
1. **Focus Trap, Tecla Escape e Restauração de Foco (`Modal`, `FormModal`, `ConfirmDialog`, `GlobalSessionModal`):**
   - Inclusão da propriedade `isSaving` no componente `Modal` do Design System Trilhara, repassada automaticamente pelo `FormModal` via `isSaving={isLoading}`.
   - **Bloqueio de Escape durante Salvamento:** Quando `isSaving === true`, tanto a tecla `Escape` quanto o clique no overlay e o botão fechar do cabeçalho são desabilitados, prevenindo fechamento acidental e perda de dados em operações em andamento.
   - **Restauração de Foco:** Ao fechar qualquer modal ou diálogo de confirmação (`ConfirmDialog`, `GlobalSessionModal`), o foco do teclado retorna deterministicamente para o elemento disparador (`triggerElement` / `previousActiveElement`).
   - **Focus Trap:** Navegação com tecla `Tab` e `Shift+Tab` mantém o foco ciclando exclusivamente dentro da caixa de diálogo ativa.
2. **Semântica dos Cronômetros e Transições de Fase:**
   - Nos cronômetros de foco dos módulos Estudos (`EstudarView.tsx`) e Pomodoro (`PomodoroApp.tsx`), os dígitos numéricos utilizam `role="timer"` e `aria-live="off"`, garantindo que leitores de tela não leiam a contagem a cada segundo.
   - As transições de ciclo/fase (`statusText`) e os banners de pausa curta/longa (`breakBanner`) utilizam `role="status"` e `aria-live="polite"`.
   - O componente `SyncStatusBadge` opera com `role="status"` e `aria-live="polite"`, reportando transições de salvamento e erros para tecnologias assistivas.
3. **Ergonomia e Alvos de Toque / Clique (SC 2.5.8):**
   - Alvos mínimos de interação interativa (`button`, `a`, `input`, `select`, `textarea`, `[role="button"]`, etc.) configurados para `24x24px` no desktop.
   - Em dispositivos touch (`@media (pointer: coarse)`), os alvos mínimos expandem para `40x40px`.
   - Prevenção de oclusão por barra inferior fixa mobile: `scroll-padding-bottom: 88px` aplicado globalmente em containers e listas no `@media (max-width: 900px)`.
   - Suporte nativo a `@media (prefers-reduced-motion: reduce)` anulando durações de animação e transições CSS para usuários com sensibilidade vestibular.

#### 1.3 Benchmark de Índices e `explain("executionStats")` no MongoDB 7 (`MongoExplainBenchmarkTest.java`)
1. **Perfil Opt-in:**
   - Configurado com `@Tag("benchmark")` e `@EnabledIfSystemProperty(named = "benchmark", matches = "true")`.
   - Não sobrecarrega a suíte unitária padrão (`./mvnw test` pula os 5 testes de benchmark em 0.01s).
   - Executado sob demanda em ambiente com MongoDB 7 via `./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true`.
2. **Carga Sintética Representativa Injetada:**
   - 2.190 sessões de estudo / ano (6 sessões diárias ao longo de 365 dias).
   - Trilha com 20 disciplinas e 2.000 tópicos (100 tópicos por disciplina).
   - Cronograma de 366 dias com 24 blocos/dia = 8.784 blocos.
   - Caderno com 300 páginas de notas em Markdown (10 seções * 30 páginas).
   - Séries recorrentes com ocorrências de tarefas.
3. **Evidências de `explain("executionStats")` nas Consultas Críticas:**
   - **Blocos do Dia / Atrasados:** Ativação de `IXSCAN` no índice composto `idx_blocos_usuario_data_concluido`. Alta seletividade comprovada (`totalDocsExamined <= 24` para um volume de 8.784 blocos).
   - **Paginação Estável de Sessões:** Ativação de `IXSCAN` em `idx_sessoes_usuario_trilha_data_criado_id`. A ordenação composta `(dataSessao DESC, criadoEm DESC, _id DESC)` cobre a consulta, resultando em `totalDocsExamined == 20` (exatamente o tamanho da página solicitada em 2.190 registros).
   - **Disciplinas da Trilha:** Ativação de `IXSCAN` em `idx_disciplinas_usuario_trilha_ordem` retornando 20 registros com ordenação indexada.
   - **Busca Textual em Notas (`$text`):** Ativação do estágio de índice de texto do MongoDB 7 com prefixos de tenant `usuarioId` e `deletedAt`.
   - **Tarefas de Hoje:** Ativação de `IXSCAN` no índice composto `idx_tarefas_usuario_deleted_concluida_vencimento`.

#### 1.4 Limpeza de Código e Documentação Final
1. **Auditoria de Código Morto:**
   - Verificação de que nenhuma classe legada ou rota substituída permanece ativa na aplicação.
2. **Variáveis de Ambiente (`.env.example`):**
   - Documentação consolidada contemplando segredos JWT, pepper PBKDF2, CORS, cookies de sessão, WiredTiger cache, Resend API e chaves VAPID RFC 8030 / RFC 8292 de Web Push.
3. **Documentação Geral (`README.md`):**
   - Criação de documentação completa cobrindo arquitetura dos 4 módulos, requisitos de ambiente (Java 21+, Node 20+, Docker/MongoDB 7), guia de execução local/Docker e comandos para testes e benchmark opt-in.

---


### 3. Checklist de Validação da FASE 11

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **11.1** | Auditoria Anti-IDOR: Usuário B recebe 404 sem vazar existência ao tentar ler, alterar ou excluir dados de Usuário A | **Atendido** | Validado em `AuthorizationByOwnerTest` cobrindo 13 coleções/entidades |
| **11.2** | Hardening em Push: desinscrição de endpoint inexistente ou de terceiro retorna 404 Not Found | **Atendido** | Validado em `PushResource.java` e `AuthorizationByOwnerTest#auditarPush` |
| **11.3** | Modal e FormModal: Focus trap funcional e fechamento com Escape prevenido durante salvamento (`isSaving == true`) | **Atendido** | Validado em `fase11Accessibility.test.tsx` (seções 1 e 2) |
| **11.4** | ConfirmDialog e GlobalSessionModal: Fechamento com Escape e restauração automática de foco ao disparador original | **Atendido** | Validado em `fase11Accessibility.test.tsx` (seções 3 e 4) |
| **11.5** | Semântica de cronômetros: `role="timer"` e `aria-live="off"` nos dígitos em Estudos e Pomodoro | **Atendido** | Validado em `EstudarView.tsx`, `PomodoroApp.tsx` e `fase11Accessibility.test.tsx` |
| **11.6** | Transições de ciclo e badges: `role="status"` e `aria-live="polite"` nas mudanças de estado e no SyncStatusBadge | **Atendido** | Validado em `SyncStatusBadge.tsx` e `fase11Accessibility.test.tsx` |
| **11.7** | Alvos mínimos de toque e clique: 24x24px no desktop e 40x40px em `@media (pointer: coarse)` (SC 2.5.8) | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.8** | Evitar sobreposição de barra inferior móvel: `scroll-padding-bottom: 88px` em `@media (max-width: 900px)` | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.9** | Suporte a acessibilidade motora e sensorial: `@media (prefers-reduced-motion: reduce)` | **Atendido** | Implementado em `index.css` e validado em `fase11Accessibility.test.tsx` |
| **11.10** | Benchmark opt-in com carga representativa (2.190 sessões, 2.000 tópicos, 8.784 blocos, 300 páginas notas) | **Atendido** | Implementado em `MongoExplainBenchmarkTest.java` com profile opt-in |
| **11.11** | Execução de `explain("executionStats")` confirmando uso de `IXSCAN` e plano de índice de texto MongoDB 7 | **Atendido** | Validado com sucesso via `./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true` |
| **11.12** | Isolamento do benchmark: suíte padrão (`mvn test`) ignora o benchmark (tempo de execução: 10ms, 5 skipped) | **Atendido** | Validado na execução padrão do Surefire com `@EnabledIfSystemProperty` |
| **11.13** | Remoção de código legado e documentação completa de ambiente (`.env.example` e `README.md`) | **Atendido** | Atualizado `.env.example` com chaves VAPID/Resend e redigido `README.md` abrangente |
| **11.14** | Sucesso em 100% dos testes de frontend Vitest (`npm test -- --run`) | **Atendido** | 14 suítes de teste, 109/109 testes aprovados (9 testes exclusivos da Fase 11) |
| **11.15** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem erros de tipagem |
| **11.16** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 134 testes executados com 0 falhas e 0 erros (13 testes exclusivos da Fase 11) |

---

## Anexos pertinentes  de revisão

## Anexo A — Allowlist de rotas HTTP após a revisão

> Fonte para `RotasPublicasContratoTest` e `AuthorizationByOwnerTest`. Rotas ausentes desta lista devem ser removidas ou acrescentadas aqui com justificativa (no mesmo PR). Rotas de autenticação já existentes não relacionadas à reestruturação (login, cadastro, confirmação de e-mail, recuperação/alteração de senha, logout, logout-all, refresh, me) permanecem e devem ser listadas na primeira execução de R0.

**Auth/Usuário:** `POST /api/auth/handoff`, `POST /api/auth/handoff/consume`, `DELETE /api/auth/conta`, `GET /api/auth/me`, `PUT /api/usuarios/me/preferencias/tema`, `GET|PUT /api/usuarios/me/disponibilidade-semanal`.

**Estudos — trilhas/disciplinas/progresso:** `GET|POST /api/trilhas`, `GET|PUT|DELETE /api/trilhas/{trilhaId}`, `GET /api/trilhas/{trilhaId}/impacto-exclusao`, `POST /api/trilhas/{trilhaId}/disciplinas`, `PUT /api/trilhas/{trilhaId}/disciplinas/reordenar`, `PUT|DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}`, `PUT|DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso`.

**Estudos — sessões/estatísticas:** `GET|POST /api/trilhas/{trilhaId}/sessoes`, `PATCH|DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`, `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`, `GET /api/trilhas/{trilhaId}/estatisticas`.

**Estudos — cronogramas/blocos:** `GET|POST /api/trilhas/{trilhaId}/cronogramas`, `PUT|DELETE /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}`, `PATCH /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/ativar`, `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`, `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`, `GET /api/blocos/hoje`, `PATCH /api/blocos/{blocoId}/conclusao`.

**Catálogo:** rotas existentes de `editais` (leitura pública/autenticada e administração com `403` para não-ADMIN), a listar em R0.

**Tarefas:** `GET|POST /api/listas-tarefas`, `GET|PUT|DELETE /api/listas-tarefas/{listaId}`, `GET /api/listas-tarefas/{listaId}/impacto-exclusao`, `POST /api/listas-tarefas/{listaId}/restaurar`, `GET|POST /api/tarefas`, `GET /api/tarefas/hoje`, `GET|PUT|DELETE /api/tarefas/{tarefaId}`, `PATCH /api/tarefas/{tarefaId}/conclusao`, `PATCH /api/tarefas/{tarefaId}/subtarefas/{subtarefaId}`, `POST /api/tarefas/{tarefaId}/pular`, `GET|POST /api/series-recorrentes`, `GET|PUT|DELETE /api/series-recorrentes/{serieId}`, `GET /api/tarefas/lixeira`, `POST /api/tarefas/lixeira/{tarefaId}/restaurar`, `DELETE /api/tarefas/lixeira/esvaziar`.

**Notas:** `GET|POST /api/cadernos`, `PUT|DELETE /api/cadernos/{cadernoId}`, `GET /api/cadernos/{cadernoId}/impacto-exclusao`, `GET|POST /api/cadernos/{cadernoId}/secoes`, `PUT|DELETE /api/secoes/{secaoId}`, `GET /api/secoes/{secaoId}/impacto-exclusao`, `GET|POST /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`, `GET|PUT|DELETE /api/notas/{notaId}`, `GET /api/notas/busca`, `POST /api/cadernos/importar-trilha/{trilhaId}`, `POST /api/cadernos/{cadernoId}/sincronizar-trilha`, `GET /api/notas/lixeira`, `POST /api/notas/lixeira/{tipo}/{id}/restaurar`, `DELETE /api/notas/lixeira/esvaziar`.

**Push:** `GET /api/push/config`, `POST|DELETE /api/push/subscriptions`, `PUT /api/push/preferencias`.

**Removidas explicitamente:** `PATCH /api/trilhas/{id}/progresso/{topicoId}`, `GET /api/cadernos/{c}/secoes/{s}/notas/{n}`, `PATCH /api/push/preferencias`, qualquer rota de "ciclo"/"planejamento-ciclo".

---

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

## Anexo D — Resumo de rastreabilidade Achado → Decisão → Fase

| Achados | Decisões | Fase |
|---|---|---|
| META-01..05 | D-01, D-02 | R0 |
| RI-02, SEC-05, SEC-07, SEC-10, UX-01..05, UX-12, DC-11 | D-03, D-04, D-05, D-37, D-42 | R1 |
| SEC-01, SEC-06, SEC-08, SEC-13, UX-06, UX-08, DC-02, DC-14, DC-17 | D-06, D-07, D-08, D-09, D-10, D-12, D-41 | R2 |
| RI-01..05, SEC-09, DC-03..05 | D-06, D-11, D-38, D-39 | R3 |
| RI-06..09, DC-06 | D-13, D-14, D-15 | R4 |
| RI-10..15, RI-27, DC-07, DC-08, DC-15 | D-16, D-17, D-18, D-19, D-20 | R5 |
| RI-16, RI-17, UX-07, UX-11 | D-21, D-22 | R6 |
| RI-18..25, RI-38, SEC-16, DC-09 | D-23..D-28 | R7 |
| RI-26, RI-27, SEC-02, DC-10, DC-11 | D-23, D-46, D-48 | R8 |
| RI-28..33, SEC-03, SEC-04, DC-12, DC-13 | D-29..D-34 | R9 |
| RI-34..36, SEC-11, SEC-15 | D-08, D-35 | R10 |
| RI-37, SEC-12 | D-26, D-36 | R11 |
| RI-39, RI-40, UX-09, UX-10 | D-37, D-40, D-43 | R12 |
| DC-01..18, SEC-04, SEC-07, SEC-08, SEC-14 | D-44, D-45, D-47, D-49 | R13 |

---

