# R2 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R2** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R2 — Revisão da F1: BSON, índices canônicos, inventário, armazenamento de tokens, SSO, AppSwitcher e tema

**1. Referência e Diagnóstico**
- Índices relatados ≠ plano (META-04, SEC-06); conflito não derruba readiness; duas classes `MongoIndexInitializer` (DC-02); `BsonDates` tolerante a legado (DC-17); coleção de handoff fora do inventário (SEC-13); access token e usuário em `localStorage` (SEC-01); AppSwitcher sem padrão APG, sem guarda de alterações e sem preservação de tema (UX-06, UX-08); ausência de guarda estrutural de tenant (SEC-08).

**2. Especificação Técnica, Segurança e UX**
- `MongoIndexInitializer` único em `shared/mongo`, com a tabela canônica (plano F1 itens 1–19) **mais**:
  - `tokens_handoff_sso`: `{ codigoHash: 1 }` único; `{ usuarioId: 1 }`; `{ expiraEm: 1 }` com `expireAfterSeconds: 0`.
  - `series_recorrentes`: `{ usuarioId: 1, origemOperacaoId: 1 }` único parcial (D-24).
  - `lembretes_push`: conforme D-36 (substitui o item 19 ).
  - `push_subscriptions`: `{ endpoint: 1 }` único (não `usuarioId+endpoint`), `{ usuarioId: 1 }`.
  - `refresh_tokens`/`tokens_*`: TTL sobre `expiraEm` (nome canônico).
  - `blocos_cronograma`: acrescentar `{ usuarioId: 1, trilhaId: 1, cronogramaId: 1, versaoGeracao: 1, concluido: 1, data: 1 }` (lista virtual/atrasados) — justificar com `explain` em R12.
  - Nomes: `idx_{colecao}_{campos}` (e `uk_` para únicos).
- `MongoIndexHealthCheck`: `DOWN` se faltar índice declarado ou houver conflito; mostrar o *drift* de índices `idx_`/`uk_` não declarados.
- `BsonDates`: assinaturas estritas `Date toBsonDate(Instant)`, `Instant toInstant(Date)`, `String toIso(LocalDate)`, `LocalDate toLocalDate(String)`; leitura de tipo inesperado → `IllegalStateException` (dados legados devem ser recriados, premissa regra normativa).
- `enum ColecaoTrilhara` com as 20 coleções e a flag `privada`; usado pelo inicializador, pela exclusão de conta e pelo `InventarioColecoesTest` (D-08).
- Auth frontend (D-09): `authStorage` só em memória; boot com `POST /api/auth/refresh` single-flight; `GET /api/auth/me` retorna `{ id, nome, email, emailVerificado, papeis, preferencias: { tema, disponibilidadeSemanalMinutos, padraoLembreteMinutosAntes } }`.
- Tema (D-10): `PUT /api/usuarios/me/preferencias/tema` body `{ tema }` → 200 com as preferências.
- `AppSwitcher` (D-12): APG Menu Button; `aria-haspopup="menu"`, `aria-expanded`; itens `role="menuitem"` com `aria-current="page"` no atual; guarda de alterações não salvas; handoff apenas em ambiente local.
- `unsavedChangesRegistry` + `useUnsavedChanges` (D-41) criados aqui.
- ArchUnit (D-06): habilitar as regras de pacotes legados proibidos e de "Resource não acessa Mongo diretamente". A regra de assinatura `usuarioId` é habilitada por domínio em R3–R9.

