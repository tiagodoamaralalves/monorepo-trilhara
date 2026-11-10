# Plano de Implementação por Fases — Reestruturação Trilhara (Guia para Agentes Construtores)

**Documento de requisitos:** [REQUISITOS-REESTRUTURACAO-DADOS.md](./REQUISITOS-REESTRUTURACAO-DADOS.md)
**Registro histórico não normativo:** [COMPLEMENTO-REESTRUTURACAO.md](./COMPLEMENTO-REESTRUTURACAO.md). Em caso de divergência, este plano e `REQUISITOS-REESTRUTURACAO-DADOS.md` são as fontes normativas; prevalece o requisito de domínio sobre o detalhe de implementação.
**Escopo:** Backend Quarkus 3 / Java 25 / MongoDB 7 e Frontend React 18 / TypeScript (módulos Estudos, Tarefas, Notas e Pomodoro)
**Premissa de dados:** Não preservar compatibilidade com modelos, rotas ou dados de teste legados. O banco de desenvolvimento/teste inicia vazio no novo esquema.

---

## Como usar este documento com Agentes Construtores

Este documento descreve o escopo e os critérios de saída de cada fase. Os contratos completos e regras de domínio normativas estão em `REQUISITOS-REESTRUTURACAO-DADOS.md`; uma fase só é autocontida quando lida junto com esse documento e com o estado do repositório. Para executar uma fase em uma nova sessão de agente, use:

> `"Implemente a FASE X conforme especificado no arquivo PLANO-IMPLEMENTACAO-REESTRUTURACAO.md"`

### Regras de ouro para evitar conflito entre agentes em fases sequenciais

1. **Fronteiras de mudança por fase:** cada fase indica os domínios/arquivos principais e exclusões de escopo. Antes de editar, o agente inventaria usos/imports e todos os call sites; mudanças transversais indispensáveis para compilar/testar são permitidas se justificadas e registradas no resumo. Não criar mocks/callbacks temporários que serão descartados por fase posterior.
2. **Contratos antes, componentes junto de quem consome:** A **FASE 0** congela os contratos e tipos compartilhados, mas os componentes visuais compartilhados são implementados **na fase que os consome pela primeira vez**:
   - `AppSwitcher` → implementado na **FASE 1** (junto do SSO e `AppSidebar`);
   - `GlobalSessionModal` → implementado na **FASE 5** (junto do serviço global de sessão e timers);
   - `SectionErrorCard` → implementado na **FASE 7** (junto da Visão Hoje compartilhada e listas virtuais);
   - `SyncStatusBadge` → implementado na **FASE 8** (junto do autosave e rascunho local de Notas).
3. **Dependências oficiais (não presumir conclusão por ordem numérica):**
   - Grafo normativo: `F0 → F1`; `F1 → F2`; `F2 → F3` e `F2 → F4`; `F3 + F4 → F5`; `F1 → F6`; `F4 + F5 + F6 → F7`; `F1 + F2 → F8`; `F2..F8 → F9`; `F6 + F9 → F10`; `F0..F10 → F11`.
   - Execução linear segura, quando não houver branches independentes: `F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10 → F11`.
   - Paralelização permitida após F1: F2 e F6; após F2, F3, F4 e F8 podem avançar em branches separados. F5 depende de F3 e F4; F7 só começa após F4/F5/F6 integradas; F9 só após F2–F8 integradas. Paralelizar apenas em branches/worktrees separados e integrar/testar cada marco antes dos dependentes.
4. **Inventário oficial de coleções (19 no total: 18 privadas + 1 catálogo público):**
   - **Privadas** (17 coleções filhas têm `usuarioId: ObjectId`; em `usuarios`, o `_id` é o ID do usuário):
     1. `usuarios`
     2. `refresh_tokens`
     3. `tokens_recuperacao_senha`
     4. `tokens_confirmacao_email`
     5. `trilhas`
     6. `disciplinas`
     7. `progresso_topicos`
     8. `cronogramas`
     9. `blocos_cronograma`
     10. `sessoes`
     11. `listas_tarefas`
     12. `series_recorrentes`
     13. `tarefas`
     14. `cadernos`
     15. `secoes`
     16. `notas`
     17. `push_subscriptions`
     18. `lembretes_push`
   - **Catálogo público** (gerido por ADMIN): `editais`.

### Grafo de dependências

Fases numeradas não implicam serialidade. Dependências normativas: `F0→F1`; `F1→F2`; `F2→F3` e `F2→F4`; `F3+F4→F5`; `F1→F6`; `F4+F5+F6→F7`; `F1+F2→F8`; `F2..F8→F9`; `F6+F9→F10`; `F0..F10→F11`.

```text
F0 → F1 → F2 ─┬→ F3 ─┐
              ├→ F4 ─┴→ F5 ─┐
       ├→ F6 ─────────────────┼→ F7
       └───────────────→ F8    │
F2..F8 integradas → F9; F6 + F9 → F10; F0..F10 → F11
```

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 0                                 -->
<!-- ===================================================================== -->

## FASE 0 — Contratos de Erro (RFC 9457), Cabeçalho `X-Timezone` e Interfaces Base

### 0.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** Nenhum (primeira fase).
- **Objetivo da fase:** Padronizar o tratamento de erros HTTP do backend no formato **RFC 9457 (`Problem Details`)**, implementar o transporte e resolução do cabeçalho `X-Timezone` (fuso IANA do navegador) entre frontend e backend, e congelar as interfaces TypeScript dos contratos compartilhados em `frontend/src/shared/contracts/`, **sem** implementar ainda os componentes visuais nem refatorar os repositórios de domínio.
- **O que NÃO fazer nesta fase:**
  - Não alterar repositórios MongoDB de Estudos, Tarefas ou Notas.
  - Não implementar ainda os componentes visuais `GlobalSessionModal`, `SectionErrorCard`, `SyncStatusBadge` ou `AppSwitcher` (apenas declarar seus tipos/contratos TypeScript se necessário).

### 0.2 Arquivos a Criar ou Modificar
1. **Backend (`backend/src/main/java/br/com/trilhara/shared/`):**
   - Criar `br/com/trilhara/shared/erro/ProblemDetails.java`: record imutável representando a resposta RFC 9457 com os campos:
     - `String type` (URI semântica do erro, ex.: `"urn:trilhara:error:validation"` ou `"urn:trilhara:error:conflict"`)
     - `String title` (resumo curto legível)
     - `int status` (código HTTP)
     - `String detail` (explicação clara para o usuário)
     - `String instance` (path da requisição)
     - `String code` (extensão Trilhara, código de domínio estável, ex.: `"VALIDATION_ERROR"`, `"IDEMPOTENCY_KEY_CONFLICT"`, `"LATEST_SESSION_ONLY"`, `"DUPLICATE_MODALITY"`, `"SCHEDULE_GENERATION_IN_PROGRESS"`, `"UNAUTHORIZED"`, `"FORBIDDEN"`, `"NOT_FOUND"`)
     - `Map<String, List<String>> errors` (extensão opcional `campo -> mensagens`; RFC 9457 permite extensões; `type`, `title`, `status`, `detail`, `instance` seguem a semântica da RFC)
   - Criar `br/com/trilhara/shared/erro/DomainConflictException.java`: exceção de negócio (`RuntimeException`) que carrega `code` e mensagem e produz HTTP `409 Conflict` via mapper.
   - Atualizar os mappers existentes e criar `DomainConflictExceptionMapper.java`; respostas de erro JSON usarão `application/problem+json` e RFC 9457. Erros inesperados não expõem stack trace, consulta, segredo ou dado pessoal; são registrados com correlation/request ID.
   - Criar `br/com/trilhara/shared/TimezoneContext.java`: bean `@RequestScoped` que lê o cabeçalho HTTP `X-Timezone`, valida via `ZoneId.of(header)` (usando `ZoneId.of("America/Sao_Paulo")` como fallback seguro quando ausente ou inválido) e expõe:
     - `ZoneId zoneId()`
     - `LocalDate hojeLocal()` (`LocalDate.now(zoneId())`)
2. **Frontend (`frontend/src/shared/`):**
   - Atualizar `frontend/src/shared/auth/api.ts`:
     - Em todas as requisições enviadas por `apiFetch`, incluir automaticamente o cabeçalho `X-Timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo'`.
     - Garantir que `ApiError` exponha `status: number`, `code?: string`, `detail: string`, `errors?: Record<string, string[]>`.
     - Atualizar CORS/proxy para permitir `X-Timezone`, `Idempotency-Key`, `Authorization` e `Content-Type` nas origens locais/produtivas explicitamente permitidas; não usar wildcard com credenciais.
   - Criar `frontend/src/shared/contracts/domainContracts.ts`:
     - Declarar os tipos/interfaces compartilhados congelados para uso nas fases seguintes:
       - `ProblemDetailsResponse` (membros RFC `type`, `title`, `status`, `detail`, `instance`; extensões `code`, `errors`)
       - `ModoModalSessao = 'ESTUDOS_TIMER' | 'BLOCO_DIRETO' | 'BLOCO_TIMER' | 'MANUAL'`
       - `GlobalSessionModalOpenParams` com `modo`, `trilhaId?`, `disciplinaId?`, `blocoId?`, `duracaoInicialSegundos?`, `dataSessao?`; `RegistrarSessaoPayload` com `disciplinaId`, `dataSessao`, `duracaoSegundos`, `questoes`, `acertos`, `chaveIdempotencia`; `SessaoRegistradaDTO` com `id`, IDs, duração/questões/acertos, snapshots, timestamps e flags `disciplinaRemovida`/`editavel`.
       - `BlocoHojeDTO` com IDs de usuário omitidos, `blocoId`, `trilhaId`, `cronogramaId`, `disciplinaId`, nomes/cores snapshot, data, ordem, duração e concluído; `ItemAgendaHojeDTO` união discriminada `tipo: 'MANUAL' | 'BLOCO_CRONOGRAMA'` com campos mínimos da tarefa/bloco.
       - `SyncStatusState = 'salvo' | 'salvando' | 'alteracoes-locais' | 'erro'`
   - Frontend: adicionar dependências de desenvolvimento compatíveis `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` e `jsdom`; configurar `vite.config.ts`, setup, scripts `npm test`/`npm run test:watch` e pelo menos um smoke test do runner. Testes de componente/serviço são adicionados nas fases consumidoras.
3. **Testes (`backend/src/test/java/br/com/trilhara/shared/`):**
   - Criar `TimezoneContextTest.java`: testar resolução de `hojeLocal()` com `X-Timezone: America/Sao_Paulo`, `X-Timezone: Asia/Tokyo`, cabeçalho inválido (fallback para `America/Sao_Paulo`) e cabeçalho ausente.
   - Criar `ProblemDetailsMapperTest.java`: testar retorno estruturado `400`, `404` e `409` (`DomainConflictException`).

### 0.3 Critérios de Saída da FASE 0
- `mvn test -Dtest=TimezoneContextTest,ProblemDetailsMapperTest` passa no diretório `backend/`.
- Configurar Vitest + React Testing Library + jsdom e script `npm test` na FASE 0; `npm test` e `npm run build` passam no diretório `frontend/`.
- Todas as requisições de `apiFetch` enviam o cabeçalho `X-Timezone`.

### 0.4 Convenções normativas de API e implementação (aplicam-se a F0–F11)
- Recursos privados exigem autenticação. Toda leitura/escrita de recurso raiz ou filho consulta simultaneamente ID e `usuarioId`; recurso de outro usuário e ID inexistente respondem `404`, sem revelar existência. Ausência de autenticação responde `401`; falha de autorização de função administrativa responde `403`.
- IDs HTTP são strings hexadecimais de 24 caracteres. ID malformado em path/query/body responde `400 INVALID_IDENTIFIER`; ID válido mas inexistente/fora do tenant responde `404 NOT_FOUND`.
- Erros usam RFC 9457 (`application/problem+json`) com `type`, `title`, `status`, `detail`, `instance`; extensões estáveis `code` e `errors` são documentadas pelo produto. `detail` não é contrato de máquina; clientes decidem pelo status e `code`.
- Paginação por offset para este MVP: parâmetros `pagina` (1-based, default 1) e `tamanho` (default definido por endpoint, máximo 100); valor menor que 1 ou maior que o máximo retorna `400 INVALID_PAGINATION`. Resposta: `{ itens, pagina, tamanho, totalItens, totalPaginas }`. Toda listagem tem ordenação estável incluindo `_id` como desempate. Mudança do conjunto durante paginação pode alterar páginas; não se promete snapshot transacional.
- Datas de calendário são strings ISO estritas `yyyy-MM-dd`; instantes são RFC 3339 em API e `Instant`/BSON Date no banco. Duração é inteiro em segundos ou minutos conforme campo explicitamente nomeado. Não inferir fuso a partir do servidor.
- Requisições de escrita idempotentes declaram escopo/chave, hash funcional, status de retry e retenção. Conflito de mesma chave com payload funcional diferente responde `409 IDEMPOTENCY_KEY_CONFLICT`; chave de outro usuário não colide.
- Operações compostas sem transação multi-documento definem estados intermediários, recuperação/retry e efeitos parciais em requisito e teste. Nunca afirmar atomicidade se ela não for oferecida.
- Vitest, React Testing Library e jsdom são as dependências de teste frontend aprovadas para esta reestruturação; fixar versões compatíveis com React 18/Vite 6 no lockfile e documentar os comandos `npm test` e `npm run build`.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 0                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 1                                 -->
<!-- ===================================================================== -->

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

