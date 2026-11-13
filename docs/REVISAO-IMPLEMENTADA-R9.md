# R9 — Revisão Implementada (F8: Notas — importação/sincronização de trilha, busca PT-BR, autosave, lixeira e limites)

> Relatório da revisão **R9**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R8.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-13 · backend Java/Quarkus + frontend React.

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-28** reimportação silenciosa | **Confirmado** | `NotaSincronizacaoService.java:60` desvinculava incondicionalmente; `CadernoResource` não aceitava `reimportar`; `CodigoErro.TRAIL_ALREADY_IMPORTED` existia sem nenhum `throw` | `importarTrilha(usuarioId, trilhaId, reimportar)` responde `409 TRAIL_ALREADY_IMPORTED` com `errors.cadernoId`; `?reimportar=true` desvincula o antigo e cria novo com `titulo = trilha.nome` (sem sufixo). Testes `importarSemReimportarComVinculoExistenteRetorna409`, `reimportarDesvinculaAntigoECriaNovoComNomeDaTrilha` |
| `SincronizacaoTrilhaResponse` sem `secoesRenomeadas` | **Confirmado** | record com 4 contadores, nenhum de renomeação | Record com `secoesCriadas`, `secoesRenomeadas`, `secoesDesvinculadas`, `paginasCriadas`. Teste `sincronizarCriaSecaoParaDisciplinaNovaComPaginas` |
| Sincronização criando/alterando páginas | **Confirmado (RI-29 descartado como regressão)** | O fluxo só renomeia/desvincula, mas nenhum teste provava que páginas existentes ficam intactas | Teste `sincronizarNaoCriaNotasEmSecoesExistentes`: edita o conteúdo, adiciona tópico na disciplina, sincroniza e verifica `paginasCriadas=0` + conteúdo preservado |
| Caderno sem vínculo → 400 genérico | **Confirmado** | `BadRequestException("Caderno não vinculado...")` sem `errors` | `ValidationException("cadernoId", ...)` → `400 VALIDATION_ERROR` com `errors.cadernoId`. Teste `sincronizarCadernoSemOrigemRetorna400ComCampo` |
| Trilha de outro usuário | **Confirmado** | busca não checava tenant antes de importar | `trilhaRepository.buscarEntidade(usuarioId, trilhaId)` → `404`. Teste `importarTrilhaDeOutroUsuarioRetorna404` |
| **`impacto-exclusao` inexistente** | **Confirmado** | frontend usava `totalSecoes`/`totalNotas` em cache; nenhuma rota de impacto | `GET /api/cadernos/{id}/impacto-exclusao` e `GET /api/secoes/{id}/impacto-exclusao` contando **só ativos**. Teste `impactoExclusaoContaSoAtivos` |
| **"Restauradas" sem sufixo** | **Confirmado** | `obterOuCriarCadernoRestauradas` retornava o nome fixo, colidindo em múltiplas restaurações | `tituloDisponivel(...)` aplica `" (2)"`, `" (3)"`… apenas ao container novo. Resposta ganhou `realocadaPara { cadernoId, secaoId }`. Teste `restaurarComCadernoPurgadoCriaCadernoESecaoRestauradas` |
| **Offset do snippet** | **Confirmado** | `localizarPrimeiroToken` indexava o texto NFD e fatiava o original; consultas acentuadas deslocavam a janela; snippet podia passar de 180 | `localizarOcorrencias` dobra caractere a caractere com mapa de índices; snippet limitado a 180 **incluindo** reticências. Testes `deveExtrairSnippetComDestaqueDeTermoAcentuado`, `snippetNuncaExcedeOLimite` |
| `destaques` ausente | **Confirmado** | DTO sem realces; frontend teria de reparsear | `DestaqueDTO { inicio, fim }` em UTF-16 do próprio snippet; render por `<mark>` no cliente. Teste `buscaRetornaSnippetTextoPuroComDestaques` |
| **`POST /api/notas` duplicado** | **Confirmado** | rota aninhada **e** `POST /api/notas` com `cadernoId`/`secaoId` no corpo | Rota raiz removida; criação só em `POST /api/cadernos/{id}/secoes/{secaoId}/notas`. Teste `rotaDuplicadaDeCriacaoNaoExiste` |
| `secao.cadernoId` não validado | **Confirmado** | `criarNota` nunca comparava o caderno do path com o da seção | `secaoDoCaderno(...)` responde 404. Teste `notaComSecaoDeOutroCadernoRetorna404` |
| Read endpoint aninhado | **Descartado** | Não existia leitura aninhada; só `GET /api/notas/{id}` | Nenhuma ação; teste `endpointAninhadoDeNotaNaoExiste` documenta o contrato |
| **DC-13** resíduos do modelo embutido | **Confirmado** | `CadernoResponse.secoes`, `CriarNotaRequest.blocoId`, `buscarPorIdSemUsuario` sem call site, `Caderno.secoes?`/`Secao.cor`/`totalNotas`/`notasMetadados as Nota[]`, filtro em `conteudoMarkdown` sempre vazio | DTOs reescritos; campos removidos; `ListaPaginas` consome `NotaMetadata`; tipos do frontend normalizados |
| Trava da última seção | **Confirmado** | `SecaoTabs.tsx:86` escondia "excluir" com 1 seção | Botão sempre visível; caderno com zero seções é estado válido |
| **SEC-16** listagens sem teto | **Confirmado** | `listarCadernos`/`listarSecoes` sem `limit`; `listarNotasMetadados` devolvia `List` truncando com `Math.min(100, …)` silencioso; lixeira carregava tudo | Tetos 500/500 e paginação real com `PaginaNotasResponse`/`LixeiraNotasResponse`; `400 INVALID_PAGINATION`. Testes `paginaInvalidaRetorna400`, `limiteDe500CadernosRetorna409` |
| `?origemTrilhaId` ausente | **Confirmado** | nenhum filtro por trilha na listagem | `GET /api/cadernos?origemTrilhaId=` com `400 INVALID_IDENTIFIER`. Teste `filtroOrigemTrilhaId` |
| Validação de Markdown | **Confirmado ausente** | limite de 512 KB declarado em `Nota.MAX_MARKDOWN_BYTES` mas nunca aplicado; caracteres de controle aceitos | `validarMarkdown` (bytes UTF-8 + controle exceto `\t\n\r`). Testes `markdownAcimaDe512KBRetorna400ValidationError`, `markdownComCaractereDeControleRetorna400` |
| `purgarExpiradasGlobal` sem `@SystemScope` | **Confirmado** | varredura multi-tenant dentro de serviço de tenant | `NotaPurgaService` em `@SystemScope` + `PurgaLixeiraNotasJob` delegando; teste `esvaziarLixeiraEPurgar` |
| N+1 nas contagens | **Confirmado** | uma consulta por caderno/seção | `contarPorCadernosEmLote`/`contarPorSecoesEmLote` via `$group` |
| `origemTopicoId` vs norma | **Confirmado** | campo divergente do padrão `<x>OrigemId` | Renomeado para `topicoOrigemId` em entidade, DTOs, repositórios e `TrilhaRepository` |
| Snippet HTML / SEC-03 / SEC-04 | **Descartados** | backend já devolvia texto puro e `html:false` já estava no `Markdown` | Mantidos; agora com teste anti-`<mark>` do servidor e `LinkSeguro` |
| 800 ms de debounce / índice de texto / `danger` ao esvaziar / ação de importar sempre visível | **Descartados** | já conformes | Nenhuma ação |

### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | Nenhum protocolo de link era validado no editor | `@tiptap/extension-link` não instalado; `tiptap-markdown` registra só o serializador | `LinkSeguro` (mark `link`) com allowlist `http:`/`https:`/`mailto:`, `rel`/`target` reescritos. Teste `linkSeguro.test.ts` |
| **N-2** | Rascunho comparava **relógios**, não conteúdo | `TiptapEditor` comparava `atualizadoEmLocal > atualizadoEm` | Comparação de conteúdo; aviso explícito quando a base do servidor mudou. Teste `rascunho idêntico ao servidor não mostra a barra` |
| **N-3** | Barra de recuperação com `role="alert"` | leitor de tela anunciava como erro urgente | `role="region"` + `aria-label="Rascunho local encontrado"` |
| **N-4** | `alterarTitulo` + `alterarConteudo` em sequência liam estado defasado | restauração de rascunho perdia o título | `aplicarRascunho(titulo, conteudo)` atômico no hook |
| **N-5** | `fase8Notas.test.tsx` alimentava `E3b-arquivos-por-fase` | `verificar-legado.sh` → 5 arquivos | Split em 6 suítes normativas; padrão caiu para **4** |

---

## 2. Contratos congelados (D-29 a D-34)

### 2.1 Rotas

| Método | Rota | Contrato |
|---|---|---|
| GET | `/api/cadernos?origemTrilhaId=` | `List<CadernoResponse>` limitado a 500; ID malformado → `400 INVALID_IDENTIFIER` |
| POST | `/api/cadernos` | `201` + `Location`; `409 RESOURCE_LIMIT_EXCEEDED` no teto |
| GET/PUT/DELETE | `/api/cadernos/{id}` | `CadernoResponse` / `204` / `ExclusaoLixeiraResponse` |
| GET | `/api/cadernos/{id}/impacto-exclusao` | `{ id, titulo, secoes, paginas }` — **só ativos** |
| POST | `/api/cadernos/importar-trilha/{trilhaId}?reimportar=` | `201` / `409 TRAIL_ALREADY_IMPORTED` (+`errors.cadernoId`) / `404` |
| POST | `/api/cadernos/{id}/sincronizar-trilha` | `SincronizacaoTrilhaResponse` (4 contadores) / `400` (+`errors.cadernoId`) |
| GET/POST | `/api/cadernos/{id}/secoes` | `List<SecaoResponse>` (teto 500) / `201` |
| GET/PUT/DELETE | `/api/secoes/{id}` | `SecaoResponse` / `ExclusaoLixeiraResponse` |
| GET | `/api/secoes/{id}/impacto-exclusao` | `{ id, titulo, paginas }` — **só ativos** |
| GET | `/api/cadernos/{id}/secoes/{secaoId}/notas` | `PaginaNotasResponse` (`{ itens, pagina, tamanho, totalItens, totalPaginas }`), sem `conteudoMarkdown` |
| POST | `/api/cadernos/{id}/secoes/{secaoId}/notas` | `201` + `NotaDetalheResponse`; seção de outro caderno → `404` |
| GET/PUT/DELETE | `/api/notas/{id}` | **endpoint canônico** de leitura/atualização/exclusão (D-32) |
| GET | `/api/notas/busca?q=&pagina=&tamanho=` | `BuscaNotasResponse` com `snippet` texto puro + `destaques[]`; query fraca → `400 INVALID_SEARCH_QUERY` |
| GET | `/api/notas/lixeira?pagina=&tamanho=` | `LixeiraNotasResponse` com `breadcrumb` por item, `deletedAt DESC, _id DESC` |
| POST | `/api/notas/lixeira/{tipo}/{id}/restaurar` | `RestauracaoLixeiraResponse` (`ancestralRestaurado`, `realocadaPara`); expirado → `404 TRASH_ITEM_EXPIRED` |
| DELETE | `/api/notas/lixeira/esvaziar` | contadores por coleção |

