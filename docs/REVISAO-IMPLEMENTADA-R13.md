# R13 — Revisão Implementada (Limpeza de Código Morto e Hardening de Segurança Transversal)

> Relatório da revisão **R13** (gate final da reestruturação). Complementa
> `docs/REVISAO-IMPLEMENTADA.md` (R0) e os relatórios R1–R12.
> Regra mantida: **nenhum critério é declarado "Atendido" sem teste automatizado nomeado**.
>
> Execução: 2026-11-13 · Java 25 / Quarkus 3.40.1 · React 18 / Vite 6 · MongoDB 7

---

## 0. Baseline × resultado

| Item | Baseline (pós-R12) | R13 |
|---|---|---|
| Backend `./mvnw -q verify` | 472 testes, 0 falhas | **494 testes, 0 falhas, 10 skipped** (benchmark opt-in) |
| Frontend `npm test -- --run --coverage` | 438 testes, sem cobertura | **57 arquivos / 438 testes verdes** |
| `npm run typecheck` | verde | verde |
| `npm run lint -- --max-warnings 0` | verde | verde |
| `npx knip` | verde (falso negativo) | verde com `entry` de produção |
| `bash scripts/verificar-legado.sh` | 0 achados, **com 2 falsos negativos** | **0 achados com regex endurecida** |
| `dependency:analyze` | não executável (bytecode Java 25) | **limpo** |
| JaCoCo | 25,5% (não instrumentava `@QuarkusTest`) | **80,0% com `check` enforce** |
| Vitest coverage | inexistente | **enforce, medido (ver §3)** |
| Cabeçalhos D-47 | ausentes | **5/5 origens verdes** |

---

## 1. Achados do guia **descartados** (com evidência)

O guia da R13 foi escrito sem inspeção do código. Os achados abaixo estavam listados como
suspeitas e **não se confirmaram**:

| Hipótese do guia | Veredito | Evidência |
|---|---|---|
| Pacotes `br.com.trilhara.{estudos,tarefas,notas}` ainda existem | **Descartado** | `find backend/src/main/java -type d` → só `auth, cronograma, edital, nota, push, sessao, shared, tarefa, trilha` (+`dto`) |
| Classes `AjusteQuestoes`, `TipoSessao`, `TipoRegistroSessao`, `NovaSessao`, `PlanejamentoCiclo*`, `DisciplinaProgramada`, `CicloAtivoResponse`, `OrigemTarefa`, `OperacaoConcluirAtrasadosRepository` | **Descartado** | padrao `simbolos-legado` do gate → 0 ocorrências |
| Método `removerTarefasDeCicloLegadas` | **Descartado** | idem |
| Constante `USUARIO_LOCAL_PADRAO` / fallback `"local"` | **Descartado** | `UsuarioAtual.donoId()` só aceita ObjectId do token; fallback existe **apenas** em `LaunchMode.TEST` via `%test.trilhara.auth.test-fallback-user-id` |
| `tarefaStorage.ts` não migrado em R7 | **Descartado** | arquivo não existe; `estudosStorage.ts` é o único storage de domínio e tem consumidores |
| Chaves de storage antigas | **Descartado** | padrões `storage-inseguro` e `canais-antigos` → 0 |
| CSS modules sem uso | **Descartado** | varredura de todos os `*.module.css` sem import → 0 |
| Dependências npm não usadas | **Descartado** | `knip` → 0 achados |
| Componentes/hooks/serviços sem import | **Descartado** | `knip` → 0; `useRecurso` tem consumidor real em `shared/hooks` e testes |
| Frontend com código morto/warnings | **Descartado** | `lint --max-warnings 0` e `typecheck` verdes |
| `InventarioColecoesTest` / `AuthorizationByOwnerTest` insuficientes | **Descartado** | `AuthorizationByOwnerTest` é gerado por allowlist (`RotasPrivadas.casosDeclarados()`, 56 casos) + guard de classificação total |
| `docs/CONTRATO-ERROS.md` desatualizado | **Descartado** | catálogo bate 1:1 com o enum `CodigoErro` (22 códigos); `NETWORK_ERROR`/`UNKNOWN` são exclusivos de transporte e estão documentados como tal |
| Pacotes duplicados (META-02) | **Descartado** | nenhum diretório repetido; `pacotesLegadosProibidos` ativo no ArchUnit |
| `scripts/verificar-legado.sh` ainda com achados | **Descartado** (mas com defeito, ver §1.1) | modo estrito → 0 achados |

### 1.1 Achado crítico: o gate anti-legado era cego (não estava no guia)

