# R11 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R11** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R11 — Revisão da F10: Web Push de Tarefas

**1. Referência e Diagnóstico**
- Modelo de lembrete não versionado e com nomes não canônicos (RI-37); herança × "sem alerta" (RI-38, resolvido no modelo em R7); origem do SW e destino do clique indefinidos; inscrição de dispositivo compartilhado persiste após logout (SEC-12); `PATCH` em vez de `PUT`; scheduler a cada 1 min; 3 tentativas fixas em vez de backoff limitado à janela.

**2. Especificação Técnica, Segurança e UX**
- `lembretes_push` conforme D-36(a). O ciclo de vida é calculado a partir de `tarefa.{dataVencimento, horario, modoLembrete, lembreteMinutosAntes, fusoAgendamento, estado, deletedAt}`:
  - Elegível = `estado = PENDENTE` ∧ `deletedAt = null` ∧ `dataVencimento ≠ null` ∧ `horario ≠ null` ∧ `modoLembrete ≠ SEM_ALERTA` ∧ `instanteEntregaUtc > agora`.
  - Qualquer mudança em um desses campos → `versaoAgendamento + 1`, versões anteriores `PENDENTE` → `CANCELADO`, nova versão `PENDENTE` se elegível.
  - `HERDAR` usa `usuarios.preferencias.padraoLembreteMinutosAntes` (default 15); `PUT /api/push/preferencias` recalcula somente as tarefas `HERDAR` pendentes.
- Scheduler (`@SystemScope`, a cada 30 s): claim atômico `PENDENTE → PROCESSANDO` com `leaseToken`/`leaseAte = agora + 60 s` para `instanteEntregaUtc <= agora`; atraso > 15 min → `EXPIRADO_FORA_DA_JANELA`; falha transitória → volta a `PENDENTE` com `tentativas + 1` e próxima tentativa em `min(2^n s, restante da janela)`; janela esgotada → `FALHA_FINAL`; `404/410` → remove a inscrição; envio com `TTL: 900` e `Urgency: normal`. Somente o detentor do lease conclui.
- Payload criptografado (RFC 8291): `{ lembreteId, tarefaId, titulo, url }`, com `titulo` truncado em 80 caracteres e `url = {trilhara.app.tarefas-url}/?tarefaId={id}`.
- `GET /api/push/config` → `{ habilitado, publicKey?, padraoMinutosAntes }`; `POST /api/push/subscriptions` (upsert por `endpoint`, reatribuindo ao usuário atual; valida `endpoint` HTTPS e chaves base64url); `DELETE /api/push/subscriptions` body `{ endpoint }` (não pertencente → 404); `PUT /api/push/preferencias` body `{ padraoMinutosAntes ∈ {0,5,15,30,60} }`.
- Frontend:
  - SW registrado **somente** no app Tarefas (`navigator.serviceWorker.register('/sw.js', { scope: '/' })`); os outros módulos não registram SW.
  - `sw.js`: dedupe por `lembreteId` (últimos 100 em `IndexedDB` ou em memória do SW); `notificationclick` foca uma aba existente da origem ou abre `url`; nunca chama API de conclusão.
  - `ConfiguracaoLembreteModal`: estados `unsupported` / `denied` / `default` / `granted` / `ios-nao-instalado`, cada um com texto do Anexo B; o pedido de permissão só ocorre no clique.
  - Logout: `DELETE` da inscrição deste dispositivo (best-effort, timeout de 2 s, sem bloquear).
  - Deep link `?tarefaId=` (D-36f).