`POST /api/notas` **não existe** (DC-12) e não há leitura aninhada de página.

### 2.2 Regras cravadas

- **Reimportação:** sem `reimportar=true` a trilha já vinculada é `409`; com `true` o caderno
  antigo é **mantido e desvinculado** (`origemTrilhaId` e `origemDisciplinaId` limpos) e o novo
  recebe `titulo = trilha.nome`.
- **Sincronização:** renomeia caderno/seções vinculadas, cria seções (com páginas vazias) apenas
  para disciplinas novas, desvincula removidas. **Nunca** cria ou altera páginas de seções
  existentes.
- **Snippet:** texto puro ≤ 180 caracteres (reticências incluídas) e `destaques` em UTF-16 do
  próprio snippet; offsets corretos com acento.
- **Markdown:** ≤ 524 288 bytes UTF-8; caracteres de controle rejeitados exceto `\t`, `\n`, `\r`.
- **Limites:** 500 cadernos/usuário, 500 seções/caderno, 2000 páginas/seção →
  `409 RESOURCE_LIMIT_EXCEEDED`; paginação fora do intervalo → `400 INVALID_PAGINATION`.
- **Lixeira:** 30 dias; item expirado é `404 TRASH_ITEM_EXPIRED`, item nunca excluído é
  `404 NOT_FOUND`; containers novos usam `Restauradas`, `Restauradas (2)`, `Restauradas (3)`…
- **Autosave (frontend):** debounce 800 ms, `seq` por página + `AbortController`, flush em
  `visibilitychange: hidden`, rascunho apagado só após `200` com conteúdo **igual** ao enviado.

---

## 3. Arquivos criados / alterados / removidos