### 1.2 Especificação Técnica Detalhada

#### A. Identidade (`usuarioId` como `ObjectId`) e Utilitários BSON
1. Criar `backend/src/main/java/br/com/trilhara/shared/mongo/BsonIds.java`:
   - `public static ObjectId toObjectId(String hexId, String nomeCampo)`: valida `ObjectId.isValid(hexId)`; se inválido ou nulo, lança `BadRequestException` (`"Identificador inválido para " + nomeCampo`).
   - `public static ObjectId toObjectIdOrNotFound(String hexId)`: se `!ObjectId.isValid(hexId)`, lança `NotFoundException` (`404`).
   - `public static String novoIdHex()`: retorna `new ObjectId().toHexString()`.
2. Criar `backend/src/main/java/br/com/trilhara/shared/mongo/BsonDates.java`:
   - Conversão direta sem JSON intermediário:
     - `Instant` ↔ `java.util.Date` (`BSON Date` nativo no `org.bson.Document`: `Date.from(instant)` na escrita, `doc.getDate(campo).toInstant()` na leitura).
     - `LocalDate` ↔ `String` ISO `yyyy-MM-dd` (`localDate.toString()` na escrita, `LocalDate.parse(doc.getString(campo))` na leitura), jamais convertendo `LocalDate` para `Date` com fuso implícito.
   - Inventariar todos os repositórios existentes que serializam via JSON ou gravam IDs/data como strings. Migrar os acessos tocados pela reestruturação para mapeamento BSON direto e incluir verificação estática/teste que impeça introdução do padrão proibido nos novos repositórios; não declarar removido o legado sem migrar o uso.
3. Atualizar `backend/src/main/java/br/com/trilhara/shared/UsuarioAtual.java`:
   - Remover a constante `USUARIO_LOCAL_PADRAO = "local"`.
   - Em produção/dev normal, se `!authContext.estaAutenticado()`, lançar `NotAuthorizedException("Autenticação obrigatória.")`.
   - Remover `strictMode`/fallback de produção. Para testes, usar autenticação simulada ou helper de teste; o fallback fixo só pode ser habilitado pelo profile `%test`, validado como ObjectId, e nunca aplicado em produção/dev. Fases devem migrar fixtures antes de depender dele; não preservar indefinidamente compatibilidade de testes legados.

#### B. Inicializador Explícito de Índices das 19 Coleções (`MongoIndexInitializer.java`)
3. Criar `backend/src/main/java/br/com/trilhara/shared/mongo/MongoIndexInitializer.java` (`@ApplicationScoped`, observando `@Observes StartupEvent`):
   - Deve criar de forma idempotente (com nomes explícitos em `IndexOptions().name(...)`) os índices das **19 coleções** no startup. Se qualquer criação falhar, log estruturado e readiness não fica pronta. Em %test, usar MongoDB 7 Dev Services. Antes da criação, detectar conflito de key/options com índice existente e falhar com diagnóstico; não apagar índices de forma automática.
   - **Tabela exata de índices a criar:**
     1. `usuarios`: `{ email: 1 }` (`unique: true`); `{ criadoEm: -1 }`
     2. `refresh_tokens`: `{ tokenHash: 1 }` (`unique: true`); `{ usuarioId: 1, familiaId: 1 }`; `{ expiraEm: 1 }`
     3. `tokens_recuperacao_senha`: `{ tokenHash: 1 }` (`unique: true`); `{ usuarioId: 1 }`; `{ expiraEm: 1 }`
     4. `tokens_confirmacao_email`: `{ tokenHash: 1 }` (`unique: true`); `{ usuarioId: 1 }`; `{ expiraEm: 1 }`
     5. `editais`: `{ status: 1, esfera: 1, atualizadoEm: -1 }`
     6. `trilhas`: `{ usuarioId: 1, atualizadoEm: -1 }`; `{ usuarioId: 1, origemEditalId: 1 }`; `{ usuarioId: 1, cronogramaAtivoId: 1 }` se necessário para consulta do ponteiro ativo.
     7. `disciplinas`: `{ usuarioId: 1, trilhaId: 1, ordem: 1 }`
     8. `progresso_topicos`: `{ usuarioId: 1, trilhaId: 1, topicoId: 1 }` (`unique: true`)
     9. `cronogramas`:
        - `{ usuarioId: 1, trilhaId: 1, modalidade: 1 }` (`unique: true`)
        - `{ usuarioId: 1, trilhaId: 1 }` (`unique: true`, `partialFilterExpression: Filters.eq("ativo", true)`)
     10. `blocos_cronograma`:
         - `{ usuarioId: 1, trilhaId: 1, cronogramaId: 1, versaoGeracao: 1, data: 1, ordem: 1 }` (`unique: true`, idempotência de staging e leitura paginada/versionada)
         - `{ usuarioId: 1, data: 1, concluido: 1 }`
     11. `sessoes`:
         - `{ usuarioId: 1, trilhaId: 1, dataSessao: -1, criadoEm: -1, _id: -1 }`
         - `{ usuarioId: 1, trilhaId: 1, disciplinaId: 1, dataSessao: -1 }`
         - `{ usuarioId: 1, chaveIdempotencia: 1 }` (`unique: true`, `partialFilterExpression: Filters.exists("chaveIdempotencia", true)`)
         - Tombstone de exclusão permanece no mesmo documento da sessão por 24h, sem conteúdo pessoal; após prazo é purgado. Índice único cobre chave no tombstone também.
     12. `listas_tarefas`: `{ usuarioId: 1, deletedAt: 1, ordem: 1 }`
     13. `series_recorrentes`: `{ usuarioId: 1, deletedAt: 1, ativa: 1 }`
     14. `tarefas`:
         - `{ usuarioId: 1, deletedAt: 1, listaId: 1, concluida: 1, ordem: 1 }`
         - `{ usuarioId: 1, deletedAt: 1, concluida: 1, dataVencimento: 1 }`
         - `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }` (`unique: true`, `partialFilterExpression: Filters.exists("serieId", true)`)
     15. `cadernos`:
         - `{ usuarioId: 1, deletedAt: 1, atualizadoEm: -1 }`
         - `{ usuarioId: 1, origemTrilhaId: 1 }` (`unique: true`, `partialFilterExpression: Filters.and(Filters.eq("deletedAt", null), Filters.exists("origemTrilhaId", true))`)
     16. `secoes`: `{ usuarioId: 1, cadernoId: 1, deletedAt: 1, ordem: 1 }`; `{ usuarioId: 1, cadernoId: 1, origemDisciplinaId: 1 }`
     17. `notas`:
         - `{ usuarioId: 1, secaoId: 1, deletedAt: 1, ordem: 1 }`
         - `{ usuarioId: 1, cadernoId: 1, deletedAt: 1 }`
         - `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` (`defaultLanguage("portuguese")`, `weights(new Document("titulo", 10).append("conteudoMarkdown", 1))`) — **sem** `partialFilterExpression` (restrição do MongoDB 7).
     18. `push_subscriptions`: `{ usuarioId: 1 }`; `{ endpoint: 1 }` (`unique: true`)
     19. `lembretes_push`: `{ estado: 1, instanteEntregaUtc: 1 }`; `{ usuarioId: 1, tarefaId: 1 }`
2. Remover de `TarefaRepository.java` o método `removerTarefasDeCicloLegadas()` chamado no construtor (`deleteMany` em startup).

#### C. Cookies e SSO: Tratamento Separado para Produção vs `localhost` / `*.localhost`
1. **No Backend (`AuthResource.java` e `application.properties`):**
   - Criar helper privado em `AuthResource` que determina se `cookieDomain` deve ser emitido no `Set-Cookie`:
     - Se `cookieDomain` estiver vazio, em branco, ou igual a `"localhost"` / `".localhost"` (ou se o `Host` da requisição terminar em `localhost` ou `127.0.0.1`), **omitir o atributo `Domain`** do cabeçalho `Set-Cookie` (criando *host-only cookie*, pois navegadores rejeitam `Domain=localhost` segundo RFC 6265 / Public Suffix List).
   - Em **Produção**, só emitir o Domain configurado se o Host validado pertencer ao domínio configurado; nunca confiar em `Host`/`Forwarded` sem proxy confiável. Cookies de autenticação usam `Secure` em HTTPS, `HttpOnly` no refresh, `Path` mínimo, `SameSite` explicitamente configurado e rotação/revogação server-side. `trilhara_session_state` não contém `usuarioId` nem dado pessoal; usar valor opaco de versão/nonce, `Secure` em HTTPS, `SameSite` compatível e `Path=/`. Logout expira ambos com mesmos atributos Domain/Path/SameSite usados na criação.
     - No `logout`, `logout-all`, `alterarSenha` e `resetarSenha`, expirar (`Max-Age=0`) ambos os cookies (`trilhara_rt` e `trilhara_session_state`).
2. **No Frontend (`frontend/src/shared/auth/AuthContext.tsx`, `sso.ts`, `authStorage.ts`):**
   - Implementar `POST /api/auth/handoff` (origem autenticada) que emite código aleatório de uso único persistido apenas como hash, TTL 60s, ligado ao usuário e destino exato de allowlist; `POST /api/auth/handoff/consume` consome atomicamente e emite sessão/access token. Fragmento leva somente código opaco, URL é limpa após consumo e código nunca vai a logs/Referer. Responder 400/401 para inválido/expirado/usado/destino divergente. Produção usa cookie SSO, não handoff.
   - Adicionar listeners `visibilitychange` (quando visible) e window `focus`, throttle de 5s e single-flight de refresh por aba. Em produção, marcador opaco do cookie é apenas sinal; em localhost validar sessão server-side ao foco. `BroadcastChannel`/`storage` funciona somente na mesma origem; não prometer detecção imediata em dispositivos distintos.
   - Em `401` final, limpar sessão e pausar timer em memória; manter rascunhos/timers pausados por usuário conforme política, salvo exclusão de conta.
   - Quando uma chamada autenticada de escrita/leitura falhar com `401` e o refresh também falhar, abrir o `AuthModal` sobre a tela atual (`isAuthModalOpen = true`) **sem recarregar a página e sem desmontar os componentes filhos**, preservando formulários e rascunhos em edição.

#### D. Componente `AppSwitcher` no Design System (`frontend/src/shared/design-system/AppSidebar/AppSidebar.tsx`)
1. Adicionar na `AppSidebar` (desktop e navegação mobile) o seletor/menu rápido **App Switcher** com links para os 4 módulos (**Estudos**, **Tarefas**, **Notas** e **Pomodoro**), destacando o módulo atual e usando `obterUrlAppEstudos()`, `obterUrlAppTarefas()`, `obterUrlAppNotas()` e `obterUrlAppPomodoro()` (com handoff SSO quando autenticado).

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

## FASE 2 — Domínio Estudos (Parte 1): Trilhas, Disciplinas, Tópicos e Progresso Normalizados

### 2.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 e 1 concluídas.
- **Objetivo da fase:** Desmembrar o documento monolítico legado de `Trilha` em três coleções normalizadas (`trilhas`, `disciplinas` e `progresso_topicos`), implementar os endpoints de resumo leve para o Hub, detalhe sob demanda, CRUD granular de disciplinas/tópicos e marcação pontual de progresso de tópicos, e atualizar as views correspondentes em `frontend/src/modules/estudos`.
- **O que NÃO fazer nesta fase:**
  - Não alterar ainda `SessaoResource` / `SessaoService` (FASE 3) nem `PlanejamentoView` / cronogramas (FASE 4).

