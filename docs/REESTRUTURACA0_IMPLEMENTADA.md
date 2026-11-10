# Reestruturação Implementada — Trilhara

Documento de acompanhamento da execução das fases de reestruturação do Trilhara conforme os requisitos normativos de `REQUISITOS-REESTRUTURACAO-DADOS.md` e o roteiro de execução em `PLANO-IMPLEMENTACAO-REESTRUTURACAO.md`.

---

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

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/shared/erro/ProblemDetails.java
  - backend/src/main/java/br/com/trilhara/shared/erro/DomainConflictException.java
  - backend/src/main/java/br/com/trilhara/shared/erro/DomainConflictExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/NotFoundExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/TimezoneContext.java
  - backend/src/test/java/br/com/trilhara/shared/TimezoneContextTest.java
  - backend/src/test/java/br/com/trilhara/shared/ProblemDetailsMapperTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/auth/SecurityFilter.java
  - backend/src/main/java/br/com/trilhara/shared/erro/BadRequestExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/ConstraintViolationExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/EmailJaCadastradoExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/ForbiddenExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/GlobalThrowableMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/IllegalArgumentExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/MongoExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/NotAuthorizedExceptionMapper.java
  - backend/src/main/java/br/com/trilhara/shared/erro/RateLimitExceptionMapper.java

Frontend (Criados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/shared/contracts/index.ts
  - frontend/src/test/setup.ts
  - frontend/src/test/api.test.ts

Frontend (Modificados):
  - frontend/src/shared/auth/api.ts
  - frontend/vite.config.ts
  - frontend/package.json
  - frontend/package-lock.json
```

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

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/shared/mongo/BsonIds.java
  - backend/src/main/java/br/com/trilhara/shared/mongo/BsonDates.java
  - backend/src/main/java/br/com/trilhara/shared/mongo/MongoIndexInitializer.java
  - backend/src/main/java/br/com/trilhara/shared/mongo/MongoIndexHealthCheck.java
  - backend/src/main/java/br/com/trilhara/auth/HandoffService.java
  - backend/src/main/java/br/com/trilhara/auth/dto/HandoffRequest.java
  - backend/src/main/java/br/com/trilhara/auth/dto/HandoffResponse.java
  - backend/src/main/java/br/com/trilhara/auth/dto/ConsumeHandoffRequest.java
  - backend/src/test/java/br/com/trilhara/shared/mongo/BsonRoundTripTest.java
  - backend/src/test/java/br/com/trilhara/shared/mongo/MongoIndexInitializerTest.java
  - backend/src/test/java/br/com/trilhara/auth/AuthCookieDomainTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/shared/UsuarioAtual.java
  - backend/src/main/java/br/com/trilhara/auth/AuthResource.java
  - backend/src/main/java/br/com/trilhara/auth/RefreshTokenService.java
  - backend/src/main/java/br/com/trilhara/auth/SecurityFilter.java
  - backend/src/main/java/br/com/trilhara/tarefas/repository/TarefaRepository.java
  - backend/src/main/java/br/com/trilhara/tarefas/repository/ListaTarefasRepository.java
  - backend/src/main/java/br/com/trilhara/auth/UserRepository.java
  - backend/src/main/java/br/com/trilhara/auth/RefreshTokenRepository.java
  - backend/src/main/java/br/com/trilhara/auth/TokenConfirmacaoEmailRepository.java
  - backend/src/main/java/br/com/trilhara/auth/TokenRecuperacaoRepository.java
  - backend/src/main/java/br/com/trilhara/estudos/repository/SessaoRepository.java
  - backend/src/main/java/br/com/trilhara/estudos/repository/TrilhaRepository.java
  - backend/src/main/java/br/com/trilhara/estudos/repository/EditalRepository.java
  - backend/src/main/java/br/com/trilhara/estudos/repository/PlanejamentoCicloRepository.java
  - backend/src/main/java/br/com/trilhara/notas/repository/CadernoRepository.java
  - backend/src/main/java/br/com/trilhara/notas/repository/NotaRepository.java
  - backend/src/main/resources/application.properties

Frontend (Criados):
  - frontend/src/shared/auth/sso.ts
  - frontend/src/shared/design-system/AppSwitcher/AppSwitcher.tsx
  - frontend/src/shared/design-system/AppSwitcher/AppSwitcher.module.css
  - frontend/src/test/appSwitcher.test.tsx
  - frontend/src/test/sso.test.ts
  - frontend/src/test/authContextModal.test.tsx

Frontend (Modificados):
  - frontend/src/shared/auth/api.ts
  - frontend/src/shared/auth/AuthContext.tsx
  - frontend/src/shared/design-system/index.ts
  - frontend/src/shared/design-system/AppSidebar/AppSidebar.tsx
  - frontend/src/shared/design-system/AppSidebar/AppSidebar.module.css
```

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

## FASE 2 — Trilha Normalizada, Disciplinas e Progresso de Tópicos Granulares

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25 / MongoDB Reactive Streams)
1. **Desmembramento do Monólito BSON em Coleções Normalizadas:**
   - Desmembrado o documento monolítico legado de `Trilha` em 3 coleções BSON independentes:
     - `trilhas`: metadados da trilha, dono/usuário, datas, cargo/banca, cronograma ativo (sem árvores aninhadas de tópicos).
     - `disciplinas`: documentos de primeiro nível vinculados a `trilhaId` e `usuarioId`, com array embutido de tópicos (`TopicoEmbutido`), peso (1-5), ordem sequencial e link de caderno.
     - `progresso_topicos`: registros granulares de tópicos concluídos (`usuarioId`, `trilhaId`, `topicoId`, `concluidoEm`).
2. **Entidades e Repositórios BSON Nativos:**
   - Criados `Disciplina.java` e `TopicoEmbutido.java` com anotações de validação (`@NotBlank`, `@Size`, `@Min`, `@Max`).
   - Criado `ProgressoTopico.java` para rastreamento idempotente de conclusão.
   - Criado `DisciplinaRepository.java`: busca ordenada por `ordem ASC`, inserção, atualização atômica, exclusão e reordenação em lote.
   - Criado `ProgressoTopicoRepository.java`: upsert atômico e idempotente via `$setOnInsert` para marcação, deleção atômica para desmarcação e agregação em lote para contagens por disciplina e trilha.
   - Refatorado `TrilhaRepository.java`: migrado para leitura e escrita BSON direta, desacoplado de conversões destrutivas Jackson/JSON.
3. **Endpoints de Hub e Detalhe sob Demanda:**
   - `GET /api/trilhas`: Endpoint leve para o Hub. Retorna `TrilhaResumoDTO[]` com contadores calculados (`totalDisciplinas`, `totalTopicos`, `topicosConcluidos`, `percentualProgresso`, `cronogramaAtivoResumo`), sem carregar arrays de tópicos ou cronogramas.
   - `GET /api/trilhas/{id}`: Detalhe sob demanda. Retorna `TrilhaDetalheDTO` contendo metadados, lista de disciplinas ordenadas com tópicos limpos (sem status de conclusão acoplado) e array `topicosConcluidosIds: string[]`.
4. **CRUD Granular de Disciplinas e Reordenação Estrita:**
   - `POST /api/trilhas/{trilhaId}/disciplinas`: Adiciona nova disciplina (retorna 201 com `DisciplinaDTO`).
   - `PUT /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}`: Atualiza disciplina, peso, cor, caderno e tópicos (retorna 200).
   - `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}`: Exclui disciplina e remove os registros órfãos de progresso de tópicos associados (retorna 204).
   - `PUT /api/trilhas/{trilhaId}/disciplinas/reordenar`: Reordena disciplinas com validação estrita (conjunto de IDs idêntico ao existente, sem repetições nem omissões; retorna 400 sob inconsistência).
5. **Marcação e Desmarcação Idempotente de Progresso:**
   - `PUT /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso`: Marca tópico como concluído (idempotente; retorna 200 com `ProgressoTopicoDTO`).
   - `DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso`: Desmarca conclusão do tópico (idempotente; retorna 204).
   - Mantido `PATCH /api/trilhas/{id}/progresso/{topicoId}` delegando para o fluxo normalizado para total retrocompatibilidade.
6. **Limites de Negócio Estritos e Integridade:**
   - Validados limites estritos: máximo de 50 trilhas por usuário, 100 disciplinas por trilha e 500 tópicos por disciplina (retornando Problem Details RFC 9457 com status 400/422).
   - Preservação da integridade histórica de sessões de estudo ao excluir disciplinas ou tópicos (§6.2).
7. **Suíte de Testes de Integração Backend:**
   - Implementada a suíte `TrilhaNormalizadaIntegrationTest.java` com 9 cenários completos cobrindo isolamento multitenant de `usuarioId`, contratos leves do Hub, detalhe sob demanda, idempotência de progresso, renomeação segura de IDs, limpeza de progresso em cascata na exclusão, rejeição de tópicos desconhecidos, reordenação estrita e limites de negócio.

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Contratos Compartilhados da FASE 2 (`domainContracts.ts` e `types/trilhara.ts`):**
   - Declarados `TrilhaResumoDTO`, `TrilhaDetalheDTO`, `DisciplinaDTO`, `TopicoDTO`, `ProgressoTopicoDTO`, `CriarDisciplinaRequest`, `AtualizarDisciplinaRequest` e `ReordenarDisciplinasRequest`.
   - Compatibilizada a interface `Trilha` para suportar tanto os contadores de resumo do Hub quanto a representação detalhada completa.
2. **Serviços de Estudos Granulares (`estudosService.ts`):**
   - Implementados métodos granulares: `criarDisciplina`, `atualizarDisciplina`, `excluirDisciplina`, `reordenarDisciplinas`, `marcarTopicoConcluido` e `desmarcarTopicoConcluido`.
   - Adicionados adaptadores bidirecionais `adaptarTrilhaResumo` e `adaptarTrilhaDetalhe`, montando transparentemente o mapa de progresso reativo a partir de `topicosConcluidosIds`.