O script reportava 0 achados, mas os dois padrões de nomenclatura por fase **nunca podiam
falhar**. Dois defeitos independentes:

1. **`E3` filtrado pela allowlist de teste.** `ALLOWLIST_TESTE` inclui `backend/src/test/`
   (`verificar-legado.sh:67-70`), então `class Fase6...` era descartado antes de contar. O
   padrão existe exatamente para varrer `src/test`.
2. **`E3b` com regex impossível.** Casava `fase[0-9].*\.test\.`, mas os arquivos Java são
   `Fase6*Test.java` (sem `.test.`) e o padrão era case-sensitive.

**Prova de que a correção funciona:** com as regex corrigidas e a allowlist desativada para
esses dois padrões, o gate passou a falhar com **6 achados reais** (4 blocos `describe('FASE n')`
+ 2 arquivos `Fase6*Test.java`). Após a renomeação do Bloco 3, voltou a 0.

---

## 2. Achados **confirmados** e corrigidos

| # | Achado | Evidência antes | Correção |
|---|---|---|---|
| **C1** | D-45 incompleto: faltavam 6 de 9 limitadores | `grep verificarLimite` → só login/cadastro/recuperação/reset/confirmação/exclusão | `RateLimitService` ganhou `verificarLimiteRefresh` (10/min IP), `HandoffEmissao` (10/min IP), `HandoffConsumo` (20/min IP), `BuscaNotas` (60/min usuário), `CriacaoTrilha` e `CriacaoCronograma` (30/min usuário), conectados em `AuthService.renovarToken`, `AuthResource.emitirHandoff/consumirHandoff`, `NotaResource.buscaGlobal`, `TrilhaService.criarTrilha`, `CronogramaService.criar` |
| **C2** | D-47 ausente: sem CSP, `Permissions-Policy`, HSTS | `security_headers` tinha só `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` | `Caddyfile` com CSP estrita, `Permissions-Policy`, HSTS condicional a `{scheme} == "https"` (não quebra dev em `:80`) |
| **C3** | CSP conflitava com Google Fonts | `index.html:14-19` carregava `fonts.googleapis.com` + `fonts.gstatic.com`; `default-src 'self'` bloquearia | **Self-host**: 6 `woff2` (latin/latin-ext) em `frontend/public/fonts/` + `src/shared/styles/fonts.css` com `@font-face`; tags externas removidas |
| **C4** | ArchUnit com 2 `@ArchIgnore` e regras semanticamente vazias | `TenantIsolationArchTest:118,196`; ambas eram `noClasses().that(resideInAPackage("..job..")).should().bePublic()` | Substituídas por `repositoriosNaoDependemDeJobs` (direção da dependência) e `schedulersExigemEscopoDeSistema` (`@SystemScope` **ou** delegação a classe anotada). `LegacyPackagesArchTest` criado |
| **C5** | `AccountDeletionJob` multi-tenant sem `@SystemScope` | `grep -rln '@Scheduled'` → 5 de 6 jobs anotados | Anotado; a regra nova passou a exigir |
| **C6** | Mass assignment provado só em `POST /api/trilhas` | `MassAssignmentTest` = 3 `@Test` para 45 DTOs | `MassAssignmentParametrizadoTest`: varre os 45 records `*Request` por reflexão e falha se algum declarar campo de sistema |
| **C7** | `LogSemPiiTest` inexistente e **PII real em `INFO+`** | `EmailService:57,109` (`warnf` com e-mail + **link com token**), `:173,176,182,186`; `PushDeliveryScheduler:133` (`infof` com `usuarioId` hex) | 7 pontos trocados por `Pseudonimo.de(...)`; `LogSemPiiTest` com `CapturadorDeLogs` varre e-mail, nome, hex de ObjectId e token |
| **C8** | JaCoCo não instrumentava `@QuarkusTest` | `target/site/jacoco/jacoco.csv`: `TrilhaService` **0/1885**, `TarefaService` **0/3710** — apesar de 17 e 16 testes de integração verdes | `io.quarkus:quarkus-jacoco` (test scope); `TrilhaService` passou a **1364/1893 = 72%** e o total a **80,0%** |
| **C9** | Vitest sem provider de cobertura | `npm ls @vitest/coverage-v8` → empty | `@vitest/coverage-v8@4.1.12` + `test.coverage.thresholds` |
| **C10** | `dependency:analyze` inexecutável | plugin 3.7.0 → `Unsupported class file major version 69` | fixado **3.10.0** (lê Java 25) + `ignoredUsedUndeclaredDependencies` por grupo (transitivos do BOM) |
| **C11** | CI rodava o gate em `--report` e omitia 5 verificações | `ci-cd.yml:41`; sem `lint`, `knip`, cobertura, `dependency:analyze`, cabeçalhos | CI com `typecheck`, `lint --max-warnings 0`, `knip`, `test --coverage`, gate **estrito**, `verificar-cabecalhos.sh`, `dependency:analyze` e artefato JaCoCo |
| **C12** | Código morto real remanescente | `META-INF/resources/index.html` (placeholder "Dashboard") e `js/core/api.js` iam **dentro do jar**; `ReordenarTarefasRequest` com 0 referências; `FrontendDevRoute` com default para `../frontend-trilhara-javascript` (inexistente) | Os 4 removidos |
| **C13** | `knip.json` mascarava dependências | `entry: ["src/test/**"]` resolvia o grafo pelos testes; `knip --production` acusava 11 deps de produção | `entry: ["index.html", "src/test/**"]` |
| **C14** | README com Java errado e comandos não reais | `README.md:14` "Java 21 / 25"; `:32` "JDK 21+"; `./mvnw test` | Java **25** obrigatório, gate completo documentado, seção de testes reescrita |
| **C15** | `.env.example` incompleto (10 chaves) | `AUTH_JWT_ISSUER`, `AUTH_JWT_AUDIENCE`, `AUTH_PASSWORD_ITERATIONS`, `AUTH_MAX_LOGIN_FAILURES`, `AUTH_LOCK_MINUTES`, `AUTH_REFRESH_EXP_DAYS`, `AUTH_RESET_EXP_MINUTES`, `TRILHAS_MAX_POR_USUARIO`, `NOTAS_MAX_*`, `MONGO_INDEX_HEALTH_INTERVAL_SECONDS` | Todas adicionadas, sem segredos reais |