### 2.2 Esquema BSON e Regras de Domínio
   - **Coleção `trilhas`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `nome`: `String` (1..120 chars, obrigatório)
   - `cargo`: `String` (opcional)
   - `banca`: `String` (opcional)
   - `dataProva`: `String` ISO `yyyy-MM-dd` (`LocalDate` opcional)
   - `cor`: `String` hex (ex.: `"#8F9E8B"`)
   - `origemEditalId`: `ObjectId` (opcional, snapshot sem vínculo vivo)
   - `cronogramaAtivoId`: `ObjectId` opcional (ponteiro de leitura para cronograma ativo, atualizado atomicamente durante publicação)
   - `geracaoCronogramaLeaseToken`: `String` opcional; `geracaoCronogramaEmAndamentoAte`: `BSON Date` / `Instant` (lease com fencing da FASE 4)
   - `criadoEm`, `atualizadoEm`: `BSON Date` / `Instant`
   - **Limite:** máximo de **50 trilhas** por `usuarioId`.
2. **Coleção `disciplinas`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `trilhaId`: `ObjectId`
   - `nome`: `String` (1..120 chars, obrigatório)
   - `cor`: `String` hex
   - `peso`: `int` (`1..5`, default `1`)
   - `ordem`: `int` (`>= 0`)
   - `topicos`: `List<TopicoEmbutido>` onde cada tópico possui `{ id: String (24-char ObjectId hex estável), nome: String (1..300 chars), ordem: int }`.
   - `criadoEm`, `atualizadoEm`: `BSON Date` / `Instant`
   - **Limites:** máximo de **100 disciplinas** por trilha e máximo de **500 tópicos** por disciplina. Renomear disciplina ou tópico **nunca** altera seu `_id` ou `topico.id`. IDs de tópicos removidos nunca são reutilizados.
3. **Coleção `progresso_topicos`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `trilhaId`: `ObjectId`
   - `disciplinaId`: `ObjectId`
   - `topicoId`: `String`
   - `concluidoEm`: `BSON Date` / `Instant`
   - **Regra:** Registro existe somente quando o tópico está concluído. Progresso de uma disciplina = `concluidosDisciplina / totalTopicosDisciplina`. Progresso geral da trilha = `totalTopicosConcluidosTrilha / totalTopicosTrilha` (média ponderada pelo total de tópicos, nunca média simples das porcentagens das disciplinas).

### 2.3 Contratos de Endpoints a Implementar (`br.com.trilhara.trilha`)
1. `GET /api/trilhas`:
   - Retorna lista de `TrilhaResumoDTO`: `{ id, nome, cargo, banca, dataProva, cor, origemEditalId, totalDisciplinas, totalTopicos, topicosConcluidos, percentualProgresso, cronogramaAtivoResumo: null | { cronogramaAtivoId, modalidade, dataInicio, dataFim }, criadoEm, atualizadoEm }`. Não retorna arrays de tópicos; trilhas sem cronograma têm `cronogramaAtivoResumo: null`.
2. `POST /api/trilhas`: body `{ nome, cargo?, banca?, dataProva?, cor, editalId? }`, retorna `201` com `TrilhaDetalheDTO` mínimo e `Location`. Importar edital copia seu snapshot como disciplinas/tópicos iniciais; a API não aceita junto payload de edital e disciplinas iniciais. Sem `editalId`, body pode incluir `disciplinasIniciais` com tópicos. Máximo 50 trilhas; exceder retorna `409 RESOURCE_LIMIT_EXCEEDED`.
3. `GET /api/trilhas/{trilhaId}`:
   - Retorna `TrilhaDetalheDTO` contendo metadados da trilha, lista ordenada de disciplinas com seus tópicos, contadores e `Set<String> topicosConcluidosIds` (extraídos de `progresso_topicos`).
4. `PUT /api/trilhas/{trilhaId}`: atualiza metadados da trilha (`nome`, `cargo`, `banca`, `dataProva`, `cor`).
   - `DELETE /api/trilhas/{trilhaId}`:
     - Adquire lease; remove trilha e dados de Estudos em ordem segura; sob o mesmo fluxo, desvincula caderno (`origemTrilhaId=null`), seções (`origemDisciplinaId=null`) e páginas (`origemTopicoId=null`), preservando todo conteúdo de Notas. Retorna 204 somente após conclusão; repetição em ID já ausente responde 404.
6. Endpoints granulares de disciplinas e tópicos:
   - `POST /api/trilhas/{trilhaId}/disciplinas` body `{ nome, cor, peso?, topicos?: [{ nome, ordem? }] }`; servidor gera IDs e ordem padrão append; retorna `201 DisciplinaDTO`.
   - `PUT /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}` aplica campos enviados; `topicos`, se presente, é a lista completa. Tópicos existentes mantêm `id`; tópico novo omite `id` e recebe ID do servidor; IDs desconhecidos são rejeitados. Tópico existente omitido é removido e seu progresso apagado. Retorna disciplina atualizada.
   - `PUT /api/trilhas/{trilhaId}/disciplinas/reordenar` body `{ disciplinaIds: [id...] }`; lista precisa conter cada disciplina ativa da trilha exatamente uma vez; ordem é atribuída por posição 0..N-1.
   - `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}` (remove a disciplina e seus registros em `progresso_topicos`; **preserva** sessões; remove blocos futuros não concluídos conforme F4; essa operação adquire lease da trilha antes das mudanças em cronogramas/blocos)
7. Endpoints de progresso de tópicos:
     - `PUT /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso` (upsert idempotente em `progresso_topicos`; retry não altera `concluidoEm` existente)
   - `DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso` (delete pontual em `progresso_topicos`)

### 2.4 Arquivos Frontend a Atualizar (`frontend/src/modules/estudos/`)
- Atualizar `EstudosApp.tsx`, `views/HubView.tsx` (se separada ou integrada em `EstudosApp`), `views/DisciplinasView.tsx` e `views/EditalVerticalizadoView.tsx` para consumir `GET /api/trilhas` (resumo no Hub), carregar `GET /api/trilhas/{trilhaId}` ao selecionar uma trilha e usar os endpoints granulares de disciplinas e de progresso (`PUT`/`DELETE .../progresso`).

### 2.5 Critérios de Saída da FASE 2
- Testes de integração em `backend/src/test/java/br/com/trilhara/trilha/` cobrindo:
  - Isolamento por `usuarioId` (usuário A não lê nem altera trilha/disciplina/progresso do usuário B).
  - Listagem do Hub sem transferência de arrays de tópicos.
  - Cálculo de `percentualProgresso` ponderado pelo número total de tópicos.
  - Renomeação de disciplina e tópico preservando IDs e progresso; exclusão de tópico limpando `progresso_topicos`.
- `mvn test` e `npm run build` passando sem erros.
- Testar limite de 50 trilhas, 100 disciplinas e 500 tópicos, payload de criação/importação, IDs desconhecidos e reordenação incompleta.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 2                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 3                                 -->
<!-- ===================================================================== -->

## FASE 3 — Domínio Estudos (Parte 2): Sessões em Segundos, Edição/Exclusão e Estatísticas Agregadas

### 3.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas.
- **Objetivo da fase:** Refatorar completamente o pacote `br.com.trilhara.sessao` para tornar a coleção `sessoes` a fonte única de verdade de tempo (`duracaoSegundos`) e questões (`questoes`, `acertos`), implementar idempotência de criação, edição restrita à sessão mais recente, exclusão individual de sessão com confirmação, preservação de sessões ao excluir disciplina (com grupo de alerta amarelo e exclusão explícita do histórico da disciplina removida) e atualizar o Dashboard de Estudos.
- **O que NÃO fazer nesta fase:**
  - Não criar ainda o `GlobalSessionModal` em `frontend/src/shared` (será feito na FASE 5).

### 3.2 Esquema BSON e Regras de Domínio (`sessoes`)
1. **Campos do documento em `sessoes`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `trilhaId`: `ObjectId`
   - `disciplinaId`: `ObjectId`
   - `trilhaNomeSnapshot`: `String`
   - `disciplinaNomeSnapshot`: `String`
   - `disciplinaCorSnapshot`: `String`
   - `dataSessao`: `String` ISO `yyyy-MM-dd` (`LocalDate`, validado `<= TimezoneContext.hojeLocal()`)
   - `duracaoSegundos`: `int` (obrigatório, `60 <= duracaoSegundos <= 86400`)
   - `questoes`: `int` (`>= 0`, default `0`)
   - `acertos`: `int` (`0 <= acertos <= questoes`, default `0`)
   - `chaveIdempotencia`: `String` (1..128 chars, obrigatória na criação)
   - `requestHash`: `String` (hash SHA-256 dos parâmetros funcionais, imutável)
   - `deletedAt`: `Instant?`; tombstone idempotente mantém somente usuário, chave/hash e instante da exclusão por 24h, sem conteúdo da sessão, para responder retries após exclusão.
   - `criadoEm`, `atualizadoEm`: `BSON Date` / `Instant`
2. **Remoção de legado:**
   - Remover as classes `AjusteQuestoes.java`, `TipoSessao.java` e `TipoRegistroSessao.java`. Não persistir campos `minutos`, `erros` nem `tipo`.
   - `erros = questoes - acertos` e `percentualAcerto = questoes > 0 ? (acertos * 100.0 / questoes) : 0.0` são sempre calculados em leitura.
3. **Ordenação canônica global do histórico e definição de "sessão mais recente":**
   - Ordenação fixa e imutável: `(dataSessao DESC, criadoEm DESC, _id DESC)`.
   - A **sessão mais recente da trilha** é estritamente o primeiro documento retornado por `{ usuarioId, trilhaId }` ordenado por `(dataSessao DESC, criadoEm DESC, _id DESC)`.

### 3.3 Contratos de Endpoints a Implementar (`br.com.trilhara.sessao`)
1. `POST /api/trilhas/{trilhaId}/sessoes`:
   - Exige `Idempotency-Key`; se o body também possuir `chaveIdempotencia`, deve ser idêntico ou responde `400 IDEMPOTENCY_KEY_MISMATCH`. Formato UUID v4 recomendado, tamanho máximo 128, escopo usuário + endpoint. Retry idêntico retorna `200` com sessão atual se existe; tombstone de sessão excluída por até 24h retorna `409 IDEMPOTENCY_RESULT_REMOVED`; payload diferente responde `409 IDEMPOTENCY_KEY_CONFLICT`. Cliente nunca reutiliza chave; tombstone não mantém conteúdo pessoal.
   - Valida que a trilha e a disciplina pertencem ao `usuarioId`, que `duracaoSegundos >= 60`, `acertos <= questoes` e `dataSessao <= hojeLocal`.
   - Se já existir sessão com `{ usuarioId, chaveIdempotencia }`:
     - Se `trilhaId`, `disciplinaId`, `dataSessao`, `duracaoSegundos`, `questoes` e `acertos` forem idênticos, retorna `200 OK` com a sessão existente. Criação inicial retorna `201 Created`.
     - Se qualquer parâmetro funcional divergir, lança `DomainConflictException("IDEMPOTENCY_KEY_CONFLICT", "Chave de idempotência já utilizada com dados diferentes.")` (`409 Conflict`).
2. `PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}`:
   - Busca a primeira sessão de `{ usuarioId, trilhaId }` na ordem `(dataSessao DESC, criadoEm DESC, _id DESC)`.
   - Se `!primeiraSessao.id().equals(sessaoId)`, lança `DomainConflictException("LATEST_SESSION_ONLY", "Apenas a sessão mais recente da trilha pode ser editada.")` (`409 Conflict`).
   - Atualiza no lugar `duracaoSegundos` (`>= 60`), `questoes` (`>= 0`), `acertos` (`<= questoes`) e `atualizadoEm = Instant.now()`.
3. `DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`:
   - Exclui individualmente a sessão `{ _id: sessaoId, usuarioId, trilhaId }`, substituindo conteúdo por tombstone de idempotência por 24h; fora do tombstone não aceita recriação com chave antiga. Retorna `204`.
4. `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`:
   - Exclui todas as sessões de `{ usuarioId, trilhaId, disciplinaId }` mesmo se disciplina já não existir; retorna `{ sessoesRemovidas, totalSegundosRemovidos }`. UI obtém contagem/tempo pela agregação para confirmação.