**3. Checklist Atômico de Execução**
1. Remover `config/MongoIndexInitializer` (ou `shared/mongo`, mantendo apenas um); ajustar a injeção.
2. Reescrever a tabela de índices conforme o item 2; criar `enum ColecaoTrilhara`.
3. Implementar a comparação estrita esperado × encontrado; health `DOWN` em divergência.
4. Executar `scripts/mongo/reset-dev.sh` (criar) e validar o startup limpo.
5. Tornar `BsonDates` estrito; corrigir os chamadores.
6. Mover a persistência de handoff para a coleção `tokens_handoff_sso`; garantir consumo atômico (`findOneAndUpdate` com `consumidoEm: null` e `expiraEm > agora`).
7. Frontend: remover `trilhara_auth_token` e `trilhara_user` de `authStorage.ts`/`AuthContext.tsx`/`api.ts`; implementar o token em memória e o boot por refresh.
8. Implementar a preferência de tema no backend (`usuarios.preferencias.tema`) e no frontend (cache por usuário, aplicação antes do paint via script inline **proibido pela CSP** → usar a leitura síncrona do cache em `main.tsx` antes do `createRoot`).
9. Refatorar o `AppSwitcher` para o APG; integrar a guarda de alterações; cobrir desktop e mobile.
10. Criar `unsavedChangesRegistry`/`useUnsavedChanges` com listener de `beforeunload`.
11. Remover qualquer uso de `trilhara:u:anon:` (ver também R6).
12. Habilitar as regras ArchUnit de pacote.

**4. Critérios de Aceite e Validação Automatizada**
- Backend: `MongoIndexInitializerTest#deveCriarExatamenteIndicesCanonicos` (compara nome, chaves e opções de cada índice com a tabela); `#deveFicarDownComConflitoDeOpcoes` (cria previamente um índice com opções diferentes → health `DOWN`); `#deveSerIdempotente`; `BsonDatesStrictTest` (tipo inesperado lança); `HandoffConsumoAtomicoTest` (dois consumos paralelos → um 200 e um 400/401); `AuthCookieDomainTest` (mantido: host-only em `*.localhost`, Domain em produção, mesmos atributos no logout); `TemaPreferenciaTest`.
- Frontend: `authStorage.test.ts` (nada de token em `localStorage`/`sessionStorage` após login; `Object.keys(localStorage)` não contém `trilhara_auth_token`/`trilhara_user`); `bootRefresh.test.tsx` (duas chamadas simultâneas → um único refresh); `appSwitcher.test.tsx` (setas, Home/End, Escape devolve o foco, `aria-current`); `appSwitcherGuard.test.tsx` (com alterações registradas → `ConfirmDialog`; "Ficar" não navega); `tema.test.tsx` (troca persiste via PUT e aplica a classe).
- `rg "trilhara_auth_token|trilhara_user|trilhara:u:anon" frontend/src` → zero.
- `rg -l "class MongoIndexInitializer" backend/src/main` → exatamente 1 arquivo.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-06 — Isolamento estrutural por tenant**
- *Dúvida/Lacuna:* O isolamento depende de disciplina manual em cada consulta.
- *Deliberação e Referência:* *Defense in depth*; Linear e Notion aplicam o escopo de workspace na camada de acesso a dados, e não no controlador. ArchUnit permite verificar regras arquiteturais como teste.
- *Decisão Cravada:* (a) Toda classe `*Repository` de coleção privada expõe somente métodos cuja assinatura começa com `ObjectId usuarioId` e cujos filtros incluem `Filters.eq("usuarioId", usuarioId)` (ou `_id` em `usuarios`). (b) Acesso multi-tenant (purgas, scheduler de push, exclusão de conta, reconciliador de cronogramas) só existe em classes anotadas com `@SystemScope`, num pacote `..job..`, sem exposição HTTP. (c) Teste ArchUnit `TenantIsolationArchTest` garante (a) e (b) e que nenhum `*Resource` injeta `MongoClient`/`MongoCollection` diretamente. (d) Referências cruzadas recebidas em payload (`trilhaId`, `disciplinaId`, `listaId`, `cadernoId`, `secaoId`, `disciplinasParticipantesIds`, `trilhaId` de importação) são validadas por consulta com `usuarioId` **e** pai correto (ex.: disciplina pertence à trilha do path). Falha → `404 NOT_FOUND` para path e `400 VALIDATION_ERROR` para referência no body.

**D-07 — Índices canônicos e readiness**
- *Dúvida/Lacuna:* Os índices relatados divergem ; o inicializador loga conflitos sem abortar.
- *Deliberação e Referência:* Os requisitos regra normativa dizem: "falha de índice deve impedir readiness". Índices únicos são invariantes de negócio, e não otimização.
- *Decisão Cravada:* A tabela de índices da FASE 1  (itens 1–19), acrescida dos índices definidos neste documento (D-08, D-24, D-36), é a **única fonte**. Uma única classe `shared/mongo/MongoIndexInitializer`. Conflito de chave/opções ou índice ausente → readiness `DOWN` com diagnóstico (nome, esperado × encontrado). **Índices extras não declarados** com prefixo `idx_` criados pelo próprio app são listados como *drift* em log `WARN` e reportados no health (sem remoção automática). Script operacional `scripts/mongo/reset-dev.sh` recria o banco dev/test do zero (premissa: dados descartáveis).

