# R10 — Revisão Implementada (F9: exclusão de conta e isolamento ponta a ponta)

> Relatório da revisão **R10**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R9.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-13 · backend Java 25/Quarkus 3.40.1 + frontend React 18/Vite 6.

---

## 1. Achados confirmados e descartados

### 1.1 Já conformes no repositório (hipóteses descartadas com evidência)

| Achado do guia | Veredito | Evidência |
|---|---|---|
| Inventário sem `tokens_handoff_sso` e com coleção de operações | **Descartado** | `ColecaoTrilhara.java:21` declara `TOKENS_HANDOFF_SSO`; `filhasPrivadas()` devolve 18; enum tem 20 (19 privadas + `editais`); `HandoffService.java:59` persiste na coleção formalizada; `InventarioColecoesTest:35` tem `ALLOWLIST_LEGADO = Set.of()` (nenhum resíduo tolerado) |
| `AccountDeletionService` não itera o inventário | **Descartado** | `COLECOES_FILHAS = ColecaoTrilhara.nomesFilhasPrivadas()` (fonte única) |
| Ordem filhas → contagem zero → `usuarios` | **Descartado** | laço em `filhasPrivadas()`, verificação de contagem zero, `deleteOne` na raiz por último |
| `AppSidebar` já tinha entrada de Conta adequada | **Descartado** | existia apenas clique no avatar, alimentado por prop `onOpenAccount` propagada por app |
| `FaultInjector` inexistente no projeto | **Descartado** | `cronograma/FaultInjector` (NoOp/Controlavel) já existia desde a R5; faltava o equivalente em auth |

### 1.2 Confirmados e corrigidos

| # | Achado | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-35** | `FALHA` era estado terminal silencioso | `AccountDeletionService` gravava `estado=FALHA` e parava | Estados `PENDENTE → EM_ANDAMENTO → (RETENTANDO_LENTO)*`; após `max-tentativas` a conta segue em retentativa horária indefinida |
| **D-35b** | Sem backoff nem `proximaTentativa` | apenas `Updates.inc("tentativas")` | Backoff 1s→2s→4s→8s→16s (teto 30s) em `proximaTentativa`; `RETENTANDO_LENTO` = 1h; métrica `trilhara_account_deletion_stuck` |
| **RI-34/D-04** | `usuarioId` em logs | `AuthService` (10 linhas) e `RefreshTokenService` (2) logavam `usuarioId=%s` | `Pseudonimo.de(...)` (8 hex de SHA-256) + `event=`; `grep usuarioId \| grep log.` → **0** |
| **SEC-11/D-35a** | `202` sem media type explícito | `Response.status(ACCEPTED).entity(...)` | `.type(MediaType.APPLICATION_JSON)` |
| **SEC-15/D-35e** | Hook de falha em produção | `HookFalhaExclusao` + `AtomicReference` + `setHookFalha` públicos em `src/main` | Removidos; substituídos por `FaultInjectorExclusaoConta` (NoOp em produção, `@Alternative` só em `src/test`) |
| **D-08** | Exclusão não coberta por teste dirigido pelo enum | fixture sequencial hardcoded | `FixturesConta` dirigida por `filhasPrivadas()` com `validarCobertura()` |
| **RI-36** | Pomodoro sem entrada de Conta | `PomodoroApp` só tinha `abrirAuthModal` | `ContaMenuButton variant="header"` no `headerTopActions` |
| **D-35f** | Entrada de Conta propagada por prop em 3 apps | `onAbrirConta`/`onOpenAccount` em 3 Navbars + 3 apps | `ContaMenuButton` no DS lê `AuthContext` direto; props removidas |