5. `GET /api/trilhas/{trilhaId}/sessoes?pagina=1&tamanho=20&disciplinaId=...&dataInicio=...&dataFim=...`:
   - Paginação obrigatória (`tamanho` padrão `20`, máximo `100`), ordenada por `(dataSessao DESC, criadoEm DESC, _id DESC)`.
   - Para cada sessão retornada, resolve o nome/cor atual da disciplina se ela ainda existir em `disciplinas`, ou usa `disciplinaNomeSnapshot`/`disciplinaCorSnapshot` com flag `disciplinaRemovida: true` caso a disciplina tenha sido excluída da trilha.
   - Marca `editavel: true` apenas na sessão que corresponde à número 1 global da trilha.
   - `GET /api/trilhas/{trilhaId}/estatisticas?periodo=7d|14d|30d|180d|tudo&dataInicio=...&dataFim=...`:
   - Executa agregação no MongoDB (`aggregate`) filtrando por `{ usuarioId, trilhaId, dataSessao }` e retorna:
     - `totalSegundos`, `totalSessoes`, `totalQuestoes`, `totalAcertos`, `totalErros`, `percentualAcertoGlobal` (ponderado: `totalAcertos / totalQuestoes`);
     - `porDisciplina`: lista com `{ disciplinaId, nome, cor, disciplinaRemovida, totalSegundos, totalSessoes, totalQuestoes, totalAcertos, totalErros, percentualAcerto }`;
     - `serieTemporal`: lista diária `{ data, totalSegundos, totalQuestoes, totalAcertos }`;
     - `streakDias`: calculado em relação a `TimezoneContext.hojeLocal()`.
   - `dataInicio`/`dataFim` personalizados são inclusivos e devem vir ambos; se presente, têm precedência sobre `periodo`. Para `periodo`, limites são `hojeLocal` e os N-1 dias anteriores, inclusivos. `tudo` inclui todas as sessões da trilha. Com zero questões, percentual é 0.

### 3.4 Arquivos Frontend a Atualizar (`frontend/src/modules/estudos/`)
- Atualizar `views/DashboardView.tsx` para:
  - Consumir `GET /api/trilhas/{trilhaId}/estatisticas` e `GET /api/trilhas/{trilhaId}/sessoes` paginado (20 por página).
  - Exibir os tempos a partir de `totalSegundos` / `duracaoSegundos`.
  - Exibir o grupo/alerta amarelo para disciplinas com `disciplinaRemovida == true` e oferecer o botão **"Apagar histórico desta disciplina"** com `ConfirmDialog` mostrando a contagem de sessões e tempo total antes de chamar `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`.
  - Permitir editar apenas a sessão com `editavel == true` e permitir excluir uma sessão individual com `ConfirmDialog`.

### 3.5 Critérios de Saída da FASE 3
- Testes de integração em `backend/src/test/java/br/com/trilhara/sessao/` validando:
  - Gravação em segundos (`duracaoSegundos = 95` soma exatamente `95` segundos).
  - Idempotência (`Idempotency-Key` igual com mesmo payload retorna a mesma sessão; com payload diferente retorna `409`).
  - Edição permitida apenas na sessão número 1 de `(dataSessao DESC, criadoEm DESC, _id DESC)` e `409` nas demais.
  - Exclusão de disciplina na trilha preserva suas sessões e marca `disciplinaRemovida = true`; chamada ao endpoint de limpeza da disciplina removida apaga apenas as sessões daquela disciplina.
- `mvn test` e `npm run build` passando sem erros.
- Testar header ausente, divergência header/body, concorrência da mesma chave, retries depois de editar/excluir, períodos invertidos e estatística vazia.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 3                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 4                                 -->
<!-- ===================================================================== -->

## FASE 4 — Domínio Estudos (Parte 3): Cronogramas, Coordenação de Concorrência/Modalidade, Geradores e APIs de Blocos

### 4.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas. F4 pode ocorrer em paralelo com F3 em branch isolado; F5 aguarda ambas.
- **Objetivo da fase:** Substituir o modelo legado de ciclos (`PlanejamentoCicloRepository`) pelas coleções normalizadas `cronogramas` e `blocos_cronograma`, implementar a coordenação de concorrência e mudança de modalidade, os 3 algoritmos geradores (Sequencial, Ponderado e Manual), a publicação versionada (`versaoGeracao`/`versaoAtiva`), a disponibilidade semanal do usuário, todos os endpoints de blocos de Estudos e a view `PlanejamentoView.tsx`.
- **O que NÃO fazer nesta fase:**
  - Não alterar o módulo Tarefas nem o módulo Pomodoro (eles consumirão os endpoints desta fase na FASE 7).

### 4.2 Esquema BSON, Concorrência e Regras de Geração

#### A. Documento `cronogramas` e `blocos_cronograma`
1. **Coleção `cronogramas`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `trilhaId`: `ObjectId`
   - `modalidade`: `String` (`"SEQUENCIAL"`, `"PONDERADO"`, `"MANUAL"`)
   - `ativo`: `boolean`
   - `versaoAtiva`: `long` (`>= 1`)
   - `ativo` é flag reconciliável; fonte de leitura é o ponteiro `cronogramaAtivoId`/`versaoAtivaCronograma` no documento da trilha.
   - `versaoPreparada`: `long` opcional; `estadoPublicacao`: `ATIVO | PREPARANDO | RECUPERACAO_NECESSARIA`; configuração ativa e preparada persistidas durante publicação.
   - `duracaoBlocoMinutos`: `int` (`30..240`)
   - `tipoPeriodo`: `String` (`"ATE_PROVA"`, `"UMA_SEMANA"`, `"UM_MES"`, `"TRES_MESES"`)
   - `dataInicio`: `String` ISO `yyyy-MM-dd` (`LocalDate`)
   - `dataFim`: `String` ISO `yyyy-MM-dd` (`LocalDate`, inclusiva, `dataFim <= dataInicio.plusDays(365)` — horizonte máximo de **366 dias**)
   - `cronogramaAtivoId`: `ObjectId?`, `versaoAtivaCronograma`: `long?` em `trilhas`; ponteiro atômico usado por leituras multi-documento.
   - `diasAtivosSemana`: `List<Integer>` (`1..7` ISO-8601 Seg..Dom; obrigatório em `SEQUENCIAL`/`PONDERADO`)
   - `blocosPorDia`: `int` (`1..24`, para Sequencial e Ponderado)
   - `disciplinasParticipantesIds`: `List<ObjectId>` (ao menos 1 disciplina obrigatória)
   - `semanaModeloManual`: `Map<Integer, List<String>>` (para modalidade `MANUAL`: chave ISO weekday `1..7` → lista ordenada de 0..24 IDs de disciplina; dia ausente equivale lista vazia; total não excede 24 por data)
   - `criadoEm`, `atualizadoEm`: `BSON Date` / `Instant`
2. **Coleção `blocos_cronograma`:**
   - `_id`: `ObjectId`
   - `usuarioId`: `ObjectId`
   - `trilhaId`: `ObjectId`
   - `cronogramaId`: `ObjectId`
   - `versaoGeracao`: `long`
   - `data`: `String` ISO `yyyy-MM-dd` (`LocalDate`)
   - `ordem`: `int` (`1..24` dentro do dia)
   - `disciplinaId`: `ObjectId`
   - `disciplinaNomeSnapshot`: `String`
   - `disciplinaCorSnapshot`: `String`
   - `duracaoMinutos`: `int` (`30..240`)
   - `concluido`: `boolean` (default `false`)
   - `concluidoEm`: `BSON Date` / `Instant` (opcional)
   - `criadoEm`, `atualizadoEm`: `BSON Date` / `Instant`

#### B. Coordenação de Concorrência (Lock Atômico na Trilha) e Conflito de Modalidade
1. **Lock/Lease atômico na trilha pai (`trilhas`) para impedir duas gerações/ativações concorrentes:**
   - Usar campos `geracaoCronogramaLeaseToken` (`String`) e `geracaoCronogramaEmAndamentoAte` (`Instant`) da trilha: token de fencing UUID, expiração de 2 minutos, renovada a cada 30 segundos. Aquisição atômica filtra trilha/tenant e `{ token ausente OU expiração < agora }`; grava token/expiração. Liberação filtra o token adquirido. Uma trilha inexistente/fora do tenant responde 404; lease ocupado responde 409 `SCHEDULE_GENERATION_IN_PROGRESS`.
   - Toda operação que altera cronogramas/blocos, inclusive criação, edição, ativação, inativação, exclusão de cronograma, exclusão de disciplina e exclusão de trilha, participa do mesmo lock por trilha. Worker que perde lease/token deve interromper e não publicar versão.
2. **Regra para mudança de modalidade na edição pelo lápis (`PUT /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}`):**
   - Cada trilha comporta até 3 cronogramas, sendo no máximo 1 por modalidade (`SEQUENCIAL`, `PONDERADO`, `MANUAL`).
   - Na edição de um cronograma existente, se a nova `modalidade` for diferente da modalidade atual daquele `cronogramaId`, consultar primeiro em `cronogramas` se já existe documento com `{ usuarioId, trilhaId, modalidade: novaModalidade, _id: { $ne: cronogramaId } }`.
   - Se existir, abortar **antes de gerar blocos** lançando `DomainConflictException("DUPLICATE_MODALITY", "Esta trilha já possui um cronograma na modalidade selecionada.")` (`409 Conflict`).
3. **Protocolo de publicação/recuperação versionada dentro do lease:**
   - Não criar coleção de operações. Em `cronogramas`, persistir `versaoAtiva`, `versaoPreparada` (opcional), `estadoPublicacao` (`ATIVO`, `PREPARANDO`, `RECUPERACAO_NECESSARIA`) e `configuracaoAtiva`/`configuracaoPreparada` durante a troca. Novo cronograma começa inativo e só fica visível após publicar geração completa.
   - Construir nova versão determinística com limite de 366 dias/8.784 blocos; inserir em lotes idempotentes e criar índice único `{ usuarioId: 1, cronogramaId: 1, versaoGeracao: 1, data: 1, ordem: 1 }`. Só considerar staging completo após contar/verificar todos os blocos esperados. Leituras ignoram versões não publicadas.
   - Publicação, em ordem: (1) persistir config preparada no alvo sem alterar config/versão visíveis; (2) gerar e verificar todos os blocos da versão preparada; (3) atualizar atomicamente na trilha `cronogramaAtivoId` + `versaoAtivaCronograma` como ponteiro de leitura; (4) reconciliar flags `ativo` no documento alvo e anterior, desativando anterior antes de marcar novo ativo para respeitar índice parcial único; (5) marcar estado `ATIVO`; (6) apagar gerações antigas somente depois da confirmação. Leitores usam ponteiro/versão, nunca o flag sozinho, e veem a geração anterior ou a nova completa.
   - Em falha antes de publicar, remover staging e manter configuração/versão ativa anterior. Em falha depois da troca atômica do ponteiro, manter ponteiro novo e versões completas, marcar `RECUPERACAO_NECESSARIA`, retornar erro com `operationId` e executar reconciliação idempotente no startup/job. Nunca apagar staging/versão necessária à recuperação.
   - A ativação simples troca atomicamente `cronogramaAtivoId`/versão no documento da trilha sob lease, depois reconcilia flags `ativo`; em falha antes da troca, ponteiro anterior permanece. Se alvo for excluído, ponteiro promove válido por `(atualizadoEm DESC, _id DESC)` ou fica `null`. Índice garante no máximo um `ativo:true`.
   - Só limpar versões antigas após confirmar o novo `versaoAtiva` e estado `ATIVO`. Testar injeção de falhas após cada etapa, repetição do reconciliador, lease expirado e worker obsoleto.

#### C. Algoritmos Geradores Puros (`GeradorBlocosCronograma.java`)
1. **Sequencial (`SEQUENCIAL`):** percorre os dias de `dataInicio` a `dataFim` (inclusivos); para cada dia cujo `DayOfWeek` esteja ativo, gera `blocosPorDia` blocos atribuindo as disciplinas participantes em fila circular na ordem de cadastro (`ordem ASC`).
2. **Ponderado (`PONDERADO`):** seja $B_{\text{total}}$ o total de blocos no período e $P = \sum p_k$ a soma dos pesos ($p_k \in \{1..5\}$) das disciplinas participantes. Para cada posição global $t \in \{1..B_{\text{total}}\}$, escolhe a disciplina $i$ que maximiza $\Delta_i(t) = \frac{t \cdot p_i}{P} - \text{alocados}_i(t-1)$, desempatando pela menor `ordem` de cadastro da disciplina na trilha.
3. **Manual (`MANUAL`):** para cada dia entre `dataInicio` e `dataFim`, lê a lista de disciplinas configurada em `semanaModeloManual` para aquele `DayOfWeek` e cria um bloco por posição (`ordem = 1..N`).

#### D. Efeitos de Alteração/Exclusão de Disciplina e Exclusão/Inativação de Cronograma
1. **Renomear disciplina ou mudar cor:** atualiza `disciplinaNomeSnapshot` e `disciplinaCorSnapshot` nos blocos de `blocos_cronograma` da trilha. Alterar `peso` da disciplina **não** regenera cronogramas existentes.
2. **Excluir disciplina da trilha:** remove de `blocos_cronograma` apenas os blocos daquela disciplina com `data >= hojeLocal` e `concluido == false`; blocos com `data < hojeLocal` ou `concluido == true` permanecem como histórico visual.
3. **Excluir ou inativar o cronograma ativo:** remove seus blocos de `blocos_cronograma` (se excluído) e ativa automaticamente o cronograma restante da mesma trilha com maior `atualizadoEm`; se não restar outro cronograma, a trilha fica sem cronograma ativo.