3. **Otimização do Hub (`HubView.tsx`):**
   - Eliminados flatMaps custosos na renderização da listagem de trilhas.
   - O Hub consome diretamente os contadores normalizados (`totalDisciplinas`, `totalTopicos`, `topicosConcluidos`, `percentualProgresso`), garantindo tempo de resposta instantâneo.
4. **Visualização e Edição de Disciplinas (`DisciplinasView.tsx`):**
   - Migradas as ações de criação, edição de disciplina, atualização de caderno, reordenação e alternância de progresso para os novos endpoints REST granulares da FASE 2.
5. **Carregamento sob Demanda no `EstudosApp.tsx`:**
   - Hub listado via resumo leve; ao selecionar ou ativar uma trilha, o detalhe completo é carregado sob demanda via `buscarTrilha(trilhaId)`.
6. **Testes Unitários Frontend (`estudosNormalizado.test.ts`):**
   - Implementados 10 testes com Vitest cobrindo todos os fluxos de resumo, detalhe, mutações granulares de disciplinas e marcação/desmarcação de progresso.

---

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/trilha/TopicoEmbutido.java
  - backend/src/main/java/br/com/trilhara/trilha/ProgressoTopico.java
  - backend/src/main/java/br/com/trilhara/trilha/Disciplina.java
  - backend/src/main/java/br/com/trilhara/trilha/DisciplinaRepository.java
  - backend/src/main/java/br/com/trilhara/trilha/ProgressoTopicoRepository.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/CronogramaAtivoResumoDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/TrilhaResumoDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/TrilhaDetalheDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/DisciplinaDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/TopicoDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/ProgressoTopicoDTO.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/CriarTopicoRequest.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/CriarDisciplinaRequest.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/AtualizarDisciplinaRequest.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/ReordenarDisciplinasRequest.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/CriarTrilhaRequest.java
  - backend/src/main/java/br/com/trilhara/trilha/dto/AtualizarMetadadosTrilhaRequest.java
  - backend/src/test/java/br/com/trilhara/trilha/TrilhaNormalizadaIntegrationTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/trilha/Trilha.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaRepository.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaService.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaResource.java

Frontend (Criados):
  - frontend/src/test/estudosNormalizado.test.ts

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/modules/estudos/types/trilhara.ts
  - frontend/src/modules/estudos/services/estudosService.ts
  - frontend/src/modules/estudos/views/HubView.tsx
  - frontend/src/modules/estudos/views/DisciplinasView.tsx
  - frontend/src/modules/estudos/EstudosApp.tsx
```

---

### 3. Checklist de Validação da FASE 2

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **2.1** | Desmembramento do monólito em 3 coleções BSON normalizadas (`trilhas`, `disciplinas`, `progresso_topicos`) | **Atendido** | Entidades, mapeamentos BSON e repositórios segregados operando com sucesso |
| **2.2** | Preservação dos índices obrigatórios criados na FASE 1 para as coleções `trilhas`, `disciplinas` e `progresso_topicos` | **Atendido** | Validado em `MongoIndexInitializerTest` e inicialização das 19 coleções |
| **2.3** | `GET /api/trilhas` leve retornando `TrilhaResumoDTO[]` com contadores calculados e sem arrays de tópicos | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveListarResumosLevesNoHubSemCarregarTopicos` |
| **2.4** | `GET /api/trilhas/{id}` retornando detalhe completo com `disciplinas`, tópicos e `topicosConcluidosIds` | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveRetornarDetalheCompletoSobDemandaComTopicosConcluidos` |
| **2.5** | Cálculo ponderado do progresso geral da trilha pelo total de tópicos (`totalConcluidos / totalTopicos * 100`) | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveCalcularProgressoPonderadoPeloTotalDeTopicos` |
| **2.6** | Isolamento estrito de `usuarioId` em todas as operações de trilha, disciplinas e progresso | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveIsolarTrilhasEDisciplinasPorUsuarioId` |
| **2.7** | Marcação (`PUT`) e desmarcação (`DELETE`) de progresso idempotentes | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveMarcarEDesmarcarProgressoDeFormaIdempotente` |
| **2.8** | Rejeição de marcação de progresso para tópicos inexistentes na disciplina (404) | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveRejeitarProgressoParaTopicoInexistente` |
| **2.9** | Exclusão de disciplina com remoção em cascata dos registros de progresso e preservação de sessões históricas | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveExcluirDisciplinaELimparProgressoAssociadoSemAfetarSessoes` |
| **2.10** | Reordenação estrita de disciplinas via `PUT /disciplinas/reordenar` validando integridade da lista | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveReordenarDisciplinasComValidacaoEstrita` |
| **2.11** | Aplicação estrita dos limites de negócio: 50 trilhas/usuário, 100 disciplinas/trilha e 500 tópicos/disciplina | **Atendido** | Validado em `TrilhaNormalizadaIntegrationTest#deveRespeitarLimitesMaximosDeNegocio` |
| **2.12** | Adaptação dos contratos e métodos de `EstudosService` para os endpoints granulares | **Atendido** | Validado em `frontend/src/test/estudosNormalizado.test.ts` (10 testes) |
| **2.13** | Atualização de `HubView` e `DisciplinasView` eliminando overhead de renderização e consumindo APIs granulares | **Atendido** | Implementado e verificado no build de produção |
| **2.14** | Sucesso no comando de testes backend (`mvn test` cobrindo suítes das Fases 0, 1 e 2) | **Atendido** | 53 testes executados, 0 falhas, 0 erros |
| **2.15** | Sucesso nos comandos `npm test` e `npm run build` no frontend | **Atendido** | 28 testes vitest aprovados e bundle de produção gerado com sucesso |


---

## FASE 3 — Domínio Estudos (Parte 2): Sessões em Segundos, Edição/Exclusão e Estatísticas Agregadas

### 1. Resumo Executivo da Implementação

A **FASE 3** consolidou a coleção `sessoes` como a **única fonte da verdade** para métricas de tempo de estudo, questões resolvidas e acertos na plataforma Trilhara, eliminando campos legados em minutos e contadores manuais desacoplados.

Principais entregas realizadas:
1. **Modelo BSON de Sessão Normalizado e Integridade Temporal**:
   - Duração estritamente em segundos (`duracaoSegundos` no intervalo `60..86400`, equivalente a 1 minuto até 24 horas).
   - Data da sessão padronizada (`dataSessao` em `yyyy-MM-dd`), com validação rigorosa proibindo datas futuras no fuso horário do usuário.
   - Questões e acertos consolidados na própria sessão (`questoes >= 0`, `acertos` entre `0` e `questoes`), com cálculo automático de erros e taxa de acerto.
   - Remoção completa de classes e estruturas legadas: `AjusteQuestoes`, `TipoSessao`, `TipoRegistroSessao`, `NovaSessao` e `EstatisticaDisciplina`.

2. **Idempotência Estrita com Hash Funcional SHA-256 e Tombstone**:
   - Endpoint `POST /api/trilhas/{trilhaId}/sessoes` com suporte a `Idempotency-Key` (via Header e Body).
   - Em caso de repetição idêntica de payload, retorna `200 OK` com o registro existente.
   - Em caso de conflito funcional com a mesma chave, rejeita com `409 IDEMPOTENCY_KEY_CONFLICT`.
   - Se o valor do header divergir do corpo, rejeita com `400 IDEMPOTENCY_KEY_MISMATCH`.
   - Exclusão via `DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}` grava tombstone de 24 horas retendo a chave e hash para evitar re-criação indevida (`409 IDEMPOTENCY_RESULT_REMOVED`).

3. **Ordenação Canônica e Regra de Edição da Última Sessão**:
   - Ordenação canônica padronizada em todas as consultas e relatórios: `(dataSessao DESC, criadoEm DESC, _id DESC)`.
   - Endpoint `PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}` restrito estritamente à **sessão #1 mais recente global** da trilha. Qualquer tentativa de alteração em sessões anteriores é rejeitada com `409 LATEST_SESSION_ONLY`.
   - A listagem paginada (`GET /api/trilhas/{trilhaId}/sessoes`) sinaliza o atributo booleano `editavel: true` exclusivamente na primeira sessão da primeira página.

4. **Preservação de Histórico e Endpoint Dedicado de Exclusão por Disciplina**:
   - Ao excluir uma disciplina da trilha, as sessões de estudo associadas são integralmente preservadas com a flag `disciplinaRemovida: true` e snapshots imutáveis de nome e cor.
   - Implementado endpoint dedicado `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes` permitindo a purga voluntária do histórico, retornando `{ sessoesRemovidas, totalSegundosRemovidos }`.

5. **Estatísticas Agregadas e Cálculo de Sequência de Dias (Streak)**:
   - Endpoint `GET /api/trilhas/{trilhaId}/estatisticas` operando via pipeline de agregação BSON com suporte a períodos pré-definidos (`7d`, `14d`, `30d`, `180d`, `tudo`) ou intervalo customizado (`dataInicio` e `dataFim`).
   - Retorno detalhado contendo métricas globais (`totalSegundos`, `totalSessoes`, `totalQuestoes`, `totalAcertos`, `totalErros`, `percentualAcertoGlobal`), métricas agrupadas `porDisciplina`, distribuição cronológica `serieTemporal` e cálculo contínuo de dias consecutivos de estudo (`streakDias`).

6. **Modernização do Frontend**:
   - Atualização de contratos em `domainContracts.ts` e tipagens em `trilhara.ts`.
   - Modernização do serviço `EstudosService` para consumir os novos endpoints paginados e agregados.
   - Adaptação do `EstudarView.tsx` para persistir tempo em segundos e data local ISO.
   - Atualização do `DashboardView.tsx` com paginação de 20 itens, edição rápida da última sessão, confirmação de exclusão, alerta para disciplinas removidas com ação de limpeza definitiva, e formatação amigável de horas/minutos/segundos.
   - Ajuste em `DisciplinasView.tsx` tornando a visualização de questões informativa a partir do pipeline de sessões.