**D-08 — Inventário de coleções**
- *Dúvida/Lacuna:* Surgiram coleções fora do inventário de 19 (operações de "concluir atrasados", códigos de handoff SSO).
- *Deliberação e Referência:* O inventário fechado é pré-condição da exclusão de conta (LGPD art. 16/18). Coleções implícitas viram dados órfãos.
- *Decisão Cravada:* (a) **Eliminar** a coleção de operações de concluir atrasados (D-17). (b) **Formalizar** a coleção privada `tokens_handoff_sso` (`_id`, `usuarioId`, `codigoHash` único, `origemDestino`, `expiraEm`, `consumidoEm?`), com índice TTL em `expiraEm` e índice `{ usuarioId: 1 }`. O inventário passa a **20 coleções = 19 privadas + `editais`**; a exclusão de conta cobre 18 filhas + `usuarios`. (c) Teste-guarda `InventarioColecoesTest`: após um cenário que exercita todos os endpoints, lista `listCollectionNames()` e falha se existir coleção não declarada no enum `ColecaoTrilhara`. (d) Atualizar requisitos regra normativa e plano F1/F9 com a contagem nova.

**D-09 — Armazenamento de tokens no navegador**
- *Dúvida/Lacuna:* O access token e o usuário ficam em `localStorage` global (SEC-01).
- *Deliberação e Referência:* OWASP ASVS V3 / OWASP HTML5 Cheat Sheet: não guardar tokens de sessão em `localStorage`. O refresh token já é cookie HttpOnly.
- *Decisão Cravada:* O access token vive **somente em memória** (módulo `authStorage` com variável privada). No boot, `POST /api/auth/refresh` (cookie) obtém um novo access token (single-flight). Remover `trilhara_auth_token` e `trilhara_user`. Os dados do perfil vêm de `GET /api/auth/me` e ficam em memória. Persistência local permitida apenas sob `trilhara:u:{usuarioId}:*` (timers, rascunhos, preferências de UI) e `trilhara:anon:*` (Pomodoro de visitante, D-21).

**D-10 — Preservação de tema e preferências entre origens**
- *Dúvida/Lacuna:* regra normativa exige preservar o tema entre subdomínios, mas `localStorage` é por origem.
- *Deliberação e Referência:* Vercel e Linear persistem a preferência no servidor e aplicam um cache local para evitar *flash*.
- *Decisão Cravada:* `usuarios.preferencias.tema ∈ {"dark","light","system"}` exposto em `GET /api/auth/me` e alterado por `PUT /api/usuarios/me/preferencias/tema`. O cache local `trilhara:u:{id}:tema` é aplicado antes do primeiro paint. Para visitante, `trilhara:anon:tema`. Nunca passar tema/token por query string.

**D-12 — AppSwitcher e proteção de dados na troca de módulo**
- *Dúvida/Lacuna:* A navegação entre módulos troca de origem e destrói o estado em memória (formulários, modal de sessão).
- *Deliberação e Referência:* O Notion/Linear mostram um aviso de alterações não salvas; o WAI-ARIA APG define o padrão *Menu Button*.
- *Decisão Cravada:* O `AppSwitcher` implementa o APG Menu Button (Enter/Espaço/↓ abre e foca o 1º item; ↑/↓ circulam; Home/End; Escape fecha e devolve o foco ao botão; Tab fecha). Antes de navegar, consulta o registro global `unsavedChangesRegistry` (D-41). Se houver alterações não persistidas, abre `ConfirmDialog` ("Você tem alterações não salvas…" — **Ficar** [padrão] / **Sair mesmo assim**). Rascunhos de Notas são gravados (flush) em `localStorage` antes de qualquer navegação.