### 1.3 Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | `ExecutorService` fixo de 2 threads criado no construtor, sem ciclo de vida CDI | `Executors.newFixedThreadPool(2)` | Removido: a limpeza é dirigida pelo checkpoint em `usuarios.exclusaoConta` pelo job periódico (sem estado em memória; sobrevive a reinício) |
| **N-2** | `SecurityFilter` usava `ativo=false` como proxy de "conta em exclusão" | só `usuario.ativo()` | `UserRepository.estaEmExclusao(...)` (projeção pontual): conta em exclusão → `401` em qualquer rota, exceto o retry do próprio `DELETE` **com** `Idempotency-Key` |
| **N-3** | Nenhum teste de log no backend | nenhum `Handler`/captura em `src/test` | `support/CapturadorDeLogs` (JUL, `AutoCloseable`) |
| **N-4** | Job de 5 min reprocessava toda conta com `exclusaoConta`, ignorando backoff | `find(exists(exclusaoConta))` | Filtro por `proximaTentativa <= agora`; `every=30s`; `%test` desativa o disparo autônomo |
| **N-5** | Idempotência comparava apenas a string da chave | `idempotencyKey.equals(existing)` | `hashPayload` (SHA-256 do payload funcional, nunca da senha): mesma chave + payload diferente → `409 IDEMPOTENCY_KEY_CONFLICT` |
| **N-6** | Chave de idempotência aceita no corpo **e** no cabeçalho | `ExcluirContaRequest.idempotencyKey` | Campo removido do DTO; header é o único canal |
| **N-7** | `Idempotency-Key` era opcional | fallback para `operationId` | Obrigatório → `400 IDEMPOTENCY_KEY_REQUIRED` |
| **N-8** | Frase de confirmação aceita em qualquer caixa | `equalsIgnoreCase` | `"EXCLUIR MINHA CONTA".equals(trim())` → `400 VALIDATION_ERROR` com `errors.confirmacao` |
| **N-9** | Sem rate limit no fluxo de exclusão (D-45) | `RateLimitService` sem método | `verificarLimiteExclusaoConta` (5/15 min/usuário, janela própria) → `429` + `Retry-After` |
| **N-10** | Frontend não tratava 401 pós-202 nem 429 | `request` tentava refresh e abria `AuthModal` | `semRefresh` em `request`; `ApiError.retryAfterSeconds`; `ContaModal` traduz 401/409/429 em erro inline |
| **N-11** | Sem redirecionamento nem mensagem após o 202 | `AuthContext` só limpava e fechava | `redirecionarAposExclusaoDeConta()` + `AvisoContaExcluida` nos 4 módulos com a mensagem normativa |
| **N-12** | Limpeza local duplicada (contexto + serviço) | `AuthContext` e `AuthService` chamavam `limparDadosLocaisDoUsuario` | Só o `AuthContext` limpa (uma vez, sem broadcast duplicado) |
| **N-13** | `fase9ExclusaoConta.test.tsx` alimentava `E3b-arquivos-por-fase` | 1 achado do gate | Split em `contaModal.test.tsx`, `authExclusaoConta.test.ts`, `appSidebarConta.test.tsx`; `E3b` caiu 4 → 3 |

---

## 2. Contratos congelados (D-35)

### 2.1 `DELETE /api/auth/conta`

| Item | Contrato |
|---|---|
| Autenticação | obrigatória (`401` sem token) |
| `Idempotency-Key` | **obrigatório** no cabeçalho; ausente → `400 IDEMPOTENCY_KEY_REQUIRED` |
| Body | `{ senha, confirmacao: "EXCLUIR MINHA CONTA" }` — frase exata, sensível a caixa |
| Senha inválida | `401 UNAUTHORIZED`, **sem efeito colateral** (validada antes de qualquer escrita) |
| Rate limit | 5 tentativas/15 min/usuário → `429 RATE_LIMITED` + `Retry-After` |
| Sucesso | `202` + `Content-Type: application/json` + `{ operationId, estado }` |
| Repetição | mesma chave + mesmo payload → `202` com o **mesmo** `operationId`; payload diferente ou outra chave → `409 IDEMPOTENCY_KEY_CONFLICT` |
| Cookies | `trilhara_rt` e `trilhara_session_state` com `Max-Age=0` |
| Pós-202 | qualquer rota autenticada → `401`; retry do `DELETE` sem chave → `401` |

### 2.2 Máquina de estados

```
PENDENTE ──> EM_ANDAMENTO ──(sucesso)──> [usuarios removido] + log/métrica ACCOUNT_DELETION_CONCLUDED
                    │
                    ├──(falha 1..4)──> EM_ANDAMENTO (proximaTentativa = +1s, 2s, 4s, 8s, 16s)
                    └──(falha 5)─────> RETENTANDO_LENTO (proximaTentativa = +1h, métrica stuck)
                                            └──(nova falha)──> RETENTANDO_LENTO (+1h, indefinido)
```

### 2.3 Logs (sem PII)

| Evento | Campos |
|---|---|
| `USER_REGISTERED`, `LOGIN_SUCESSO`, `LOGIN_CONTA_*`, `CONTA_BLOQUEADA_BRUTE_FORCE`, `SENHA_ALTERADA`, `SENHA_REDEFINIDA`, `TOKEN_*`, `EMAIL_VERIFICADO` | `event`, `conta` (pseudônimo de 8 hex), `ip` |
| `REFRESH_GRACE_PERIOD`, `REFRESH_TOKEN_REUSO` | `event`, `conta`, `sessao` (pseudônimos), `ip` |
| `ACCOUNT_DELETION_*` | `event`, `operationId`, `colecao` |

Nenhum evento carrega `usuarioId`, e-mail ou nome.