---

## 3. Cobertura (D-43) — **parcialmente atendido**

### 3.1 Backend — atendido

`check` do JaCoCo em `verify`, fonte `target/jacoco-quarkus.exec`:

| Pacote | Instruções cobertas | % |
|---|---|---|
| `sessao` | 3085/3387 | **91,1%** |
| `edital` | 1233/1331 | **92,6%** |
| `shared.mongo` | 2096/2451 | **85,5%** |
| `nota` | 6550/7779 | **84,2%** |
| `cronograma` | 5125/6175 | **83,0%** |
| `auth` | 6593/8079 | **81,6%** |
| `trilha` | 3294/4263 | **77,3%** |
| `tarefa` | 7121/9905 | **71,9%** |
| `shared.erro` | 989/1427 | **69,3%** |
| `push` | 2193/3380 | **64,9%** |
| `shared` (fachada de fuso/usuário) | 125/231 | **54,1%** |
| **TOTAL** | **40551/50676** | **80,0%** |

Regras enforce: `BUNDLE` ≥ **0,75** de instrução; `PACKAGE` ≥ **0,60** com exclusão de `*.dto`
(records sem lógica) e `br.com.trilhara.shared` (fachada fina usada indiretamente). Meta de D-43
(≥ 75%) **atingida**.

### 3.2 Frontend — **NÃO atendido** (pendência registrada)

Medição real com v8 (`57 arquivos / 438 testes`):

| Escopo | Linhas | Meta D-43 | Situação |
|---|---|---|---|
| global | 47,91% (3369/7031) | — | — |
| `src/shared/` | **69,9%** (1887/2700) | 80% | **Abaixo em 10,1 p.p.** |
| `src/modules/` | **34,4%** (1482/4310) | 70% | **Abaixo em 35,6 p.p.** |
| branches global | 42,78% (2735/6392) | — | — |

The fixed thresholds in `vite.config.ts` are the **measured value minus safety margin**
(`lines 47`, `functions 40`, `branches 42`, `statements 45`): a *ratchet* that blocks immediate
regression. **D-43 não é declarado Atendido no frontend.**

Por que o gap existe: os testes do frontend são de **comportamento de componente** (RTL +
user-event) e cobrem os fluxos dos quatro módulos; os arquivos com 0% são, na maioria, os
mesmos componentes já exercitados por caminho feliz, mas com ramos de erro/estado vazio não
visitados, mais telas de baixo tráfego (`LandingView`, `CatalogoView`, modais de catálogo).
Fechar os 35,6 p.p. de `modules/` exige uma rodada dedicada de testes de ramo — fora do
orçamento desta revisão.

---

## 4. Arquivos

### 4.1 Criados

