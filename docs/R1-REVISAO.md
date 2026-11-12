# R1 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R1** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R1 — Revisão da F0: Contratos de erro, catálogo, cliente HTTP e primitivas de estado do Design System

**1. Referência e Diagnóstico**
- F0 entregou `ProblemDetails`, mappers, `X-Timezone` e `ApiError`, mas: sem catálogo fechado de códigos (D-04); limite como 400/422 (RI-02); `PAYLOAD_TOO_LARGE`; `E11000`/`MongoException` sem mapeamento seguro (SEC-10); `IllegalArgumentExceptionMapper` pode vazar mensagens internas; ID malformado → 404 (SEC-05); ausência de política de propriedades desconhecidas (SEC-07); `type` não padronizado.
- **Decisão de revisão:** as primitivas de estado do Design System (D-37) entram **nesta fase**, e não por fase consumidora, porque a ausência delas gerou as inconsistências UX-01..03. Isso altera a regra de ouro 2  (registrar no plano).

**2. Especificação Técnica, Segurança e UX**
- Backend:
  - `shared/erro/CodigoErro.java` (enum com `httpStatus`, `typeUri()`), conforme D-04.
  - `ProblemDetailsFactory` única; todos os mappers delegam a ela. `detail` em pt-BR, sem stack/SQL/consulta/valores sensíveis.
  - Exceções de domínio: `InvalidIdentifierException`, `ResourceLimitExceededException`, `DomainConflictException(CodigoErro, detail)`, `TrashItemExpiredException`.
  - `MongoWriteException` com `ErrorCategory.DUPLICATE_KEY` → `409 DUPLICATE_RESOURCE` (os repositórios que esperam `E11000` como idempotência devem capturá-lo antes).
  - `IllegalArgumentExceptionMapper` → `500 INTERNAL_ERROR` genérico (argumento ilegal é bug), salvo exceções de domínio explícitas.
  - Jackson: `FAIL_ON_UNKNOWN_PROPERTIES=true` para desserialização de requests; o mapper converte `UnrecognizedPropertyException` em `400 VALIDATION_ERROR` com `errors[prop]`.
  - `BsonIds.parse(valor, campo)` (D-03); remover `toObjectIdOrNotFound`.
  - Header `X-Correlation-ID` em toda resposta (gerado se ausente); `instance` = path sem query string.
- Frontend:
  - `shared/api/erros.ts`: tipo `CodigoErro` (união literal idêntica ao enum), `classificarErro` (D-37), `extrairErrosDeCampo(ApiError)`.
  - `ApiError` passa a ter `code: CodigoErro | 'NETWORK_ERROR' | 'UNKNOWN'` (sempre presente), `status: number` (0 em erro de rede), `errors`, `detail`, `instance`, `correlationId`.
  - `shared/contracts/paginacao.ts`: `Pagina<T> = { itens: T[]; pagina: number; tamanho: number; totalItens: number; totalPaginas: number }`. Remover os tipos de página específicos duplicados (`PaginaSessoesDTO`, `PaginaBlocosDTO` no front) e usar `Pagina<SessaoDTO>`, `Pagina<BlocoCronogramaItemDTO>`.
  - Unificar `BlocoHojeDTO` em um único arquivo de contrato (DC-11).
  - Design System: `Skeleton`, `Spinner`, `EmptyState`, `FieldError`, `Toast` + `ToastProvider` (Radix-like, `aria-live="polite"`), `useDelayedFlag(ms)`, `useRecurso`, `Button.loading`; revisar `SectionErrorCard` (props `titulo`, `descricao`, `onRetry`, `variant`, botão "Tentar novamente" com `aria-describedby` apontando para a descrição) e `ConfirmDialog` (`variant='danger'`, foco inicial no botão seguro, `isConfirming` bloqueando Escape).
  - `shared/format/` (D-42).