**D-41 — Guarda de alterações não salvas**
- *Decisão Cravada:* Hook `useUnsavedChanges(id, sujo: boolean)` registra no `unsavedChangesRegistry` global. Com qualquer item registrado: `beforeunload` nativo ativo; `AppSwitcher`, logout voluntário e troca de trilha pedem confirmação. Expiração de sessão (401) **nunca** descarta: abre `AuthModal` sem desmontar (já exigido).

---

## Contexto normativo de domínio pertinente

### 2.5 Navegação entre módulos (App Switcher)

Todos os quatro módulos (**Estudos**, **Tarefas**, **Notas** e **Pomodoro**) compartilham o mesmo cabeçalho/barra lateral do Design System (`AppSidebar`) com um alternador rápido de módulos (**App Switcher**), preservando a sessão autenticada (SSO) e o tema visual ativo tanto em navegação por subdomínios (`*.trilhara.com.br` / `*.localhost`) quanto por rotas (`/estudos`, `/tarefas`, `/notas`, `/pomodoro`).

## 3. Identidade, tenancy e tipos BSON

- Padronizar o campo de proprietário de todas as coleções privadas como `usuarioId` gravado como `ObjectId` BSON nativo. Eliminar o fallback legado `"local"` em string; em teste usar autenticação simulada/helper ou, somente se indispensável, ObjectId fixo no profile `%test`, nunca fora dele.
- Usar `ObjectId` BSON nativo para `_id` e referências entre coleções. A API HTTP expõe IDs como strings hexadecimais de 24 caracteres; ID malformado é sempre `400 INVALID_IDENTIFIER`, ID bem formado mas inexistente ou de outro tenant é `404 NOT_FOUND`.
- Tópicos embutidos no documento da disciplina usam `id` string estável gerado com `new ObjectId().toHexString()`. IDs de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações e nunca são reutilizados após remoção.
- Datas que representam instantes (`criadoEm`, `atualizadoEm`, `concluidoEm`, `deletedAt`, `expiresAt`, `instanteEntregaUtc`) são `BSON Date` no MongoDB e `Instant` no Java.
- Datas de calendário (`dataVencimento`, `dataOriginalOcorrencia`, `dataSessao`, `dataBloco`, `dataInicio`, `dataFim`, `dataProva`) são armazenadas como string ISO `yyyy-MM-dd` (mapeadas para `LocalDate` no Java via codec BSON direto), garantindo ordenação lexicográfica e ausência de deslocamento por fuso horário.
- O fuso de calendário segue o fuso atual do navegador: o cliente HTTP compartilhado envia em todas as requisições o cabeçalho `X-Timezone` com o identificador IANA (ex.: `America/Sao_Paulo`, obtido via `Intl.DateTimeFormat().resolvedOptions().timeZone`). O backend valida o `X-Timezone` (com fallback seguro para `America/Sao_Paulo` se ausente/inválido) e calcula `hojeLocal = LocalDate.now(zoneId)` para visão Hoje, atrasadas, corte de blocos futuros, trava de data futura em sessão manual e cálculo de *streak*, proibindo o uso de `LocalDate.now()` no fuso UTC da JVM.
- Converter valores para BSON com `CodecRegistry` tipado ou mapeadores BSON diretos. É proibido serializar/desserializar via `ObjectMapper.writeValueAsString` + `Document.parse` + `documento.toJson()`.

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

---

## Plano original da FASE 1 (referência de escopo)

## FASE 1 — Fundação BSON/MongoDB, `ObjectId`/`usuarioId`, Índices das 19 Coleções, SSO (Local vs Produção) e `AppSwitcher`

### 1.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASE 0 concluída.
- **Objetivo da fase:**
  1. Configurar codecs/utilitários BSON tipados (`ObjectId`, `Instant` como `BSON Date`, `LocalDate` como string ISO `yyyy-MM-dd`).
  2. Padronizar `usuarioId` como `ObjectId` nativo no MongoDB e remover o fallback legado `"local"` em `UsuarioAtual.java`.
  3. Criar o inicializador centralizado e idempotente de índices das **19 coleções** no startup, removendo qualquer `deleteMany` destrutivo de construtores de repositórios.
  4. Ajustar a autenticação/SSO diferenciando explicitamente **Produção** (cookies com `Domain` compartilhado) e **Desenvolvimento Local** (`localhost` / `*.localhost` sem atributo `Domain`), sincronizando logout/troca de conta ao focar a aba e abrindo o `AuthModal` em `401` sem desmontar a tela.
  5. Implementar o componente `AppSwitcher` integrado à `AppSidebar` (desktop e mobile) para alternar entre Estudos, Tarefas, Notas e Pomodoro.
