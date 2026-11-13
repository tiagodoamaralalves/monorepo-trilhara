# R0 — Revisão Implementada (Baseline, Inventário Real e Gate Anti-Legado)

> Relatório da revisão **R0** da reestruturação Trilhara. Substitui, como fonte de
> evidência, o relato histórico de implementação (D-01: *trust but verify*).
> Nenhum item é declarado "Atendido" sem teste automatizado nomeado na matriz
> `docs/RASTREABILIDADE-REESTRUTURACAO.md`.
>
> Execução: 2026-11-12 · branch `main` · commit base `dc4d731`.

---

## 1. Ambiente de execução e bloqueadores encontrados

| Item | Estado encontrado | Ação na R0 |
|---|---|---|
| `rg` (ripgrep) | **ausente** na máquina (sem `/usr/bin/rg`, `sudo` exige senha) | `verificar-legado.sh` usa `rg --pcre2` quando existe e cai para `grep -rInE` (GNU) quando não |
| `JAVA_HOME` | apontava para JRE do VS Code (sem `javac`) → `mvnw` abortava com "JAVA_HOME is not defined correctly" | `scripts/env-gate.sh` resolve um JDK 25 com `javac` |
| MongoDB | container `trilhara_mongo` **sem porta publicada**; `%test` de `application.properties` fixa `172.22.0.2` (IP inexistente) | `env-gate.sh` descobre IP do container (`172.25.0.3` em 2026-11-13) ou `localhost:27017`. **Correção de 2026-11-13:** a descoberta falhava porque o `docker` do `PATH` é o `docker.exe` do Desktop (interop WSL) e não o daemon do snap; o script agora valida o cliente com `docker info` e testa cada IP do container |
| `RESEND_API_KEY` | config **sem default** (`trilhara.email.resend-api-key`). Sem valor, o Quarkus não sobe: `Tests run: 136, Errors: 1, Skipped: 106` + `BUILD FAILURE` | `env-gate.sh` fornece valor dummy; CI corrigido |
| Node/npm | 20.20.2 / 10.8.2 (local) × 22 (CI) | divergência registrada para R13 |
| Banco de dev | 19 coleções canônicas + **3 fora do inventário** (`handoff_tokens`, `operacoes_concluir_atrasados`, `planejamento_ciclo`) | registrado; correção é de R2/R5/R9 |

**Comando reprodutível:**
```bash
eval "$(bash scripts/env-gate.sh)"        # JAVA_HOME, MONGODB_URL, RESEND_API_KEY
cd backend && ./mvnw -q verify
cd frontend && npm run typecheck && npm test -- --run && npm run build
bash scripts/verificar-legado.sh --report
```

---

## 2. Baseline de testes (antes × depois, obrigatório para R13)

| Suíte | Antes da R0 | Depois da R0 |
|---|---|---|
| Backend (`./mvnw verify`) | 136 testes, 0 falhas, 5 skipped, **BUILD SUCCESS** (com `RESEND_API_KEY`) | 144 testes (136 + 8 novos), ver seção 7 |
| Backend sem `RESEND_API_KEY` | **136, 1 erro, 106 skipped, BUILD FAILURE** | comportamento inalterado (config obrigatória) |
| Frontend (`npm test -- --run`) | 14 arquivos, **109 testes, 109 verdes** | 14 arquivos, **109 testes, 109 verdes** |
| Frontend `npm run build` (`tsc -b && vite build`) | **FALHA: 50 erros TS6133** | **SUCCESS** |
| Frontend `vite build` isolado | SUCCESS (chunk `NotasApp` 545 kB > 500 kB) | SUCCESS (mesmo warning) |

Os 5 skips do backend são o `MongoExplainBenchmarkTest`, opt-in por
`-Dbenchmark=true` (não é regressão).

---

## 3. Inventário de pacotes Java (item 2 — META-02)

`find backend/src/main/java -type d` → **21 diretórios** sob `br.com.trilhara`:

```
auth, auth/dto, cronograma, cronograma/dto, edital, nota, nota/dto, push,
push/dto, sessao, sessao/dto, shared, shared/erro, shared/mongo,
tarefa, tarefa/dto, trilha, trilha/dto, trilha/schema
```

- **Duplicidades de caminho: 0** (nenhum `find -type d` repetido).
- **Classes homônimas em pacotes distintos: 0** (`uniq -d` sobre nomes de arquivo).
- `grep 'package br.com.trilhara.(estudos|tarefas|notas)'` → **0**.

**Veredito META-02 ("pacotes duplicados"): DESCARTADO.** Não existe duplicidade
de pacote. O que existe é **resíduo legado dentro de `trilha/`** (achado novo):

| Arquivo | Evidência | Fase dona |
|---|---|---|
| `trilha/Ciclo.java` | record `Ciclo` vivo | R5 |
| `trilha/Trilha.java:33-34` | campos `ciclos`, `cicloAtivoId` | R5 |
| `trilha/TrilhaRepository.java` | `persistirCiclosTrilha`, `converterCiclos`, +11 refs | R5 |
| `trilha/TrilhaRepository.java:351` | `deleteMany` na coleção **`planejamento_ciclo`** (fora das 19) | R5/R7 |
| `trilha/dto/SalvarTrilhaRequest.java` | 3 refs a ciclo | R5 |

---

## 4. Inventário de rotas JAX-RS (item 3 — insumo do Anexo A)

Teste executável criado: `backend/src/test/java/br/com/trilhara/shared/RotasInventarioTest.java`
(lê `@Path` de classe + método + verbo em todas as `*Resource`, imprime e valida).

**Resultado: 108 métodos HTTP em 15 classes `*Resource`.**

| Recurso | Métodos |
|---|---|
| `AuthResource` | 18 |
| `TrilhaResource` | 14 |
| `CadernoResource` | 13 |
| `TarefaResource` | 12 |
| `NotaResource` | 8 |
| `CronogramaResource` | 7 |
| `EditalAdminResource` | 6 |
| `SessaoResource` | 6 |
| `ListaTarefasResource` | 6 |
| `SerieRecorrenteResource` | 5 |
| `PushResource` | 4 |
| `SecaoResource` | 3 |
| `BlocoResource` | 2 |
| `UsuarioDisponibilidadeResource` | 2 |
| `EditalResource` | 2 |

**Divergências contra o Anexo A do guia R13 (registradas, não corrigidas):**
- **Rotas legadas ainda publicadas:** `PATCH /api/trilhas/{id}/progresso/{topicoId}`
  (o Anexo A declara removida; existe a variante canônica
  `PUT|DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso`).
- **Rotas fora do Anexo A:** `POST /api/trilhas/importar/{editalId}`,
  `POST /api/auth/confirmar-email`, `POST /api/auth/recuperar-senha`,
  `POST /api/auth/reenviar-confirmacao`, `POST /api/auth/resetar-senha`,
  `GET|DELETE /api/auth/sessoes`, `GET /api/auth/admin/usuarios`.
- **Ausentes do código** (planejadas no Anexo A): `GET /api/listas-tarefas/{id}/impacto-exclusao`,
  `GET /api/cadernos/{id}/impacto-exclusao`, `GET /api/secoes/{id}/impacto-exclusao`,
  `GET /api/notas/lixeira`, `POST /api/notas/lixeira/{tipo}/{id}/restaurar`,
  `DELETE /api/notas/lixeira/esvaziar`, `DELETE /api/push/subscriptions/{id}`.


---

## 5. Índices reais × declarados (item 4 — D-07)

`docker exec trilhara_mongo mongosh --quiet trilhara --eval 'listIndexes()'` em
banco de dev **vazio de dados**:

- `MongoIndexInitializer` declara **40 índices** em **19 coleções**.
- Banco real tem **42 nomes `idx_`**.
- **Índices declarados e ausentes no banco: 0.**
- **Diferenças de chave/opções nos 40: 0** (nomes, chaves e `partialFilterExpression` idênticos).

**Divergências encontradas (não corrigidas — pertencem a R2/R5/R9):**