**3. Checklist Atômico de Execução**
1. Criar `CodigoErro` e `ProblemDetailsFactory`; refatorar todos os mappers existentes para usá-los.
2. Substituir os retornos 400/422 de limite por `ResourceLimitExceededException` (409).
3. Substituir `PAYLOAD_TOO_LARGE` por `VALIDATION_ERROR` com `errors.conteudoMarkdown`.
4. Implementar o mapeamento de `DUPLICATE_KEY` e de `MongoTimeoutException` (→ 503 `SERVICE_UNAVAILABLE`).
5. Ativar `FAIL_ON_UNKNOWN_PROPERTIES` e o mapper de propriedade desconhecida.
6. Implementar `BsonIds.parse`; trocar todas as chamadas de `toObjectId`/`toObjectIdOrNotFound` e de `new ObjectId(String)` em resources/services (`rg "new ObjectId\(" backend/src/main`).
7. Frontend: criar `erros.ts`, ajustar `ApiError`/`apiFetch` (erro de rede → `code: 'NETWORK_ERROR'`, `status: 0`).
8. Criar `Pagina<T>` e migrar os usos; remover os tipos duplicados.
9. Implementar as primitivas do Design System com stories/testes; exportar em `shared/design-system/index.ts`.
10. Implementar `shared/format/` e substituir funções locais de formatação equivalentes (`modules/estudos/utils/format.ts` etc.) por re-export ou remoção.
11. Documentar o catálogo de erros em `docs/CONTRATO-ERROS.md` (gerado da enum, se possível).

**4. Critérios de Aceite e Validação Automatizada**
- Backend: `ProblemDetailsCatalogoTest` (para cada `CodigoErro`, provoca o erro em uma rota real ou de teste e valida `status`, `type`, `code`, `Content-Type: application/problem+json`, ausência de stack trace); `InvalidIdentifierTest` (`/api/trilhas/xyz` → 400 `INVALID_IDENTIFIER`; ID válido inexistente → 404); `MassAssignmentTest` (POST com `usuarioId` no body → 400 `VALIDATION_ERROR`); `DuplicateKeyMapperTest` (→ 409 `DUPLICATE_RESOURCE`).
- Frontend (`vitest` + RTL): `apiError.test.ts` (rede → `NETWORK_ERROR`; problem+json → campos); `classificarErro.test.ts`; `Skeleton.test.tsx` (não aparece antes de 200 ms com fake timers; `aria-busy`); `EmptyState.test.tsx`; `SectionErrorCard.test.tsx` (retry chamado, foco por teclado); `Toast.test.tsx` (região live, "Desfazer"); `ConfirmDialog.test.tsx` (foco inicial no botão seguro, Escape bloqueado durante `isConfirming`); `formatarDuracao.test.ts` (45 s, 12 min, 1 h 05 min); `formatarData.test.ts` (`'2026-03-01'` não vira 28/02 em `America/Sao_Paulo`).
- `rg "toObjectIdOrNotFound|PAYLOAD_TOO_LARGE|SCHEDULE_LIMIT_EXCEEDED|422" backend/src/main` → zero ocorrências.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-03 — ID malformado**
- *Dúvida/Lacuna:* `BsonIds.toObjectIdOrNotFound` retorna 404 para ID malformado; os requisitos exigem 400.
- *Deliberação e Referência:* Os requisitos regra normativa são explícitos. Retornar 400 para sintaxe inválida e 404 para ausência/posse alheia é o padrão de APIs como Stripe e GitHub, e não vaza existência.
- *Decisão Cravada:* Um único conversor `BsonIds.parse(String valor, String campo)` lança `InvalidIdentifierException` → `400 INVALID_IDENTIFIER` com `errors[campo]`. Remover `toObjectIdOrNotFound`. A posse alheia é sempre `404 NOT_FOUND`, igual à inexistência.

**D-04 — Catálogo de erros RFC 9457**
- *Dúvida/Lacuna:* Os códigos estão espalhados (400/422 para limite; `PAYLOAD_TOO_LARGE`; `E11000` possivelmente 500); o formato de `type` não está fixado.
- *Deliberação e Referência:* RFC 9457 regra normativa (`type` como URI estável); Stripe/GitHub mantêm um catálogo fechado de códigos de máquina.
- *Decisão Cravada:* `type = "urn:trilhara:problem:" + code em kebab-case` (ex.: `urn:trilhara:problem:latest-session-only`). `Content-Type: application/problem+json` em **todo** erro e **somente** em erro. Catálogo fechado (enum `CodigoErro` no backend e união literal `CodigoErro` no frontend, gerados da mesma lista):