**Criados (backend, 16)**
```
backend/src/main/java/br/com/trilhara/nota/dto/DestaqueDTO.java
backend/src/main/java/br/com/trilhara/nota/dto/CadernoImpactoExclusaoResponse.java
backend/src/main/java/br/com/trilhara/nota/dto/SecaoImpactoExclusaoResponse.java
backend/src/main/java/br/com/trilhara/nota/dto/PaginaNotasResponse.java
backend/src/main/java/br/com/trilhara/nota/dto/LixeiraNotasResponse.java
backend/src/main/java/br/com/trilhara/nota/dto/RealocacaoDestinoDTO.java
backend/src/main/java/br/com/trilhara/nota/NotaPurgaService.java
```
**Criados (frontend, 8)**
```
frontend/src/modules/notas/components/Editor/linkSeguro.ts
frontend/src/modules/notas/components/BuscaModal/SnippetDestacado.tsx
frontend/src/modules/notas/components/BuscaModal/BuscaModal.module.css
frontend/src/modules/notas/hooks/useAutosaveNota.ts
frontend/src/test/syncStatusBadge.test.tsx
frontend/src/test/autosave.test.tsx
frontend/src/test/recuperacaoRascunho.test.tsx
frontend/src/test/buscaModal.test.tsx
frontend/src/test/cadernoVazio.test.tsx
frontend/src/test/importacaoAutomatica.test.tsx
frontend/src/test/linkSeguro.test.ts
docs/REVISAO-IMPLEMENTADA-R9.md
```

**Reescritos (backend):** `CadernoResource`, `SecaoResource`, `NotaResource`, `NotaService`,
`NotaSincronizacaoService`, `NotaSnippetExtractor`, `NotaRepository`, `CadernoRepository`,
`SecaoRepository`, `PurgaLixeiraNotasJob`, `Nota`, todos os DTOs de `nota/dto`,
`DomainConflictException` (+`errors`), `DomainConflictExceptionMapper`.

**Reescritos (frontend):** `types/nota.ts`, `services/notaService.ts`, `NotasApp.tsx`,
`TiptapEditor.tsx`, `BuscaModal.tsx`, `LixeiraModal.tsx`, `ImportarTrilhaModal.tsx`,
`SecaoModal.tsx`, `Navbar/SecaoTabs.tsx`, `Navbar/CadernoContextPod.tsx`, `ListaPaginas.tsx`,
`NotaModal.tsx`.

**Removidos:** `frontend/src/test/fase8Notas.test.tsx` (dividido em 6 suítes normativas).

---

## 4. Testes nomeados (R9)

### Backend — `NotaResourceTest` (24 casos) e `NotaSnippetExtractorTest` (6 casos)

`deveExigirAutenticacaoEmTodasAsRotas` · `deveGerenciarCicloCompletoEPermitirCadernoComZeroSecoes` ·
`listagemNaoContemConteudoMarkdown` · `paginacaoInvalidaRetorna400` ·
`endpointAninhadoDeNotaNaoExiste` · `rotaDuplicadaDeCriacaoNaoExiste` ·
`notaComSecaoDeOutroCadernoRetorna404` · `markdownAcimaDe512KBRetorna400ValidationError` ·
`markdownComCaractereDeControleRetorna400` · `buscaRetornaSnippetTextoPuroComDestaques` ·
`buscaNaoRetornaLixeiraNemOutroUsuario` · `importarSemReimportarComVinculoExistenteRetorna409` ·
`reimportarDesvinculaAntigoECriaNovoComNomeDaTrilha` · `importarTrilhaDeOutroUsuarioRetorna404` ·
`sincronizarCadernoSemOrigemRetorna400ComCampo` · `sincronizarNaoCriaNotasEmSecoesExistentes` ·
`sincronizarCriaSecaoParaDisciplinaNovaComPaginas` · `impactoExclusaoContaSoAtivos` ·
`lixeiraPaginadaRestauraAncestraisDoMesmoLote` · `restaurarForaDoPrazoRetorna404TrashItemExpired` ·
`restaurarComCadernoPurgadoCriaCadernoESecaoRestauradas` · `limiteDe500CadernosRetorna409` ·
`filtroOrigemTrilhaId` · `esvaziarLixeiraEPurgar`

Snippet: `deveRejeitarQueryNulaSemLancarNullPointerException` · `deveRejeitarApenasStopWords` ·
`deveExtrairSnippetComDestaqueDeTermoAcentuado` · `snippetNuncaExcedeOLimite` ·
`deveCairNoTituloQuandoNaoHaConteudo` · `deveRejeitarExtracaoComQueryNula`