### 4.3 Contratos de Endpoints a Implementar
1. **Disponibilidade semanal do usuário:**
   - `GET /api/usuarios/me/disponibilidade-semanal` retorna `{ disponibilidadeSemanalMinutos: [7 ints], cargaAtivaMinutosPorDia: [7 ints], sobrecargaPorDia: [7 booleans] }`; default de disponibilidade `[0,0,0,0,0,0,0]`. Carga é computada somente de blocos futuros em cronogramas ativos.
   - `PUT /api/usuarios/me/disponibilidade-semanal` body `{ disponibilidadeSemanalMinutos: [7 ints 0..1440] }`, retorna o mesmo DTO após persistir em `usuarios.preferencias`.
2. **Cronogramas da trilha:**
   - Rotas de criação/edição recebem campos definidos no esquema do cronograma e respondem `CronogramaDTO { id, trilhaId, modalidade, ativo, versaoAtiva, configuracao, quantidadeBlocos, cargaMinutosPorDia, estadoPublicacao }`.
   - `GET /api/trilhas/{trilhaId}/cronogramas` (lista até 3 com ordenação modalidade/id), `POST` cria + gera + ativa, `PUT /{cronogramaId}` altera + regenera + ativa, `PATCH /{cronogramaId}/ativar` ativa sob lease e `DELETE /{cronogramaId}` exclui sob lease; exclusão do ativo troca ponteiro atomicamente para restante válido por `(atualizadoEm DESC, _id DESC)` ou `null`, depois reconcilia flags.
3. **Blocos de cronograma (fonte única para Estudos, Tarefas e Pomodoro):**
   - `GET /api/blocos/hoje`: retorna somente blocos pendentes da data `hojeLocal`, da `versaoAtiva` do cronograma ativo em cada trilha, ordenados por `trilhaNome ASC, ordem ASC, _id ASC`; DTO tem campos listados nos requisitos §9.
   - `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos?concluido=...&dataInicio=...&dataFim=...&pagina=1&tamanho=50`: filtros opcionais; default lista pendentes e intervalo integral do cronograma, ordenação `data ASC, ordem ASC, _id ASC`; retorna `{ itens, pagina, tamanho, totalItens, totalPaginas }`, cada item com `statusData`.
   - Bloco persistido contém `concluido` boolean. `PATCH` para `true` define `concluidoEm=agora`; para `false` define `concluidoEm=null`; repetição no mesmo estado não altera `atualizadoEm` nem `concluidoEm`.
   - `PATCH /api/blocos/{blocoId}/conclusao` (body `{ concluido: boolean }`): operação idempotente validando `usuarioId`; atualiza `concluido` e `concluidoEm`.
   - `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados` body `{ operationId: UUID }`; retry com mesmo `operationId` retorna resultado original/atual, nunca duplica efeitos. Processa item a item e retorna `{ operationId, totalProcessados, totalConcluidos, falhas: [{ blocoId, code }] }`; não executa duas operações do mesmo usuário/trilha/cronograma em paralelo.

### 4.4 Arquivos Frontend a Atualizar (`frontend/src/modules/estudos/`)
- Remover `PlanejamentoCicloRepository.java` no backend e atualizar `views/PlanejamentoView.tsx` no frontend para usar a terminologia **Cronograma**, suportar até 3 cronogramas por trilha (desabilitando modalidades já usadas), botão final **Criar cronograma**, período padrão "Até a data da prova" (quando houver prova futura) ou "1 mês (30 dias)", aviso não bloqueante de sobrecarga frente ao perfil semanal e visualização paginada dos blocos gerados.

### 4.5 Critérios de Saída da FASE 4
- Testes unitários e de integração cobrindo:
  - Determinismo dos geradores `SEQUENCIAL`, `PONDERADO` e `MANUAL` e limite de 366 dias / 24 blocos por dia.
  - Concorrência: duas chamadas simultâneas de geração na mesma trilha resultam em `200/201` para uma e `409 SCHEDULE_GENERATION_IN_PROGRESS` para a outra, mantendo exatamente 1 cronograma ativo.
  - Mudança de modalidade na edição para uma modalidade já existente na trilha retorna `409 DUPLICATE_MODALITY` sem alterar os blocos atuais.
  - Endpoints `GET /api/blocos/hoje`, paginação, conclusão individual e conclusão em lote de atrasados.
- `mvn test` e `npm run build` passando sem erros.
- Testar falha injetada em cada passo de staging, promoção de fallback, lease expirado, índice único e retry de lote parcial.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 4                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 5                                 -->
<!-- ===================================================================== -->

## FASE 5 — Frontend Shared & Timers: `GlobalSessionModal`, Serviço Único de Sessão e Timers Isolados por Conta

### 5.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1, 2, 3 e 4 concluídas.
- **Objetivo da fase:** Implementar em `frontend/src/shared/` o serviço único `sessaoGlobalService` e o componente compartilhado `GlobalSessionModal` (reutilizado por Estudos, Tarefas e Pomodoro), conectar a aba **Estudar** e a aba **Planejamento** de Estudos a esse modal, e refatorar os timers de Estudos e do Pomodoro para persistirem em `localStorage` sob chaves escopadas por `usuarioId` (`trilhara:u:{usuarioId}:*`) com sincronização entre abas.
- **O que NÃO fazer nesta fase:**
  - Não alterar ainda as listas de Tarefas (`TarefasApp.tsx`) nem a lista de tarefas dentro do Pomodoro (isso será feito na FASE 7). Na FASE 5, em `PomodoroApp.tsx`, ajuste apenas a persistência/sincronização do cronômetro Pomodoro em si.

### 5.2 Especificação Técnica Detalhada

#### A. Serviço `sessaoGlobalService.ts` (`frontend/src/shared/services/sessaoGlobalService.ts`)
1. Responsável por centralizar toda criação de sessão (`POST /api/trilhas/{trilhaId}/sessoes`) e conclusão de bloco (`PATCH /api/blocos/{blocoId}/conclusao`).
2. Recebe a `chaveIdempotencia` (UUID v4 gerado quando o `GlobalSessionModal` é aberto) e envia no cabeçalho `Idempotency-Key` e no body.
3. **Fluxo em duas etapas quando houver `blocoId` vinculado:**
   - **Etapa 1:** Se a sessão ainda não foi salva nesta abertura do modal, chama `POST /api/trilhas/{trilhaId}/sessoes`. Ao receber sucesso, marca `sessaoSalva = true` no estado do modal e persiste o `sessaoId`/estado de retry em memória da operação enquanto o modal estiver aberto. Se o usuário recarregar/fechar a aba entre etapas, a sessão permanece salva e o bloco pendente; ao voltar, não há retry automático e o usuário pode concluir o bloco pela lista sem criar outra sessão.
   - Etapa 2: Chama `PATCH /api/blocos/{blocoId}/conclusao` com `{ concluido: true }`.
   - **Tratamento de falha parcial:** Se a Etapa 1 suceder e a Etapa 2 falhar, o modal permanece aberto exibindo o aviso claro: *"A sessão de estudo foi salva com sucesso, mas não foi possível marcar o bloco como concluído."* O botão principal passa a ser **"Tentar concluir bloco novamente"**, que executa exclusivamente a Etapa 2 (sem reenviar a Etapa 1).

#### B. Componente `GlobalSessionModal` (`frontend/src/shared/design-system/GlobalSessionModal/`)
1. Exportar `GlobalSessionModal` e `GlobalSessionModalProvider` / hook `useGlobalSessionModal()` em `frontend/src/shared/design-system/index.ts`.
2. Suportar os 4 modos de abertura:
   - `ESTUDOS_TIMER`: aberto apenas ao clicar em **Encerrar e salvar** no cronômetro de Estudos; duração inicial = tempo real focado em segundos (`duracaoSegundos`), editável em horas/minutos; `dataSessao = hojeLocal` (somente leitura).
   - `BLOCO_DIRETO`: aberto ao marcar um bloco de cronograma como concluído; duração inicial = `duracaoMinutos * 60` segundos, editável; `dataSessao = hojeLocal`. Exibe botão secundário **"Concluir sem salvar sessão"** (que chama apenas `PATCH /api/blocos/{blocoId}/conclusao`).
   - `BLOCO_TIMER`: aberto ao clicar em **Encerrar e salvar** quando o cronômetro de Estudos foi iniciado a partir de um bloco (**Estudar este bloco**); duração inicial = foco real acumulado em segundos no cronômetro; ao salvar, executa as 2 etapas (salva sessão + conclui `blocoId`).
   - `MANUAL`: aberto pelo botão **Registrar sessão manual**; permite selecionar trilha/disciplina (ou criar trilha e disciplina inline se o usuário ainda não tiver), editar duração (horas/minutos) e selecionar `dataSessao` (`<= hojeLocal`).
3. Validações no modal:
   - Duração mínima de 60 segundos (`duracaoSegundos >= 60`).
   - Campos opcionais `questoes` (`>= 0`) e `acertos` (`0 <= acertos <= questoes`), exibindo em tempo real os valores derivados de **Erros** (`questoes - acertos`) e **Aproveitamento (%)**.
   - Fechar/Cancelar/`Escape` (desabilitado enquanto `isSaving == true`) não grava sessão e mantém eventual bloco como pendente.
   - Acessibilidade WCAG 2.2 AA: *focus trap* completo, foco inicial no primeiro campo editável e devolução do foco ao botão acionador ao fechar.

#### C. Timers de Estudos e Pomodoro Isolados por Usuário e Sincronizados entre Abas
1. **Cronômetro de Estudos (`frontend/src/modules/estudos/context/StudyTimerContext.tsx` e `views/EstudarView.tsx`):**
   - Armazenar estado completo em `localStorage` na chave `trilhara:u:${usuarioId}:study_timer` contendo `{ trilhaId, disciplinaId, blocoId?, mode, targetSeconds, accumulatedElapsedMs, runningSince: number | null, updatedAt }`.
   - Calcular o tempo decorrido por diferença de timestamp absoluto (`accumulatedElapsedMs + (runningSince ? Date.now() - runningSince : 0)`).
   - **Regra do alarme:** Quando o cronômetro regressivo chega a zero (ou atinge a meta), tocar o alerta sonoro/visual, mas **nunca abrir o modal nem salvar sessão automaticamente**. O modal só abre quando o usuário clica em **Encerrar e salvar**.
   - **Máximo 1 timer ativo por usuário/dispositivo na mesma origem:** Sincronizar via evento `storage` e `BroadcastChannel('trilhara_study_timer')`. Iniciar outro timer (em outra disciplina, trilha ou aba da mesma origem) pausa o anterior (pedindo confirmação se houver tempo acumulado `>= 60s` de outro contexto). Entre subdomínios/portas, armazenamento do browser é isolado; não prometer exclusão mútua instantânea. O servidor não sincroniza timer entre dispositivos.
   - **Logout / Troca de conta:** Ao ouvir `trilhara:auth:logout` ou mudança de `usuarioId`, se `runningSince != null`, acumular o tempo até aquele instante (`accumulatedElapsedMs += Date.now() - runningSince; runningSince = null`), gravar na chave `trilhara:u:${usuarioId}:study_timer` e limpar o estado em memória. Ao fazer login novamente na mesma conta, restaurar o timer pausado.
2. **Cronômetro do Pomodoro (`frontend/src/modules/pomodoro/PomodoroApp.tsx`):**
   - Armazenar estado e configurações em `trilhara:u:${usuarioId || 'anon'}:pomodoro_timer` usando timestamp absoluto e sincronizando entre abas da mesma conta. O término de um ciclo Pomodoro nunca abre modal de sessão nem cria sessão em Estudos.

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

## FASE 6 — Domínio Tarefas: Tarefas Manuais, Séries Recorrentes (Janela 90d) e Lixeira (30d)

### 6.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 e 1 concluídas. Pode ser implementada em branch independente de F2–F5; integração final precede F7.
- **Objetivo da fase:** Refatorar o pacote `br.com.trilhara.tarefa` para operar exclusivamente com listas manuais (`listas_tarefas`), séries recorrentes (`series_recorrentes`) e tarefas/ocorrências manuais (`tarefas`), implementar a recorrência por calendário fixo com janela móvel de 90 dias e `dataOriginalOcorrencia` imutável, implementar a lixeira de 30 dias com `loteExclusaoId` e atualizar o CRUD de tarefas manuais, recorrência e lixeira em `frontend/src/modules/tarefas`.
- **O que NÃO fazer nesta fase:**
  - Não implementar ainda a agregação com blocos de Estudos (`AgendaHojeService` e listas virtuais de trilha) nem tocar em `PomodoroApp.tsx` — isso pertence integralmente à **FASE 7**.