| HTTP | `code` | Uso |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation, campos inválidos (inclusive Markdown > 512 KB, `dataSessao` futura, propriedade desconhecida), sempre com `errors` |
| 400 | `INVALID_IDENTIFIER` | ID malformado (D-03) |
| 400 | `INVALID_PAGINATION` | `pagina < 1`, `tamanho` fora dos limites |
| 400 | `INVALID_SEARCH_QUERY` | Busca de Notas |
| 400 | `INVALID_LOCAL_DATETIME` | Gap de DST |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Falta `Idempotency-Key` onde é obrigatória |
| 400 | `IDEMPOTENCY_KEY_MISMATCH` | Header ≠ body |
| 401 | `UNAUTHORIZED` | Sem autenticação/sessão revogada/conta em exclusão |
| 403 | `FORBIDDEN` | Operação administrativa sem papel |
| 404 | `NOT_FOUND` | Inexistente ou de outro tenant |
| 404 | `TRASH_ITEM_EXPIRED` | Restauração fora do prazo |
| 409 | `RESOURCE_LIMIT_EXCEEDED` | 50 trilhas / 100 disciplinas / 500 tópicos |
| 409 | `DUPLICATE_MODALITY` | Cronogramas |
| 409 | `SCHEDULE_GENERATION_IN_PROGRESS` | Lease ocupado na trilha |
| 409 | `LATEST_SESSION_ONLY` | Edição de sessão |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | Mesma chave, payload diferente |
| 409 | `IDEMPOTENCY_RESULT_REMOVED` | Retry após exclusão (tombstone) |
| 409 | `TRAIL_ALREADY_IMPORTED` | Importação sem `reimportar=true` (D-29) |
| 409 | `DUPLICATE_RESOURCE` | `E11000` não mapeado especificamente |
| 429 | `RATE_LIMITED` | Com header `Retry-After` |
| 500 | `INTERNAL_ERROR` | Genérico, `detail` neutro, `instance` + `X-Correlation-ID` |
| 503 | `SERVICE_UNAVAILABLE` | Mongo indisponível / readiness |

Remover os códigos `PAYLOAD_TOO_LARGE` e `SCHEDULE_LIMIT_EXCEEDED` e o status 422.

**D-05 — Proteção contra mass assignment e tenant implícito**
- *Dúvida/Lacuna:* O que impede o cliente de enviar `usuarioId` ou campos de sistema?
- *Deliberação e Referência:* OWASP API Security Top 10 — API3:2023 (*Broken Object Property Level Authorization*) e API1:2023 (BOLA).
- *Decisão Cravada:* (a) Nenhum DTO de request contém `usuarioId`, `criadoEm`, `atualizadoEm`, `deletedAt`, `expiresAt`, `loteExclusaoId`, `versao*`, `concluidoEm`, `requestHash`. (b) Jackson com `FAIL_ON_UNKNOWN_PROPERTIES = true` para requests → `400 VALIDATION_ERROR` com `errors["<prop>"] = ["Propriedade não permitida."]`. (c) `usuarioId` vem **somente** de `UsuarioAtual` (token).

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

**D-42 — Formatação**
- *Decisão Cravada:* `shared/format/`: `formatarDuracao(segundos)` → "45 s" (< 60), "12 min", "1 h 05 min"; `formatarDataCurta`/`formatarDataLonga` via `Intl.DateTimeFormat('pt-BR')` a partir de `yyyy-MM-dd` **sem** construir `Date` em UTC (evitar o deslocamento de fuso: parse manual ano/mês/dia); percentuais com 0 casas e `Intl.NumberFormat`. `font-variant-numeric: tabular-nums` em timers, contadores, tabelas e badges numéricos.

---

## Contexto normativo de domínio pertinente

## 1. Objetivo e princípios

Reestruturar o modelo de dados e a integração backend/banco sem confundir os limites dos quatro produtos, mantendo somente integrações explicitamente úteis.

Princípios:

1. **Cada domínio possui sua fonte de verdade.** Tarefas manuais pertencem a Tarefas; cronogramas e blocos pertencem a Estudos; sessões pertencem ao histórico de Estudos; cadernos e páginas pertencem a Notas.
2. **Não persistir cópias de leitura como entidades de domínio.** Blocos exibidos em Tarefas/Pomodoro são consultados de Estudos, não copiados para a coleção de tarefas.
3. **Uma métrica não deve ser armazenada em duplicidade sem necessidade.** Segundos de estudo e resultados de questões são calculados a partir das sessões. O progresso de tópicos é calculado a partir das conclusões dos tópicos.
4. **Atualizações pequenas e delimitadas.** Progresso, conclusão de bloco e alterações de sessão não devem substituir documentos grandes.
5. **Dados pessoais têm proprietário explícito.** Operações de leitura, escrita e exclusão verificam o usuário autenticado no backend.
6. **Erros de integração são visíveis.** Falha de uma API não deve virar uma lista vazia com aparência de sucesso.
7. **Destruição é explícita.** Exclusões em cascata e remoção de histórico mostram o impacto antes de confirmar.