---

### 2. Arquivos Criados, Modificados e Excluídos

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/sessao/dto/RegistrarSessaoRequest.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/AtualizarSessaoRequest.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/SessaoDTO.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/PaginaSessoesDTO.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/RemocaoSessoesDisciplinaResponse.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/EstatisticaDisciplinaDTO.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/SerieTemporalDiaDTO.java
  - backend/src/main/java/br/com/trilhara/sessao/dto/EstatisticasTrilhaDTO.java
  - backend/src/test/java/br/com/trilhara/sessao/SessaoNormalizadaIntegrationTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/sessao/Sessao.java
  - backend/src/main/java/br/com/trilhara/sessao/SessaoRepository.java
  - backend/src/main/java/br/com/trilhara/sessao/SessaoService.java
  - backend/src/main/java/br/com/trilhara/sessao/SessaoResource.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaService.java

Backend (Excluídos):
  - backend/src/main/java/br/com/trilhara/sessao/AjusteQuestoes.java
  - backend/src/main/java/br/com/trilhara/sessao/TipoSessao.java
  - backend/src/main/java/br/com/trilhara/sessao/TipoRegistroSessao.java
  - backend/src/main/java/br/com/trilhara/sessao/NovaSessao.java
  - backend/src/main/java/br/com/trilhara/sessao/EstatisticaDisciplina.java
  - backend/src/test/java/br/com/trilhara/sessao/SessaoResourceTest.java

Frontend (Criados):
  - frontend/src/test/sessaoNormalizada.test.ts

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/modules/estudos/types/trilhara.ts
  - frontend/src/modules/estudos/services/estudosService.ts
  - frontend/src/modules/estudos/utils/format.ts
  - frontend/src/modules/estudos/views/EstudarView.tsx
  - frontend/src/modules/estudos/views/DashboardView.tsx
  - frontend/src/modules/estudos/views/DashboardView.module.css
  - frontend/src/modules/estudos/views/DisciplinasView.tsx
```

---

### 3. Checklist de Validação da FASE 3

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **3.1** | Coleção `sessoes` como única fonte da verdade de tempo, questões e acertos | **Atendido** | `Sessao.java`, BSON aggregation em `SessaoRepository` e remoção de endpoints de ajuste manual |
| **3.2** | Duração estritamente em segundos com validação no intervalo `60..86400` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRegistrarSessaoNormalizadaEmSegundos` |
| **3.3** | Proibição de registro de sessão com data futura no fuso horário do usuário | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRejeitarSessaoComDataFutura` |
| **3.4** | Idempotência estrita via Header/Body `Idempotency-Key` com SHA-256 funcional (201/200/409) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveGarantirIdempotenciaEstritaComHashFuncional` |
| **3.5** | Rejeição por divergência entre Header `Idempotency-Key` e Body com `400 IDEMPOTENCY_KEY_MISMATCH` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRejeitarDivergenciaEntreHeaderECorpoIdempotencyKey` |
| **3.6** | Ordenação canônica `(dataSessao DESC, criadoEm DESC, _id DESC)` em todas as listagens | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveOrdenarSessoesPelaOrdemCanonica` |
| **3.7** | Edição via `PATCH` restrita estritamente à sessão #1 mais recente global (`409 LATEST_SESSION_ONLY` nas demais) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePermitirEditarApenasAUltimaSessaoGlobalDaTrilha` |
| **3.8** | Exclusão de sessão com gravação de Tombstone retido por 24h (`409 IDEMPOTENCY_RESULT_REMOVED` em replay) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveGravarTombstoneNaExclusaoERejeitarReplayComIdempotencia` |
| **3.9** | Preservação de sessões históricas ao excluir disciplina (`disciplinaRemovida: true`) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePreservarSessoesHistoricasAoExcluirDisciplina` |
| **3.10** | Endpoint dedicado para purga em lote de sessões de disciplina excluída | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePermitirExcluirHistoricoDeDisciplinaRemovidaViaEndpointDedicado` |
| **3.11** | Paginação obrigatória em `GET /api/trilhas/{id}/sessoes` com sinalização de `editavel: true` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePaginarSessoesComSinalizacaoDeEditavelApenasNaPrimeira` |
| **3.12** | Pipeline de estatísticas agregadas por período, disciplina, série temporal e streak contínuo | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveCalcularEstatisticasAgregadasComSerieTemporalESubmeterFiltrosDePeriodo` |
| **3.13** | Isolamento estrito por `usuarioId` em todas as operações de sessões e agregações | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveIsolarSessoesEEstatisticasPorUsuarioId` |
| **3.14** | Exclusão de trilha limpa em cascata todas as suas sessões e tombstones no MongoDB | **Atendido** | Validado no `TrilhaService.excluirTrilha` invocando `sessaoRepository.excluirPorTrilha` |
| **3.15** | Contratos de frontend alinhados e testes unitários Vitest | **Atendido** | 34 testes vitest aprovados em `frontend/src/test/` incluindo `sessaoNormalizada.test.ts` |
| **3.16** | Sucesso no build do frontend com checagem rigorosa do TypeScript (`tsc -b && vite build`) | **Atendido** | Bundle de produção gerado com sucesso sem erros |
| **3.17** | Sucesso na suíte de testes backend com Quarkus e MongoDB (`mvn test`) | **Atendido** | 47 testes executados com 0 falhas e 0 erros |

---

## FASE 4 — Domínio Estudos (Parte 3): Cronogramas, Coordenação de Concorrência, Geradores e APIs de Blocos

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25 / MongoDB Reactive Streams)
1. **Modelagem de Domínio Normalizada (`cronogramas` e `blocos_cronograma`):**
   - Criação das entidades `Cronograma.java`, `BlocoCronograma.java` e do enum `ModalidadeCronograma.java` (`SEQUENCIAL`, `PONDERADO`, `MANUAL`) no pacote `br.com.trilhara.cronograma`.
   - Limite estrito de horizonte de 366 dias (`dataFim <= dataInicio.plusDays(365)`), duração de bloco entre 30 e 240 minutos e máximo de 24 blocos por dia.
   - Cada bloco de cronograma é um documento individual desacoplado na coleção `blocos_cronograma` (eliminando arrays ilimitados embutidos), indexado por `{ usuarioId, trilhaId, cronogramaId, versaoGeracao, data, ordem }`.
   - Remoção completa de classes legadas: `PlanejamentoCicloRepository.java`, `PlanejamentoCiclo.java`, `DisciplinaProgramada.java`, `AtualizacaoPlanejamentoCiclo.java` e `AtualizacaoConclusaoBloco.java`.

2. **Coordenação de Concorrência via Lease Fencing na Trilha e Conflito de Modalidade:**
   - Implementado lock distribuído/lease atômico no documento da trilha (`trilhas.geracaoCronogramaLeaseToken` e `trilhas.geracaoCronogramaEmAndamentoAte`): token UUID com validade de 2 minutos, aquisição atômica sob filtro de concorrência e liberação estrita. Operações concorrentes são rejeitadas com `409 SCHEDULE_GENERATION_IN_PROGRESS`.
   - Regra de limite de modalidade: cada trilha comporta até 3 cronogramas, sendo no máximo 1 por modalidade. Tentativa de cadastrar ou alterar modalidade de um cronograma existente para uma já presente na trilha rejeita com `409 DUPLICATE_MODALITY` antes de qualquer geração de blocos.
   - Staging versionado: novos blocos são inseridos sob a nova `versaoGeracao`. O ponteiro da trilha (`cronogramaAtivoId` e `versaoAtivaCronograma`) é trocado atomicamente em operação única, seguido da reconciliação de flags `ativo: true` respeitando o índice parcial único do MongoDB (máximo 1 ativo por trilha). Gerações antigas são limpas somente após confirmação.

3. **Algoritmos Geradores Puros e Determinísticos (`GeradorBlocosCronograma.java`):**
   - **Sequencial (`SEQUENCIAL`):** alocação em fila circular ordenada de disciplinas participantes pelos dias ativos da semana até atingir `blocosPorDia`.
   - **Ponderado (`PONDERADO`):** algoritmo de deficit proporcional $\Delta_i(t) = \frac{t \cdot p_i}{P} - \text{alocados}_i(t-1)$ sobre o total de blocos $B_{\text{total}}$ e soma de pesos $P$, com desempate determinístico pela ordem de cadastro da disciplina.
   - **Manual (`MANUAL`):** template semanal por dia da semana ISO (1=Segunda a 7=Domingo), mapeando disciplinas customizadas diretamente nas posições ordinais do dia.

4. **APIs de Estudos e Blocos (Fonte Única):**
   - `GET /api/blocos/hoje`: retorna exclusivamente os blocos pendentes da data local do usuário (`hojeLocal`), considerando a versão ativa do cronograma ativo de todas as trilhas do usuário, ordenados por `trilhaNome ASC, ordem ASC, _id ASC`.
   - `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`: listagem paginada (`pagina`, `tamanho`), com filtros opcionais de data e conclusão, ordenados por `data ASC, ordem ASC, _id ASC`, com derivação do campo `statusData` (`ATRASADO`, `HOJE`, `FUTURO`).
   - `PATCH /api/blocos/{blocoId}/conclusao`: atualização idempotente de conclusão; marca `concluido=true` com `concluidoEm=agora` ou desfaz marcando `concluido=false` com `concluidoEm=null`.
   - `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`: conclusão em lote de blocos pendentes com `data < hojeLocal`, exigindo `operationId` UUID idempotente; reexecuções com o mesmo ID retornam a resposta cacheada sem duplicar trabalho.

5. **Disponibilidade Semanal do Usuário e Detecção de Sobrecarga:**
   - `GET /api/usuarios/me/disponibilidade-semanal` e `PUT /api/usuarios/me/disponibilidade-semanal`: gerencia array de 7 posições (minutos disponíveis de Segunda a Domingo) em `usuarios.preferencias.disponibilidadeSemanalMinutos`.
   - Computação da carga ativa em minutos por dia a partir dos cronogramas ativos com sinalização de `sobrecargaPorDia: [7 booleans]`.