**3. Checklist Atômico de Execução**
1. Migrar `LembretePush`/repositório/índices para o modelo D-36 (renomear `horarioDisparoUtc` → `instanteEntregaUtc`; remover o TTL `expiraEm` de `lembretes_push`; adicionar `versaoAgendamento`).
2. Reescrever `LembretePushService` com a regra de elegibilidade e versionamento.
3. Ajustar o scheduler (30 s, lease, backoff na janela, `FALHA_FINAL`).
4. Trocar `PATCH /api/push/preferencias` por `PUT`.
5. Implementar a reatribuição de `endpoint` e o 404 para endpoint alheio no DELETE.
6. Montar a `url` absoluta no backend a partir de configuração com allowlist.
7. Frontend: registrar o SW apenas em Tarefas; dedupe e clique; `DELETE` no logout; deep link.
8. Habilitar ArchUnit para o pacote `push`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend: `LembretePushCalculatorTest` (gap DST → 400 `INVALID_LOCAL_DATETIME`; overlap → offset anterior; cálculo com `America/Sao_Paulo` e `Europe/Lisbon`); `LembretePushLifecycleTest` (`semAlertaNaoAgenda`; `herdarAcompanhaPadrao`; `personalizadoNaoAcompanhaPadrao`; `removerHorarioCancelaEPreservaModo`; `redefinirHorarioReagendaNovaVersao`; `concluirCancela`; `lixeiraCancela`; `restaurarReagendaSeFuturo`; `ocorrenciaRecorrenteHerdaModoDaSerie`); `PushDeliverySchedulerTest` (`doisWorkersNaoProcessamOMesmoItem`; `atrasoMaiorQue15MinExpiraSemEnviar`; `falhaTransitoriaRespeitaJanela`; `gone410RemoveInscricao`; `ttl900NoEnvio`); `PushResourceTest` (`putPreferenciasRecalculaSoHerdar`; `deleteEndpointAlheioRetorna404`; `upsertReatribuiEndpoint`).
- Frontend: `pushService.test.ts` (detecção de suporte e de iOS fora de standalone; permissão só no clique); `sw.test.ts` (dedupe por `lembreteId`; clique não chama `fetch` de conclusão); `logoutPush.test.ts` (DELETE chamado; falha não bloqueia o logout); `deepLinkTarefa.test.tsx` (abre modal; 404 → toast e limpeza do parâmetro).
- `rg "horarioDisparoUtc|usaPadraoGlobalLembrete|PATCH.*preferencias" backend frontend` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-26 — Modelo de lembrete explícito**
- *Dúvida/Lacuna:* `lembreteMinutosAntes == null` significando "herdar" impede "sem alerta".
- *Deliberação e Referência:* Regra aplicável: "sem alerta, no horário ou 5/15/30/60 min; herdeiras acompanham alteração do padrão".
- *Decisão Cravada:* Em `tarefas` e `series_recorrentes`: `modoLembrete ∈ {"HERDAR","SEM_ALERTA","PERSONALIZADO"}` (default `HERDAR`) + `lembreteMinutosAntes ∈ {0,5,15,30,60}` obrigatório só em `PERSONALIZADO`. Remover `usaPadraoGlobalLembrete`. `fusoAgendamento` = `TimezoneContext.zoneId()` capturado ao salvar. Remover o horário **mantém** `modoLembrete`/minutos e cancela o lembrete pendente (suspensão); redefinir o horário reagenda.

**D-36 — Web Push**
- *Decisão Cravada:* (a) `lembretes_push`: `{ _id, usuarioId, tarefaId, versaoAgendamento (long), instanteEntregaUtc, estado, tentativas, leaseAte?, leaseToken?, criadoEm, atualizadoEm }`; índice único `{ tarefaId: 1, versaoAgendamento: 1 }`, índices `{ estado: 1, instanteEntregaUtc: 1 }` e `{ usuarioId: 1, tarefaId: 1 }` (não único). Cada alteração relevante da tarefa incrementa `versaoAgendamento`, cancela as versões anteriores `PENDENTE` e cria uma nova. (b) Scheduler a cada **30 s**, lease de 60 s. (c) `PUT /api/push/preferencias` (remover o `PATCH`). (d) O SW e a inscrição pertencem **somente à origem do app Tarefas**; o payload contém `{ lembreteId, tarefaId, titulo, url }`, com `url` absoluta montada no backend a partir de `trilhara.app.tarefas-url` (allowlist). (e) O logout chama `DELETE /api/push/subscriptions` do dispositivo atual (best-effort, sem bloquear o logout); o upsert com um `endpoint` existente de outro usuário **reatribui** ao usuário atual. (f) O deep link `?tarefaId=` abre o `TaskModal`; com 404 → toast "Tarefa não encontrada ou excluída" e limpeza do parâmetro.