### 1.1 Convenções normativas de API

- Toda rota de recurso privado exige autenticação. O backend escopa cada consulta e mutação ao `usuarioId` autenticado; ID válido inexistente ou pertencente a outro usuário resulta em `404`, sem revelar existência. Falta de autenticação é `401`; falta de privilégio para operação administrativa é `403`.
- ID malformado (não é hexadecimal de 24 caracteres) resulta em `400 INVALID_IDENTIFIER`. Restrições de campo/payload inválidas resultam em `400 VALIDATION_ERROR`; conflito de estado/regra resulta em `409` com `code` estável.
- Erros usam `application/problem+json` conforme RFC 9457: `type`, `title`, `status`, `detail`, `instance`; extensões Trilhara `code` e `errors` são documentadas e usadas por cliente apenas como campos de máquina. Mensagem `detail` pode ser localizada e não deve ser usada como identificador.
- Listagens usam `pagina` 1-based, `tamanho` dentro dos limites do endpoint e ordenação estável que inclui `_id` como desempate. Resposta comum `{ itens, pagina, tamanho, totalItens, totalPaginas }`; `tamanho` inválido é `400 INVALID_PAGINATION`. Paginação por offset não promete snapshot enquanto dados mudam.
- Horários de API são ISO 8601/RFC 3339; datas de calendário são `yyyy-MM-dd`; propriedade ausente e `null` têm semântica definida por cada DTO. Não enviar documentos Mongo diretamente como contrato público.

### 1.1 Convenções normativas de API

- Toda rota de recurso privado exige autenticação. O backend escopa cada consulta e mutação ao `usuarioId` autenticado; ID válido inexistente ou pertencente a outro usuário resulta em `404`, sem revelar existência. Falta de autenticação é `401`; falta de privilégio para operação administrativa é `403`.
- ID malformado (não é hexadecimal de 24 caracteres) resulta em `400 INVALID_IDENTIFIER`. Restrições de campo/payload inválidas resultam em `400 VALIDATION_ERROR`; conflito de estado/regra resulta em `409` com `code` estável.
- Erros usam `application/problem+json` conforme RFC 9457: `type`, `title`, `status`, `detail`, `instance`; extensões Trilhara `code` e `errors` são documentadas e usadas por cliente apenas como campos de máquina. Mensagem `detail` pode ser localizada e não deve ser usada como identificador.
- Listagens usam `pagina` 1-based, `tamanho` dentro dos limites do endpoint e ordenação estável que inclui `_id` como desempate. Resposta comum `{ itens, pagina, tamanho, totalItens, totalPaginas }`; `tamanho` inválido é `400 INVALID_PAGINATION`. Paginação por offset não promete snapshot enquanto dados mudam.
- Horários de API são ISO 8601/RFC 3339; datas de calendário são `yyyy-MM-dd`; propriedade ausente e `null` têm semântica definida por cada DTO. Não enviar documentos Mongo diretamente como contrato público.

## 3. Identidade, tenancy e tipos BSON