| # | Achado | Evidência | Impacto normativo |
|---|---|---|---|
| I-1 | **2 índices criados fora do inicializador** | `HandoffService.java:56-57` cria `idx_handoff_tokens_tokenHash_unique` e `idx_handoff_tokens_expiraEm` no construtor | viola D-07 ("única classe `MongoIndexInitializer`") |
| I-2 | **3 coleções fora do inventário de 19** | `handoff_tokens`, `operacoes_concluir_atrasados`, `planejamento_ciclo` | regras §2 fixam 19 coleções |
| I-3 | **Exclusão de conta não purga essas 3** | `AccountDeletionService.COLECOES_FILHAS` lista 17 filhas; não inclui as 3 | risco de dado órfão (R9) |
| I-4 | **`MongoIndexInitializerTest` valida só nomes** | compara apenas `nomesExistentes.contains(...)` | D-07 exige nome **+ chaves + opções** (R2) |
| I-5 | **`scripts/mongo/reset-dev.sh` não existe** | `ls scripts/mongo` → ausente | exigido por D-07 (R2) |
| I-6 | **`OperacaoConcluirAtrasadosRepository` não cria índice algum** | 0 `createIndex` no arquivo | `operacoes_concluir_atrasados` sem índice por `operationId` |

Índices parciais confirmados corretos: `cronogramas {usuarioId,trilhaId}` com
`partialFilterExpression {ativo:true}`, `tarefas` com `serieId $exists`,
`sessoes` com `chaveIdempotencia $exists`.

---

## 6. Reconciliação da FASE 4 duplicada (item 5)

Os dois relatos eram autocontraditórios. **Veredito por código:**

| Ponto | Relato 1 | Relato 2 | Código | Vencedor |
|---|---|---|---|---|
| Campos do bloco | `data, ordem, concluido, concluidoEm` | `dataEstudo, status, ordemDia, duracaoRealSegundos` | `BlocoCronograma.java:16-21` | **Relato 1** |
| Filtro do lote "atrasados" | `data < hojeLocal` | `data <= hoje` | `BlocoCronogramaRepository:188` → `lt("data", dataHoje)` | **Relato 1** |
| Coleção de operações | `operacoes_concluir_atrasados` | idem | `OperacaoConcluirAtrasadosRepository:33` | ambos |
| Índices dos blocos | `..._versao_data_ordem_unique` + `..._data_concluido` | `..._busca_hoje`, `..._paginada`, `..._concluir_atrasados` | `MongoIndexInitializer` + banco real | **Relato 1** |
| Limite de página de blocos | — | — | `BlocoCronogramaRepository:165` → `min(200)` | **divergente da norma** (máx. 100 → R5) |
| Pacote do serviço | `br.com.trilhara.cronograma` | idem | confirmado | ambos |
| `TarefaService` path | `tarefas/service/` | `tarefa/` | `tarefa/TarefaService.java` | **Relato 2** |

**Conclusão:** o contrato realmente implementado é o do **Relato 1** (nomes
canônicos D-02), com duas ressalvas: `OperacaoConcluirAtrasados` — nome que o
Anexo C lista como símbolo proibido — é a implementação **válida e ativa** da F4
(contradição normativa a resolver em D-02/R5), e o limite de `tamanho` na
paginação de blocos é 200, acima do máximo normativo de 100.



---

## 7. Testes criados na R0

| Teste | Arquivo | Testes | Resultado |
|---|---|---|---|
| `RotasInventarioTest` | `backend/src/test/java/br/com/trilhara/shared/RotasInventarioTest.java` | 3 | **3/3 verdes** |
| `TenantIsolationArchTest` | `backend/src/test/java/br/com/trilhara/arquitetura/TenantIsolationArchTest.java` | 5 | **2 ativas verdes + 3 `@ArchIgnore`** com a fase de habilitação na razão |

Regras ArchUnit ativas e verdes desde a R0:
- `recursosNaoAcessamMongoDiretamente` (D-06c);
- `pacoteBaseDeveExistir` (sanidade do scan).