---

## 3. Arquivos criados / alterados / removidos

### Backend — criados
`auth/FaultInjectorExclusaoConta.java` · `auth/FaultInjectorExclusaoContaNoOp.java` ·
`shared/Pseudonimo.java`

### Backend — alterados
`auth/AccountDeletionService.java` · `auth/AccountDeletionJob.java` · `auth/AuthResource.java` ·
`auth/SecurityFilter.java` · `auth/UserRepository.java` · `auth/RateLimitService.java` ·
`auth/AuthService.java` · `auth/RefreshTokenService.java` · `auth/dto/ExcluirContaRequest.java` ·
`auth/dto/AccountDeletionResponse.java` · `pom.xml` · `application.properties`

### Backend — testes
Criados: `support/CapturadorDeLogs.java` · `auth/FaultInjectorExclusaoContaControlavel.java` ·
`auth/FixturesConta.java`
Reescrito: `auth/AccountDeletionIntegrationTest.java`

### Frontend — criados
`shared/design-system/ContaMenuButton/ContaMenuButton.tsx` (+ `.module.css`) ·
`shared/design-system/AvisoContaExcluida/AvisoContaExcluida.tsx` (+ `.module.css`) ·
`shared/auth/redirecionamentoConta.ts`

### Frontend — alterados
`shared/design-system/index.ts` · `shared/design-system/AppSidebar/AppSidebar.tsx` ·
`shared/auth/api.ts` · `shared/auth/authService.ts` · `shared/auth/AuthContext.tsx` ·
`shared/auth/ContaModal.tsx` · `shared/auth/index.ts` ·
`modules/estudos/components/Navbar/Navbar.tsx` · `modules/notas/components/Navbar/Navbar.tsx` ·
`modules/tarefas/components/Navbar/Navbar.tsx` · `modules/estudos/EstudosApp.tsx` ·
`modules/notas/NotasApp.tsx` · `modules/tarefas/TarefasApp.tsx` · `modules/pomodoro/PomodoroApp.tsx`

### Frontend — testes
Criados: `test/contaModal.test.tsx` · `test/authExclusaoConta.test.ts` · `test/appSidebarConta.test.tsx`
Removido: `test/fase9ExclusaoConta.test.tsx` (conteúdo redistribuído; `E3b` 4 → 3)
Mantido: `test/authStorage.test.ts`

---

## 4. Testes nomeados (R10)

### Backend — `auth/AccountDeletionIntegrationTest` (29 casos)

`excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital` ·
`falhaInjetadaEmCadaColecaoRetomaSemOrfaos` (`@ParameterizedTest` sobre as **18** filhas) ·
`apos5FalhasEntraEmRetentandoLentoENaoConclui` · `retomadaAposRetentandoLentoConcluiSemOrfaos` ·
`semIdempotencyKeyRetorna400` · `senhaInvalidaRetorna401ESemEfeito` ·
`confirmacaoEmMinusculasRetorna400` · `respostaNaoEhProblemJson` ·
`retryComMesmaChaveRetorna202ComMesmoOperationId` · `outraChaveRetorna409` ·
`pos202QualquerRotaRetorna401` · `logsNaoContemUsuarioId`

Suportes: `FixturesConta.validarCobertura()` (faltar fixture para uma filha falha o teste),
`CapturadorDeLogs` (JUL), `FaultInjectorExclusaoContaControlavel` (`@Alternative` só em teste).

### Frontend (3 suítes novas, 24 casos)

- `contaModal.test.tsx` (8): dados da conta + Zona de Perigo; `ConfirmDialog danger` antes da
  senha; botão desabilitado sem senha; desabilitado com frase divergente; habilitado só com frase
  exata + senha; `onExcluirConta` chamado 1× com a senha; 401 → "Senha atual incorreta." inline
  (modal permanece aberto); 429 → "Tente novamente em 42 segundos."
- `authExclusaoConta.test.ts` (9): header `Idempotency-Key` e corpo sem a chave; geração de chave;
  limpeza de `trilhara:u:{id}:*` em `localStorage`/`sessionStorage` preservando B; evento local;
  broadcast `CONTA_EXCLUIDA` recebido por outra aba; `location.assign` com `?conta=excluida`;
  mensagem normativa + remoção do parâmetro; ausência de mensagem sem o parâmetro; 401 chega como
  `ApiError` 401
- `appSidebarConta.test.tsx` (7): entrada de Conta no rodapé desktop **e** no header mobile da
  `AppSidebar`; ausente para visitante; header do Pomodoro (RI-36); presença nos 4 módulos

---

## 5. Gates executados

