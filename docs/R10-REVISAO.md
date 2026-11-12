# R10 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R10** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R10 — Revisão da F9: Exclusão de conta e isolamento ponta a ponta

**1. Referência e Diagnóstico**
- Estado terminal `FALHA` (RI-35); logs com `usuarioId` (RI-34); 202 descrito como Problem Details (SEC-11); hook de falha em produção (SEC-15); inventário sem `tokens_handoff_sso` e com coleção de operações (D-08); Pomodoro sem entrada de Conta (RI-36).

**2. Especificação Técnica, Segurança e UX**
- `DELETE /api/auth/conta`: header `Idempotency-Key` obrigatório; body `{ senha, confirmacao: "EXCLUIR MINHA CONTA" }` (o frontend envia a frase; o backend a valida como confirmação **adicional**); senha inválida → `401 UNAUTHORIZED` (com o rate limit D-45); resposta `202 application/json { operationId, estado: "PENDENTE" }`; repetição → `202` com o estado atual.
- `AccountDeletionService` itera `ColecaoTrilhara.filhasPrivadas()` (18, incluindo `tokens_handoff_sso`) e depois `usuarios`; checkpoint em `usuarios.exclusaoConta`; estados `PENDENTE → EM_ANDAMENTO → (RETENTANDO_LENTO)* → CONCLUIDA` (a conclusão é registrada **apenas** em log/métrica, pois o documento é removido).
- Logs: `event`, `operationId`, `colecao`, `etapa` — sem `usuarioId`, e-mail ou nome.
- `SecurityFilter`: usuário com `exclusaoConta` → `401 UNAUTHORIZED` em qualquer rota. Antes do 202, o retry com a mesma `Idempotency-Key` devolve o mesmo `operationId`. Depois do 202, as credenciais já foram revogadas e o cliente não precisa repetir: um retry sem sessão recebe 401, que o frontend interpreta nesse fluxo como "exclusão em andamento".
- Frontend: entrada "Conta" na `AppSidebar` (D-35f); fluxo `ConfirmDialog danger` → formulário (senha + frase) → 202 → `limparDadosLocaisDoUsuario` + `CONTA_EXCLUIDA` + redirecionamento com mensagem; erros inline (senha incorreta) e 429 com tempo de espera.

**3. Checklist Atômico de Execução**
1. Trocar o estado `FALHA` por `RETENTANDO_LENTO` com retentativa horária e métrica.
2. Remover `usuarioId` dos logs da exclusão (e revisar todos os logs do backend: `rg "usuarioId" backend/src/main --glob '*.java' | rg -i "log\."`).
3. Ajustar o content type do 202.
4. Remover `testFailOnCollectionHook`; usar `FaultInjector` (R5).
5. Incluir `tokens_handoff_sso` e retirar a coleção de operações do inventário (via `ColecaoTrilhara`).
6. Centralizar a entrada de Conta na `AppSidebar`; remover as props `onAbrirConta` propagadas pelos apps.
7. Ajustar o tratamento de 401 pós-202 no frontend.