- `backend/src/test/java/br/com/trilhara/arquitetura/LegacyPackagesArchTest.java` (6 regras)
- `backend/src/test/java/br/com/trilhara/auth/RateLimitPorUsuarioTest.java` (6 testes)
- `backend/src/test/java/br/com/trilhara/security/RotasPublicasContratoTest.java` (4 testes)
- `backend/src/test/java/br/com/trilhara/shared/MassAssignmentParametrizadoTest.java` (3 testes)
- `backend/src/test/java/br/com/trilhara/shared/LogSemPiiTest.java` (3 testes)
- `frontend/src/shared/styles/fonts.css` (6 `@font-face` self-hosted)
- `frontend/public/fonts/*.woff2` (6 arquivos, 256 KB)
- `scripts/verificar-cabecalhos.sh` (teste de fumaça D-47)
- `docs/REVISAO-IMPLEMENTADA-R13.md` (este arquivo)

### 4.2 Removidos

- `backend/src/main/resources/META-INF/resources/index.html`
- `backend/src/main/resources/META-INF/resources/js/core/api.js`
- `backend/src/main/java/br/com/trilhara/tarefa/dto/ReordenarTarefasRequest.java`
- `backend/src/main/java/br/com/trilhara/shared/FrontendDevRoute.java`

### 4.3 Renomeados (D-43)

- `Fase6RecorrenciaEngineTest` → `RecorrenciaCalendarioTest`
- `Fase6TarefaResourceTest` → `TarefaRecorrenciaResourceTest`

### 4.4 Alterados (principais)

`backend/pom.xml` (dependency-plugin 3.10.0, `quarkus-jacoco`, JaCoCo enforce),
`RateLimitService`, `AuthService`, `AuthResource`, `TrilhaService`, `CronogramaService`,
`NotaResource`, `EmailService`, `PushDeliveryScheduler`, `AccountDeletionJob`,
`TenantIsolationArchTest`, `RotasPrivadas` (allowlist de 109 rotas), `AuthCookieDomainTest`,
`Caddyfile`, `.github/workflows/ci-cd.yml`, `frontend/knip.json`, `frontend/vite.config.ts`,
`frontend/index.html`, `frontend/src/shared/styles/index.css`, 5 testes frontend (describe),
`scripts/verificar-legado.sh`, `README.md`, `.env.example`, `.gitignore`.

---

## 5. Testes nomeados por critério

### 5.1 Critérios de aceite da R13

| Critério do guia | Teste / comando | Situação |
|---|---|---|
| `npx knip` → 0 achados | `npx knip` → exit 0 | **Atendido** |
| ESLint → 0 warnings | `npm run lint -- --max-warnings 0` | **Atendido** |
| `tsc` → 0 erros | `npm run typecheck` | **Atendido** |
| `scripts/verificar-legado.sh` → exit 0 | gate endurecido, 0 achados | **Atendido** |
| `TenantIsolationArchTest` (sem `@ArchIgnore`) | 13 regras, 0 ignoradas | **Atendido** |
| `LegacyPackagesArchTest` | 6 regras | **Atendido** |
| `RotasPublicasContratoTest` | 4 testes (109 rotas, 3 asserções bidirecionais) | **Atendido** |
| `MassAssignmentParametrizadoTest` | 3 testes (45 DTOs) | **Atendido** |
| `LogSemPiiTest` | 3 testes | **Atendido** |
| `InventarioColecoesTest` | pré-existente, verde | **Atendido** |
| `AuthorizationByOwnerTest` | pré-existente (56 casos + guard), verde | **Atendido** |
| Backend ≥ 75% (JaCoCo) | `check` → 80,0% | **Atendido** |
| `shared/` ≥ 80% (Vitest) | 69,9% | **NÃO ATENDIDO** |
| módulos ≥ 70% (Vitest) | 34,4% | **NÃO ATENDIDO** |
| Contagem final ≥ baseline R0 | backend 494 ≥ 144; frontend 438 ≥ 109 | **Atendido** |
| Gate comum verde 2× | ver §6 | **Atendido** |

### 5.2 Decisões → teste