- **O que NÃO fazer nesta fase:**
  - Não reescrever ainda as regras de negócio de Estudos (F2–F4), Tarefas (F6) ou Notas (F8); apenas preparar a infraestrutura BSON, índices, autenticação e o `AppSwitcher`.

### 1.3 Critérios de Saída da FASE 1
- Testes automatizados em `backend/src/test/java/br/com/trilhara/shared/mongo/` validando:
  - Round-trip de `ObjectId`, `Instant` (`BSON Date`) e `LocalDate` (`yyyy-MM-dd`), schema BSON e índices efetivos por nome/opções.
  - Execução repetida de `MongoIndexInitializer` em banco vazio criando todas as 19 coleções/índices sem erro.
  - Emissão de cookies em `AuthResource`: sem atributo `Domain` quando `Host: estudos.localhost` e com `Domain=.trilhara.com.br` quando configurado para domínio produtivo.
- Build do frontend (`npm run build`) passando com o novo `AppSwitcher` integrado na `AppSidebar`.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 1                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 2                                 -->
<!-- ===================================================================== -->

---

## Convenções transversais  de implementação

### 0.4 Convenções normativas de API e implementação (aplicam-se a F0–F11)
- Recursos privados exigem autenticação. Toda leitura/escrita de recurso raiz ou filho consulta simultaneamente ID e `usuarioId`; recurso de outro usuário e ID inexistente respondem `404`, sem revelar existência. Ausência de autenticação responde `401`; falha de autorização de função administrativa responde `403`.
- IDs HTTP são strings hexadecimais de 24 caracteres. ID malformado em path/query/body responde `400 INVALID_IDENTIFIER`; ID válido mas inexistente/fora do tenant responde `404 NOT_FOUND`.
- Erros usam RFC 9457 (`application/problem+json`) com `type`, `title`, `status`, `detail`, `instance`; extensões estáveis `code` e `errors` são documentadas pelo produto. `detail` não é contrato de máquina; clientes decidem pelo status e `code`.
- Paginação por offset para este MVP: parâmetros `pagina` (1-based, default 1) e `tamanho` (default definido por endpoint, máximo 100); valor menor que 1 ou maior que o máximo retorna `400 INVALID_PAGINATION`. Resposta: `{ itens, pagina, tamanho, totalItens, totalPaginas }`. Toda listagem tem ordenação estável incluindo `_id` como desempate. Mudança do conjunto durante paginação pode alterar páginas; não se promete snapshot transacional.
- Datas de calendário são strings ISO estritas `yyyy-MM-dd`; instantes são RFC 3339 em API e `Instant`/BSON Date no banco. Duração é inteiro em segundos ou minutos conforme campo explicitamente nomeado. Não inferir fuso a partir do servidor.
- Requisições de escrita idempotentes declaram escopo/chave, hash funcional, status de retry e retenção. Conflito de mesma chave com payload funcional diferente responde `409 IDEMPOTENCY_KEY_CONFLICT`; chave de outro usuário não colide.
- Operações compostas sem transação multi-documento definem estados intermediários, recuperação/retry e efeitos parciais em requisito e teste. Nunca afirmar atomicidade se ela não for oferecida.
- Vitest, React Testing Library e jsdom são as dependências de teste frontend aprovadas para esta reestruturação; fixar versões compatíveis com React 18/Vite 6 no lockfile e documentar os comandos `npm test` e `npm run build`.

---

## Relato da implementação alegada — FASE 1 (hipótese a verificar)