**4. Critérios de Aceite e Validação Automatizada**
- `AccountDeletionIntegrationTest`: popula A e B em **todas** as coleções privadas declaradas em `ColecaoTrilhara` (o teste itera a enum e falha se faltar fixture para alguma) + um edital; executa a exclusão; verifica zero documentos de A e B intacto; `falhaInjetadaEmCadaColecaoRetomaSemOrfaos` (parametrizado pelas 19 coleções privadas); `apos5FalhasEntraEmRetentandoLentoENaoConclui`; `logsNaoContemUsuarioId` (captura de log via handler de teste, `rg` do hex de A no output = vazio); `semIdempotencyKeyRetorna400`; `senhaInvalidaRetorna401ESemEfeito`; `respostaNaoEhProblemJson`.
- `InventarioColecoesTest` verde.
- Frontend: `contaModal.test.tsx` (botão habilitado só com frase exata e senha; 401 → erro inline; 202 → limpeza de `trilhara:u:{id}:*`, broadcast e redirecionamento); `appSidebarConta.test.tsx` (entrada presente nos 4 módulos, inclusive Pomodoro autenticado).
- `rg "testFailOnCollectionHook" backend/src/main` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-08 — Inventário de coleções**
- *Dúvida/Lacuna:* Surgiram coleções fora do inventário de 19 (operações de "concluir atrasados", códigos de handoff SSO).
- *Deliberação e Referência:* O inventário fechado é pré-condição da exclusão de conta (LGPD art. 16/18). Coleções implícitas viram dados órfãos.
- *Decisão Cravada:* (a) **Eliminar** a coleção de operações de concluir atrasados (D-17). (b) **Formalizar** a coleção privada `tokens_handoff_sso` (`_id`, `usuarioId`, `codigoHash` único, `origemDestino`, `expiraEm`, `consumidoEm?`), com índice TTL em `expiraEm` e índice `{ usuarioId: 1 }`. O inventário passa a **20 coleções = 19 privadas + `editais`**; a exclusão de conta cobre 18 filhas + `usuarios`. (c) Teste-guarda `InventarioColecoesTest`: após um cenário que exercita todos os endpoints, lista `listCollectionNames()` e falha se existir coleção não declarada no enum `ColecaoTrilhara`. (d) Atualizar requisitos regra normativa e plano F1/F9 com a contagem nova.

**D-35 — Exclusão de conta**
- *Decisão Cravada:* (a) O `202` retorna `application/json` `{ operationId, estado }` (não Problem Details). (b) Sem estado terminal silencioso: após 5 tentativas com backoff (1 s→30 s), o estado vira `RETENTANDO_LENTO`, com nova tentativa a cada 1 h indefinidamente, métrica `trilhara_account_deletion_stuck` e log `ERROR` contendo só `operationId` e nome da coleção. (c) Logs sem `usuarioId` (D-04/RI-34). (d) Ordem: filhas (incluindo `tokens_handoff_sso`), contagem zero, depois `usuarios`. (e) Hook de falha removido da produção (usar `FaultInjector`, D-18). (f) A entrada "Conta" fica centralizada na `AppSidebar` (os quatro módulos, incluindo Pomodoro), sem propagação manual por app. (g) Após o `202`: limpeza local, broadcast `CONTA_EXCLUIDA` e redirecionamento à tela pública com a mensagem "Sua conta está sendo excluída. Isso pode levar alguns minutos."

---

## Contexto normativo de domínio pertinente

## 12. Autenticação, exclusão e confiabilidade

- SSO unificado para os quatro módulos, tratando separadamente **Produção** e **Ambiente Local (`localhost` / `*.localhost`)**:
  1. **Em Produção (ex.: `*.trilhara.com.br`):** o backend configura o atributo `Domain` compartilhado, validando host e allowlist de proxy; `trilhara_rt` é `HttpOnly; Secure; Path` mínimo e `SameSite` explícito. Emite também cookie `trilhara_session_state` não-HttpOnly com valor opaco (sem `usuarioId`/PII), `Secure`, `Path=/` e mesmos atributos Domain/SameSite compatíveis.
  2. **Em Ambiente Local (`localhost` / `*.localhost`):** nunca enviar atributo `Domain`; cookies são host-only. Transição entre origens locais usa endpoints `POST /api/auth/handoff` e `/api/auth/handoff/consume`: código aleatório opaco, persistido como hash, TTL de 60s, uso único e vinculado a allowlist exata de origem/destino. URL nunca contém access/refresh token ou PII; código é removido do fragmento após consumo e não aparece em logs/Referer.
  3. **Sincronização ao focar a aba (ambos os ambientes):** `storage`/`BroadcastChannel` sincronizam apenas abas da mesma origem. Em produção, o marcador de sessão é opaco e serve apenas como sinal; cada aba valida sessão no servidor em `visibilitychange`/`focus` (throttle 5s, refresh single-flight). Em `*.localhost`, sem cookie cross-origin, validar servidor ao foco. Não há promessa de sincronização imediata entre dispositivos nem de timer compartilhado cross-origin; expiração/revogação é detectada pela validação.
