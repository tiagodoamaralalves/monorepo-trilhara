# R3 — Revisão Implementada (F2: Trilhas, disciplinas, tópicos e progresso)

> Relatório da revisão **R3**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0),
> `docs/REVISAO-IMPLEMENTADA-R1.md` e `docs/REVISAO-IMPLEMENTADA-R2.md`.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `r3-revisao-f2` · base `6c1fbc8` (R2).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-01/DC-03** rota legada de progresso | **Confirmado** | `TrilhaResource.java:184-203` (`@PATCH {id}/progresso/{topicoId}`) + `TrilhaService:748` (`atualizarProgresso`) | Removida a rota, o `record AtualizacaoProgresso` e o método de serviço |
| **RI-02** limites com status errado | **Confirmado (parcial)** | Mapper 409 já existia, mas `@Size(max=500)` nos requests disparava Bean Validation (400) antes do serviço, e o teste esperava 400 | `@Size` de tópicos removido dos requests e da entidade; limite centralizado em `ResourceLimitExceededException` (409 `RESOURCE_LIMIT_EXCEEDED`) |
| **RI-03/DC-04** acoplamento Disciplina→caderno | **Confirmado** | `Disciplina.java:28`, `DisciplinaRepository` (3 pontos), `DisciplinaDTO`, `Criar/AtualizarDisciplinaRequest`, `DisciplinasView` (link, input e modal) | Campo `cadernoLink` removido de entidade, BSON, DTOs, service e UI |
| **RI-04** `EditalVerticalizadoView` não migrada | **Descartado** | `rg "EditalVerticalizado"` → 0 arquivos; apenas texto de marketing em `LandingView`/`CatalogoView` | Nada a fazer; registrado como inexistente |
| **RI-05** lease ausente nas exclusões | **Confirmado (parcial)** | Lease existia com TTL de **30 s** hardcoded, liberação assimétrica (`catch` na trilha × `finally` na disciplina) e lógica triplicada com `CronogramaService` | `LeaseTrilhaService` extraído (TTL **120 s**, `executarComLease` com `finally`); `CronogramaService` passou a delegar |
| **DC-05** adaptadores e tipo `Trilha` duplo | **Confirmado** | `estudosService.ts:33-98`, `trilhara.ts:67-92`; `verificar-legado.sh` contava 8 achados em `adaptadores-legados` | Adaptadores e `interface Trilha`/`ItemProgresso` removidos; views consomem os DTOs; grep de legado em **0** |
| **SEC-09** referência cruzada | **Confirmado** | `editalId` inexistente → 404; `reordenarDisciplinas` usava `new ObjectId(...)`; `desmarcar` sem validação | `editalId` inválido/inexistente → 400 `VALIDATION_ERROR` em `errors.editalId`; reordenação usa `BsonIds.parse` (400 `INVALID_IDENTIFIER`); `desmarcar` idempotente e documentado |
| `impacto-exclusao` ausente | **Confirmado** | `rg impacto backend/src` → 0 | `GET /api/trilhas/{id}/impacto-exclusao` implementado |
| Regra ArchUnit do pacote `trilha` | **Ausente** | `TenantIsolationArchTest` com `@ArchIgnore` | `repositoriosDeTrilhaExigemUsuarioId` ativa |

### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | Frontend chamava `PATCH /api/trilhas/{id}/metadados`, rota inexistente | `estudosService.ts:147` | Passou a usar `PUT /api/trilhas/{id}` |
| **N-2** | Reordenação enviava `{ ordemIds }`; backend esperava `{ disciplinaIds }` | `estudosService.ts:194` × `ReordenarDisciplinasRequest` | Campo corrigido e coberto por teste |
| **N-3** | DTOs expunham `usuarioId` **e** `donoId` | `TrilhaResumoDTO`, `TrilhaDetalheDTO` | Identificadores de usuário omitidos do contrato público (D-06) |
| **N-4** | `cargo` e `areaCargo` duplicados com valor idêntico | `TrilhaService:119-121`, `TrilhaRepository:231-232` | Mantido apenas `cargo` |
| **N-5** | IDs sintéticos no cliente (`disciplina_lz3k_1`) | `coresDisciplina.criarIdDisciplina` | Helper removido; criação envia sem `id` e o servidor gera o hex |
| **N-6** | `POST /api/trilhas/importar/{editalId}` fora do Anexo A | `TrilhaResource:164-172` | Rota removida; importação usa `POST /api/trilhas` com `editalId` |
| **N-7** | `TrilhaDetalheDTO.progresso` (mapa `done`/`doneAt`) | `TrilhaDetalheDTO:29` | Mapa removido; cliente deriva `Set<string>` de `topicosConcluidosIds` |
| **N-8** | Payload `disciplinas` vs `disciplinasIniciais` | `catalogoVitrine.ts`, `GlobalSessionModal.tsx` | Variante `disciplinas` removida do contrato |