- Padronizar o campo de proprietário de todas as coleções privadas como `usuarioId` gravado como `ObjectId` BSON nativo. Eliminar o fallback legado `"local"` em string; em teste usar autenticação simulada/helper ou, somente se indispensável, ObjectId fixo no profile `%test`, nunca fora dele.
- Usar `ObjectId` BSON nativo para `_id` e referências entre coleções. A API HTTP expõe IDs como strings hexadecimais de 24 caracteres; ID malformado é sempre `400 INVALID_IDENTIFIER`, ID bem formado mas inexistente ou de outro tenant é `404 NOT_FOUND`.
- Tópicos embutidos no documento da disciplina usam `id` string estável gerado com `new ObjectId().toHexString()`. IDs de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações e nunca são reutilizados após remoção.
- Datas que representam instantes (`criadoEm`, `atualizadoEm`, `concluidoEm`, `deletedAt`, `expiresAt`, `instanteEntregaUtc`) são `BSON Date` no MongoDB e `Instant` no Java.
- Datas de calendário (`dataVencimento`, `dataOriginalOcorrencia`, `dataSessao`, `dataBloco`, `dataInicio`, `dataFim`, `dataProva`) são armazenadas como string ISO `yyyy-MM-dd` (mapeadas para `LocalDate` no Java via codec BSON direto), garantindo ordenação lexicográfica e ausência de deslocamento por fuso horário.
- O fuso de calendário segue o fuso atual do navegador: o cliente HTTP compartilhado envia em todas as requisições o cabeçalho `X-Timezone` com o identificador IANA (ex.: `America/Sao_Paulo`, obtido via `Intl.DateTimeFormat().resolvedOptions().timeZone`). O backend valida o `X-Timezone` (com fallback seguro para `America/Sao_Paulo` se ausente/inválido) e calcula `hojeLocal = LocalDate.now(zoneId)` para visão Hoje, atrasadas, corte de blocos futuros, trava de data futura em sessão manual e cálculo de *streak*, proibindo o uso de `LocalDate.now()` no fuso UTC da JVM.
- Converter valores para BSON com `CodecRegistry` tipado ou mapeadores BSON diretos. É proibido serializar/desserializar via `ObjectMapper.writeValueAsString` + `Document.parse` + `documento.toJson()`.

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

## Plano original da FASE 0 (referência de escopo)

## FASE 0 — Contratos de Erro (RFC 9457), Cabeçalho `X-Timezone` e Interfaces Base

### 0.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** Nenhum (primeira fase).
- **Objetivo da fase:** Padronizar o tratamento de erros HTTP do backend no formato **RFC 9457 (`Problem Details`)**, implementar o transporte e resolução do cabeçalho `X-Timezone` (fuso IANA do navegador) entre frontend e backend, e congelar as interfaces TypeScript dos contratos compartilhados em `frontend/src/shared/contracts/`, **sem** implementar ainda os componentes visuais nem refatorar os repositórios de domínio.
- **O que NÃO fazer nesta fase:**
  - Não alterar repositórios MongoDB de Estudos, Tarefas ou Notas.
  - Não implementar ainda os componentes visuais `GlobalSessionModal`, `SectionErrorCard`, `SyncStatusBadge` ou `AppSwitcher` (apenas declarar seus tipos/contratos TypeScript se necessário).

### 0.3 Critérios de Saída da FASE 0
- `mvn test -Dtest=TimezoneContextTest,ProblemDetailsMapperTest` passa no diretório `backend/`.
- Configurar Vitest + React Testing Library + jsdom e script `npm test` na FASE 0; `npm test` e `npm run build` passam no diretório `frontend/`.
- Todas as requisições de `apiFetch` enviam o cabeçalho `X-Timezone`.

---

## Relato da implementação alegada — FASE 0 (hipótese a verificar)

## FASE 0 — Contratos de Erro (RFC 9457), Cabeçalho `X-Timezone` e Interfaces Base

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25)
1. **RFC 9457 (`ProblemDetails.java`):**
   - Criado record imutável `ProblemDetails` (`br.com.trilhara.shared.erro.ProblemDetails`) com membros RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) e extensões de produto Trilhara (`code` estável e mapa opcional `errors`).
   - Respostas de erro padronizadas com o MIME type `application/problem+json`.
2. **Exceção e Mapper de Conflito (`409 Conflict`):**
   - Criada exceção de negócio `DomainConflictException` (`br.com.trilhara.shared.erro.DomainConflictException`) e seu respectivo `DomainConflictExceptionMapper` (`br.com.trilhara.shared.erro.DomainConflictExceptionMapper`).
3. **Padronização dos Mappers HTTP:**
   - Adicionado `NotFoundExceptionMapper` (`404 NOT_FOUND`).
   - Atualizados `BadRequestExceptionMapper`, `IllegalArgumentExceptionMapper`, `ConstraintViolationExceptionMapper`, `EmailJaCadastradoExceptionMapper`, `ForbiddenExceptionMapper`, `NotAuthorizedExceptionMapper`, `RateLimitExceptionMapper`, `MongoExceptionMapper` e `GlobalThrowableMapper` (com `X-Correlation-ID`, sem expor stack trace nem dados sensíveis).