- Em caso de expiração de sessão (`401 Unauthorized` após tentativa de refresh), abrir o modal de reautenticação (`AuthModal`) sobre a tela atual **sem desmontar a árvore de componentes nem recarregar a página**, preservando formulários abertos, rascunhos de notas e estado de conclusão de bloco para retomar a ação após o login.
- Isolar estado local de cada conta sob chaves `trilhara:u:{usuarioId}:*`. Logout limpa tokens e estado sensível em memória, mas preserva timers pausados e rascunhos de notas associados à conta no dispositivo, salvo exclusão de conta, quando são apagados. Conta diferente nunca lê essas chaves. Dados locais não são sincronizados entre origens/dispositivos.
- Exclusão de conta exige senha atual ou reautenticação recente server-side; texto `EXCLUIR MINHA CONTA` é confirmação de intenção adicional, nunca autenticação suficiente.
- Toda consulta por recurso filho valida `usuarioId` no backend, inclusive refs de trilha, caderno, seção, sessão e bloco.
- Exclusão de conta é cascata completa de dados pessoais nas **18 coleções privadas** (`usuarios`, `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email`, `trilhas`, `disciplinas`, `progresso_topicos`, `cronogramas`, `blocos_cronograma`, `sessoes`, `listas_tarefas`, `series_recorrentes`, `tarefas`, `cadernos`, `secoes`, `notas`, `push_subscriptions`, `lembretes_push`), sem deixar dados órfãos identificáveis. Exige reautenticação server-side; confirmação textual é adicional e não autentica. API responde `202` com `operationId`, revoga sessão e worker processa a limpeza idempotentemente, removendo `usuarios` por último. Logs não contêm identificadores pessoais.
- Não exigir transação Mongo multi-documento para métricas: elas são agregadas da coleção de sessões, não atualizadas em contador duplicado.
- Sessão e conclusão do bloco podem falhar parcialmente; chave idempotente, estado explícito e retry só da operação pendente evitam duplicidade e silêncio.
- Operação em lote de blocos atrasados é item a item com resultado parcial explícito; não exigir replica set apenas para atomicidade de lote.
- Exclusão de conta é retry-safe e assíncrona: solicitação confirmada retorna `202`; job idempotente completa etapas e registra erro operacional sem PII para retries.

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

## 13. Modelo de coleções recomendado

Nomes e responsabilidades das **19 coleções** do sistema (**18 coleções privadas** de usuário + **1 coleção de catálogo público** `editais`):