---

## 2. Contratos resultantes (Anexo A da R3)

- `GET /api/trilhas` → `TrilhaResumoDTO[]` (sem árvore de tópicos; ≤ 50).
- `POST /api/trilhas` → 201 + `Location`; `editalId` **xor** `disciplinasIniciais`.
- `GET|PUT|DELETE /api/trilhas/{trilhaId}`; `GET /api/trilhas/{trilhaId}/impacto-exclusao` → `{ disciplinas, cronogramas, sessoes, cadernosVinculados }`.
- `POST /api/trilhas/{trilhaId}/disciplinas`; `PUT .../disciplinas/reordenar` (204); `PUT|DELETE .../disciplinas/{disciplinaId}`.
- `PUT|DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso` (idempotentes).

**Removidos:** `PATCH /api/trilhas/{id}/progresso/{topicoId}`, `PATCH /api/trilhas/{id}`, `POST /api/trilhas/importar/{editalId}`.

---

## 3. Arquivos principais alterados

**Backend — criados**
`trilha/LeaseTrilhaService.java`, `trilha/dto/TrilhaImpactoExclusaoDTO.java`

**Backend — removidos**
`trilha/Trilha.java`, `trilha/Ciclo.java`, `trilha/Progresso.java`, `trilha/AtualizacaoMetadadosTrilha.java`, `trilha/dto/SalvarTrilhaRequest.java`

**Backend — alterados**
`trilha/TrilhaResource.java`, `trilha/TrilhaService.java`, `trilha/TrilhaRepository.java`, `trilha/Disciplina.java`, `trilha/DisciplinaRepository.java`, `trilha/dto/{TrilhaResumoDTO,TrilhaDetalheDTO,DisciplinaDTO,CriarDisciplinaRequest,AtualizarDisciplinaRequest,CriarTrilhaRequest,ProgressoTopicoDTO}.java`, `cronograma/CronogramaService.java`, `sessao/SessaoRepository.java`, `nota/CadernoRepository.java`

**Frontend — criados**
`test/hub.test.tsx`, `test/topicoOtimista.test.tsx`, `test/excluirTrilha.test.tsx`

**Frontend — alterados**
`shared/contracts/domainContracts.ts`, `modules/estudos/types/trilhara.ts`, `modules/estudos/services/estudosService.ts`, `modules/estudos/EstudosApp.tsx`, `views/{HubView,DisciplinasView,EstudarView,PlanejamentoView,DashboardView,CatalogoView,LandingView}`, `components/{TrilhaModal/TrilhaModal,DisciplinasTopicosModal/DisciplinasTopicosModal,Navbar/Navbar,Navbar/TrilhaPod,Navbar/MobileTrilhaControls}`, `data/catalogoVitrine.ts`, `utils/coresDisciplina.ts`, `shared/design-system/ConfirmDialog/{ConfirmDialog.tsx,.module.css}`, `test/estudosNormalizado.test.ts`

---

## 4. Testes por nome

### Backend — `TrilhaNormalizadaIntegrationTest` (17 testes)

Já existentes (ajustados): `deveCriarTrilhaERetornarResumoSemArrayDeTopicosNoHub`, `deveCalcularPercentualProgressoPonderadoPeloTotalDeTopicos`, `deveGarantirIdempotenciaNoProgressoDoTopico`, `devePreservarIdsEProgressoAoRenomearDisciplinaETopico`, `deveLimparProgressoAoExcluirTopicoERejeitarIdsDesconhecidos`, `deveReordenarDisciplinasComValidacaoEstrita`, `deveIsolarIntegralmentePorUsuarioId`, `deveRetornar409ResourceLimitAoExcederLimites`, `deveRejeitarEditalEDisciplinasIniciaisJuntos`, `deveExcluirTrilhaEDadosAssociados`.

Novos (R3):

| Teste | Critério |
|---|---|
| `naoDeveExistirRotaLegadaDeProgresso` | PATCH legado → 404/405 (D-11) |
| `deveRejeitarEditalInexistenteCom400NoCampoEditalId` | SEC-09: 400 + `errors.editalId` |
| `deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha` | tópico de B no path de A → 404 |
| `deveRetornar409SeLeaseOcupadoAoExcluirDisciplina` | lease ocupado → 409 `SCHEDULE_GENERATION_IN_PROGRESS` (trilha e disciplina) |
| `impactoExclusaoTrilhaContaCorretamente` | `{2,1,1,1}` |
| `deveRejeitarIdMalformadoComInvalidIdentifierNaReordenacao` | 400 `INVALID_IDENTIFIER` |
| `deveIsolarTodasAsRotasEntreUsuarios` | A×B em 10 rotas + trilha intacta |

