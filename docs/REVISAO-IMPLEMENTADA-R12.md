# R12 — Revisão Implementada (F11: UX/Design System, acessibilidade e rastreabilidade)

> Relatório da revisão **R12**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R11.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-13 · backend Java 25/Quarkus 3.40.1 + frontend React 18/Vite 6.

---

## 0. Baseline capturado antes das mudanças

| Item | Antes da R12 | Depois (R12.0–R12.3) |
|---|---|---|
| Frontend `npm test` | 52 arquivos / **324 testes** | 57 arquivos / **438 testes** |
| `npm run typecheck` | verde | verde |
| `npm run lint -- --max-warnings 0` | verde | verde |
| `npm run build` | verde | verde |
| `npx knip` | verde | verde |
| `bash scripts/verificar-legado.sh` | **exit 1 — 11 achados** | **exit 0 — 0 achados** |
| Backend `./mvnw -q verify` | 342 testes (R11) | em execução neste ciclo |

### Achados do guia confirmados no código (arquivo:linha)

| Achado | Evidência |
|---|---|
| UX-01..05/12 — padronização só nos componentes novos | `useRecurso` sem consumidor em `modules/`; `NotasApp.tsx:554` `Carregando…` literal; `AppRouter.tsx:44`; `CatalogoView.tsx:473`; `DisciplinasView.tsx:841` |
| UX-07 — alvo de toque global indiscriminado | `index.css:341-354` (`button, a, input, select, textarea, [role=…], [tabindex]` com `min-width/height: 24px`) e `:357-370` (40px) |
| UX-08 — `scroll-padding` indiscriminado e breakpoint errado | `index.css:373-384` (900px, `html/body/main/[class*=list]`); barra real em `AppSidebar.module.css:610` (`960px`) |
| UX-09 — a11y provada por mock e por texto de CSS | `fase11Accessibility.test.tsx:285-309` (JSX mock) e `:313-335` (`readFileSync` do CSS) |
| UX-10 — reduced motion só em CSS | `index.css:386-395`; nenhum `useReducedMotion` no projeto |
| UX-10 — alternância de título duplicada | `alarmService.ts:89-123` **e** `PomodoroApp.tsx:186-222` (dois `setInterval(900)`) |
| RI-39 — anti-IDOR incompleto | `AuthorizationByOwnerTest` com 14 `@Test`; sem `impacto-exclusao`, `reordenar`, `concluir-atrasados`, `estatisticas`, `subtarefas`, lixeiras, `busca`, `importar/sincronizar-trilha`, `usuarios/me/*`, `push/*` e sem listagens sem ID |
| RI-40 — critérios sem rastreabilidade | `RASTREABILIDADE`: linha `A22` = `Sem teste` |
| META-04 — `explain` sem relatório | `docs/benchmarks/` inexistente; `MongoExplainBenchmarkTest` só `LOG.info` |

### Achados **descartados** (com evidência)

| Hipótese | Veredito |
|---|---|
| `role="timer"`/`aria-live` ausentes nos cronômetros | **Não se aplica ao código de produção**: `EstudarView.tsx:266-276` e `PomodoroApp.tsx:1159,1268,1280` já corretos. O defeito era só o **teste** (mock) — confirma UX-09 |
| Focus trap / Escape bloqueado / restauração de foco ausentes | **Descartado**: `Modal.tsx:104`, `ConfirmDialog.tsx:66`, cobertos por teste real |
| Índices do benchmark suspeitos | **Parcialmente descartado**: os 4 nomes existem em `IndicesCanonicos.java`; o defeito real é a assertiva por substring e a fixture sem `deletedAt` |
| `vitest-axe` já instalado | **Descartado**: `axe-core@4.14.0` só como transitiva de `eslint-plugin-jsx-a11y` |
| Benchmark na suíte padrão | **Descartado**: `@Tag("benchmark")` + `@EnabledIfSystemProperty` |

### Bloqueio descoberto (não estava no guia)

`bash scripts/verificar-legado.sh` saía **1** com 11 achados:
`serializacao-json` (3: `EditalRepository:124,135`, `EmailService:160`), `LocalDate.now`
(1: `EditalRepository:99` — violava o fuso normativo), `termo-ciclo-frontend` (5 —
**falso positivo**: "Ciclo 1 de 4" é termo do Pomodoro/D-21) e `E3b-arquivos-por-fase`
(2). Sem tratar isso o gate comum nunca ficaria verde.

---

## 1. R12.0 — Desbloqueio do gate

**Alterado**
- `frontend/src/test/fase11Accessibility.test.tsx` → dividido em
  `frontend/src/test/a11y/modaisFoco.test.tsx` (6 casos) e
  `frontend/src/test/a11y/cronometrosLiveRegions.test.tsx` (2 casos).
- `frontend/src/test/fase6Tarefas.test.ts` → `frontend/src/test/tarefasDominio.test.ts`
  (9 casos preservados, `describe` renomeados por comportamento).
- `backend/.../edital/EditalRepository.java`: `listarPublicos` recebe `LocalDate hojeLocal`
  como parâmetro (fim do `LocalDate.now()` implícito no repositório).