| Gate | Comando | Resultado |
|---|---|---|
| Backend (compila) | `./mvnw -o -q -DskipTests test-compile` | OK |
| Backend (suíte) | `./mvnw -o test` | **313 testes, 0 falhas, 0 erros, 7 skipped** |
| Frontend tipos | `npx tsc -p tsconfig.json --noEmit` | 0 erros |
| Frontend lint | `npx eslint src --max-warnings 0` | 0 problemas |
| Frontend testes | `npx vitest run` | **47 arquivos / 312 testes** |
| Frontend build | `npm run build` | OK (aviso herdado de chunk > 500 kB) |
| Exports ociosos | `npx knip` | limpo |
| Gate de legado | `bash scripts/verificar-legado.sh` | **14 achados** (era 15) |

### Gate de legado — composição dos 14

| Padrão | Achados | Origem |
|---|---|---|
| `serializacao-json` | 4 | herdado de R12/R13 (`PushDeliveryScheduler`, `EditalRepository`×2, `EmailService`) |
| `LocalDate.now` | 1 | herdado de R13 (`EditalRepository:99`) |
| `ZoneId-America/Sao_Paulo` | 1 | herdado de R13 (`LembretePushCalculator:37`) |
| `termo-ciclo-frontend` | 5 | herdado (nomenclatura de ciclos do Pomodoro) |
| `E3b-arquivos-por-fase` | 3 | **caiu de 4 → 3** com a remoção de `fase9ExclusaoConta.test.tsx` |

**Nenhum achado novo introduzido pela R10** e o padrão `E3b` foi reduzido. Os 14 restantes não
pertencem ao escopo de exclusão de conta e continuam sendo dívida de R12/R13.

### Comandos de verificação dos critérios

```
grep -rn "testFailOnCollectionHook\|setHookFalha\|HookFalhaExclusao" backend/src   -> 0
grep -rn "usuarioId" backend/src/main --include=*.java | grep -i "log\."           -> 0
grep -rn "onAbrirConta\|onOpenAccount" frontend/src --include=*.tsx                -> so ContaMenuButton
```

---

## 6. Resultado final das suítes

```
Backend:  Tests run: 313, Failures: 0, Errors: 0, Skipped: 7   -> BUILD SUCCESS
Frontend: Test Files 47 passed (47) · Tests 312 passed (312)
```

Comparativo com a linha de base da R9 (backend 288 / frontend 45 arquivos · 293 testes):
**+25 testes backend** e **+19 testes frontend** em 3 arquivos novos, com
`fase9ExclusaoConta.test.tsx` eliminado pelo padrão `E3b`.

`Skipped: 7` no backend é herdado (testes condicionais de ambiente).

---

## 7. Pendências e limites declarados

1. **Gate de legado ainda falha** com 14 achados herdados de R12/R13 (`serializacao-json` ×4,
   `LocalDate.now`, `ZoneId`, `termo-ciclo-frontend` ×5, `E3b` ×3). A R10 **não** os declara
   resolvidos: nenhum pertence ao domínio de exclusão de conta. O critério da revisão é *não
   introduzir achados novos* — atendido — e o `E3b` foi reduzido de 4 para 3.
2. **Métrica `trilhara_account_deletion_stuck`**: exposta via `MeterRegistry` do
   `quarkus-micrometer` (dependência nova no `pom.xml`). O backend **não** inclui o registry
   Prometheus (`quarkus-micrometer-registry-prometheus`), portanto o contador é observável pelo
   `MeterRegistry` injetado (e pelos testes) mas não é raspado por `/q/metrics` até que o registry
   seja adicionado. Registrado como lacuna de observabilidade, fora do escopo funcional da R10.
3. **`%test.trilhara.jobs.account-deletion.every=60m`**: o job não dispara sozinho em teste; as
   suítes chamam `processarExclusoesPendentes()` explicitamente. Em produção o intervalo é 30 s.
4. **Fixtures exigem Mongo acessível**: o profile `%test` usa `MONGODB_URL` (o compose do
   repositório publica `trilhara_mongo` apenas na rede interna). A execução local usou
   `MONGODB_URL=mongodb://<ip-do-container>:27017` e `RESEND_API_KEY` de teste; sem essas
   variáveis o Quarkus não inicia (config `trilhara.email.resend-api-key`).
5. **Operação composta sem transação**: a limpeza percorre 18 coleções em sequência. Uma falha no
   meio deixa estado parcial, explicitado no checkpoint (`ultimaColecaoProcessada`, `erroColecao`)
   e recuperável pelo job. Não há promessa de atomicidade multi-documento.
6. **`202` não é Problem Details**: contrato documentado em `docs/CONTRATO-ERROS.md`
   §"Exceção documentada", porque é o único `2xx` com media type fixado e forma própria.