### Backend — demais ajustados

`AuthorizationByOwnerTest#auditarProgressoTopicos` (fixture passou a usar hex de 24), `AuthResourceTest` (payload `disciplinasIniciais`, sem `donoId`), `NotaResourceTest` (construtor de `Disciplina` sem caderno), `TenantIsolationArchTest#repositoriosDeTrilhaExigemUsuarioId` (nova regra ativa).

### Frontend

| Arquivo | Testes |
|---|---|
| `estudosNormalizado.test.ts` (reescrito, 10) | sem adaptadores; `disciplinaIds`; `PUT /trilhas/{id}`; `POST /trilhas` na importação; progresso via `topicosConcluidosIds` |
| `hub.test.tsx` (novo, 5) | skeleton ×3 → cards; vazio → `EmptyState`; erro → `SectionErrorCard` + retry; nunca `EmptyState` antes da 1ª resposta |
| `topicoOtimista.test.tsx` (novo, 2) | marca antes da resposta; rollback em falha |
| `excluirTrilha.test.tsx` (novo, 3) | botão só habilita com nome exato; impacto em números; exclusão só com nome correto |

---

## 5. Gate de saída (executado)

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -o verify` | **BUILD SUCCESS** — 190 testes, 0 falhas, 0 erros, 7 skipped (R2: 181) |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** — 0 erros |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** — 0 erros, 0 avisos |
| Frontend testes | `npm test -- --run` | **SUCCESS** — 25 arquivos, 177 testes (R2: 167) |
| Frontend build | `npm run build` | **SUCCESS** (aviso pré-existente de chunk `NotasApp` 545 kB) |
| Frontend dead code | `npx knip` | **SUCCESS** — exit 0 |
| Raiz (relatório) | `bash scripts/verificar-legado.sh --report` | **186 achados** (R2: 213) — `adaptadores-legados` **8 → 0** |
| Raiz (estrito) | `bash scripts/verificar-legado.sh` | **exit 1** — resíduos de ciclo (`termo-ciclo-*`: 4 backend + 86 frontend), nomenclatura e arquivos por fase |

### Critérios de aceite da R3

| Critério | Evidência | Status |
|---|---|---|
| `rg "adaptarTrilha\|progresso/\{topicoId\}\|cadernoId" frontend/src/modules/estudos backend/src/main/java/br/com/trilhara/trilha` → 0 | execução do grep | Coberto |
| 409 em 50/100/500 limites | `deveRetornar409ResourceLimitAoExcederLimites` | Coberto |
| `editalId` xor `disciplinasIniciais` | `deveRejeitarEditalEDisciplinasIniciaisJuntos`; `deveRejeitarEditalInexistenteCom400NoCampoEditalId` | Coberto |
| Tópico de outra trilha → 404 | `deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha` | Coberto |
| Lease nas exclusões | `deveRetornar409SeLeaseOcupadoAoExcluirDisciplina` | Coberto |
| Rota legada inexistente | `naoDeveExistirRotaLegadaDeProgresso` | Coberto |
| `impacto-exclusao` | `impactoExclusaoTrilhaContaCorretamente` | Coberto |
| Isolamento A×B | `deveIsolarTodasAsRotasEntreUsuarios`; `AuthorizationByOwnerTest` | Coberto |
| Estados de Hub | `hub.test.tsx` (5) | Coberto |
| Otimista com rollback | `topicoOtimista.test.tsx` (2) | Coberto |
| Exclusão por nome | `excluirTrilha.test.tsx` (3) | Coberto |
| ArchUnit do pacote `trilha` | `TenantIsolationArchTest#repositoriosDeTrilhaExigemUsuarioId` | Coberto |

---

## 6. Bloqueios e pendências herdadas (não pertencem à R3)

| Item | Dono | Evidência |
|---|---|---|
| `ciclos`/`Ciclo` remanescentes no backend e frontend | R5/R7 | `termo-ciclo-backend` 4, `termo-ciclo-frontend` 86 no `verificar-legado.sh` |
| `TrilhaDetalheDTO.ciclos` como `List<Object>` (passagem sem tipo) | R5/R7 | DTO documentado no Javadoc |
| Link "Abrir no Notas" depende de `GET /api/cadernos?origemTrilhaId=` | R9 | não renderizado nesta revisão (sem stub) |
| `verificar-legado.sh` estrito ainda retorna 1 | R5/R7/R12 | nomenclatura, ciclo e arquivos por fase |
| `planejamento_ciclo` ainda referenciada em `desvincularDadosTrilhaENotas` | R5 | `TrilhaRepository` |