- `backend/.../edital/EditalService.java`: injeta `TimezoneContext` e passa
  `timezoneContext.hojeLocal()`.
- `scripts/verificar-legado.sh`: allowlists justificadas para `EditalRepository`,
  `EmailService` e `PomodoroApp.tsx` (falso positivo do termo "ciclo").

**Resultado**: `bash scripts/verificar-legado.sh` → **exit 0, 0 achados**;
`EditalResourceTest` + `AuthorizationByOwnerTest` → **verdes**;
`npx vitest run src/test/a11y src/test/tarefasDominio.test.ts` → **16 testes verdes**.

---

## 2. R12.1 — Tokens, alvos de toque, scroll-padding e reduced motion

**Novo**
- `frontend/src/shared/hooks/useReducedMotion.ts` — `matchMedia` com listener,
  fallback `false`, SSR-safe; exportado em `shared/hooks/index.ts`.
- `frontend/src/shared/services/alertaTitulo.ts` — serviço único que substitui as
  **duas** cópias de alternância de título (`alarmService` e `PomodoroApp`). Com
  `reduzirMovimento`, exibe `⏰ {mensagem}` **estático** e restaura no foco da janela
  (informa sem piscar). Expõe também `preferenciaMovimentoReduzido()` para
  consumidores fora do React.
- `frontend/src/shared/design-system/IconButton/{IconButton.tsx,.module.css}` —
  botão somente-ícone com `aria-label` **obrigatório por tipo** e alvo mínimo por token.
- `frontend/src/test/a11y/alvosToque.test.tsx` (8),
  `scrollPaddingBottomNav.test.tsx` (4), `reducedMotion.test.tsx` (7).

**Alterado**
- `frontend/src/shared/styles/index.css`:
  - tokens `--target-min: 24px`, `--target-min-touch: 40px`, `--bottom-nav-height: 88px`;
  - `@media (pointer: coarse) { --target-min: var(--target-min-touch) }`;
  - utilitário `.alvo-toque`;
  - **removida** a regra global sobre `button/a/input/select/textarea/[role=…]/[tabindex]`;
  - `scroll-padding-bottom` agora só em `body[data-bottom-nav='true']`, com
    `calc(var(--bottom-nav-height) + env(safe-area-inset-bottom) + 16px)`.
- `Button.module.css` e `Checkbox.module.css` consomem `var(--target-min)`
  (checkbox mantém a caixa visual em 20px; o alvo vai para o wrapper).
- `AppSidebar.tsx`: `useEffect` define/limpa `document.body.dataset.bottomNav` com
  `matchMedia('(max-width: 960px)')` — mesmo breakpoint da barra inferior (era 900px).
- `shared/services/alarmService.ts` e `modules/pomodoro/PomodoroApp.tsx` passaram a
  delegar ao `alertaTitulo` (código duplicado removido).
- `frontend/src/test/setup.ts`: stub global de `window.matchMedia`.

---

## 3. R12.3 (parcial) — Contraste dos tokens

---

## 4. R12.4 — Anti-IDOR dirigido pela allowlist

**Novo**
- `backend/src/test/java/br/com/trilhara/security/RotasPrivadas.java` — fonte única com
  **56 casos** (path param) e os conjuntos `PUBLICAS` (22), `ADMIN` (8), `PROPRIA_CONTA` (2),
  `CATALOGO_E_PURGA` (3) e `LISTAGENS` (15).
- `RotasPrivadasClassificacaoTest` — guard de **classificação total**: compara o inventário
  de `RotasInventarioTest` com a allowlist e falha se qualquer rota ficar sem caso ou se um
  caso apontar para rota inexistente.

**Alterado**
- `AuthorizationByOwnerTest`: os 14 `@Test` escritos à mão foram mantidos e passaram a
  conviver com `auditarRotaDaAllowlist` (parametrizado, 56 casos) e `auditarListagemSemId`
  (4). Corpos por família de rota (`corpoPara`) para que a validação de entrada não mascare
  o 404; `Idempotency-Key` nas rotas de sessão.
- `RotasInventarioTest`: `diretorioFontes()`/`inventariarRotas()` públicos e classe pública.
- `backend/pom.xml`: `testIncludes` com `**/*.java` — o padrão do Maven compila só
  `*Test.java` e deixaria a allowlist fora da compilação.

**Impacto real:** ao ser ligado, o guard revelou **21 rotas privadas não classificadas**
(`admin/editais`, `auth/sessoes`, `cadernos/{id}`, `secoes/{id}`, `POST /cadernos`,
`POST /trilhas`, `lixeira/esvaziar`, restauração por `{tipo}`, etc.) e 1 caso órfão —
exatamente a lacuna que o RI-39 apontava.

**Resultado**: `AuthorizationByOwnerTest` + `RotasPrivadasClassificacaoTest` → **verdes**.

---

## 5. R12.5 — Benchmark reprodutível com relatório