### Frontend (6 suítes novas, 43 casos)

- `syncStatusBadge.test.tsx` — 4 estados + retry
- `autosave.test.tsx` — debounce, rascunho, 200 idempotente, erro+retry, digitação em voo,
  resposta obsoleta, abort na troca de página, flush em `visibilitychange`, registro global
- `recuperacaoRascunho.test.tsx` — barra por diferença de conteúdo, aviso de versão do servidor,
  descartar, restaurar e envio do rascunho
- `buscaModal.test.tsx` — mínimo alfanumérico, debounce 300 ms, vazio normativo, 429, `<mark>`
  por offsets, XSS do snippet como texto, fusão de intervalos
- `cadernoVazio.test.tsx` — título/descrição do Anexo B e as duas ações
- `importacaoAutomatica.test.tsx` — chave `trilhara:u:{id}:notas_ocultar_modal_importacao`,
  importação direta, reimportação confirmada, rótulo "já vinculada"
- `linkSeguro.test.ts` — allowlist de protocolos, controle no href, `rel`/`target` fixos

### Arquitetura

`repositoriosDeNotaExigemUsuarioId` (habilitada em R9) e `repositoriosDeNotaNaoBuscamSemUsuario`
(anti-regressão de `buscarPorIdSemUsuario`).

---

## 5. Gates executados

| Gate | Comando | Resultado |
|---|---|---|
| Backend (compila) | `./mvnw -o -q -DskipTests test-compile` | OK |
| Backend (suíte) | `./mvnw -o test` | ver §6 |
| Frontend tipos | `npx tsc --noEmit` | 0 erros |
| Frontend lint | `npx eslint src --max-warnings 0` | 0 problemas |
| Frontend testes | `npx vitest run` | **45 arquivos / 293 testes** |
| Frontend build | `npm run build` | OK (só o aviso herdado de chunk > 500 kB em `NotasApp`) |
| Exports ociosos | `npx --no-install knip` | limpo |
| Gate de legado | `bash scripts/verificar-legado.sh --quiet` | **15 achados** (era 16) |

### Gate de legado — composição dos 15

| Padrão | Achados | Origem |
|---|---|---|
| `serializacao-json` | 4 | herdado de R12 (não é escopo de R9) |
| `LocalDate.now` | 1 | herdado de R13 |
| `ZoneId-America/Sao_Paulo` | 1 | herdado de R13 |
| `termo-ciclo-frontend` | 5 | herdado (nomenclatura de ciclos em outros módulos) |
| `E3b-arquivos-por-fase` | 4 | caiu de 5 → 4 com o split de `fase8Notas.test.tsx` |

**Nenhum achado novo introduzido pela R9.** O padrão `xss-e-reload` continua em **0**: o
comentário do `SnippetDestacado` foi reescrito para não citar o identificador proibido.

---

## 6. Resultado final das suítes

```
Backend:  Tests run: 288, Failures: 0, Errors: 0, Skipped: 7  -> BUILD SUCCESS
Frontend: Test Files 45 passed (45) · Tests 293 passed (293)
```

Comparativo com a linha de base herdada da R8 (39 arquivos / 261 testes): **+6 arquivos e
+32 testes** só no frontend, com `fase8Notas.test.tsx` eliminado pelo padrão `E3b`.

`Skipped: 7` no backend é herdado (testes condicionais de ambiente, não relacionados a Notas).

---

## 7. Pendências e limites declarados

1. **Gate de legado ainda falha** com 15 achados herdados de R12/R13
   (`serializacao-json` ×4, `LocalDate.now`, `ZoneId`, `termo-ciclo-frontend` ×5, `E3b` ×4).
   A R9 **não** os declara resolvidos: nenhum pertence ao domínio Notas e a correção exige a
   revisão R12/R13. O critério de aceite da R9 é *não introduzir achados novos* — atendido.
2. **Operações compostas sem transação:** importação/sincronização gravam caderno, seções e
   páginas em sequência. Uma falha no meio deixa caderno parcial, identificável pelo vínculo e
   recuperável pela reimportação. Não há promessa de atomicidade.
3. **Busca** depende do índice textual `idx_notas_usuarioId_deletedAt_text` com analisador
   `portuguese`. Consultas por substring no meio da palavra não retornam resultados — isso é
   comportamento do `$text`, explicitado no estado vazio do modal.
4. **`NotasApp` mantém chunk > 500 kB** por causa do Tiptap; é aviso herdado do Vite, não erro
   de build.