---

## Contexto normativo de domínio pertinente

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

---

## Plano original da FASE 10 (referência de escopo)

## FASE 10 — Entrega Isolada e Tardia: Notificações Web Push de Tarefas

### 10.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 6 e 9 concluídas e integradas, além da infraestrutura F0–F1.
- **Objetivo da fase:** Implementar as notificações Web Push para tarefas manuais com horário explícito de forma **100% isolada e não bloqueante** (se o navegador não suportar Push, se a permissão for negada ou se as chaves VAPID não estiverem configuradas no ambiente, o módulo Tarefas continua funcionando integralmente sem erros).

### 10.3 Critérios de Saída da FASE 10
- Testes automatizados em `backend/src/test/java/br/com/trilhara/push/` validando:
  - Cálculo de `instanteEntregaUtc` a partir de `LocalDate`, `LocalTime` e `ZoneId`.
  - Cancelamento ao concluir/excluir, suspensão ao remover horário e atualização apenas de herdeiras ao mudar o padrão global.
  - Expiração sem envio para lembretes atrasados há mais de 15 minutos (`EXPIRADO_FORA_DA_JANELA`) e remoção de inscrição ao receber `410 Gone`.
- `mvn test` e `npm run build` passando sem erros.
- Testes frontend verificam estado de permissão, ausência de suporte, clique requerido para prompt iOS e ação de clique da notificação sem concluir tarefa.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 10                                               -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 11                                -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 10 (hipótese a verificar)

## FASE 10 — Entrega Isolada e Tardia: Notificações Web Push de Tarefas

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25 / MongoDB)
1. **Modelagem de Domínio e Coleções (`push_subscriptions` e `lembretes_push`):**
   - Criação das entidades `PushSubscription.java` e `LembretePush.java` no pacote `br.com.trilhara.push`.
   - Estados canônicos de lembrete: `PENDENTE`, `PROCESSANDO`, `ENVIADO`, `CANCELADO`, `EXPIRADO_FORA_DA_JANELA` e `FALHA_FINAL`.
   - As coleções contam com índices otimizados e idempotentes previamente validados na Fase 1:
     - `push_subscriptions`: chave única em `{ endpoint: 1 }` e índice em `{ usuarioId: 1, ativo: 1 }`.
     - `lembretes_push`: chave única em `{ usuarioId: 1, tarefaId: 1 }`, índice de elegibilidade do scheduler `{ estado: 1, horarioDisparoUtc: 1 }` e TTL automático de expiração em `expiraEm`.
2. **Cálculo Determinístico de Disparo e Validação de Fuso Horário (`LembretePushCalculator.java`):**
   - Suporte rigoroso a fuso horário do usuário (IANA, ex.: `America/Sao_Paulo`).
   - Validação de horários inexistentes em transições de horário de verão (gap de DST) lançando `InvalidLocalDatetimeException` mapeada para HTTP `400 Bad Request` com código `"INVALID_LOCAL_DATETIME"`.
   - Resolução determinística de sobreposição (overlap de DST) favorecendo o offset de horário de verão (`earlierOffset`).
3. **Isolamento Estrito e Regras de Negócio de Notificação:**
   - **Isolamento de Tarefas:** Web Push aplica-se **exclusivamente a tarefas manuais com data de vencimento e horário definidos** (`dataVencimento != null && horario != null`).
   - **Preservação de Cronogramas:** Blocos de estudo de cronograma **nunca** geram lembretes push, preservando a autonomia do módulo de estudos.
   - **Resiliência e Não-bloqueio:** O ciclo de vida de tarefas (criação, edição, conclusão, exclusão, lixeira) é 100% não bloqueante; indisponibilidade de push, permissão negada ou ausência de chaves VAPID jamais afetam a manipulação de tarefas.
   - **Herança de Preferência:** Criação e atualização de preferência global do usuário (`padraoLembreteMinutosAntes`) recalculam apenas tarefas ativas que herdam o padrão (`lembreteMinutosAntes == null`), preservando overrides manuais específicos.