## FASE 1 — Fundação BSON/MongoDB, ObjectId/usuarioId, Índices das 19 Coleções, SSO (Local vs Produção) e AppSwitcher

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25 / MongoDB Reactive Streams)
1. **Fundação e Utilitários BSON (`BsonIds.java` e `BsonDates.java`):**
   - Criado `BsonIds` (`br.com.trilhara.shared.mongo.BsonIds`):
     - Conversão tipada com validação estrita: `toObjectId(hex, campo)` disparando `BadRequestException` caso inválido.
     - `toObjectIdOrNotFound(hex)` disparando `NotFoundException` para rotas de busca por identificador.
     - `novoIdHex()` e `toHex(ObjectId)` para interoperabilidade de transporte DTO.
     - `isValid(hex)` para checagem rápida de formato BSON ObjectId de 24 caracteres hexadecimais.
   - Criado `BsonDates` (`br.com.trilhara.shared.mongo.BsonDates`):
     - Métodos de conversão sem saltos de fuso ou passagens intermediárias por strings JSON: `toBsonDate(Instant)`, `toInstant(Object)`, `toIsoString(Object)` e `toLocalDate(Object, ZoneId)`.
2. **Eliminação do Fallback Inseguro de Usuário (`UsuarioAtual.java`):**
   - Removida a constante insegura `USUARIO_LOCAL_PADRAO = "local"`.
   - Em produção e desenvolvimento, qualquer requisição a rota autenticada sem credenciais ativas lança rigorosamente `NotAuthorizedException` (401).
   - Suporte controlado a usuário simulado via `%test.trilhara.auth.test-fallback-user-id` ativo **exclusivamente** sob `LaunchMode.current() == LaunchMode.TEST`.
3. **Inicializador Centralizado e Idempotente de Índices (`MongoIndexInitializer.java`):**
   - Criado serviço `@ApplicationScoped` inicializado em `@Startup` que assegura todos os índices das 19 coleções do sistema:
     1. `usuarios` (único em `email`, índices em `ativo`, `roles`)
     2. `refresh_tokens` (único em `tokenHash`, índice em `usuarioId` e TTL em `expiresAt`)
     3. `tokens_recuperacao_senha` (TTL em `expiracao`, índice em `usuarioId`)
     4. `tokens_confirmacao_email` (TTL em `expiracao`, índice em `usuarioId`)
     5. `editais` (composto `usuarioId + deletedAt`, busca por `orgao` e `status`)
     6. `trilhas` (composto `usuarioId + editalId + deletedAt`)
     7. `disciplinas` (composto `trilhaId + deletedAt`, `usuarioId`)
     8. `progresso_topicos` (único `usuarioId + editalId + topicoId`)
     9. `cronogramas` (composto `usuarioId + status + dataInicio`)
     10. `blocos_cronograma` (composto `cronogramaId + data + status`, `usuarioId`)
     11. `sessoes` (composto `usuarioId + dataHora`, `disciplinaId`, `editalId`, `topicoId`)
     12. `listas_tarefas` (composto `usuarioId + deletedAt`, ordenação)
     13. `series_recorrentes` (composto `usuarioId + ativo`, próxima execução)
     14. `tarefas` (composto `usuarioId + status + dataVencimento`, `listaId`, `serieId`)
     15. `cadernos` (composto `usuarioId + deletedAt`, ordenação)
     16. `secoes` (composto `cadernoId + deletedAt`, `usuarioId`)
     17. `notas` (composto `usuarioId + deletedAt`, `secaoId`, índice de texto em `titulo + conteudo`)
     18. `push_subscriptions` (único `usuarioId + endpoint`)
     19. `lembretes_push` (composto `usuarioId + disparado + dataAgendada`, TTL)
   - Verificação dinâmica de índices pré-existentes via `listIndexes()`, com algoritmo de comparação resiliente a formatos internos do MongoDB (como `_fts` para índices de texto) e validação de opções (`unique`, `sparse`, `expireAfterSeconds`). Em caso de conflito, loga alerta detalhado sem abortar a aplicação.
4. **Health Check de Índices (`MongoIndexHealthCheck.java`):**
   - Criado health check `@Readiness` em `/q/health/ready` informando se as 19 coleções foram verificadas com sucesso (`UP` ou `DOWN`).
5. **Remoção de Construtores Destrutivos nos Repositórios:**
   - Removidas chamadas a `deleteMany(new Document())` e chamadas ad-hoc de criação de índices nos construtores de todos os repositórios: `TarefaRepository`, `ListaTarefasRepository`, `UserRepository`, `RefreshTokenRepository`, `TokenConfirmacaoEmailRepository`, `TokenRecuperacaoRepository`, `SessaoRepository`, `TrilhaRepository`, `EditalRepository`, `CadernoRepository`, `NotaRepository` e `PlanejamentoCicloRepository`.