Ignoradas com fase declarada: `repositoriosRecebemUsuarioIdPrimeiro` (R2/D-06a),
`acessoMultiTenantSomenteEmSystemScope` (R2/D-06b), `pacotesLegadosProibidos` (R13).

---

## 8. Gate anti-legado — baseline de ocorrências

`bash scripts/verificar-legado.sh --report` (motor `grep`, pois `rg` está ausente).

Os números abaixo refletem o estado **após** a limpeza não comportamental da §10.
Onde havia diferença antes/depois, ela é anotada — e o motivo é sempre a remoção
de um símbolo morto que por acaso casava com o padrão.

| Padrão | Achados (final) | Antes da §10 |
|---|---|---|
| `serializacao-json` | 4 | 4 |
| `LocalDate.now` | 1 | 1 |
| `ZoneId-America/Sao_Paulo` | 1 | 1 |
| `deleteMany-generico` | 0 | 0 |
| `fallback-usuario-local` | 0 | 0 (só comentário, em allowlist) |
| `simbolos-legado` | **40** | 41 (import morto `DisciplinaProgramada` removido) |
| `nomes-nao-canonicos-d02` | 46 | 46 |
| `pacotes-legados` | 0 | 0 |
| `storage-inseguro` | 0 | 0 |
| `canais-antigos` | 3 | 3 |
| `xss-e-reload` | 0 | 0 |
| `console-em-app` | 0 | 0 |
| `adaptadores-legados` | 8 | 8 |
| `termo-ciclo-backend` | 20 | 20 |
| `termo-ciclo-frontend` | **104** | 105 (bloco morto `atrasadasAgrupadas` removido) |
| **E1** `deleteMany(new Document(...))` | 3 | 3 |
| **E2** chaves de storage de auth | 3 | 3 |
| **E3** nomenclatura por fase (blocos) | 0 | 0 |
| **E3b** arquivos de teste por fase | 7 | 7 |
| **TOTAL** | **240** | **242** |


### Allowlists declaradas (justificadas no próprio script)
`TimezoneContext.java` (fonte única do fuso), `MongoIndexInitializer.java`
(`toJson()` só em mensagem de diagnóstico de índice), `UsuarioAtual.java`
(comentário sobre o fallback removido), e `backend/src/test` + `frontend/src/test`
para `console.*` e nomenclatura por fase.

### Extensões do Anexo C (lacunas comprovadas)
- **E1** — a regex original `deleteMany\(new Document\(\)\)` casa **0**, mas o
  código usa `deleteMany(new Document(campo, valor)...)` em
  `TrilhaRepository:349-351`; igualmente destrutivo.
- **E2** — `trilhara_auth_token` não cobre as chaves reais:
  `trilhara_auth_access_token` (`authStorage.ts:3`),
  `trilhara_auth_user` (`authStorage.ts:4`),
  `trilhara_auth_refresh_token` (`authStorage.ts:158`).
- **E3/E3b** — cobre `class Fase[0-9]` (2 classes) e arquivos `faseN.test.*` (7).

### Padrões do Anexo C que são falsos positivos do próprio guia
| Padrão | Ocorrências | Natureza real |
|---|---|---|
| `usaPadraoGlobalLembrete` | 46 | campo **real e canônico** de `Tarefa`/`SerieRecorrente` (push) — renomear quebraria o domínio. Decisão necessária: allowlist em D-02 ou renomeação em R8 |
| `OperacaoConcluirAtrasados` | 4 | implementação **válida** da F4 (ver §6) |
| `dataEstudo/ordemDia/duracaoRealSegundos/horarioDisparoUtc/dataTermino` | **0** | já canônicos |
| `toObjectIdOrNotFound` | 66 | **não está** no Anexo C; é helper amplamente usado de `BsonIds` |

---

## 9. Ferramentas configuradas (item 7–8) e achados medidos

### Frontend
- `package.json`: novos scripts `typecheck`, `lint`, `knip` (o gate comum referenciava
  comandos que **não existiam**).