6. **Efeitos em Cascata e Fallback na Exclusão:**
   - Exclusão de disciplina: cancela e remove de `blocos_cronograma` apenas blocos futuros pendentes (`data >= hojeLocal && concluido == false`); blocos já concluídos ou passados são preservados como histórico.
   - Atualização de disciplina: sincroniza snapshots de nome e cor em todos os blocos existentes.
   - Exclusão do cronograma ativo: remove seus blocos e promove automaticamente o cronograma remanescente mais recente da trilha `(atualizadoEm DESC, _id DESC)` para ativo; se nenhum restar, zera o ponteiro atômico da trilha.
   - Exclusão da trilha: remove todos os cronogramas e blocos associados.

#### Frontend (React 18 / TypeScript / Vite 6)
1. **Contratos Compartilhados (`domainContracts.ts`):**
   - Adicionadas interfaces tipadas: `ModalidadeCronograma`, `CronogramaDTO`, `CriarCronogramaPayload`, `AtualizarCronogramaPayload`, `BlocoCronogramaItemDTO`, `PaginaBlocosDTO`, `ConcluirAtrasadosResponse`, `DisponibilidadeSemanalDTO` e `BlocoHojeDTO`.
2. **Serviço de Estudos (`estudosService.ts`):**
   - Implementadas 10 funções de comunicação HTTP integradas com `apiFetch`: `listarCronogramas`, `criarCronograma`, `atualizarCronograma`, `ativarCronograma`, `excluirCronograma`, `obterDisponibilidadeSemanal`, `atualizarDisponibilidadeSemanal`, `obterBlocosHoje`, `listarBlocosCronograma`, `alternarConclusaoBloco` e `concluirBlocosAtrasados`.
3. **Modernização da View de Planejamento (`PlanejamentoView.tsx`):**
   - Transição integral da terminologia legada de "Ciclos" para "Cronogramas".
   - Suporte a até 3 cronogramas por trilha, com visualização em abas, status de publicação e sinalização do cronograma ativo.
   - Desativação seletiva no formulário das modalidades já utilizadas na trilha para prevenir conflito `409 DUPLICATE_MODALITY`.
   - Período padrão inteligente: "Até a data da prova" (quando há edital com prova futura) ou "1 mês (30 dias)".
   - Banner de alerta não bloqueante indicando sobrecarga semanal em comparação com a disponibilidade configurada do usuário.
   - Visualização paginada dos blocos gerados com badges dinâmicos de status (`ATRASADO`, `HOJE`, `FUTURO`), checkbox de conclusão individual e botão de ação rápida "Concluir atrasados sem sessão".
   - Ação direta "Estudar este bloco" integrando o bloco selecionado ao cronômetro global de estudos.

---

### 2. Arquivos Criados, Modificados e Excluídos

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/cronograma/Cronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/ModalidadeCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/GeradorBlocosCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoCronogramaRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/OperacaoConcluirAtrasadosRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaService.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/UsuarioDisponibilidadeResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/CronogramaDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/CriarCronogramaRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/AtualizarCronogramaRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/BlocoHojeDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/BlocoCronogramaItemDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/PaginaBlocosDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/ConcluirBlocoRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/ConcluirAtrasadosRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/ConcluirAtrasadosResponse.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/DisponibilidadeSemanalDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/AtualizarDisponibilidadeRequest.java
  - backend/src/test/java/br/com/trilhara/cronograma/CronogramaNormalizadoIntegrationTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/trilha/Trilha.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaRepository.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaService.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaResource.java
  - backend/src/main/java/br/com/trilhara/tarefas/service/TarefaService.java
  - backend/src/main/java/br/com/trilhara/shared/UsuarioAtual.java
  - backend/src/test/java/br/com/trilhara/edital/EditalResourceTest.java
  - backend/src/test/java/br/com/trilhara/tarefas/TarefaResourceTest.java

Backend (Excluídos):
  - backend/src/main/java/br/com/trilhara/estudos/repository/PlanejamentoCicloRepository.java
  - backend/src/main/java/br/com/trilhara/estudos/domain/PlanejamentoCiclo.java
  - backend/src/main/java/br/com/trilhara/estudos/domain/DisciplinaProgramada.java
  - backend/src/main/java/br/com/trilhara/estudos/dto/AtualizacaoPlanejamentoCiclo.java
  - backend/src/main/java/br/com/trilhara/estudos/dto/AtualizacaoConclusaoBloco.java
  - backend/src/test/java/br/com/trilhara/trilha/TrilhaResourceTest.java

Frontend (Criados):
  - frontend/src/test/cronogramaNormalizado.test.ts

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/modules/estudos/services/estudosService.ts
  - frontend/src/modules/estudos/views/PlanejamentoView.tsx
  - frontend/src/modules/estudos/views/PlanejamentoView.module.css