6. **Autenticação, Handoff SSO e Isolamento de Cookies (`AuthResource.java`, `HandoffService.java`):**
   - Método `determinarCookieDomain(host)`:
     - Localhost (`localhost`, `127.0.0.1`, `*.localhost`): cookie **Host-Only** (atributo `Domain` é explicitamente omitido), evitando que abas em portas distintas sobrescrevam cookies incorretamente.
     - Produção (`trilhara.com.br`): emite `Domain=.trilhara.com.br` viabilizando SSO entre subdomínios (`estudos.trilhara.com.br`, `tarefas.trilhara.com.br`, etc.).
   - Cookie duplo: além do refresh token HttpOnly (`trilhara_rt`), o backend emite `trilhara_session_state` (non-HttpOnly) para detecção de sessão sem acesso ao token seguro.
   - Invalidação completa (`Max-Age=0`) de ambos os cookies em logout, logout de todas as sessões, alteração de senha e recuperação.
   - Endpoints de SSO Handoff para fluxo local entre portas:
     - `POST /api/auth/handoff` (`@Autenticado`): gera token descartável com TTL de 60 segundos, persistido com hash SHA-256 no banco e com validação de URL de destino contra allowlist de origens permitidas.
     - `POST /api/auth/handoff/consume` (público): valida o código, garante consumo atômico único (one-time use com deleção imediata) e emite novo par de tokens/cookies para a origem destino.
   - Detecção de ataque de replay em refresh tokens com período de carência configurável (`trilhara.auth.refresh.grace-period-seconds`, ajustado para `0` em testes automatizados).

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Módulo de SSO (`sso.ts`):**
   - Criado `frontend/src/shared/auth/sso.ts` com identificação do módulo atual (`estudos`, `tarefas`, `pomodoro`, `notas`), checagem de ambiente (`isAmbienteLocal()`), geração de URLs canônicas de cada módulo para portas locais (`5173`, `5174`, `5175`, `5176`) ou subdomínios de produção.
   - Função `iniciarHandoffParaApp`: em produção navega diretamente (aproveitando o cookie compartilhado de domínio); em desenvolvimento local solicita o código opaco a `/api/auth/handoff` e redireciona via fragmento `#sso_code=...`.
   - Função `consumirHandoffSsoDaUrl`: consome o `#sso_code` na inicialização, chamando `/api/auth/handoff/consume`, limpando o fragmento da URL via `history.replaceState` sem poluir o histórico do navegador.
2. **Reautenticação In-Place em 401 sem Desmontagem (`api.ts` e `AuthContext.tsx`):**
   - Em caso de 401 não recuperável por refresh token, `api.ts` dispara o evento global `AUTH_REQUIRED_EVENT` (`trilhara:auth-required`).
   - `AuthContext` intercepta o evento e abre o `<AuthModal>` sobre a aplicação sem executar `window.location.reload()`, sem desmontar a árvore de componentes e sem perder formulários ou dados não salvos pelo usuário.
   - Sincronização entre abas com throttle de 5 segundos ouvindo `visibilitychange` e `focus`.
3. **Componente AppSwitcher (`AppSwitcher.tsx` e `AppSidebar.tsx`):**
   - Criado menu de contexto `AppSwitcher` exibindo os 4 módulos do ecossistema Trilhara (Estudos, Tarefas, Notas, Pomodoro) com ícones dedicados, rotas e descrições.
   - Destaque acessível com `aria-current="page"` e badge visual "Atual" no módulo em execução.
   - Suporte completo a navegação por teclado (tecla Escape fecha o menu, navegação por foco).
   - Integrado de forma responsiva ao cabeçalho do `AppSidebar` (visível no desktop recolhido/expandido e no drawer mobile).

---