- `eslint.config.js` (flat config) com `js.recommended` + `typescript-eslint`,
  `react-hooks`, `jsx-a11y/recommended`, `@typescript-eslint/no-unused-vars: error`,
  `no-console: error` e `no-restricted-syntax` cobrindo `dangerouslySetInnerHTML`
  (o plugin `eslint-plugin-react` não está nas dependências aprovadas, então
  `react/no-danger` é expressa via `no-restricted-syntax` — desvio justificado).
- `knip.json` com `entry`, `project`, `ignore` e `ignoreDependencies` documentados.
- `tsconfig.json`: **`noUnusedLocals` + `noUnusedParameters` + `noFallthroughCasesInSwitch`**
  (as três flags de D-44). **`--strict` NÃO foi ligado** — ver §10.
- Dependências instaladas (versões compatíveis com React 18/Vite 6):
  `eslint@9.39.5`, `@eslint/js@9.39.5`, `typescript-eslint@8.72.2`,
  `eslint-plugin-react-hooks@7.1.1`, `eslint-plugin-jsx-a11y@6.10.2`,
  `globals@17`, `knip@6.45.1`.

**Baseline medido (NÃO corrigido nesta fase):**

| Ferramenta | Achados |
|---|---|
| `npx eslint src` | **163 problemas em 47 arquivos** |
| `npx knip` | **73 achados em 31 arquivos** (29 `exports`, 31 `types`, 7 `files`, 6 `duplicates`) |

Principais regras ESLint disparadas:

| Regra | Ocorrências |
|---|---|
| `react-hooks/set-state-in-effect` | 41 |
| `@typescript-eslint/no-explicit-any` | 37 |
| `no-console` | 15 |
| `jsx-a11y/no-autofocus` | 14 |
| `react-hooks/exhaustive-deps` | 11 |
| `jsx-a11y/click-events-have-key-events` | 10 |
| `jsx-a11y/no-static-element-interactions` | 9 |
| `no-empty` | 9 |
| `react-hooks/purity` | 5 |
| `react-hooks/refs` | 3 |
| demais | 9 |

### Backend
- `pom.xml`: `com.tngtech.archunit:archunit-junit5:1.5.1` (escopo `test`) e
  `jacoco-maven-plugin:0.8.16` com `prepare-agent` + `report` em fase `test`,
  **sem `check`** (report-only; enforce em R13).
- `TenantIsolationArchTest` criado como esqueleto (§7).

**Nota de compatibilidade:** ArchUnit 1.5.1 suporta class files até Java 27 e
JaCoCo 0.8.16 tem suporte oficial a Java 27 (Java 25 desde 0.8.14) — ambos

---

## 10. Desvios de escopo justificados (Etapa 0.5)

A R0 exige simultaneamente *"nenhuma correção funcional"* e *"build e testes
verdes"*. As duas condições só coexistem se "correção" significar mudança de
**comportamento/contrato**. As edições abaixo são **não comportamentais**
(remoção de símbolo não referenciado) e são a condição para o build voltar ao
verde, conforme autoriza a regra de que "mudanças transversais indispensáveis
para compilar/testar devem ser justificadas no resumo".

### Contexto do bloqueio
`frontend/tsconfig.json` já continha `noUnusedLocals: true` (alteração não
commitada, anterior a esta revisão) sem a limpeza correspondente, e
`npm run build` (`tsc -b && vite build`) falhava com **50 erros TS6133**.

### Edições realizadas — 55 símbolos não usados em 26 arquivos