4. **Ciclo de Vida Integrado (`TarefaService.java` e `LembretePushService.java`):**
   - Criação de tarefa com horário: agenda lembrete push `PENDENTE`.
   - Alteração de data/horário: cancela lembrete anterior e reagenda com novo horário UTC.
   - Conclusão ou pulo de tarefa: transiciona imediatamente o lembrete para `CANCELADO`.
   - Reabertura de tarefa: reagenda lembrete se o horário de disparo ainda estiver no futuro.
   - Envio para a lixeira (`lixeira: true`): cancela lembretes pendentes.
   - Restauração da lixeira: recria lembrete pendente caso a tarefa permaneça ativa com data/horário futuro.
5. **Scheduler de Entrega com Fencing e Janela Útil de 15 Minutos (`PushDeliveryScheduler.java`):**
   - Execução recorrente a cada minuto com aquisição atômica e lock/lease de claiming (`PROCESSANDO`, lease de 60 segundos) evitando duplicidade entre nós concorrentes.
   - **Janela Útil de Entrega:** Lembretes acumulados ou atrasados além de 15 minutos (`horarioDisparoUtc < agora - 15min`) são marcados diretamente como `EXPIRADO_FORA_DA_JANELA` e descartados sem envio tardio.
   - Protocolo RFC 8030 com cabeçalho `TTL: 900` (15 minutos).
   - Invalidação automática de inscrições: respostas HTTP `404 Not Found` e `410 Gone` dos gateways de push removem imediatamente o endpoint da coleção `push_subscriptions`.
   - Tolerância a falhas transitórias com contagem de tentativas até 3 antes de transição para `FALHA_FINAL`.
6. **Endpoints REST Seguros (`PushResource.java`):**
   - `GET /api/push/config`: retorna status de ativação, VAPID public key e padrão de minutos configurado.
   - `POST /api/push/subscriptions`: upsert de assinatura Web Push associada ao dispositivo do usuário autenticado.
   - `DELETE /api/push/subscriptions`: desativação/remoção de assinatura pelo endpoint.
   - `PATCH /api/push/preferencias`: atualização da preferência de minutos padrão global com recálculo transparente das tarefas ativas dependentes.

#### PWA e Infraestrutura Web
1. **Manifest Web App (`frontend/public/manifest.webmanifest`):**
   - Configurado com `"display": "standalone"`, `"name": "Trilhara"`, `"short_name": "Trilhara"`, `"start_url": "/"` e ícones responsivos, atendendo aos requisitos estritos de instalação PWA e notificações no iOS Web Push (iOS 16.4+).
   - Integrado ao `frontend/index.html` via `<link rel="manifest" href="/manifest.webmanifest" />`.
2. **Service Worker (`frontend/public/sw.js`):**
   - Registro de listener `push` com deduplicação local de mensagens para prevenir disparos repetidos.
   - Listener `notificationclick` configurado para **abrir ou focar a aba existente da tarefa** (`/tarefas?tarefaId=...`) sem jamais efetuar a conclusão ou alteração de estado da tarefa.
3. **Servidor HTTP Caddy (`Caddyfile`):**
   - Cabeçalhos de cache estritos para o Service Worker: `/sw.js` servido com `Cache-Control "no-cache, no-store, must-revalidate"` para garantir atualização imediata em clientes PWA.
   - Servimento explícito do `/manifest.webmanifest` com Content-Type apropriado.

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Serviço de Web Push (`pushService.ts`):**
   - Detecção granular de compatibilidade do navegador (`Notification`, `PushManager`, `serviceWorker`).
   - Leitura segura de estados de permissão (`granted`, `denied`, `default`, `unsupported`).
   - Detecção especializada de iOS fora do modo standalone (`isIOSNonStandalone`) orientando o usuário a "Adicionar à Tela de Início".
   - Conversão de chave VAPID (`urlB64ToUint8Array`) compatível com `BufferSource` e registro no Service Worker.
2. **Modal de Configuração de Lembretes (`ConfiguracaoLembreteModal.tsx`):**
   - Permite ao usuário alterar a preferência global de antecedência (No horário, 5 min, 15 min, 30 min, 1 hora).
   - Status visual do dispositivo em tempo real com botão para conceder permissão e ativar push no navegador atual.
   - Guia passo a passo com alerta explicativo para usuários de iOS Safari.
   - Botão de acesso direto na barra de ferramentas do módulo de tarefas (`TarefasApp.tsx`).