```

---

### 3. Checklist de Validação da FASE 4

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **4.1** | Substituição do modelo legado de ciclos por `cronogramas` e `blocos_cronograma` | **Atendido** | Remoção de `PlanejamentoCicloRepository` e criação das coleções desacopladas normalizadas |
| **4.2** | Cada bloco é um documento individual com snapshot de disciplina e sem arrays aninhados ilimitados | **Atendido** | Validado em `BlocoCronogramaRepository` e `BlocoCronograma.java` |
| **4.3** | Limite máximo de horizonte de 366 dias e até 24 blocos por dia | **Atendido** | Validado em `GeradorBlocosCronograma` e `CronogramaService` |
| **4.4** | Algoritmos geradores puros e determinísticos para `SEQUENCIAL`, `PONDERADO` e `MANUAL` | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveGerarBlocosComDeterminismoNasTresModalidades` |
| **4.5** | Algoritmo ponderado baseado em déficit acumulado $\Delta_i(t)$ com desempate por ordem | **Atendido** | Implementado e conferido em `GeradorBlocosCronograma.java` |
| **4.6** | Algoritmo manual seguindo template semanal por dia ISO (1..7) | **Atendido** | Implementado e validado em `GeradorBlocosCronograma.java` |
| **4.7** | Concorrência controlada via lease token UUID e expiração de 2 min na trilha pai | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveRejeitarGeracaoConcorrenteComLeaseAtivo` |
| **4.8** | Rejeição por modalidade duplicada na trilha com `409 DUPLICATE_MODALITY` | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveRejeitarModalidadeDuplicadaCom409` |
| **4.9** | Publicação atômica versionada via ponteiro `cronogramaAtivoId` + `versaoAtivaCronograma` na trilha | **Atendido** | Validado em `CronogramaService.java` e `TrilhaRepository.java` |
| **4.10** | Reconciliação do flag `ativo` respeitando o índice parcial único (máx 1 ativo por trilha) | **Atendido** | Validado em `CronogramaService.java` e `MongoIndexInitializer.java` |
| **4.11** | Promoção automática de cronograma remanescente mais recente ao excluir cronograma ativo | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#devePromoverFallbackAoExcluirCronogramaAtivo` |
| **4.12** | Exclusão de disciplina remove apenas blocos futuros pendentes, preservando histórico | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveRemoverApenasBlocosFuturosNaoConcluidosAoExcluirDisciplina` |
| **4.13** | Atualização de disciplina sincroniza snapshots de nome e cor nos blocos | **Atendido** | Implementado em `TrilhaService.atualizarDisciplina` |
| **4.14** | Endpoint `GET /api/blocos/hoje` retorna pendentes da data local de todos os cronogramas ativos | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveManipularBlocosHojeConclusaoEPaginacao` |
| **4.15** | Endpoint paginado de blocos com filtros e derivação de `statusData` (`ATRASADO`, `HOJE`, `FUTURO`) | **Atendido** | Validado em `BlocoResource.java` e `CronogramaNormalizadoIntegrationTest` |
| **4.16** | Endpoint `PATCH /api/blocos/{blocoId}/conclusao` idempotente com `concluidoEm` | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveManipularBlocosHojeConclusaoEPaginacao` |
| **4.17** | Endpoint `POST .../blocos/concluir-atrasados` idempotente por `operationId` | **Atendido** | Validado em `BlocoResource.java` e `OperacaoConcluirAtrasadosRepository.java` |
| **4.18** | Endpoints de disponibilidade semanal e detecção de sobrecarga frente à carga gerada | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveGerenciarDisponibilidadeSemanalESinalizarSobrecarga` |
| **4.19** | Isolamento estrito de multitenancy por `usuarioId` em cronogramas e blocos | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveGarantirIsolamentoMultitenantDeCronogramasEBlocos` |
| **4.20** | Exclusão de trilha remove todos os cronogramas e blocos em cascata | **Atendido** | Implementado em `TrilhaService.excluirTrilha` |
| **4.21** | Modernização do `PlanejamentoView.tsx` com até 3 cronogramas, bloqueio de modalidade e aviso de sobrecarga | **Atendido** | Validado em `PlanejamentoView.tsx` e `cronogramaNormalizado.test.ts` |
| **4.22** | Sucesso na suíte de testes do frontend com Vitest (7 arquivos, 43 testes aprovados) | **Atendido** | Executado `npm test -- --run` com 100% de sucesso |
| **4.23** | Sucesso no build do frontend com checagem rigorosa de tipos (`tsc -b && vite build`) | **Atendido** | Build concluído com sucesso e sem warnings impeditivos |
| **4.24** | Sucesso em toda a suíte de testes do backend com Quarkus e MongoDB (`mvn test`) | **Atendido** | 82 testes executados com 0 falhas e 0 erros |


---

## FASE 4 — Domínio Estudos (Parte 3): Cronogramas, Coordenação de Concorrência, Geradores e APIs de Blocos

### 1. Resumo Executivo das Entregas

1. **Substituição Definitiva do Modelo Legado de Ciclos por Coleções Normalizadas**:
   - Extinção completa do repositório legado `PlanejamentoCicloRepository.java` e do modelo embutido desnormalizado de ciclos.
   - Criação das coleções `cronogramas` e `blocos_cronograma` com tipagem BSON estrita e mapeamento via record classes imutáveis (`Cronograma.java` e `BlocoCronograma.java`).
   - Aplicação de índices compostos e parciais no MongoDB:
     - `idx_cronogramas_usuario_trilha_modalidade_unique`: chave única `(usuarioId, trilhaId, modalidade)` garantindo no máximo 1 cronograma por modalidade por trilha.
     - `idx_cronogramas_usuario_trilha_ativo_unique`: índice parcial único em `(usuarioId, trilhaId)` onde `ativo: true`.
     - `idx_blocos_cronograma_busca_hoje`: `(usuarioId, dataEstudo, status)`.
     - `idx_blocos_cronograma_trilha_paginada`: `(usuarioId, trilhaId, cronogramaId, versaoGeracao, dataEstudo, ordemDia)`.
     - `idx_blocos_cronograma_concluir_atrasados`: `(usuarioId, trilhaId, cronogramaId, status, dataEstudo)`.

2. **Coordenação de Concorrência via Lease Atômico em Trilhas**:
   - Coordenação de lock/lease distribuído com expiração de 120 segundos utilizando os campos atômicos `geracaoCronogramaLeaseToken` e `geracaoCronogramaEmAndamentoAte` em `trilhas`.
   - Se uma nova geração for disparada enquanto houver outra em processamento válido, a operação é rejeitada com status `409 SCHEDULE_GENERATION_IN_PROGRESS`.
   - Liberação garantida do lease via bloco `finally` e suporte a fencing token UUID.

3. **Geradores Puros e Determinísticos com Teto de 366 Dias e 24 Blocos/Dia**:
   - Implementação de `GeradorBlocosCronograma.java` cobrindo 3 modalidades puras:
     - **Sequencial**: rotação cíclica ininterrupta das disciplinas cadastradas ao longo dos dias ativos.
     - **Ponderado por Peso**: distribuição por déficit acumulado priorizando disciplinas com maior peso relativo, com critério de desempate determinístico pela ordem original de cadastro.
     - **Manual**: distribuição orientada a modelo semanal configurado pelo usuário (`semanaModeloManual`).
   - Teto rígido de horizonte de 366 dias (máximo de 8.784 blocos por versão) e validação de até 24 blocos diários.

4. **Protocolo de Staging Versionado e Publicação Atômica**:
   - Incremento atômico de `proximaVersaoCronograma` em `trilhas`.
   - Persistência inicial do novo cronograma em estado `PREPARANDO` (`ativo: false`).
   - Gravação dos blocos gerados em batch com a `versaoGeracao`.
   - Desativação prévia de outros cronogramas da trilha e comutação atômica do ponteiro em `trilhas` (`cronogramaAtivoId`, `versaoAtivaCronograma`) para o novo documento ativo (`estadoPublicacao: ATIVO`, `ativo: true`), respeitando com precisão o índice parcial único do MongoDB.
   - Purga limpa de blocos pertencentes a versões legadas substituídas.

5. **Regras de Negócio e APIs de Gestão de Cronogramas**:
   - Limite estrito de no máximo 3 cronogramas por trilha.
   - Bloqueio contra modalidades duplicadas na mesma trilha (`409 DUPLICATE_MODALITY`).
   - Exclusão de cronograma com purga em cascata de todos os seus blocos.
   - **Promoção Automática**: ao excluir o cronograma atualmente ativo, o sistema promove automaticamente o cronograma remanescente mais recente da trilha para o estado `ativo: true`.
   - **Limpeza de Blocos Futuros por Disciplina**: ao remover uma disciplina da trilha, blocos pendentes futuros da referida disciplina são expurgados imediatamente, enquanto blocos concluídos são preservados com snapshot imutável.

6. **APIs de Blocos e Conclusão com Idempotência**:
   - `GET /api/blocos/hoje`: listagem de blocos do dia atual do usuário em todas as trilhas com cronograma ativo.
   - `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`: listagem paginada com ordenação cronológica e filtros opcionais por status (`PENDENTE`, `CONCLUIDO`, `CANCELADO`) ou intervalo de datas.
   - `PATCH /api/blocos/{blocoId}/conclusao`: atualização idempotente de conclusão de bloco individual, sincronizando `concluidoEm` e `duracaoRealSegundos`.
   - `POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`: conclusão em lote de blocos pendentes com data anterior ou igual a hoje, protegido por `operationId` único contra reprocessamentos concorrentes.

7. **Disponibilidade Semanal e Alertas Não-Bloqueantes de Sobrecarga**:
   - Endpoints `GET` e `PUT /api/usuarios/me/disponibilidade-semanal` gerenciando array de 7 posições com limites diários em minutos.
   - Cálculo dinâmico da carga horária semanal alocada através de cronogramas futuros ativos e sinalização de sobrecarga por dia (`sobrecargaPorDia`).

8. **Interface do Usuário Moderna (`PlanejamentoView.tsx`)**:
   - Terminologia modernizada de "Ciclos" para "Cronogramas".
   - Navegação por cards de cronogramas indicando modalidade, versão ativa, quantidade de blocos e período.
   - Formulário de geração com seleção de modalidade (desabilitando opções já presentes na trilha), dias da semana, duração e período.
   - Exibição de aviso não-bloqueante de capacidade quando o cronograma planejado somado aos demais ultrapassar a disponibilidade semanal do usuário.
   - Visualização de blocos em tabela paginada com badges coloridos de disciplinas, status e ações rápidas de "Concluir / Desmarcar" e "Estudar".

---

### 2. Arquivos Criados, Modificados e Excluídos

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/cronograma/ModalidadeCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/Cronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/GeradorBlocosCronograma.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoCronogramaRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/OperacaoConcluirAtrasadosRepository.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaService.java
  - backend/src/main/java/br/com/trilhara/cronograma/CronogramaResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/BlocoResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/UsuarioDisponibilidadeResource.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/CriarCronogramaRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/AtualizarCronogramaRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/CronogramaDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/BlocoCronogramaItemDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/PaginaBlocosDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/AtualizarConclusaoBlocoRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/ConcluirAtrasadosRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/ConcluirAtrasadosResponse.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/FalhaBlocoDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/DisponibilidadeSemanalDTO.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/AtualizarDisponibilidadeSemanalRequest.java
  - backend/src/main/java/br/com/trilhara/cronograma/dto/BlocoHojeDTO.java
  - backend/src/test/java/br/com/trilhara/cronograma/CronogramaNormalizadoIntegrationTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/trilha/Trilha.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaRepository.java
  - backend/src/main/java/br/com/trilhara/trilha/TrilhaService.java
  - backend/src/main/java/br/com/trilhara/auth/UserRepository.java
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaService.java

Backend (Excluídos):
  - backend/src/main/java/br/com/trilhara/trilha/PlanejamentoCicloRepository.java
  - backend/src/test/java/br/com/trilhara/trilha/TrilhaResourceTest.java

Frontend (Criados):
  - frontend/src/test/cronogramaNormalizado.test.ts

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/modules/estudos/services/estudosService.ts
  - frontend/src/modules/estudos/views/PlanejamentoView.tsx
  - frontend/src/modules/estudos/views/PlanejamentoView.module.css
```

---

### 3. Checklist de Validação da FASE 4

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **4.1** | Remoção do modelo de ciclos legados e transição para `cronogramas` e `blocos_cronograma` | **Atendido** | Exclusão de `PlanejamentoCicloRepository` e criação das coleções BSON tipadas |
| **4.2** | Coordenação de concorrência atômica via lease na trilha (`409 SCHEDULE_GENERATION_IN_PROGRESS`) | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveCoordenarConcorrenciaDeGeracaoViaLeaseNaTrilha` |
| **4.3** | Geradores determinísticos Sequencial e Ponderado com teto de 366 dias e 24 blocos/dia | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveGerarCronogramaSequencialEPonderadoComDeterminismo` |
| **4.4** | Rejeição por modalidade duplicada na trilha com status `409 DUPLICATE_MODALITY` | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveRejeitarModalidadeDuplicadaNaMesmaTrilha` |
| **4.5** | Limite máximo de 3 cronogramas por trilha | **Atendido** | Validado na criação e no componente `PlanejamentoView` com desativação do botão "+ Novo Cronograma" |
| **4.6** | Protocolo de staging versionado com publicação atômica do cronograma ativo | **Atendido** | Staging com `ativo: false` / `PREPARANDO`, batch insert dos blocos, `desativarOutros` e ativação final |
| **4.7** | Promoção automática do cronograma remanescente mais recente ao excluir o ativo | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#devePromoverCronogramaMaisRecenteAoExcluirAtivo` |
| **4.8** | Exclusão em cascata de blocos ao excluir cronograma | **Atendido** | `blocoRepository.excluirPorCronograma` executado antes de remover o cronograma |
| **4.9** | Purga de blocos pendentes futuros de disciplina removida preservando concluídos | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveLimparBlocosFuturosDeDisciplinaExcluidaPreservandoConcluidos` |
| **4.10** | Listagem paginada de blocos com filtros e ordenação canônica | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveListarBlocosPaginadosDoCronograma` |
| **4.11** | Consulta consolidada de blocos de hoje (`GET /api/blocos/hoje`) | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveConsultarBlocosHojeConsolidados` |
| **4.12** | Conclusão individual idempotente de bloco (`PATCH /api/blocos/{id}/conclusao`) | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveAlternarConclusaoDeBlocoIdempotentemente` |
| **4.13** | Conclusão em lote de atrasados com `operationId` idempotente | **Atendido** | Validado em `CronogramaNormalizadoIntegrationTest#deveConcluirBlocosAtrasadosComOperationIdIdempotente` |
| **4.14** | Gestão de disponibilidade semanal do usuário e alerta de sobrecarga | **Atendido** | Validado em `UsuarioDisponibilidadeResource` e verificado em `PlanejamentoView.tsx` |
| **4.15** | Contratos de frontend alinhados e testes unitários Vitest aprovados | **Atendido** | 9 testes em `cronogramaNormalizado.test.ts` e 43/43 testes frontend aprovados |
| **4.16** | Sucesso no build do frontend (`tsc -b && vite build`) | **Atendido** | Bundle de produção gerado com sucesso em 6.5s sem erros |
| **4.17** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 55 testes executados com 0 falhas e 0 erros |
| **4.18** | Não interferência nos módulos de Tarefas e Pomodoro (reservados para Fase 7) | **Atendido** | Módulos de Tarefas e Pomodoro preservados intocados |