| Arquivo | Símbolos removidos |
|---|---|
| `modules/estudos/EstudosApp.tsx` | `Disciplina` |
| `.../DisciplinasTopicosModal.tsx` | `DISCIPLINE_COLORS` |
| `modules/estudos/services/estudosService.ts` | `EditalResumo`, `EstatisticaDisciplina`, `EstatisticaDisciplinaDTO` |
| `modules/estudos/views/CatalogoView.tsx` | `ArrowLeft`, `ArrowRight`, `Plus`, `logoMark` |
| `modules/estudos/views/DashboardView.tsx` | `AlertTriangle`, `BarChart2`, `BookOpen`, `ExternalLink`, `TrendingUp`, `formatarMinutos`, `formatarSegundos`, `formatarHoras`, `EmptyJourneyState`, `onIrParaDisciplinas` |
| `modules/estudos/views/PlanejamentoView.tsx` | `onRegistrarBloco`, `registroPlanejamentoRevision`, `usuario`/`useAuth` |
| `modules/notas/NotasApp.tsx` | `React` (default) |
| `.../LixeiraModal.tsx` | `AlertCircle` |
| `modules/notas/services/notaService.ts` | `ResultadoBuscaNota` |
| `modules/notas/views/LandingView.tsx` | `BookMarked`, `Sparkles`, `Table` |
| `modules/pomodoro/PomodoroApp.tsx` | `TarefaDTO` |
| `modules/tarefas/TarefasApp.tsx` | `agendaHojeService`, `Prioridade`, `carregandoDados`, `atrasadasAgrupadas` (bloco morto duplicado), `dadosHoje` |
| `.../ConfiguracaoLembreteModal.tsx` | `ShieldCheck`, `AlertCircle` |
| `.../TaskModal.tsx` | `Bell` |
| `modules/tarefas/services/tarefaStorage.ts` | `DisciplinaProgramada` (import) |
| `modules/tarefas/views/LandingView.tsx` | `Clock`, `Sparkles` |
| `.../SidebarContext.tsx` | `React` (default) |
| `.../GlobalSessionModal.tsx` | `GlobalSessionModalOpenParams` |
| `shared/services/agendaHojeService.ts` | campo `inicializado` (escrito, nunca lido) |
| `test/appSwitcher`, `fase10WebPush`, `fase7IntegracaoDiaria`, `fase8Notas` | `React` (default) |
| `test/fase10WebPush.test.tsx` | `originalNavigator` |
| `test/fase11Accessibility.test.tsx` | `useRef` |
| `test/fase5GlobalSessionTimer.test.tsx` | `SessaoGlobalService`, `AlarmService` |
| `test/fase7IntegracaoDiaria.test.tsx` | `AgendaHojeData`, `useGlobalSessionModal` |
| `test/fase9ExclusaoConta.test.tsx` | `React`, `act` |
| `test/estudosNormalizado.test.ts` | 2 × parâmetro `url` → `_url` |

### O que foi deliberadamente **NÃO** tocado
- `tarefaStorage.ts:120-137` — usa `tarefa.trilhaId`, `tarefa.cicloId`,
  `tarefa.ocorrenciaId`, que **não existem** no tipo `Tarefa`. Dívida de **R7/R13**.
- `trilha/Ciclo.java`, `Trilha.ciclos`, `TrilhaRepository` e a coleção
  `planejamento_ciclo` — dívida de **R5**.
- `EditalRepository:98` (`LocalDate.now()`) — dívida de **R2/R10**.
- `usaPadraoGlobalLembrete` (46 ocorrências) — decisão **D-02** pendente.
- `--strict` no `tsconfig`: **não** está em D-44 e exporia 13 erros de domínio
  (10 × TS2339 em `tarefaStorage.ts` + 3 outros) fora do escopo da R0.
- ESLint (163) e knip (73): medidos e registrados; correções em R13.

### Mudança transversal no CI
`.github/workflows/ci-cd.yml` estava vermelho por três motivos independentes
(build TS, `RESEND_API_KEY` ausente e `MONGODB_URL` implícito).


---

## 11. Achados DESCARTADOS (com evidência)