3. **Formulário de Tarefas Aprimorado (`TaskModal.tsx`):**
   - Campo para seleção de lembrete com opção de herdar padrão global ou selecionar antecedência manual.
   - Badges informativos discretos caso o navegador não suporte push ou tenha notificações bloqueadas, mantendo o fluxo de salvamento de tarefas totalmente livre e funcional.

---


### 3. Checklist de Validação da FASE 10

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **10.1** | Push exclusivo para tarefas manuais com data e horário definidos (`dataVencimento != null && horario != null`) | **Atendido** | Validado em `LembretePushLifecycleTest#deveIgnorarTarefasSemHorarioOuSemData` |
| **10.2** | Blocos de estudo de cronograma nunca geram lembretes push | **Atendido** | Garantido pela separação estrita de domínios; blocos não passam por `LembretePushService` |
| **10.3** | Não-bloqueio: indisponibilidade de push ou permissão negada não impede operação de tarefas | **Atendido** | Validado em `fase10WebPush.test.tsx` (salvamento de tarefa com push unsupported e denied) |
| **10.4** | Validação de transições de fuso horário / DST (gap rejeitado com 400 `INVALID_LOCAL_DATETIME`) | **Atendido** | Validado em `LembretePushCalculatorTest#deveLancarExcecaoQuandoHorarioCairNoGapDoHorarioDeVerao` |
| **10.5** | Resolução determinística de sobreposição (overlap de DST) com preferência para `earlierOffset` | **Atendido** | Validado em `LembretePushCalculatorTest#deveResolverOverlapDoHorarioDeVeraoDeFormaDeterministica` |
| **10.6** | Janela útil de entrega de 15 minutos: lembretes atrasados transicionam para `EXPIRADO_FORA_DA_JANELA` sem entrega | **Atendido** | Validado em `PushDeliverySchedulerTest#deveExpirarLembreteForaDaJanelaUtilDe15MinutosSemEnviar` |
| **10.7** | Requisições Web Push utilizam cabeçalho RFC 8030 `TTL: 900` | **Atendido** | Implementado e verificado em `DefaultWebPushSender.java` e `PushDeliverySchedulerTest` |
| **10.8** | Respostas HTTP 410 (Gone) ou 404 (Not Found) removem imediatamente o endpoint de `push_subscriptions` | **Atendido** | Validado em `PushDeliverySchedulerTest#deveRemoverInscricaoQuandoGatewayRetornar410GoneOu404NotFound` |
| **10.9** | Ação de clique na notificação foca/abre a janela da tarefa sem efetuar conclusão | **Atendido** | Validado em `fase10WebPush.test.tsx` e inspecionado em `frontend/public/sw.js` |
| **10.10** | Manifest PWA com `display: standalone` e orientações contextuais para iOS Safari | **Atendido** | Validado em `fase10WebPush.test.tsx` e `manifest.webmanifest` |
| **10.11** | Caddyfile configurado com `Cache-Control: no-cache, no-store, must-revalidate` para `/sw.js` | **Atendido** | Configurado explicitamente no bloco `handle /sw.js` do `Caddyfile` |
| **10.12** | Alteração da preferência global de lembretes recalcula apenas tarefas que herdam o padrão | **Atendido** | Validado em `PushResourceTest#deveAtualizarPreferenciasGlobaisERecalcularApenasTarefasQueHeramPadrao` |
| **10.13** | Integração do ciclo de vida: reagendamento na edição, cancelamento na conclusão, lixeira e pulo, restauração segura | **Atendido** | Validado em `LembretePushLifecycleTest` cobrindo todos os fluxos de lifecycle |
| **10.14** | Sucesso em 100% dos testes automatizados de frontend Vitest (`npm test -- --run`) | **Atendido** | 13 suítes de teste, 100/100 testes aprovados (9 testes exclusivos da Fase 10) |
| **10.15** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem erros de tipagem |
| **10.16** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 116 testes executados com 0 falhas e 0 erros (13 testes exclusivos da Fase 10) |


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