---

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
   - Contagem baseada em diferença de timestamp absoluto (`accumulatedElapsedMs + (runningSince ? now - runningSince : 0)`), imune a *throttling* de abas em segundo plano, fechamento acidental e recarregamento de página.
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

### 2. Arquivos Criados e Modificados

```text
Frontend (Criados):
  - frontend/src/shared/services/sessaoGlobalService.ts
  - frontend/src/shared/design-system/GlobalSessionModal/GlobalSessionModalContext.tsx
  - frontend/src/shared/design-system/GlobalSessionModal/GlobalSessionModal.tsx
  - frontend/src/shared/design-system/GlobalSessionModal/GlobalSessionModal.module.css
  - frontend/src/shared/design-system/GlobalSessionModal/index.ts
  - frontend/src/test/fase5GlobalSessionTimer.test.tsx

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/shared/auth/authStorage.ts
  - frontend/src/shared/design-system/index.ts
  - frontend/src/App.tsx
  - frontend/src/modules/estudos/context/StudyTimerContext.tsx
  - frontend/src/modules/estudos/views/EstudarView.tsx
  - frontend/src/modules/estudos/views/PlanejamentoView.tsx
  - frontend/src/modules/pomodoro/PomodoroApp.tsx
```

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
| **5.13** | Cálculo de tempo por timestamp absoluto sem drift em segundo plano | **Atendido** | Validado em `fase5GlobalSessionTimer.test.tsx#deveCalcularTempoDecorridoPorDiferencaDeTimestampAbsoluto` |
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

### 2. Arquivos Criados, Modificados e Removidos

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/tarefa/PrioridadeTarefa.java
  - backend/src/main/java/br/com/trilhara/tarefa/Subtarefa.java
  - backend/src/main/java/br/com/trilhara/tarefa/EscopoRecorrencia.java
  - backend/src/main/java/br/com/trilhara/tarefa/ListaTarefas.java
  - backend/src/main/java/br/com/trilhara/tarefa/SerieRecorrente.java
  - backend/src/main/java/br/com/trilhara/tarefa/Tarefa.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/SubtarefaDTO.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/CriarListaTarefasRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/AtualizarListaTarefasRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/ListaTarefasResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/ExclusaoListaResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/CriarTarefaRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/AtualizarTarefaRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/AtualizarConclusaoTarefaRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/AtualizarSubtarefaRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/TarefaResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/TarefasHojeResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/CriarSerieRecorrenteRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/AtualizarSerieRecorrenteRequest.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/SerieRecorrenteResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/LixeiraTarefasResponse.java
  - backend/src/main/java/br/com/trilhara/tarefa/ListaTarefasRepository.java
  - backend/src/main/java/br/com/trilhara/tarefa/SerieRecorrenteRepository.java
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaRepository.java
  - backend/src/main/java/br/com/trilhara/tarefa/RecorrenciaEngine.java
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaService.java
  - backend/src/main/java/br/com/trilhara/tarefa/PurgaLixeiraTarefasJob.java
  - backend/src/main/java/br/com/trilhara/tarefa/ListaTarefasResource.java
  - backend/src/main/java/br/com/trilhara/tarefa/SerieRecorrenteResource.java
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaResource.java
  - backend/src/test/java/br/com/trilhara/tarefa/Fase6RecorrenciaEngineTest.java
  - backend/src/test/java/br/com/trilhara/tarefa/Fase6TarefaResourceTest.java

Backend (Modificados):
  - backend/pom.xml (adição de quarkus-scheduler)
  - backend/src/main/java/br/com/trilhara/config/MongoIndexInitializer.java
  - backend/src/test/java/br/com/trilhara/tarefa/ListaTarefasResourceTest.java
  - backend/src/test/java/br/com/trilhara/tarefa/TarefaResourceTest.java

Backend (Removidos):
  - backend/src/main/java/br/com/trilhara/tarefa/OrigemTarefa.java
  - backend/src/main/java/br/com/trilhara/tarefa/dto/CicloAtivoResponse.java

Frontend (Criados):
  - frontend/src/test/fase6Tarefas.test.ts

Frontend (Modificados):
  - frontend/src/shared/contracts/domainContracts.ts
  - frontend/src/modules/tarefas/types/tarefa.ts
  - frontend/src/modules/tarefas/services/tarefaService.ts
  - frontend/src/modules/tarefas/services/tarefaStorage.ts
  - frontend/src/modules/tarefas/components/TaskItem/TaskItem.tsx
  - frontend/src/modules/tarefas/components/TaskModal/TaskModal.tsx
  - frontend/src/modules/tarefas/components/CalendarView/CalendarView.tsx
  - frontend/src/modules/tarefas/components/Navbar/navigationConfig.ts
  - frontend/src/modules/tarefas/TarefasApp.tsx
```

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

### 2. Lista de Arquivos Alterados e Criados

```text
Frontend (Criados):
  - frontend/src/shared/design-system/SectionErrorCard/SectionErrorCard.module.css
  - frontend/src/shared/design-system/SectionErrorCard/SectionErrorCard.tsx
  - frontend/src/shared/design-system/SectionErrorCard/index.ts
  - frontend/src/shared/services/agendaHojeService.ts
  - frontend/src/shared/services/index.ts
  - frontend/src/test/fase7IntegracaoDiaria.test.tsx

Frontend (Modificados):
  - frontend/src/shared/design-system/index.ts
  - frontend/src/modules/tarefas/components/Navbar/MinhasListasNav.tsx
  - frontend/src/modules/tarefas/components/Navbar/Navbar.tsx
  - frontend/src/modules/tarefas/App.module.css
  - frontend/src/modules/tarefas/TarefasApp.tsx
  - frontend/src/modules/pomodoro/App.module.css
  - frontend/src/modules/pomodoro/PomodoroApp.tsx