| Hipótese do guia | Evidência | Veredito |
|---|---|---|
| **META-02**: pacotes duplicados | 21 diretórios, 0 duplicidade; `uniq -d` de nomes de classe → 0; `package br.com.trilhara.{estudos,tarefas,notas}` → 0 | **DESCARTADO** |
| Nomes não canônicos `dataEstudo/ordemDia/duracaoRealSegundos/horarioDisparoUtc/dataTermino` | 0 ocorrências em `backend/src` e `frontend/src` | **DESCARTADO** (já canônicos) |
| `deleteMany\(new Document\(\)\)` | 0 ocorrências da forma exata | **DESCARTADO** como escrito; **CONFIRMADO** como risco na forma com filtro (E1) |
| Fallback inseguro `"local"` | 1 ocorrência, apenas comentário em `UsuarioAtual.java:19` | **DESCARTADO** (allowlist) |
| `dangerouslySetInnerHTML` / `location.reload()` | 0 ocorrências | **DESCARTADO** |
| `console.(log\|debug\|info)` em `frontend/src` | 0 fora de teste | **DESCARTADO** |
| `MongoIndexInitializer` loga conflito sem abortar | `onStart` lança `IllegalStateException` e `MongoIndexHealthCheck` reporta `DOWN` | **DESCARTADO** |
| Contagens de teste regressivas (META-03) | Backend 136→144 (aumentou); frontend 109→109 (igual) | **DESCARTADO** |
| Relatório anterior com "100% Atendido" (META-01) | 240 achados de legado + 163 ESLint + 73 knip + 3 coleções fora do inventário | **CONFIRMADO** (relatório não era confiável) |

---

## 12. Gate de saída da R0

| Etapa do gate comum | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -q verify` (via `env-gate.sh`) | **SUCCESS** — 144 testes, 0 falhas, 0 erros, 8 skipped |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** — 0 erros (3 flags D-44 ativas) |
| Frontend lint | `npm run lint -- --max-warnings 0` | **FALHA esperada** — 163 problemas (dívida de R13) |
| Frontend testes | `npm test -- --run` | **SUCCESS** — 14 arquivos, 109 testes |
| Frontend build | `npm run build` | **SUCCESS** (antes: 50 erros TS6133) |
| Frontend dead code | `npx knip` | **FALHA esperada** — 73 achados (dívida de R13) |
| Raiz | `bash scripts/verificar-legado.sh --report` | **exit 0** com 240 achados registrados |
| Raiz (estrito) | `bash scripts/verificar-legado.sh` | **exit 1** (por desenho, até R13) |

**Critérios de aceite da R0 — todos atendidos:**
- [x] `scripts/verificar-legado.sh` existe, é executável e reporta achados;
      `--report` retorna 0.
- [x] `docs/RASTREABILIDADE-REESTRUTURACAO.md` e `docs/REVISAO-IMPLEMENTADA.md`
      existem com baseline (contagens, rotas, índices reais, pacotes).
- [x] Build e testes existentes continuam verdes; contagem de testes
      **aumentou** (136→144 backend; 109 frontend inalterado).
- [x] Nenhuma correção funcional/de comportamento foi feita.

**Bloqueios registrados (não impedem a R0, impedem R13):**
`npm run lint` (163 problemas) e `npx knip` (73 achados) ainda falham — por
desenho, as correções pertencem às fases de domínio e a R13, que exige ambos em
zero no gate duplo.

---

## 13. Arquivos criados / alterados / removidos na R0

**Criados (6):**
```
scripts/env-gate.sh
scripts/verificar-legado.sh
frontend/eslint.config.js
frontend/knip.json
backend/src/test/java/br/com/trilhara/arquitetura/TenantIsolationArchTest.java
backend/src/test/java/br/com/trilhara/shared/RotasInventarioTest.java
docs/REVISAO-IMPLEMENTADA.md
docs/RASTREABILIDADE-REESTRUTURACAO.md
```

**Alterados (30):**
```
backend/pom.xml                                  (archunit 1.5.1 + jacoco 0.8.16)
frontend/package.json                            (scripts typecheck/lint/knip + devDeps)
frontend/package-lock.json                       (novas dependências)
frontend/tsconfig.json                           (noUnusedParameters, noFallthroughCasesInSwitch)
.github/workflows/ci-cd.yml                      (RESEND_API_KEY, MONGODB_URL, npm test)
+ 26 arquivos de frontend/src com remoção de símbolo não usado (§10)
```

**Removidos:** nenhum.

executam sob o JDK 25 usado pelo backend (`Detected Java version 25.0.5.0.3`).