### 6.2 Esquema BSON e Regras de Domínio (`br.com.trilhara.tarefa`)

Todos os endpoints da FASE 6 usam `usuarioId` do token, escopo de tenant em cada filtro, DTOs HTTP e os status de API comuns da F0; `GET/POST/PUT/PATCH/DELETE` seguem métodos/retornos definidos em §6.2.C. Estados `PENDENTE`, `CONCLUIDA`, `CANCELADA` são fonte canônica; `concluida` é derivado/compatível e atualiza junto ao estado.

#### A. Coleções `listas_tarefas`, `series_recorrentes` e `tarefas`
1. **Coleção `listas_tarefas`:**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `nome`: `String` (1..100 chars), `cor`: `String` opcional, `ordem`: `int`, `deletedAt`: `Instant` opcional, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.
2. **Coleção `series_recorrentes`:**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `listaId`: `ObjectId` opcional
   - `titulo`: `String`, `descricao`: `String` opcional, `prioridade`: `String` (`"BAIXA"`, `"MEDIA"`, `"ALTA"`), `horario`: `String` (`HH:mm` opcional), `subtarefasModelo`: `List<Subtarefa>`
   - `frequencia`: `String` (`"DIARIO"`, `"SEMANAL"`, `"MENSAL"`)
   - `intervalo`: `int` (`>= 1`, a cada $N$ dias/semanas/meses)
   - `diasDaSemana`: `List<Integer>` (`1..7` Seg..Dom, quando `SEMANAL`)
   - `modoMensal`: `String` (`"DIA_DO_MES"` ou `"ORDINAL_SEMANA"`, quando `MENSAL`), `diaDoMes`: `int` (`1..31`), `ordinalSemana`: `int` (`1..4` ou `-1` para última), `diaSemanaOrdinal`: `int` (`1..7`)
   - `tipoTermino`: `String` (`"NUNCA"`, `"EM_DATA"`, `"APOS_OCORRENCIAS"`), `dataInicio`: `LocalDate` (`yyyy-MM-dd`), `dataFim`: `LocalDate` opcional, `maxOcorrencias`: `Integer` opcional
   - `geradoAte`: `LocalDate` (`yyyy-MM-dd`, último dia de regra processado), `totalGeradas`: `int` (número de posições da recorrência até cursor/término), `ativa`: `boolean`
   - `lembreteMinutosAntes`: `Integer` opcional, `usaPadraoGlobalLembrete`: `boolean`, `fusoAgendamento`: `String` opcional
   - `deletedAt`, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.
3. **Coleção `tarefas`:**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `listaId`: `ObjectId` opcional
   - `serieId`: `ObjectId` opcional
   - `dataOriginalOcorrencia`: `String` ISO `yyyy-MM-dd` (`LocalDate`, **imutável** após geração da ocorrência; garante idempotência pelo índice único parcial `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }`)
   - `desvinculadaDaSerie`: `boolean` (default `false`)
   - `titulo`: `String` (1..200 chars), `descricao`: `String` opcional
   - `prioridade`: `String` (`"BAIXA"`, `"MEDIA"`, `"ALTA"`)
   - `dataVencimento`: `String` ISO `yyyy-MM-dd` (`LocalDate` opcional)
   - `horario`: `String` (`HH:mm` opcional)
   - `estado`: `String` (`"PENDENTE"`, `"CONCLUIDA"`, `"CANCELADA"`)
   - `concluida`: `boolean` (`estado == "CONCLUIDA"`), `concluidaEm`: `Instant` opcional
   - `ordem`: `int`
   - `subtarefas`: `List<Subtarefa>` (`{ id: String, titulo: String, concluida: boolean, ordem: int }`) — marcar todas as subtarefas **não** conclui automaticamente a tarefa pai.
   - `lembreteMinutosAntes`: `Integer` opcional, `usaPadraoGlobalLembrete`: `boolean`, `fusoAgendamento`: `String` opcional
   - `deletedAt`, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.

#### B. Regras de Recorrência (Janela Móvel de 90 Dias) e Escopos de Edição/Exclusão
1. **Materialização sob demanda:** Ao listar tarefas do usuário (ou ao criar/editar uma série), expandir todas as séries ativas (`ativa == true && deletedAt == null`) até `alvo = max(hojeLocal.plusDays(90), fimIntervaloConsultado)` (limitado a `hojeLocal.plusDays(366)`). No modo mensal por dia do mês (`29`, `30`, `31`), usar `YearMonth.of(ano, mes).atEndOfMonth()` quando o dia não existir no mês.
   - Âncora é `dataInicio`, inclusiva. DIARIO: ocorrência em `dataInicio + k*intervalo dias`. SEMANAL: semanas ISO são blocos segunda–domingo contados a partir da semana de `dataInicio`; gerar apenas os dias selecionados em semanas cujo índice seja múltiplo de `intervalo`, não gerar antes de `dataInicio`. MENSAL: avançar meses de calendário em `intervalo`; dia do mês ausente vira último dia daquele mês; ordinal semanal calcula a ocorrência indicada, com `-1` para última.
   - `APOS_OCORRENCIAS` conta todas as datas programadas distintas desde `dataInicio`, incluindo ocorrências concluídas, canceladas, em lixeira ou desvinculadas; reagendar uma ocorrência não altera a contagem da posição programada. `dataFim` é inclusiva; parâmetros incompatíveis entre `tipoTermino` e campos de término são rejeitados com 400.
   - A materialização usa cursor exclusivo `geradoAte` como data de calendário expandida e operação idempotente por `(usuarioId, serieId, dataOriginalOcorrencia)`. Consultas podem ampliar a janela até 366 dias; nunca materializar fora do término da série. `dataVencimento` pode divergir da data original somente por reagendamento individual.
2. **Idempotência:** Como o índice único parcial usa `dataOriginalOcorrencia`, se o usuário reagendar a `dataVencimento` de uma ocorrência individual, `dataOriginalOcorrencia` não muda e a janela móvel jamais recria aquela ocorrência.
3. **Concluir e Pular ocorrência:** Concluir muda `estado = "CONCLUIDA"` (`concluida = true`) apenas daquela ocorrência (sem criar próxima ocorrência nem abrir modal de sessão). Pular muda `estado = "CANCELADA"` (oculta das listas ativas, mas preservada no banco para bloquear regeneração).
4. **Três escopos para editar ou excluir tarefa recorrente (`escopo = ESTA | ESTA_E_FUTURAS | TODA_A_SERIE`):**
   - `ESTA`: edição aplica apenas campos editáveis à ocorrência e define `desvinculadaDaSerie = true`; exclusão move apenas a ocorrência para lixeira e a série não a recria por causa do índice/chave de data original.
   - `ESTA_E_FUTURAS`: sob lock lógico da série, corta a original em `alvo.minusDays(1)` e inativa-a se o corte preceder `dataInicio`; remove definitivamente somente ocorrências pendentes não concluídas com data original `>= alvo`. Ocorrências concluídas permanecem no histórico e mantêm a série de origem; ocorrência alvo não concluída também é substituída. Para edição, cria nova série com configuração solicitada, `dataInicio = alvo`, reinicia contagem de `APOS_OCORRENCIAS` a partir do alvo e materializa janela. Operação é idempotente por `operationId` enviado pelo cliente; retry não cria série duplicada.
   - `TODA_A_SERIE`: marca a regra em `series_recorrentes` com `ativa = false`, `deletedAt = agora`, `expiresAt = agora + 30 dias` e `loteExclusaoId`, move todas as ocorrências `PENDENTE` (atrasadas, de hoje e futuras) para a lixeira com o mesmo `loteExclusaoId` e **preserva ocorrências já concluídas** (`estado == "CONCLUIDA"`). Restaurar uma ocorrência individual da lixeira não reativa a série encerrada.

#### C. Regras de Lixeira (30 Dias) e Restauração
1. Excluir lista manual exibe confirmação informando a quantidade de tarefas ativas na lista e move a lista e suas tarefas ativas para a lixeira com o mesmo `loteExclusaoId`, `deletedAt = agora` e `expiresAt = agora.plus(30, ChronoUnit.DAYS)`.
2. Restaurar lista restaura o container da lista e apenas as tarefas que compartilharam o mesmo `loteExclusaoId`.
3. Restaurar uma tarefa individual cuja `listaId` original esteja na lixeira restaura automaticamente o container da lista pai; se a lista pai já tiver sido purgada definitivamente, restaura a tarefa com `listaId = null` (Lista Geral).
4. Criar scheduler diário (`PurgaLixeiraTarefasJob`) que remove definitivamente documentos de `tarefas`, `series_recorrentes` e `listas_tarefas` com `deletedAt != null && expiresAt <= Instant.now()`, além de filtrar `expiresAt > agora` nas listagens da lixeira.
5. Definição de ativos: listagens operacionais filtram `deletedAt == null` e estados elegíveis; concluídas nunca entram em purga automática. Excluir lista move tarefas ativas, incluindo ocorrências pendentes, mas não move séries automaticamente: série mantém `listaId` original, fica suspensa enquanto a lista estiver na lixeira e volta a materializar na lista ao restaurá-la; purgar lista converte `listaId` das séries sobreviventes para `null` (Lista Geral).
6. Rotas obrigatórias: `GET/POST /api/listas-tarefas`, `GET/PUT/DELETE /api/listas-tarefas/{listaId}`, `POST /api/listas-tarefas/{listaId}/restaurar`; `GET/POST /api/tarefas`, `GET/PUT/DELETE /api/tarefas/{tarefaId}`, `PATCH /api/tarefas/{tarefaId}/conclusao`, `PATCH /api/tarefas/{tarefaId}/subtarefas/{subtarefaId}`, `POST /api/tarefas/{tarefaId}/pular`; `GET/POST /api/series-recorrentes`, `GET/PUT/DELETE /api/series-recorrentes/{serieId}` e operações de edição/exclusão de ocorrência exigem `escopo`; `GET /api/tarefas/hoje`, `GET /api/tarefas/lixeira`, `POST /api/tarefas/lixeira/{tarefaId}/restaurar`, `DELETE /api/tarefas/lixeira/esvaziar`. Responses usam DTOs, nunca documentos Mongo.

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

### 7.2 Especificação Técnica Detalhada

#### A. Componente `SectionErrorCard` (`frontend/src/shared/design-system/SectionErrorCard/`)
1. Card compacto de erro recuperável com ícone de alerta, mensagem clara da falha da seção e botão **"Tentar novamente"** (`onRetry`), exportado em `frontend/src/shared/design-system/index.ts`.

#### B. Serviço Compartilhado `agendaHojeService.ts` (`frontend/src/shared/services/agendaHojeService.ts`)
1. Executa em paralelo (`Promise.allSettled`):
   - `GET /api/tarefas/hoje` (retorna tarefas manuais pendentes atrasadas, vencendo em `hojeLocal` e sem data);
   - `GET /api/blocos/hoje` (retorna blocos pendentes de `hojeLocal` de todos os cronogramas ativos do usuário).