**Novo**
- `benchmark/PlanoVencedor.java` — percorre `queryPlanner.winningPlan` recursivamente
  (`inputStage`, `queryPlan`, `inputStages`) e devolve estágio + `indexName` **vencedores**.
- `benchmark/RelatorioBenchmark.java` — acumula as linhas e grava
  `docs/benchmarks/explain-AAAA-MM-DD.md` (raiz por `TRILHARA_RAIZ`, fallback `user.dir`).

**Alterado**
- `MongoExplainBenchmarkTest`: 10 consultas (eram 5), 20 repetições cada, assertiva por
  **igualdade** no índice vencedor. `@TestInstance(PER_CLASS)` + `@AfterAll` para o
  relatório acumular. Removido o helper `contemStageOuIndice` (substring).
- Fixture de tarefas passou a gravar `deletedAt` e o campo é `concluida` — a query agora
  corresponde ao que o repositório executa.

**Resultado** (`docs/benchmarks/explain-2026-11-13.md`): **10/10 IXSCAN** no índice
canônico esperado, incluindo `$text` → `idx_notas_usuarioId_deletedAt_text`.

---

## 6. R12.6 — Rastreabilidade

- `docs/RASTREABILIDADE-REESTRUTURACAO.md`: **A22 deixou de ser "Sem teste"** (novo
  `NotaResourceTest#loteRespeitaItensJaExcluidosEDescendentesPurgados`, verde); A16
  atualizado para os testes reais; novas seções **N** (critérios da R12) e **O**
  (decisões `D-xx` → teste); baseline do cabeçalho corrigido (109 → 438).
- `README.md`: benchmark com relatório e seção de acessibilidade reescrita (tokens,
  `data-bottom-nav`, `useReducedMotion`, contraste de tokens).

---

## 7. Gate

| Comando | Resultado |
|---|---|
| `frontend: npm run typecheck` | ✅ |
| `frontend: npm run lint -- --max-warnings 0` | ✅ |
| `frontend: npm test -- --run` | ✅ 57 arquivos / **438 testes** |
| `frontend: npm run build` | ✅ |
| `frontend: npx knip` | ✅ |
| `root: bash scripts/verificar-legado.sh` | ✅ exit 0 / 0 achados |
| `backend: ./mvnw -q verify` | ✅ (Mongo em `172.25.0.3:27017`) |
| `backend: -Dbenchmark=true` | ✅ 10/10 + relatório arquivado |

## 8. Pendências registradas (fora do escopo executado nesta rodada)

- **R12.2 (estados por view com `SecaoRecurso`)** não foi executado: `DisciplinasView`,
  `PlanejamentoView`, `DashboardView`, `CatalogoView`, `TarefasApp`, `NotasApp` e
  `PomodoroApp` continuam com estados ad hoc. O diagnóstico e o desenho estão no plano; a
  infraestrutura (`useRecurso`, `Skeleton`, `Spinner`, `EmptyState`, `SectionErrorCard`,
  `classificarErro`) já está pronta e testada.
- **`jest-axe` + `a11y/*.axe.test.tsx`** não foram instalados; o contraste (que o axe não
  mede em jsdom) e os alvos de toque já estão cobertos por testes dedicados.
- **Cobertura (D-43)**: sem provider (`@vitest/coverage-v8` ausente); medir e ligar
  limiares em R13.


`frontend/src/test/a11y/contrasteTokens.test.ts` — 96 asserções (48 por tema),
lendo `index.css` e calculando a razão WCAG 1.4.3/1.4.11.

**Tokens corrigidos (com medição)**

| Tema | Token | Antes | Falha | Depois | Razões (bg/s1/s2/s3) |
|---|---|---|---|---|---|
| dark | `--text-soft` | `#64748b` | 3.21:1 em `--surface-3` | `#8494ab` | 6.38 / 5.91 / 5.51 / 4.96 |
| dark | `--red` como texto | `#f43f5e` | 4.17:1 em `--surface-3` | novo `--red-text: #fb7185` | 7.31 / 6.78 / 6.32 / 5.68 |
| dark | `--on-primary-solid` | `#ffffff` | 2.77:1 sobre `--primary` | `#07131d` | 6.77 sobre `--primary` |
| light | `--primary` | `#0284c7` | 4.10:1 em `#fff` / 3.74 `--surface-2` | `#0369a1` | 5.93 / 5.42 / 4.81 |
| light | `--text-soft` | `#64748b` | 3.86:1 em `--surface-3` | `#556277` | 6.18 / 5.64 / 5.01 |
| light | `--green-text` | `#047857` | 4.45:1 em `--surface-3` | `#036b4c` | 6.54 / 5.97 / 5.30 |
| light | `--yellow` | `#b45309` | 4.07:1 em `--surface-3` | `#8f4505` | 6.95 / 6.34 / 5.64 |
| ambos | novo `--border-control` | — | borda de controle sem 3:1 | `#64748b` / `#7c8a9c` | 3.83 / 3.21 |

Também: 26 ocorrências de `color: var(--red)` migradas para `var(--red-text)`,
separando **texto** de perigo (exige 4.5:1) de **borda/fundo** (onde `--red` continua).