```

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

## FASE 8 — Reestruturação de Cadernos, Seções e Notas: BSON Estrito, Carregamento Sob Demanda, Busca Textual e Lixeira Hierárquica

### 1. Resumo Executivo e Decisões de Arquitetura

1. **Arquitetura de Dados BSON Estrita em 3 Níveis (`cadernos`, `secoes`, `notas`)**:
   - Totalmente normalizado em 3 coleções BSON independentes (`cadernos`, `secoes`, `notas`).
   - Todos os identificadores `_id`, chaves estrangeiras (`usuarioId`, `cadernoId`, `secaoId`) e referências cruzadas (`origemTrilhaId`, `origemDisciplinaId`, `origemTopicoId`) utilizam `ObjectId` nativo.
   - **Eliminação de Documentos Embutidos**: o modelo legado que embutia listas de seções e páginas inteiras dentro do documento do caderno foi completamente descontinuado, eliminando gargalos de crescimento BSON (limite de 16 MB) e concorrência destrutiva.
   - **Regra de Caderno Vazio e Hierarquia Flexível**: cadernos recém-criados são permitidos com zero seções. A criação de uma nota exige vínculo com uma seção válida dentro do caderno.

2. **Projeções de Listagem Leves e Carregamento Sob Demanda de Markdown**:
   - Os endpoints de listagem de notas por seção (`GET /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`) projetam estritamente metadados (`id`, `cadernoId`, `secaoId`, `titulo`, `ordem`, `origemTopicoId`, `criadoEm`, `atualizadoEm`), omitindo explicitamente o atributo pesado `conteudoMarkdown` (`projection(Projections.exclude("conteudoMarkdown"))`).
   - O payload integral de markdown é carregado sob demanda apenas quando a nota específica é selecionada para leitura ou edição (`GET /api/cadernos/{cadernoId}/secoes/{secaoId}/notas/{notaId}` ou `GET /api/notas/{notaId}`).
   - **Controle de Tamanho de Payload**: validação de limite rígido de 512 KB em bytes UTF-8 para o markdown (`400 BAD_REQUEST - PAYLOAD_TOO_LARGE`).
   - **Ausência de Concorrência Otimista em Notas**: conforme as premissas de projeto, adota-se *last write wins* para anotações pessoais, sem controle de versão com `@Version` ou bloqueio pessimista.

3. **Busca Textual Full-Text no MongoDB 7**:
   - Utilização do índice textual composto pré-criado na Fase 1: `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` com `default_language: "portuguese"`.
   - **Insensibilidade a Caixa e Acentuação**: processamento nativo pelo analisador de texto do MongoDB para o idioma português.
   - **Validação Estrita de Query e Bloqueio de Regex**: consultas com menos de 2 caracteres alfanuméricos ou formadas exclusivamente por stop words em português são rejeitadas com `400 BAD_REQUEST` (`INVALID_SEARCH_QUERY`). É expressamente vedado qualquer fallback para regex em coleção completa, preservando a performance do banco.
   - **Geração Segura de Snippets**: trechos de contexto gerados em tempo de execução com no máximo 180 caracteres, delimitados ao redor dos termos pesquisados, destacados com tags semânticas e com escape seguro de entidades HTML para prevenção de XSS.

4. **Importação, Reimportação e Sincronização Inteligente com Trilhas de Estudo**:
   - **Importação Inicial (`POST /api/cadernos/importar-trilha/{trilhaId}`)**: gera caderno vinculado à trilha, convertendo disciplinas em seções e tópicos em notas individuais.
   - **Reimportação Segura**: a importação repetida da mesma trilha **não sobrescreve nem apaga** o caderno pré-existente. Um novo caderno independente é criado sob a nomenclatura `[Trilha] {Nome} (importada em DD/MM/AAAA)`.
   - **Sincronização Não-Destrutiva (`POST /api/cadernos/{cadernoId}/sincronizar-trilha`)**: compara a estrutura atual da trilha com o caderno existente, adicionando novas disciplinas como seções e novos tópicos como notas, preservando integralmente todas as seções, notas e conteúdos já modificados pelo estudante.
   - **Desvinculação em Cascata**: ao excluir uma trilha de estudo no módulo Estudos (`TrilhaService`), os cadernos e notas derivados têm suas referências `origemTrilhaId`, `origemDisciplinaId` e `origemTopicoId` limpas (`null`), assegurando que anotações acadêmicas jamais sejam perdidas.

5. **Lixeira Hierárquica com Soft-Delete de 30 Dias e Retenção por Lote**:
   - Exclusões de cadernos, seções e notas realizam soft-delete marcando `deletedAt = Instant.now()` e `expiresAt = Instant.now() + 30 dias`.
   - Operações em cascata (ex.: exclusão de caderno com seções e notas) compartilham um identificador de lote UUID (`loteExclusaoId`).
   - **Restauração Inteligente com Resolução de Ancestrais**:
     - Ao restaurar um item cujo ancestral (caderno ou seção) ainda está na lixeira, os ancestrais correspondentes são automaticamente restaurados.
     - Se o ancestral foi purgado definitivamente, uma seção de contingência com título `Restauradas` é criada/reaproveitada no caderno ativo, evitando itens órfãos.
   - **Job Diário e Purga Manual**: `PurgaLixeiraNotasJob` agendado para executar diariamente removendo documentos com `expiresAt <= agora`, complementado pelo endpoint de esvaziamento imediato (`DELETE /api/notas/lixeira/esvaziar`).

6. **Frontend — Design System `SyncStatusBadge`, Autosave com Debounce e Rascunho Local**:
   - **`SyncStatusBadge`**: componente no Design System (`frontend/src/shared/design-system/SyncStatusBadge`) com `role="status"`, `aria-live="polite"` e 4 estados (`salvo`, `salvando`, `alteracoes-locais`, `erro`). No estado de erro, oferece botão de retentativa acionável.
   - **Autosave com Debounce de 800ms**: o editor dispara salvamentos automáticos 800ms após o término da digitação do usuário.
   - **Contingência em `localStorage`**: qualquer alteração não salva é imediatamente espelhada em `localStorage` sob a chave canônica `trilhara:u:${usuarioId}:nota_draft:${notaId}` contendo `atualizadoEmLocal`.
   - **Banner de Recuperação de Rascunho**: ao abrir uma nota onde existe rascunho local mais recente do que a versão persistida no servidor, exibe barra de alerta contextual permitindo restaurar o rascunho local ou descartá-lo.
   - **Proteção de Requisições In-Flight**: salvamentos em trânsito não bloqueiam a interface nem sobrepõem alterações se o usuário navegar entre páginas rapidamente.

---

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/nota/Caderno.java
  - backend/src/main/java/br/com/trilhara/nota/Secao.java
  - backend/src/main/java/br/com/trilhara/nota/Nota.java
  - backend/src/main/java/br/com/trilhara/nota/CadernoRepository.java
  - backend/src/main/java/br/com/trilhara/nota/SecaoRepository.java
  - backend/src/main/java/br/com/trilhara/nota/NotaRepository.java
  - backend/src/main/java/br/com/trilhara/nota/NotaService.java
  - backend/src/main/java/br/com/trilhara/nota/CadernoResource.java
  - backend/src/main/java/br/com/trilhara/nota/SecaoResource.java
  - backend/src/main/java/br/com/trilhara/nota/NotaResource.java
  - backend/src/main/java/br/com/trilhara/nota/PurgaLixeiraNotasJob.java
  - backend/src/main/java/br/com/trilhara/nota/dto/CadernoResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/SecaoResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/NotaMetadataResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/NotaDetalheResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/ResultadoBuscaNotaDTO.java
  - backend/src/main/java/br/com/trilhara/nota/dto/BuscaNotasResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/ItemLixeiraResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/ExclusaoLixeiraResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/SincronizacaoTrilhaResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/RestauracaoLixeiraResponse.java
  - backend/src/main/java/br/com/trilhara/nota/dto/CriarCadernoRequest.java
  - backend/src/main/java/br/com/trilhara/nota/dto/AtualizarCadernoRequest.java
  - backend/src/main/java/br/com/trilhara/nota/dto/CriarSecaoRequest.java
  - backend/src/main/java/br/com/trilhara/nota/dto/AtualizarSecaoRequest.java
  - backend/src/main/java/br/com/trilhara/nota/dto/CriarNotaRequest.java
  - backend/src/main/java/br/com/trilhara/nota/dto/AtualizarNotaRequest.java
  - backend/src/test/java/br/com/trilhara/nota/NotaResourceTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/estudos/TrilhaService.java

Frontend (Criados):
  - frontend/src/shared/design-system/SyncStatusBadge/SyncStatusBadge.module.css
  - frontend/src/shared/design-system/SyncStatusBadge/SyncStatusBadge.tsx
  - frontend/src/shared/design-system/SyncStatusBadge/index.ts
  - frontend/src/modules/notas/components/BuscaModal/BuscaModal.module.css
  - frontend/src/modules/notas/components/BuscaModal/BuscaModal.tsx
  - frontend/src/modules/notas/components/LixeiraModal/LixeiraModal.module.css
  - frontend/src/modules/notas/components/LixeiraModal/LixeiraModal.tsx
  - frontend/src/modules/notas/components/ImportarTrilhaModal/ImportarTrilhaModal.module.css
  - frontend/src/modules/notas/components/ImportarTrilhaModal/ImportarTrilhaModal.tsx
  - frontend/src/test/fase8Notas.test.tsx

Frontend (Modificados):
  - frontend/src/shared/design-system/index.ts
  - frontend/src/modules/notas/types/nota.ts
  - frontend/src/modules/notas/services/notaService.ts
  - frontend/src/modules/notas/components/Editor/TiptapEditor.module.css
  - frontend/src/modules/notas/components/Editor/TiptapEditor.tsx
  - frontend/src/modules/notas/components/Navbar/CadernoContextPod.module.css
  - frontend/src/modules/notas/components/Navbar/CadernoContextPod.tsx
  - frontend/src/modules/notas/components/Navbar/Navbar.tsx
  - frontend/src/modules/notas/NotasApp.tsx
```

---

### 3. Checklist de Validação da FASE 8

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **8.1** | Reestruturação em 3 coleções BSON normalizadas (`cadernos`, `secoes`, `notas`) com `ObjectId` nativo | **Atendido** | Entidades e repositórios BSON operando sem embedding de seções/páginas |
| **8.2** | Caderno permitido com zero seções; criação de nota exige `secaoId` válido no caderno | **Atendido** | Validado em `NotaResourceTest#devePermitirCadernoComZeroSecoesECriarNotaVinculadaASecao` |
| **8.3** | Projeções de listagem de páginas/seções excluindo `conteudoMarkdown` | **Atendido** | `NotaRepository.listarPorSecao` aplica `Projections.exclude("conteudoMarkdown")`; testado no backend e frontend |
| **8.4** | Carregamento sob demanda do markdown completo da nota | **Atendido** | Validado em `NotaResourceTest` (`GET /api/cadernos/{cId}/secoes/{sId}/notas/{nId}`) e no Vitest em `fase8Notas.test.tsx` |
| **8.5** | Limite máximo de 512 KB no tamanho do markdown UTF-8 (`400 BAD_REQUEST`) | **Atendido** | Validado em `NotaResourceTest#deveRejeitarNotaComMarkdownMaiorQue512KB` |
| **8.6** | Busca textual MongoDB 7 com índice composto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` em português | **Atendido** | Índice text composto validado em `MongoIndexInitializerTest` e consumido em `NotaRepository.buscarTexto` |
| **8.7** | Rejeição de busca inválida com `400 BAD_REQUEST` (`INVALID_SEARCH_QUERY`) para termo < 2 alfanuméricos ou stop words | **Atendido** | Validado em `NotaResourceTest#deveRejeitarBuscaInvalidaComMenosDe2CaracteresOuApenasStopWords` |
| **8.8** | Snippets de busca truncados em no máximo 180 caracteres, com marcação segura e escape contra XSS | **Atendido** | Implementado em `NotaService.gerarSnippetComHighlight` e validado em `NotaResourceTest#deveRealizarBuscaTextualERetornarSnippetsComLimiteDe180Chars` |
| **8.9** | Ausência estrita de fallback para regex em coleção completa de notas | **Atendido** | Consultas utilizam estritamente o operador `$text` e queries BSON indexadas |
| **8.10** | Sem concorrência otimista com versionamento em notas (last write wins) | **Atendido** | Entidade `Nota.java` sem anotação `@Version`, atualizações de conteúdo via `$set` atômico |
| **8.11** | Importação de Trilha de Estudos para Caderno de Notas (disciplinas -> seções, tópicos -> notas) | **Atendido** | Validado em `NotaResourceTest#deveImportarTrilhaDeEstudosParaCaderno` |
| **8.12** | Reimportação preserva o caderno anterior e gera um novo caderno referenciado | **Atendido** | Validado em `NotaResourceTest#deveReimportarTrilhaGerandoNovoCadernoSemSobrescreverOAnterior` |
| **8.13** | Sincronização explícita de Trilha sem apagar ou sobrescrever notas e seções existentes | **Atendido** | Validado em `NotaResourceTest#deveSincronizarTrilhaSemApagarOuSobrescreverItensModificados` |
| **8.14** | Lixeira hierárquica com soft-delete de 30 dias (`expiresAt = deletedAt + 30d`) e `loteExclusaoId` | **Atendido** | Validado em `NotaResourceTest#deveGerenciarLixeiraHierarquicaComExclusaoEmLoteERestauracao` |
| **8.15** | Restauração inteligente de itens com auto-restauração de ancestrais ou realocação para seção `Restauradas` se purgados | **Atendido** | Validado em `NotaResourceTest#deveRestaurarNotaParaSecaoRestauradasSeAncestraisForamPurgados` |
| **8.16** | Job de purga automática diária (`PurgaLixeiraNotasJob`) e esvaziamento manual imediato | **Atendido** | Job agendado e endpoint `DELETE /api/notas/lixeira/esvaziar` validado em `NotaResourceTest` |
| **8.17** | Desvinculação em cascata ao excluir Trilha: cadernos têm `origemTrilhaId` limpo e anotações preservadas | **Atendido** | Validado em `NotaResourceTest#deveDesvincularCadernosAoExcluirTrilhaPreservandoConteudo` |
| **8.18** | Design System `SyncStatusBadge` com estados `salvo`, `salvando`, `alteracoes-locais`, `erro`, `role="status"` e retry | **Atendido** | Validado em `fase8Notas.test.tsx` com `role="status"`, `aria-live="polite"` e clique de retry |
| **8.19** | `TiptapEditor` com autosave 800ms, contingência em `localStorage` e banner de recuperação de rascunho | **Atendido** | Validado em `fase8Notas.test.tsx` com chave `trilhara:u:${usuarioId}:nota_draft:${notaId}` e ações de restaurar/descartar |
| **8.20** | Modal de Importação com checkbox persistente "Não mostrar sugestão automaticamente" | **Atendido** | Implementado em `ImportarTrilhaModal.tsx` com gravação na chave do usuário |
| **8.21** | Modais de Busca Global e Lixeira com restauração e confirmação segura | **Atendido** | Implementados em `BuscaModal.tsx` e `LixeiraModal.tsx` com integração visual no Pod de contexto do Caderno |
| **8.22** | Sucesso em 100% dos testes automatizados de frontend Vitest (`npm test -- --run`) | **Atendido** | 11 arquivos de teste, 86/86 testes aprovados (13 testes exclusivos da Fase 8) |
| **8.23** | Sucesso completo no build de produção frontend (`npm run build`) | **Atendido** | `tsc -b && vite build` finalizado com sucesso sem erros de tipagem |
| **8.24** | Sucesso completo na suíte de testes backend com Quarkus (`mvn test`) | **Atendido** | 99 testes executados com 0 falhas e 0 erros |