2. Organiza o retorno nas 3 seções determinísticas:
   - **Seção 1 — Atrasadas:** apenas tarefas manuais pendentes com `dataVencimento < hojeLocal`, ordenadas por `dataVencimento ASC, prioridade DESC, ordem ASC`. Quando houver múltiplas ocorrências atrasadas da mesma `serieId`, agrupa as anteriores sob a mais recente exibindo um botão expansível `"+ N ocorrências anteriores atrasadas"`.
   - **Seção 2 — Hoje:** subdividir visualmente em blocos pendentes de `hojeLocal` (ordenados por `trilhaNome ASC, ordem ASC`, identificados com pílula da trilha e cor da disciplina) e tarefas manuais pendentes com `dataVencimento == hojeLocal` (ordenadas por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`). Não intercalar fontes numa ordem global artificial.
   - **Seção 3 — Sem data:** apenas tarefas manuais pendentes com `dataVencimento == null`, ordenadas por `prioridade DESC, ordem ASC`.
3. **Resiliência a falha parcial:** Se a API de Estudos falhar mas a de Tarefas responder (ou vice-versa), retorna os dados da fonte disponível e preenche `erroEstudos` ou `erroTarefas` para que a UI exiba o `SectionErrorCard` apenas na seção indisponível (nunca exibindo lista vazia silenciosa).
4. **Atualização reativa:** Atualiza após mutações locais, ao receber mensagens em `BroadcastChannel('trilhara_agenda_hoje')` e nos eventos `visibilitychange` / `focus` da aba (com *throttle* mínimo de 5 segundos).

#### C. Listas Virtuais por Trilha em Tarefas (`frontend/src/modules/tarefas/TarefasApp.tsx`)
1. Na sidebar de Tarefas, listar na seção **"Trilhas de Estudo"** as trilhas do usuário que possuem cronograma ativo (`GET /api/trilhas`).
2. Ao clicar em uma trilha na sidebar de Tarefas, renderizar a **Lista Virtual da Trilha** consultando `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaAtivoId}/blocos?concluido=false&pagina=1&tamanho=50`:
   - Exibir apenas blocos pendentes (`concluido == false`) ordenados por `data ASC, ordem ASC`, com carregamento incremental ("Carregar mais") e badges `Atrasado`, `Hoje` e `Futuro`.
   - Não permitir criar, editar texto ou excluir blocos como se fossem tarefas.
   - Ao clicar em concluir um bloco, abrir o `GlobalSessionModal` (`modo = 'BLOCO_DIRETO'`).
   - Quando houver blocos com `statusData == 'ATRASADO'`, exibir no topo da lista virtual o botão **"Concluir todas as atrasadas sem salvar (N)"**: ao clicar, abrir `ConfirmDialog` informando a quantidade exata `N` e, ao confirmar, chamar `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaAtivoId}/blocos/concluir-atrasados`, exibindo resultado parcial e botão de retry caso algum item falhe.
3. Garantir que os filtros gerais **Futuro**, **Todas** e **Concluídas** de Tarefas exibam **apenas tarefas manuais** (nunca blocos de cronograma).

#### D. Espelho da Visão Hoje no Pomodoro (`frontend/src/modules/pomodoro/PomodoroApp.tsx`)
1. Para usuários autenticados, renderizar ao lado do cronômetro Pomodoro a mesma Visão Hoje alimentada por `agendaHojeService`:
   - Selecionar uma tarefa ou bloco apenas destaca o item como contexto visual de foco (não vincula sessão ao timer).
   - Concluir uma tarefa manual chama o endpoint de conclusão de Tarefas, remove o item da lista de Hoje e **não pausa o timer nem abre modal de sessão**.
   - Concluir um bloco de estudo abre o `GlobalSessionModal` (`modo = 'BLOCO_DIRETO'`).
2. Para visitantes não autenticados, manter o cronômetro Pomodoro funcional e exibir no painel lateral um estado convidativo para entrar na conta.

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

## FASE 8 — Domínio Notas: Coleções Separadas, `SyncStatusBadge`, Busca PT-BR, Importação/Sync de Trilha, Autosave e Lixeira

### 8.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas. Pode ser implementada antes ou em paralelo a F6/F7 em branch isolado; F9 aguarda integração.
- **Objetivo da fase:**
  1. Implementar o componente `SyncStatusBadge` no Design System (`frontend/src/shared/design-system`).
  2. Refatorar o pacote `br.com.trilhara.nota` para usar três coleções separadas (`cadernos`, `secoes`, `notas`), projeção sem `conteudoMarkdown` nas listagens, busca global em português no backend com geração de snippets, importação/reimportação/sincronização explícita com trilhas de Estudos e lixeira hierárquica de 30 dias.
  3. Atualizar `frontend/src/modules/notas/` com carregamento sob demanda do Markdown, autosave com rascunho de contingência em `localStorage`, busca global paginada e lixeira hierárquica.

### 8.2 Especificação Técnica Detalhada

#### A. Componente `SyncStatusBadge` (`frontend/src/shared/design-system/SyncStatusBadge/`)
1. Indicador visual compacto com `role="status"` e `aria-live="polite"` para os estados:
   - `salvo` ("Salvo")
   - `salvando` ("Salvando...")
   - `alteracoes-locais` ("Alterações locais pendentes")
   - `erro` ("Erro ao salvar" + botão inline **"Tentar novamente"**)

#### B. Esquema BSON e Regras de Domínio (`cadernos`, `secoes`, `notas`)
1. **Coleção `cadernos`:**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `titulo`: `String` (1..120 chars), `cor`: `String`, `icone`: `String` opcional
   - `origemTrilhaId`: `ObjectId` opcional (referência fraca; índice único parcial `{ usuarioId: 1, origemTrilhaId: 1 }` quando `deletedAt == null` e `origemTrilhaId` existe)
   - `deletedAt`, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.
2. **Coleção `secoes`:**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `cadernoId`: `ObjectId`, `titulo`: `String` (1..120 chars), `ordem`: `int`
   - `origemDisciplinaId`: `ObjectId` opcional (referência fraca)
   - `deletedAt`, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.
   - **Regra:** Um caderno pode ficar com **zero seções ativas** (removendo a trava legada que proibia apagar a última seção).
3. **Coleção `notas` (páginas):**
   - `_id`: `ObjectId`, `usuarioId`: `ObjectId`, `cadernoId`: `ObjectId`, `secaoId`: `ObjectId`
   - `titulo`: `String` (1..200 chars), `conteudoMarkdown`: `String` (máx. `512 KB` = `524288` bytes validados no backend), `ordem`: `int`
   - `origemTopicoId`: `String` opcional (referência fraca informativa)
   - `deletedAt`, `expiresAt`: `Instant` opcional, `loteExclusaoId`: `String` opcional, `criadoEm`, `atualizadoEm`: `Instant`.

#### C. Contratos de Endpoints a Implementar (`br.com.trilhara.nota`)
1. **Listagens leves e leitura individual de página:**
   - `GET /api/cadernos`: lista cadernos ativos (`deletedAt == null`) do usuário com contagem de seções/páginas e flag `vinculadoATrilha: origemTrilhaId != null`.
   - `GET /api/cadernos/{cadernoId}/secoes`: lista seções ativas do caderno ordenadas por `ordem ASC`.
   - `GET /api/cadernos/{cadernoId}/secoes/{secaoId}/notas?pagina=1&tamanho=50`: lista páginas ativas da seção projetando **apenas metadados** (`Projections.exclude("conteudoMarkdown")`).
   - `GET /api/notas/{notaId}`: retorna a página completa incluindo `conteudoMarkdown`.
   - `PUT /api/notas/{notaId}` body `{ titulo?, conteudoMarkdown? }`; pelo menos um campo deve estar presente; valida máximo UTF-8 de 524288 bytes; atualiza `atualizadoEm` e retorna DTO completo. Sem optimistic concurrency: última gravação vence.
   - Criação/edição: `POST /api/cadernos` body `{ titulo, cor, icone? }`; `PUT /api/cadernos/{id}` atualiza campos enviados; `POST /api/cadernos/{id}/secoes` body `{ titulo }`; `PUT/DELETE /api/secoes/{id}`; `POST /api/cadernos/{cadernoId}/secoes/{secaoId}/notas` body `{ titulo, conteudoMarkdown? }`. Criações retornam `201` e `Location`; atualizações `200`.
2. **Importação, Reimportação e Sincronização Explícita com Estudos:**
   - `POST /api/cadernos/importar-trilha/{trilhaId}`:
     - Se já existir um caderno ativo do usuário com `origemTrilhaId == trilhaId` (reimportação), primeiro limpa `origemTrilhaId = null` no caderno antigo e `origemDisciplinaId = null` nas seções do caderno antigo, **preservando integralmente** o caderno antigo e todo o seu conteúdo.
     - Em seguida, cria um novo caderno (`titulo = trilha.nome()`, `origemTrilhaId = trilhaId`), cria uma seção para cada disciplina da trilha (`origemDisciplinaId = disciplina.id()`) e cria uma página vazia (`conteudoMarkdown = ""`) para cada tópico da disciplina (`titulo = topico.nome()`, `origemTopicoId = topico.id()`).
   - `POST /api/cadernos/{cadernoId}/sincronizar-trilha` (sincronização acionada explicitamente pelo usuário; retorna contagem de seções/páginas criadas e desvinculadas):
       1. Atualiza o título do caderno com o nome atual da trilha.
       2. Para seções com `origemDisciplinaId` que ainda existem na trilha: atualiza o `titulo` da seção com o nome atual da disciplina.
       3. Para disciplinas novas da trilha que ainda não possuem seção com aquele `origemDisciplinaId` no caderno: cria novas seções (com páginas vazias para seus tópicos).
       4. Para seções cujo `origemDisciplinaId` foi removido da trilha: limpa `origemDisciplinaId = null` e **preserva a seção e todas as suas páginas**.
       5. Seções criadas manualmente pelo usuário (`origemDisciplinaId == null`) permanecem intocadas. Não compara tópicos nem altera o conteúdo de páginas existentes.
   - Ao excluir uma trilha, no mesmo fluxo coordenado pela F2, limpar `origemTrilhaId` do caderno, `origemDisciplinaId` das seções e `origemTopicoId` das páginas correspondentes, preservando caderno, seções, títulos e conteúdo.
3. **Busca Global no Backend (`GET /api/notas/busca?q=...&pagina=1&tamanho=20`):**
   - A busca MVP é por tokens/palavras inteiras, case-insensitive e diacritic-insensitive conforme semântica do índice de texto; não promete substring/infix, fuzzy search nem igualdade perfeita de stemming. Query com menos de 2 caracteres alfanuméricos, vazia após tokenização ou composta só por stop words retorna 400 `INVALID_SEARCH_QUERY` com orientação ao usuário; não executar fallback regex sobre a coleção inteira.
   - Executa query sempre escopada por `{ usuarioId, deletedAt: null }` usando o índice de texto MongoDB 7 (`default_language: "portuguese"`). Ordem: relevância decrescente, `atualizadoEm DESC`, `_id DESC`. Paginação offset até tamanho 100; contagem corresponde aos resultados da query.
   - Projeta metadados (`notaId`, `titulo`, `cadernoId`, `cadernoTitulo`, `secaoId`, `secaoTitulo`, `atualizadoEm`) e extrai no backend um `snippet` de até 180 caracteres ao redor do primeiro token encontrado; nunca retorna `conteudoMarkdown` completo na lista. Snippet é texto não confiável e deve ser escapado no frontend.
4. **Lixeira Hierárquica de 30 Dias:**
   - `DELETE /api/cadernos/{cadernoId}`, `DELETE /api/secoes/{secaoId}` e `DELETE /api/notas/{notaId}` movem o item e seus descendentes ativos para a lixeira com o mesmo `loteExclusaoId`, `deletedAt = agora` e `expiresAt = agora + 30 dias` (endpoints de contagem prévia ou resposta de confirmação informam quantas seções/páginas serão afetadas).
   - `GET /api/notas/lixeira?pagina=1&tamanho=50` lista páginas/seções/cadernos removidos ainda dentro do prazo, com tipo e breadcrumbs; `POST /api/notas/lixeira/{tipo}/{id}/restaurar`:
     - Restaurar caderno ou seção restaura somente descendentes que compartilham o `loteExclusaoId` da exclusão pai; conteúdo já na lixeira antes dessa exclusão permanece na lixeira. Descendentes são restaurados com `deletedAt = null`, expirations/lote removidos.
     - Restaurar página ou seção cujo ancestral esteja na lixeira restaura automaticamente ancestrais ainda existentes e dentro do prazo, mas não irmãos. Se ancestral já foi purgado, criar/reutilizar caderno e seção ativos chamados `Restauradas` (sufixos numéricos em conflito) e mover a página para eles; retornar aviso explícito. Restauração fora do prazo responde 404 `TRASH_ITEM_EXPIRED`.
   - `DELETE /api/notas/lixeira/esvaziar` apaga definitivamente todos os itens em lixeira do usuário e retorna contagens; job diário `PurgaLixeiraNotasJob` apaga vencidos em lotes idempotentes.
   - Rotas de criação/edição: `POST /api/cadernos`, `PUT /api/cadernos/{id}`, `POST /api/cadernos/{id}/secoes`, `PUT/DELETE /api/secoes/{id}`, `POST /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`, `PUT /api/notas/{id}`. Validar tenancy e estado ativo de cada nível da hierarquia.

#### D. Autosave e Rascunho de Contingência no Frontend (`frontend/src/modules/notas/`)
1. No editor Tiptap (`NotasApp.tsx` / `TiptapEditor.tsx`), usar debounce de `800ms` e exibir o `SyncStatusBadge`.
2. Antes da confirmação da API (ou se `PUT /api/notas/{notaId}` falhar), salvar rascunho em `localStorage` na chave `trilhara:u:${usuarioId}:nota_draft:${notaId}` com `{ titulo, conteudoMarkdown, atualizadoEmLocal, baseAtualizadoEmServidor }`. Ao confirmar `200 OK`, remover a chave do rascunho.
3. Ao abrir uma página que possua rascunho local com `atualizadoEmLocal` posterior ao `atualizadoEm` do servidor, exibir barra no topo do editor com as ações **"Restaurar rascunho não salvo"** ou **"Descartar rascunho"**.
4. Ao abrir Notas, consultar `GET /api/trilhas` em background: se existir trilha ainda não vinculada a nenhum caderno ativo do usuário e `localStorage.getItem('trilhara:u:' + usuarioId + ':notas_hide_auto_import_modal') !== 'true'`, abrir o modal sugerindo importar a trilha (com checkbox *"Não mostrar automaticamente"*). Manter sempre visível na sidebar a ação **"Importar trilha"**.

### 8.3 Critérios de Saída da FASE 8
- Testes de integração em `backend/src/test/java/br/com/trilhara/nota/` validando:
  - Listagem de páginas e busca global não retornam `conteudoMarkdown` completo.
  - Busca em português encontra palavras com e sem acento (`"Constituição"` / `"constituicao"`), ignora itens na lixeira e isola `usuarioId`.
  - Importação, reimportação (desvinculando o anterior sem apagar), sincronização explícita e desvinculação ao excluir trilha.
  - Lixeira hierárquica com `loteExclusaoId` e restauração de página com ancestral na lixeira.
- `mvn test` e `npm run build` passando sem erros.
- Testes frontend com Vitest cobrem autosave, recuperação de rascunho, retry, badge e não exposição de Markdown completo em resultados/listagens.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 8                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 9                                 -->
<!-- ===================================================================== -->

## FASE 9 — Exclusão de Conta nas 18 Coleções Privadas e Isolamento Ponta a Ponta

### 9.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 2 a 8 concluídas e integradas, além da infraestrutura F0–F1.
- **Objetivo da fase:** Implementar a exclusão completa, idempotente e auditável da conta do usuário (`DELETE /api/auth/conta`) percorrendo nominalmente todas as **18 coleções privadas** do sistema (das 19 coleções totais, apenas `editais` não é privada de usuário), revogando tokens e limpando o estado local no navegador.

### 9.2 Especificação Técnica Detalhada
1. **Inventário executável das 18 coleções privadas (`AccountDeletionService.java` em `br.com.trilhara.auth`):**
   - `DELETE /api/auth/conta` exige senha atual/reautenticação recente, `Idempotency-Key` e confirmação de intenção no frontend. Responde `202 Accepted { operationId, estado: "PENDENTE" }` depois de persistir atomicamente `usuarios.exclusaoConta { operationId, estado, solicitadaEm }`, revogar credenciais e agendar limpeza. A confirmação textual sozinha nunca autentica. Frontend limpa dados locais imediatamente; cliente não precisa manter sessão para completar a exclusão.
   - Worker interno retenta idempotentemente a limpeza por coleção; `usuarios` é removida por último. O estado da operação fica no documento raiz `usuarios` até purge completo, sem nova coleção. Ao falhar, persiste coleção/etapa e retenta com backoff; logs contêm apenas operationId não correlacionável a PII e nome da coleção.
   - Para as **17 coleções filhas privadas**, remover por proprietário tipado `usuarioId: ObjectId` (sem fallback string; documentos históricos em string só são tratados por ferramenta de limpeza específica se detectados):
        - `refresh_tokens`
        - `tokens_recuperacao_senha`
        - `tokens_confirmacao_email`
        - `lembretes_push`
        - `push_subscriptions`
        - `blocos_cronograma`
        - `cronogramas`
        - `progresso_topicos`
        - `disciplinas`
        - `sessoes`
        - `trilhas`
        - `tarefas`
        - `series_recorrentes`
        - `listas_tarefas`
        - `notas`
        - `secoes`
        - `cadernos`
     - Por último, remover documento raiz na **18ª coleção privada**:
        - `usuarios` (`{ _id: alvoObjectId }`).
   - Quando todas as coleções filhas tiverem zero documentos, remover raiz `usuarios`, invalidar cookies com atributos originais e registrar `event="ACCOUNT_DELETED"`, `collectionsCleaned=18`, sem PII. Falha parcial não é reportada como concluída.
2. **Limpeza no Frontend (`AuthStorage` e `AuthContext`):**
   - Criar helper `limparDadosLocaisDoUsuario(usuarioId: string)` que remove todas as chaves `trilhara:u:${usuarioId}:*` na origem atual e chaves de sessão. Por requisito de privacidade, conta excluída remove também timers/rascunhos locais dessa conta; outras origens/dispositivos não são acessíveis por este helper. Notificar abas da mesma origem por `BroadcastChannel`.
   - Adicionar na tela/modal de Conta do usuário a opção **"Excluir minha conta"** com `ConfirmDialog` destrutivo detalhando que todos os estudos, cronogramas, sessões, tarefas, notas e lixeiras serão permanentemente apagados.

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

## FASE 10 — Entrega Isolada e Tardia: Notificações Web Push de Tarefas

### 10.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 6 e 9 concluídas e integradas, além da infraestrutura F0–F1.
- **Objetivo da fase:** Implementar as notificações Web Push para tarefas manuais com horário explícito de forma **100% isolada e não bloqueante** (se o navegador não suportar Push, se a permissão for negada ou se as chaves VAPID não estiverem configuradas no ambiente, o módulo Tarefas continua funcionando integralmente sem erros).

### 10.2 Especificação Técnica Detalhada
1. **Infraestrutura Web / PWA (`Caddyfile` e `frontend/public/`):**
   - Criar `frontend/public/manifest.webmanifest` com `"display": "standalone"`, `"name": "Trilhara"`, `"short_name": "Trilhara"`, `"start_url": "/"` (requisito obrigatório do iOS/iPadOS Safari 16.4+ para habilitar `PushManager`) e referenciá-lo no `frontend/index.html`.
   - Criar `frontend/public/sw.js` para escutar os eventos `push` (exibindo `self.registration.showNotification(...)`) e `notificationclick` (focando aba aberta ou abrindo a URL da tarefa `/tarefas?tarefaId=...` **sem** concluir a tarefa).
   - No `Caddyfile` (bloco `(spa_frontend)`), adicionar regra explícita para `/sw.js` e `/manifest.webmanifest`:
     - `/sw.js` deve sempre retornar `header Cache-Control "no-cache, no-store, must-revalidate"`.
2. **Backend (`br.com.trilhara.push`):**
   - Configurar `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` e `VAPID_SUBJECT` via MicroProfile Config (com degradação graciosa `pushHabilitado = false` se não configurados em dev).
   - Endpoints em `PushResource.java` (`/api/push`):
     - `GET /api/push/config`: retorna `{ habilitado: boolean, publicKey?: string, padraoMinutosAntes: number }`.
     - `POST /api/push/subscriptions`: registra/atualiza inscrição do dispositivo em `push_subscriptions` (`usuarioId`, `endpoint` único, `p256dh`, `auth`, `userAgent`, `criadoEm`).
     - `DELETE /api/push/subscriptions`: remove inscrição do dispositivo atual.
     - `PUT /api/push/preferencias`: atualiza o padrão global do usuário (default `15` min antes) em `usuarios.preferencias` e recalcula os registros `PENDENTE` em `lembretes_push` das tarefas que usam `usaPadraoGlobalLembrete == true`.
   - **Ciclo de vida em `lembretes_push`:**
     - Apenas tarefas manuais com `dataVencimento != null && horario != null` geram lembrete; blocos de cronograma nunca geram push.
     - Opções por tarefa: `SEM_ALERTA`, `NO_HORARIO` (`0`), `5`, `15`, `30` ou `60` minutos antes.
     - Ao salvar a tarefa com horário, capturar o fuso IANA (`TimezoneContext.zoneId()`) e calcular `instanteEntregaUtc = ZonedDateTime.of(dataVencimento, horario, zoneId).minusMinutes(minutosAntes).toInstant()`.
     - Horário em gap DST responde `400 INVALID_LOCAL_DATETIME`; overlap DST escolhe offset anterior/horário de verão deterministicamente, incluindo instante calculado na resposta.
     - Concluir ou mover a tarefa para a lixeira cancela o registro em `lembretes_push`; remover o horário suspende o lembrete preservando a preferência escolhida; voltar a definir horário reagenda.
   - **Scheduler de envio (`PushDeliveryScheduler.java` a cada 30s) com janela útil de 15 minutos:**
     - Lembrete tem estados `PENDENTE`, `PROCESSANDO`, `ENVIADO`, `CANCELADO`, `EXPIRADO_FORA_DA_JANELA`, `FALHA_FINAL`, versão de agendamento e tentativas. Claim atômico aplica lease; somente worker detentor pode concluir.
     - Se além de `instanteEntregaUtc + 15min`, marca `EXPIRADO_FORA_DA_JANELA` sem enviar. Falhas temporárias usam backoff dentro da janela; timeout externo pode resultar em entrega ambígua, portanto não se promete exactly-once. Payload tem `lembreteId` para dedupe no service worker.
     - Dentro da janela, enviar com `TTL: 900` para inscrições ativas e marcar estado/resultado por inscrição. `410 Gone`/`404 Not Found` remove imediatamente inscrição inválida.
3. **Frontend (`frontend/src/modules/tarefas/`):**
   - Exibir seletor de lembrete nas tarefas com horário e configuração da preferência global.
   - Se o navegador não suportar Web Push ou a permissão estiver `denied`, exibir badge informativo discreto sem bloquear nenhuma operação da tarefa. Em iOS Safari fora do modo `standalone`, exibir dica orientando *"Adicionar à Tela de Início"* quando o usuário clicar para ativar notificações.

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

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 11.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 a 10 concluídas.
- **Objetivo da fase:** Auditar segurança por proprietário (`usuarioId`), acessibilidade WCAG 2.2 AA nos quatro módulos, executar benchmark sintético verificando `IXSCAN` com `explain("executionStats")` no MongoDB 7 e remover qualquer classe, rota ou arquivo morto remanescente.

### 11.2 Passos Obrigatórios
1. **Auditoria de Autorização por Proprietário (`AuthorizationByOwnerTest.java`):**
   - Verificar que todas as rotas de leitura, escrita e exclusão de `trilhas`, `disciplinas`, `progresso_topicos`, `cronogramas`, `blocos_cronograma`, `sessoes`, `listas_tarefas`, `series_recorrentes`, `tarefas`, `cadernos`, `secoes`, `notas` e `push` rejeitam acesso cruzado entre `usuarioA` e `usuarioB` com `404` (sem vazar existência).
2. **Auditoria e Ajustes Finais de Acessibilidade (WCAG 2.2 Level AA):**
   - Verificar *focus trap*, fechamento com `Escape` (exceto quando `isSaving == true`) e restauração de foco em `Modal`, `FormModal`, `ConfirmDialog` e `GlobalSessionModal`.
   - Verificar `role="timer"` com `aria-live="off"` nos dígitos dos cronômetros (Estudos e Pomodoro) e `aria-live="polite"` para transições de fase e `SyncStatusBadge`.
   - Verificar área mínima de toque/clique (`24x24px` desktop e `40x40px` em `@media (pointer: coarse)`), `scroll-padding-bottom` para listas com barra inferior mobile fixa e suporte a `@media (prefers-reduced-motion: reduce)`.
3. **Teste de Performance e Plano de Índices (`MongoExplainBenchmarkTest.java`, perfil opt-in):**
   - Inserir carga sintética representativa (2.190 sessões/ano, trilha com 20 disciplinas e 2.000 tópicos, cronograma de 366 dias com 24 blocos/dia = 8.784 blocos, caderno com 300 páginas de Markdown e séries recorrentes).
   - Executar `explain("executionStats")` nas consultas críticas com massa representativa e comparar `totalDocsExamined`, `totalKeysExamined`, cardinalidade e latência com baseline. Confirmar uso do índice esperado quando seletividade justificar; não impor `IXSCAN` em consultas que legitimamente leem grande parte da coleção. Para `$text`, verificar estágio/plano de índice de texto suportado pela versão (não nomear `TEXT_MATCH` como stage universal). Benchmark não roda na suíte unitária padrão.
4. **Remoção de Código Legado e Documentação:**
   - Garantir por busca e build que nenhuma classe, rota ou componente legado explicitamente substituído permaneça ativo; arquivos históricos só podem permanecer se sem referências e fora do build.
   - Atualizar a documentação de execução e variáveis de ambiente (`.env.example` / `README.md`).

### 11.3 Critérios de Saída da FASE 11 (Encerramento da Reestruturação)
- Suíte completa do backend (`mvn test`), build frontend (`npm run build`) e testes frontend (`npm test`) verdes. Benchmark opt-in executado em ambiente MongoDB 7 especificado e relatório arquivado.
- Todos os critérios de aceite da Seção 16 de [REQUISITOS-REESTRUTURACAO-DADOS.md](./REQUISITOS-REESTRUTURACAO-DADOS.md) comprovados por testes automatizados.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 11                                               -->
<!-- ===================================================================== -->