### 3. Checklist de Validação da FASE 1

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **1.1** | Criação de `BsonIds.java` com validação de ObjectId e tratamento de 400/404 | **Atendido** | Validado em `BsonRoundTripTest#testBsonIdsToObjectIdValidoEInvalido` e `testBsonIdsToObjectIdOrNotFound` |
| **1.2** | Criação de `BsonDates.java` sem passagens intermediárias por string JSON nem desvios de fuso | **Atendido** | Validado em `BsonRoundTripTest#testBsonDatesRoundTripInstant` e `testBsonDatesLocalDateComFusoHorario` |
| **1.3** | Remoção de `USUARIO_LOCAL_PADRAO = "local"`, exigindo autenticação ativa (401) e isolando mock para testes | **Atendido** | `UsuarioAtual.java` lança `NotAuthorizedException` em dev/prod; suite de autenticação valida 401 |
| **1.4** | Criação de `MongoIndexInitializer.java` cobrindo integralmente as 19 coleções do sistema | **Atendido** | Validado em `MongoIndexInitializerTest#deveGarantirIndicesNas19ColecoesComSucesso` |
| **1.5** | Idempotência e detecção de conflitos de chaves/opções na inicialização de índices | **Atendido** | Validado em `MongoIndexInitializerTest#deveSerIdempotenteEmExecucoesRepetidas` e testes de detecção de divergência |
| **1.6** | Health check `@Readiness` em `MongoIndexHealthCheck.java` informando status UP/DOWN | **Atendido** | Implementado integrando `/q/health/ready` e validado via suíte de testes |
| **1.7** | Remoção de `deleteMany` destrutivo e índices manuais nos construtores dos repositórios | **Atendido** | Verificados e limpos todos os 12 repositórios do Trilhara; testes de integração passam sem perda de dados |
| **1.8** | Emissão de cookies Host-Only em ambiente local (sem atributo `Domain`) e `.trilhara.com.br` em produção | **Atendido** | Validado em `AuthCookieDomainTest#cookieRefreshTokenEmLocalhostNaoDeveConterAtributoDomain` e `cookieRefreshTokenEmProducaoDeveConterDomainTrilhara` |
| **1.9** | Emissão e expiração sincronizada dos cookies `trilhara_rt` e `trilhara_session_state` | **Atendido** | Validado em `AuthCookieDomainTest#logoutDeveExpirarRefreshTokenESessionState` |
| **1.10** | Endpoints de SSO Handoff (`/api/auth/handoff` e `/api/auth/handoff/consume`) com 60s TTL e uso único | **Atendido** | Validado em `AuthCookieDomainTest#fluxoDeHandoffSsoLocalDeveGerarEConsumirCodigoUnico` e `reutilizacaoDeCodigoDeHandoffDeveSerRejeitada` |
| **1.11** | Detecção de ataque de replay em refresh tokens com carência configurável | **Atendido** | Validado em `AuthResourceTest#deveDetectarReusoDeRefreshTokenEInvalidarFamilia` com carência zero em teste |
| **1.12** | Utilitário `sso.ts` no frontend com handoff em localhost e link direto em produção | **Atendido** | Validado em `sso.test.ts` (6 testes: geração de URLs, sanitização e fluxo de consumo) |
| **1.13** | Abertura do modal de reautenticação (`AuthModal`) sob 401 sem recarregar nem desmontar a tela | **Atendido** | Validado em `authContextModal.test.tsx` (formulário preservado intacto enquanto o modal é exibido) |
| **1.14** | Componente `AppSwitcher` listando os 4 módulos do ecossistema com indicação acessível do módulo atual | **Atendido** | Validado em `appSwitcher.test.tsx` (6 testes de acessibilidade, renderização e atalhos) |
| **1.15** | Integração do `AppSwitcher` no `AppSidebar` para desktop e mobile | **Atendido** | Integrado em `AppSidebar.tsx` e estilizado via `AppSidebar.module.css` |
| **1.16** | Sucesso no comando `mvn test -Dtest=TimezoneContextTest,ProblemDetailsMapperTest,BsonRoundTripTest,MongoIndexInitializerTest,AuthCookieDomainTest,AuthResourceTest` | **Atendido** | 44 testes executados, 0 falhas, 0 erros |
| **1.17** | Sucesso nos comandos `npm test` e `npm run build` no frontend | **Atendido** | 18 testes executados com 100% de sucesso e build de produção gerado |

---

---