4. **Transporte de Fuso Horário (`TimezoneContext.java`):**
   - Criado bean `@RequestScoped` `TimezoneContext` (`br.com.trilhara.shared.TimezoneContext`) que extrai o cabeçalho `X-Timezone`, valida com `ZoneId.of(...)` (fallback seguro para `America/Sao_Paulo` quando ausente ou inválido) e expõe `zoneId()` e `hojeLocal()`.
   - Atualizado `SecurityFilter` para popular o contexto e liberar os cabeçalhos `X-Timezone` e `Idempotency-Key` no preflight CORS.

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Padronização de Transporte e Erros em `api.ts`:**
   - Atualizado `frontend/src/shared/auth/api.ts` para enviar automaticamente o cabeçalho `X-Timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo'` em todas as requisições enviadas via `apiFetch` e `request`.
   - `ApiError` estendido para expor: `status: number`, `code?: string`, `detail: string`, `errors?: Record<string, string[]>`, `type?: string`, `title?: string`, `instance?: string`.
2. **Contratos Compartilhados Congelados (`domainContracts.ts`):**
   - Criado `frontend/src/shared/contracts/domainContracts.ts` e `frontend/src/shared/contracts/index.ts` com os tipos base:
     - `ProblemDetailsResponse`
     - `ModoModalSessao`
     - `GlobalSessionModalOpenParams`
     - `RegistrarSessaoPayload`
     - `SessaoRegistradaDTO`
     - `BlocoHojeDTO`
     - `ItemAgendaHojeDTO` (união discriminada `tipo: 'MANUAL' | 'BLOCO_CRONOGRAMA'`)
     - `SyncStatusState`
3. **Infraestrutura de Testes Frontend:**
   - Adicionados `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` e `jsdom`.
   - Configurados `frontend/vite.config.ts`, `frontend/src/test/setup.ts` e scripts `npm test` / `npm run test:watch`.
   - Implementada suíte `frontend/src/test/api.test.ts` validando cabeçalho `X-Timezone`, contratos RFC 9457 e tipagens.

---


### 3. Checklist de Validação da FASE 0

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **0.1** | Respostas de erro no formato RFC 9457 com `application/problem+json` e campos `type`, `title`, `status`, `detail`, `instance`, `code`, `errors` | **Atendido** | Validado em `ProblemDetailsMapperTest` (400, 404, 409 e validação bean) |
| **0.2** | Exceção de negócio `DomainConflictException` (`409 Conflict`) e seu mapper | **Atendido** | Criado e testado em `ProblemDetailsMapperTest#deveMapearConflict409` |
| **0.3** | `TimezoneContext` lendo `X-Timezone`, resolvendo `ZoneId` e `hojeLocal()` com fallback seguro `America/Sao_Paulo` | **Atendido** | Validado em `TimezoneContextTest` com SP, Tóquio, fuso inválido e ausente |
| **0.4** | Liberação de `X-Timezone` e `Idempotency-Key` no CORS do backend sem wildcard de credenciais | **Atendido** | Atualizado em `SecurityFilter.java` com origens explícitas |
| **0.5** | Inclusão automática de `X-Timezone: Intl... \|\| 'America/Sao_Paulo'` em `apiFetch` no frontend | **Atendido** | Validado em `src/test/api.test.ts#deve incluir automaticamente o cabeçalho X-Timezone` |
| **0.6** | `ApiError` expondo `status`, `code`, `detail` e `errors` | **Atendido** | Validado em `src/test/api.test.ts#deve mapear resposta RFC 9457 em ApiError` |
| **0.7** | Congelamento dos contratos base em `frontend/src/shared/contracts/domainContracts.ts` sem implementar componentes visuais antecipadamente | **Atendido** | Tipos `ProblemDetailsResponse`, `ModoModalSessao`, `GlobalSessionModalOpenParams`, `RegistrarSessaoPayload`, `SessaoRegistradaDTO`, `BlocoHojeDTO`, `ItemAgendaHojeDTO` e `SyncStatusState` declarados e validados |
| **0.8** | Configuração de Vitest + React Testing Library + jsdom e scripts de teste no frontend | **Atendido** | `npm test` executa 5 testes com 100% de aprovação |
| **0.9** | Não alteração de repositórios MongoDB de domínio nem quebra de escopo da FASE 0 | **Atendido** | Repositórios de domínio preservados intactos para as fases posteriores |
| **0.10** | Sucesso no comando `mvn test -Dtest=TimezoneContextTest,ProblemDetailsMapperTest` | **Atendido** | 10 testes executados, 0 falhas, 0 erros |
| **0.11** | Sucesso nos comandos `npm test` e `npm run build` no frontend | **Atendido** | Build TypeScript e Vite gerados com sucesso |

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