| Decisão | Regra | Teste |
|---|---|---|
| **D-44** | Código morto e legado | `LegacyPackagesArchTest` (6); `knip`; `dependency:analyze`; `verificar-legado.sh` |
| **D-45** | Rate limiting por usuário e IP | `RateLimitPorUsuarioTest` (6: refresh, handoff emissão, handoff consumo, busca, criação de trilha, isolamento entre usuários) |
| **D-47** | CSP, Permissions-Policy, HSTS | `scripts/verificar-cabecalhos.sh` (5 origens × 9 verificações + ausência de `unsafe-eval` + HSTS só em HTTPS) |
| **D-49** | Documentação | `README.md` (Java 25 + gate real); `.env.example` (43 chaves); este relatório |
| **D-43** | Cobertura enforce | JaCoCo `check` (backend); `test.coverage.thresholds` (frontend, ratchet) |
| **D-05** | Mass assignment | `MassAssignmentTest` (3) + `MassAssignmentParametrizadoTest` (3) |
| **D-04** | Sem PII em log | `LogSemPiiTest` (3) + `AccountDeletionIntegrationTest#logsNaoContemUsuarioId` |
| **D-06** | Isolamento por tenant | `TenantIsolationArchTest` (13) + `AuthorizationByOwnerTest` |

---

## 6. Gate

```bash
eval "$(bash scripts/env-gate.sh)"
cd backend  && ./mvnw -q verify
cd ../frontend && npm run typecheck && npm run lint -- --max-warnings 0 \
  && npm test -- --run --coverage && npm run build && npx knip
cd .. && bash scripts/verificar-legado.sh
cd .. && bash scripts/verificar-cabecalhos.sh
```

| Comando | Resultado |
|---|---|
| `backend: ./mvnw -q verify` | **494 testes, 0 falhas, 10 skipped · BUILD SUCCESS** |
| `backend: jacoco:check` | **passou** (80,0% ≥ 75%) |
| `backend: dependency:analyze` | **limpo** |
| `frontend: npm run typecheck` | ✅ |
| `frontend: npm run lint -- --max-warnings 0` | ✅ |
| `frontend: npm test -- --run --coverage` | ✅ **57 arquivos / 438 testes** |
| `frontend: npm run build` | ✅ (aviso de chunk 551 kB em `NotasApp`, pré-existente) |
| `frontend: npx knip` | ✅ exit 0 |
| `root: bash scripts/verificar-legado.sh` | ✅ exit 0 / 0 achados |
| `root: bash scripts/verificar-cabecalhos.sh` | ✅ 5/5 origens |

### 6.1 Gate duplo com ambiente limpo (não-determinismo)

`bash scripts/mongo/reset-dev.sh --db trilhara_test --yes` entre as duas passagens.

| Passagem | Backend | JaCoCo `check` | Frontend | Cobertura v8 | Gate raiz |
|---|---|---|---|---|---|
| **1** (banco recriado) | 494 testes, 0 falhas, 10 skipped | passou (80,0%) | 57 arquivos / 438 testes | 47,91% lines · 42,78% branches | legado 0 achados · cabeçalhos 5/5 |
| **2** (banco recriado) | 494 testes, 0 falhas, 10 skipped | passou (80,0%) | 57 arquivos / 438 testes | 47,91% lines · 42,78% branches | legado 0 achados · cabeçalhos 5/5 |

**Resultado idêntico nas duas passagens** — nenhum teste não-determinístico detectado.

Um ajuste de ambiente foi necessário: o `testTimeout` do Vitest era o padrão de **5 s**, e sob
instrumentação de cobertura os testes de componente (RTL + `user-event` em árvores grandes)
ficam 3–5× mais lentos, gerando **12 falhas falsas** por timeout. Fixado em 30 s em
`vite.config.ts` (`testTimeout`/`hookTimeout`).

---

## 7. Pendências registradas (não declaradas atendidas)

1. **D-43 no frontend**: `shared/` 69,9% (meta 80%) e `modules/` 34,4% (meta 70%). Thresholds
   foram fixados no valor medido como ratchet. Fechar o gap exige rodada dedicada de testes de
   ramo/estado vazio.
2. **`connect-src` da CSP**: mantido `'self'` porque o frontend fala com a API pela **mesma
   origem** (proxy Caddy). Se algum ambiente apontar para origem de API separada, a CSP precisa
   incluir aquela origem (o guia previa `<api-origins>`).
3. **`quarkus.swagger-ui.always-include=true`**: Swagger UI exposto também fora de `dev`.
   Encontrado durante a R13, mas não está no escopo literal desta revisão (não é código morto
   nem um dos SEC-04/07/08/14). Recomenda-se tratar como hardening adicional.
4. **`docs/REQUISITOS` e `docs/PLANO` não existem** no repositório: o normativo vive nos guias
   `docs/R*-REVISAO.md`. D-49 pede "requisitos/plano atualizados"; optou-se por registrar a
   ausência em vez de criar documentos que duplicariam os guias.