---

## FASE 9 — Exclusão de Conta nas 18 Coleções Privadas e Isolamento Ponta a Ponta

### 1. Resumo das Implementações Realizadas

#### Backend (Quarkus / Java 25)
1. **Contrato de Exclusão de Conta (`DELETE /api/auth/conta`):**
   - Implementado endpoint em `AuthResource.java` com resposta `202 ACCEPTED` e payload padronizado RFC 9457 / Trilhara contendo `{ "operationId": "...", "estado": "PENDENTE" }`.
   - Adicionada expiração imediata de cookies nos cabeçalhos de resposta: `trilhara_rt` e `trilhara_session_state` com `Max-Age=0`, `Path=/`, `HttpOnly` e flags seguras (`SameSite=Strict`, `Secure` em produção).
   - Suporte a cabeçalho `Idempotency-Key` e corpo `ExcluirContaRequest` contendo `senha` (validação com Argon2id/BCrypt via `PasswordService`), `confirmacao` obrigatória ("EXCLUIR MINHA CONTA") e `idempotencyKey` opcional.
   - Idempotência: caso a solicitação seja repetida com a mesma chave ou com a conta já desativada (`ativo=false`), o serviço retorna o status atual da operação sem duplicar tarefas em segundo plano.
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

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/auth/AccountDeletionService.java
  - backend/src/main/java/br/com/trilhara/auth/AccountDeletionJob.java
  - backend/src/main/java/br/com/trilhara/auth/dto/ExcluirContaRequest.java
  - backend/src/main/java/br/com/trilhara/auth/dto/AccountDeletionResponse.java
  - backend/src/test/java/br/com/trilhara/auth/AccountDeletionIntegrationTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/auth/AuthResource.java
  - backend/src/main/java/br/com/trilhara/auth/SecurityFilter.java
  - backend/src/main/java/br/com/trilhara/auth/RefreshTokenRepository.java
  - backend/src/main/java/br/com/trilhara/auth/TokenRecuperacaoRepository.java
  - backend/src/main/java/br/com/trilhara/auth/TokenConfirmacaoEmailRepository.java
  - backend/src/test/java/br/com/trilhara/shared/mongo/AuthCookieDomainTest.java

Frontend (Criados):
  - frontend/src/shared/auth/ContaModal.tsx
  - frontend/src/shared/auth/ContaModal.module.css
  - frontend/src/test/fase9ExclusaoConta.test.tsx

Frontend (Modificados):
  - frontend/src/shared/auth/authStorage.ts
  - frontend/src/shared/auth/authService.ts
  - frontend/src/shared/auth/AuthContext.tsx
  - frontend/src/shared/auth/index.ts
  - frontend/src/shared/design-system/ConfirmDialog/ConfirmDialog.tsx
  - frontend/src/shared/design-system/AppSidebar/AppSidebar.tsx
  - frontend/src/modules/estudos/EstudosApp.tsx
  - frontend/src/modules/tarefas/TarefasApp.tsx
  - frontend/src/modules/notas/NotasApp.tsx
  - frontend/src/test/fase5GlobalSessionTimer.test.tsx
  - frontend/src/test/fase7IntegracaoDiaria.test.tsx
```

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

### 2. Lista de Arquivos Alterados e Criados

```text
Backend (Criados):
  - backend/src/main/java/br/com/trilhara/push/PushSubscription.java
  - backend/src/main/java/br/com/trilhara/push/LembretePush.java
  - backend/src/main/java/br/com/trilhara/push/PushSubscriptionRepository.java
  - backend/src/main/java/br/com/trilhara/push/LembretePushRepository.java
  - backend/src/main/java/br/com/trilhara/push/LembretePushCalculator.java
  - backend/src/main/java/br/com/trilhara/push/LembretePushService.java
  - backend/src/main/java/br/com/trilhara/push/PushDeliveryScheduler.java
  - backend/src/main/java/br/com/trilhara/push/WebPushSender.java
  - backend/src/main/java/br/com/trilhara/push/DefaultWebPushSender.java
  - backend/src/main/java/br/com/trilhara/push/PushResource.java
  - backend/src/main/java/br/com/trilhara/push/InvalidLocalDatetimeException.java
  - backend/src/main/java/br/com/trilhara/push/dto/PushConfigResponse.java
  - backend/src/main/java/br/com/trilhara/push/dto/PushSubscriptionRequest.java
  - backend/src/main/java/br/com/trilhara/push/dto/PushSubscriptionResponse.java
  - backend/src/main/java/br/com/trilhara/push/dto/AtualizarPreferenciasPushRequest.java
  - backend/src/main/java/br/com/trilhara/push/dto/AtualizarPreferenciasPushResponse.java
  - backend/src/test/java/br/com/trilhara/push/LembretePushCalculatorTest.java
  - backend/src/test/java/br/com/trilhara/push/LembretePushLifecycleTest.java
  - backend/src/test/java/br/com/trilhara/push/PushDeliverySchedulerTest.java
  - backend/src/test/java/br/com/trilhara/push/PushResourceTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaRepository.java
  - backend/src/main/java/br/com/trilhara/tarefa/TarefaService.java
  - backend/src/main/java/br/com/trilhara/auth/UserRepository.java
  - backend/src/main/java/br/com/trilhara/auth/SecurityFilter.java
  - backend/src/main/resources/application.properties

PWA & Infraestrutura (Criados e Modificados):
  - frontend/public/manifest.webmanifest
  - frontend/public/sw.js
  - frontend/index.html
  - Caddyfile

Frontend (Criados):
  - frontend/src/shared/services/pushService.ts
  - frontend/src/modules/tarefas/components/ConfiguracaoLembreteModal/ConfiguracaoLembreteModal.tsx
  - frontend/src/test/fase10WebPush.test.tsx

Frontend (Modificados):
  - frontend/src/modules/tarefas/components/TaskModal/TaskModal.tsx
  - frontend/src/modules/tarefas/TarefasApp.tsx
```

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

## FASE 11 — Hardening Final, Acessibilidade WCAG 2.2 AA, Benchmark `explain("executionStats")` e Limpeza Final

### 1. Relatório Técnico Resumido da Fase 11

A **FASE 11** conclui integralmente a reestruturação arquitetural do ecossistema Trilhara, realizando a auditoria transversal de segurança e autorização anti-IDOR, adequação estrita aos padrões de acessibilidade WCAG 2.2 Level AA, validação sintética do plano de índices do MongoDB 7 com `explain("executionStats")` e fechamento da documentação de execução e variáveis de ambiente.

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

### 2. Arquivos Criados e Modificados na FASE 11

```text
Backend (Criados):
  - backend/src/test/java/br/com/trilhara/security/AuthorizationByOwnerTest.java
  - backend/src/test/java/br/com/trilhara/benchmark/MongoExplainBenchmarkTest.java

Backend (Modificados):
  - backend/src/main/java/br/com/trilhara/push/PushResource.java

Frontend (Criados):
  - frontend/src/test/fase11Accessibility.test.tsx

Frontend (Modificados):
  - frontend/src/shared/design-system/Modal/Modal.tsx
  - frontend/src/shared/design-system/FormModal/FormModal.tsx
  - frontend/src/modules/estudos/views/EstudarView.tsx
  - frontend/src/modules/pomodoro/PomodoroApp.tsx
  - frontend/src/shared/styles/index.css

Documentação e Configuração (Criados e Modificados):
  - .env.example
  - README.md
```

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