| Coleção | Unidade de documento | Índices principais |
|---|---|---|
| `usuarios` | Conta + subdocumento `preferencias` (disponibilidade semanal e padrão de push) | `email` único normalizado; `criadoEm DESC` |
| `refresh_tokens` | Sessão/dispositivo autenticado (família de rotação) | `tokenHash` único; `usuarioId + familiaId`; `expiraEm` (TTL/limpeza) |
| `tokens_recuperacao_senha` | Token de uso único para redefinição de senha | `tokenHash` único; `usuarioId`; `expiraEm` |
| `tokens_confirmacao_email` | Token de verificação de e-mail da conta | `tokenHash` único; `usuarioId`; `expiraEm` |
| `editais` | Catálogo público de editais (gerido por ADMIN) | `status + esfera + atualizadoEm DESC` |
| `trilhas` | Metadados da trilha do usuário | `usuarioId + atualizadoEm DESC`; `usuarioId + origemEditalId` |
| `disciplinas` | Disciplina da trilha + lista limitada de tópicos (máx. 500) | `usuarioId + trilhaId + ordem` |
| `progresso_topicos` | Um tópico concluído | único `usuarioId + trilhaId + topicoId` |
| `cronogramas` | Configuração, modalidade, período, `ativo` e `versaoAtiva` | único `usuarioId + trilhaId + modalidade`; único parcial `usuarioId + trilhaId` onde `ativo == true` |
| `blocos_cronograma` | Uma ocorrência gerada vinculada a `versaoGeracao` | `usuarioId + trilhaId + cronogramaId + versaoGeracao + data + ordem`; `usuarioId + data + concluido` (para visão Hoje) |
| `sessoes` | Um registro de sessão (`duracaoSegundos`, `questoes`, `acertos`) | `usuarioId + trilhaId + dataSessao DESC + criadoEm DESC`; `usuarioId + trilhaId + disciplinaId + dataSessao DESC`; único parcial `usuarioId + chaveIdempotencia` |
| `listas_tarefas` | Lista manual de tarefas (com suporte a lixeira) | `usuarioId + deletedAt + ordem` |
| `series_recorrentes` | Regra de recorrência e cursor `geradoAte` | `usuarioId + deletedAt + ativa` |
| `tarefas` | Tarefa manual ou ocorrência materializada de série | `usuarioId + deletedAt + listaId + concluida + ordem`; `usuarioId + deletedAt + concluida + dataVencimento`; único parcial `usuarioId + serieId + dataOriginalOcorrencia` |
| `cadernos` | Caderno de notas | `usuarioId + deletedAt + atualizadoEm DESC`; único parcial `usuarioId + origemTrilhaId` quando vinculado e ativo |
| `secoes` | Seção de caderno | `usuarioId + cadernoId + deletedAt + ordem`; `usuarioId + cadernoId + origemDisciplinaId` |
| `notas` | Página com `conteudoMarkdown` | `usuarioId + secaoId + deletedAt + ordem`; `usuarioId + cadernoId + deletedAt`; índice de texto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` (`default_language: "portuguese"`) |
| `push_subscriptions` | Inscrição Web Push por navegador/dispositivo | `usuarioId`; `endpoint` único |
| `lembretes_push` | Entrega de notificação agendada (`instanteEntregaUtc`) | `estado + instanteEntregaUtc`; `usuarioId + tarefaId` |

Use soft delete com `deletedAt`, `expiresAt`, `loteExclusaoId`/rotina de purga e estado hierárquico para lixeiras. TTL Mongo pode auxiliar, mas sua execução é assíncrona; restauração e prazo devem ser validados pela aplicação.

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

## Plano original da FASE 9 (referência de escopo)

## FASE 9 — Exclusão de Conta nas 18 Coleções Privadas e Isolamento Ponta a Ponta

### 9.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 2 a 8 concluídas e integradas, além da infraestrutura F0–F1.
- **Objetivo da fase:** Implementar a exclusão completa, idempotente e auditável da conta do usuário (`DELETE /api/auth/conta`) percorrendo nominalmente todas as **18 coleções privadas** do sistema (das 19 coleções totais, apenas `editais` não é privada de usuário), revogando tokens e limpando o estado local no navegador.

### 9.3 Critérios de Saída da FASE 9
- Teste de integração `AccountDeletionIntegrationTest.java` que:
  1. Popula documentos para o `usuarioA` e para o `usuarioB` nas **18 coleções privadas** (e um edital em `editais`).
  2. Executa `DELETE /api/auth/conta` como `usuarioA`, recebe 202 e aguarda worker de exclusão.
  3. Verifica nominalmente nas 18 coleções privadas que `countDocuments` para `usuarioA` é `0` em todas elas, enquanto todos os documentos do `usuarioB` e o catálogo `editais` permanecem intactos.
  4. Injeta falha após cada coleção e verifica que worker retoma sem deixar órfãos; nova requisição sem autenticação não é necessária para completar operação.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 9                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 10                                -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 9 (hipótese a verificar)

## FASE 9 — Exclusão de Conta nas 18 Coleções Privadas e Isolamento Ponta a Ponta

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25)
1. **Contrato de Exclusão de Conta (`DELETE /api/auth/conta`):**
   - Implementado endpoint em `AuthResource.java` com resposta `202 ACCEPTED` e payload padronizado RFC 9457 / Trilhara contendo `{ "operationId": "...", "estado": "PENDENTE" }`.
   - Adicionada expiração imediata de cookies nos cabeçalhos de resposta: `trilhara_rt` e `trilhara_session_state` com `Max-Age=0`, `Path=/`, `HttpOnly` e flags seguras (`SameSite=Strict`, `Secure` em produção).
   - Suporte a cabeçalho `Idempotency-Key` e corpo `ExcluirContaRequest` contendo `senha` (validação com Argon2id/BCrypt via `PasswordService`), `confirmacao` obrigatória ("EXCLUIR MINHA CONTA") e `idempotencyKey` opcional.
   - Idempotência: caso a solicitação seja repetida com a mesma chave ou com a conta já desativada (`ativo=false`), o serviço retorna o status atual da operação sem duplicar tarefas em segun.
2. **Revogação Imediata de Credenciais e Segurança no `SecurityFilter`:**
   - Invalidação atômica imediata de todos os tokens de atualização (`refresh_tokens`), tokens de recuperação de senha (`tokens_recuperacao_senha`) e tokens de confirmação (`tokens_confirmacao_email`).
   - Bloqueio imediato da conta (`ativo = false`) com marcação de `exclusaoConta.estado = "PENDENTE"`.
   - `SecurityFilter` atualizado para rejeitar com `401 Unauthorized` qualquer requisição subsequente do usuário desativado, liberando exclusivamente chamadas idempotentes a `DELETE /api/auth/conta`.
3. **Serviço de Exclusão Assíncrono (`AccountDeletionService.java`):**
   - Processamento de exclusão em background via `ExecutorService` assíncrono gerenciado pelo Quarkus.
   - Varredura e limpeza estrita, tipada e nominal de todas as **18 coleções privadas** por `usuarioId: ObjectId` (sem fallback para String):
     1. `refresh_tokens`
     2. `tokens_recuperacao_senha`
     3. `tokens_confirmacao_email`
     4. `lembretes_push`
     5. `push_subscriptions`
     6. `blocos_cronograma`
     7. `cronogramas`
     8. `progresso_topicos`
     9. `disciplinas`
     10. `sessoes`
     11. `trilhas`
     12. `tarefas`
     13. `series_recorrentes`
     14. `listas_tarefas`
     15. `notas`
     16. `secoes`
     17. `cadernos`
     18. `usuarios` (coleção raiz — deletada em 18º lugar, somente após verificação atômica de que as 17 coleções filhas contêm exatamente 0 documentos remanescentes).
4. **Isolamento e Preservação de Catálogo e Outros Usuários:**
   - A coleção de catálogo público `editais` (19ª coleção) é intocável e preservada integralmente.
   - Isolamento ponta a ponta: dados de outros usuários (`userB`) em todas as 18 coleções privadas permanecem rigorosamente intactos durante e após a exclusão de `userA`.
5. **Resiliência contra Falhas, Interrupções e Backoff (`AccountDeletionJob.java`):**
   - Checkpointing de progresso registrado na entidade `Usuario` (`exclusaoConta.colecoesExcluidas`, `exclusaoConta.tentativas`, `exclusaoConta.proximaTentativa`, `exclusaoConta.ultimoErro`).
   - Job agendado `@Scheduled(every = "{trilhara.account-deletion.reconciliation-cron:60s}")` para retomar exclusões pendentes ou interrompidas por reinicialização do servidor, operando com credenciais de sistema sem exigir que o usuário se reautentique.
   - Backoff exponencial configurado (base de 1s, fator 2x, teto de 30s) com tolerância a até 5 tentativas antes de transição para estado `FALHA`.
   - Gancho controlado para testes integrados (`AccountDeletionService.testFailOnCollectionHook`).
6. **Auditoria e Privacidade:**
   - Logs estruturados contendo `operationId` e `usuarioId` em hexadecimal, com ausência absoluta de PII (nomes, e-mails ou senhas em texto claro).

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Purga Completa de Dados Locais (`limparDadosLocaisDoUsuario` em `authStorage.ts`):**
   - Implementada função utilitária que percorre integralmente o `localStorage` e o `sessionStorage` da origem corrente, removendo todas as chaves associadas ao padrão `trilhara:u:${usuarioId}:*` (ex.: rascunhos de notas, estado de temporizadores pomodoro, preferências de interface e filtros).
   - Remoção de chaves globais de sessão (`trilhara_auth_token`, `trilhara_session_state`, `trilhara_user`).
   - Disparo de evento local no DOM (`trilhara:auth:conta_excluida`).
   - Sincronização multi-abas em tempo real via `BroadcastChannel('trilhara:auth')` transmitindo `{ type: 'ACCOUNT_DELETED', usuarioId }`, forçando o encerramento imediato da sessão em todas as abas abertas.
2. **Serviço de Autenticação (`AuthService.excluirConta` em `authService.ts`):**
   - Chamada autenticada ao endpoint `DELETE /api/auth/conta` enviando `senha`, `confirmacao: "EXCLUIR MINHA CONTA"` e `Idempotency-Key` único gerado via `crypto.randomUUID()`.
   - Execução síncrona de `limparDadosLocaisDoUsuario` logo após confirmação HTTP do servidor.
3. **Modal de Gerenciamento de Conta (`ContaModal.tsx`):**
   - Componente de diálogo acessível exibindo informações da conta (Nome, E-mail, Status de Verificação, Data de Criação e Permissões).
   - Seção "Zona de Perigo" em destaque visual com avisos claros sobre a irreversibilidade do procedimento.
   - Fluxo de exclusão em duas etapas:
     1. Acionamento de diálogo de confirmação destrutivo (`ConfirmDialog`) com variant `danger`.
     2. Abertura do formulário de autorização estrita solicitando a senha atual e a digitação manual exata da frase `"EXCLUIR MINHA CONTA"`.
   - Desabilitação reativa do botão de submissão enquanto a frase não coincidir perfeitamente ou enquanto a senha estiver vazia.
   - Tratamento de erros exibindo mensagens amigáveis em caso de falha de credencial ou rede.
4. **Integração na Experiência do Usuário (Sidebars e Navbars):**
   - Propagação de `onAbrirConta` e `abrirContaModal()` a partir do `AuthContext` para a barra lateral unificada (`AppSidebar`) e navbars contextuais das aplicações (`EstudosApp`, `TarefasApp`, `NotasApp`).
   - Clique no avatar ou nome do usuário no rodapé da sidebar abre diretamente o `ContaModal`.

---


### 3. Checklist de Validação da FASE 9

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **9.1** | Endpoint `DELETE /api/auth/conta` aceita senha atual e frase `EXCLUIR MINHA CONTA` | **Atendido** | Validado em `AccountDeletionIntegrationTest#deveExcluirContaEmTodas18ColecoesEIsolarOutrosUsuariosEEditais` e `deveRejeitarExclusaoComSenhaInvalidaOuConfirmacaoIncorreta` |
| **9.2** | Retorno HTTP `202 ACCEPTED` com payload de tracking `{ "operationId": "...", "estado": "PENDENTE" }` | **Atendido** | Validado no `AuthResource.java` e verificado em `AccountDeletionIntegrationTest` |
| **9.3** | Expiração imediata de cookies `trilhara_rt` e `trilhara_session_state` (`Max-Age=0`) | **Atendido** | Verificado nos cabeçalhos `Set-Cookie` com `Max-Age=0` em `AccountDeletionIntegrationTest` |
| **9.4** | Invalidação atômica e imediata de credenciais de acesso (`refresh_tokens`, tokens de recuperação e confirmação) | **Atendido** | Executado em `AccountDeletionService.iniciarExclusao` antes do retorno HTTP 202 |
| **9.5** | Desativação imediata da conta do usuário (`ativo = false`) com bloqueio de login e sessão | **Atendido** | Atualização atômica `$set: { ativo: false, exclusaoConta: ... }` e verificação no `SecurityFilter` |
| **9.6** | Varredura estrita e nominal de remoção das **18 coleções privadas** | **Atendido** | Remoção de `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email`, `lembretes_push`, `push_subscriptions`, `blocos_cronograma`, `cronogramas`, `progresso_topicos`, `disciplinas`, `sessoes`, `trilhas`, `tarefas`, `series_recorrentes`, `listas_tarefas`, `notas`, `secoes`, `cadernos` e `usuarios` |
| **9.7** | Consultas e remoções executadas exclusivamente por `usuarioId: ObjectId` nativo | **Atendido** | `Filters.eq("usuarioId", usuarioId)` usando BSON `ObjectId`, sem fallback permissivo para String |
| **9.8** | Coleção raiz `usuarios` é a 18ª e última a ser excluída, somente após 17 coleções filhas terem 0 documentos | **Atendido** | Validado pelo log de execução sequencial e teste de asserção de contagem zero nas 17 coleções antes do `deleteOne` em `usuarios` |
| **9.9** | Preservação intocável da coleção de catálogo `editais` (19ª coleção) | **Atendido** | Validado em `AccountDeletionIntegrationTest`: edital público permanece com contagem inalterada |
| **9.10** | Isolamento de dados de outros usuários (`userB` intacto em todas as 18 coleções privadas) | **Atendido** | Validado em `AccountDeletionIntegrationTest`: todos os documentos de `userB` persistem intactos após exclusão de `userA` |
| **9.11** | Tolerância a falhas e retomada por worker assíncrono (`AccountDeletionJob`) sem depender de reautenticação | **Atendido** | Validado em `AccountDeletionIntegrationTest#deveRetomarExclusaoInterrompidaSemReautenticacaoDoUsuario` com injeção de falha programada |
| **9.12** | Checkpointing atômico do progresso de exclusão na entidade `Usuario` | **Atendido** | Gravação de `colecoesExcluidas`, `tentativas`, `proximaTentativa` e `ultimoErro` a cada etapa |
| **9.13** | Backoff exponencial com jitter (1s a 30s) e limite de 5 tentativas | **Atendido** | Calculado em `AccountDeletionService.calcularProximoBackoff` |
| **9.14** | Suporte a `Idempotency-Key` com retorno seguro de requisições repetidas | **Atendido** | Validado em `AccountDeletionIntegrationTest#deveSuportarIdempotenciaComMesmaChaveOuChamadasRepetidas` |
| **9.15** | Segurança estrita no `SecurityFilter` permitindo apenas `DELETE /api/auth/conta` para usuário desativado em processo de exclusão | **Atendido** | `SecurityFilter.java` valida status do usuário e impede qualquer acesso a rotas privadas normais |
| **9.16** | Auditoria e logs sem vazamento de PII | **Atendido** | Registros contêm apenas `operationId` e `usuarioId` em hexadecimal |
| **9.17** | Frontend: `limparDadosLocaisDoUsuario(usuarioId)` remove todas as chaves `trilhara:u:${usuarioId}:*` do origin | **Atendido** | Validado em `fase9ExclusaoConta.test.tsx` no `localStorage` e `sessionStorage` |
| **9.18** | Frontend: Notificação multi-abas via `BroadcastChannel('trilhara:auth')` com `ACCOUNT_DELETED` | **Atendido** | Validado em `fase9ExclusaoConta.test.tsx` interceptando eventos do canal de broadcast |
| **9.19** | Frontend: `ContaModal` com confirmação dupla (`ConfirmDialog` destrutivo + senha + frase "EXCLUIR MINHA CONTA") | **Atendido** | Validado em `fase9ExclusaoConta.test.tsx` com renderização e validação de botões e campos |
| **9.20** | Frontend: Ponto de entrada na Sidebar (`AppSidebar`) e navegações das 3 aplicações | **Atendido** | Conectado ao `abrirContaModal` do `AuthContext` em `EstudosApp`, `TarefasApp` e `NotasApp` |
| **9.21** | Sucesso em 100% dos testes automatizados de frontend Vitest (`npm test -- --run`) | **Atendido** | 12 arquivos de teste, 91/91 testes aprovados (5 testes exclusivos da Fase 9) |
| **9.22** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem erros de tipagem em 5.65s |
| **9.23** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 103 testes executados com 0 falhas e 0 erros (4 testes de integração de ponta a ponta na Fase 9) |


---

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

---

