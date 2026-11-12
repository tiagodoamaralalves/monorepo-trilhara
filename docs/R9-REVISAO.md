# R9 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R9** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R9 — Revisão da F8: Notas (importação, sincronização, busca, autosave, lixeira)

**1. Referência e Diagnóstico**
- Reimportação divergente (RI-28); sincronização mexe em tópicos (RI-29); rascunho incompleto e descarte de resposta vago (RI-30); contagem prévia e `TRASH_ITEM_EXPIRED` ausentes (RI-31); "Restauradas" incompleto (RI-32); estado vazio/pílula (RI-33); snippet com HTML (SEC-03); sanitização de Markdown/CSP (SEC-04); endpoint duplicado (DC-12); modelo embutido legado (DC-13); listagens de cadernos/seções sem limite (SEC-16).

**2. Especificação Técnica, Segurança e UX**
- Contratos:
  - `GET /api/cadernos?origemTrilhaId=` (filtro opcional) → `CadernoResponse[]` com `{ id, titulo, cor, icone, origemTrilhaId, trilhaNome?, totalSecoes, totalPaginas, atualizadoEm }` (≤ 500).
  - `GET /api/cadernos/{id}/secoes` (≤ 500); `GET /api/cadernos/{c}/secoes/{s}/notas` → `Pagina<NotaMetadataResponse>` sem `conteudoMarkdown`.
  - `GET /api/notas/{notaId}` (canônico); `PUT /api/notas/{notaId}` body `{ titulo?, conteudoMarkdown? }` → 200 `NotaDetalheResponse` (`atualizadoEm` atualizado).
  - `POST /api/cadernos/importar-trilha/{trilhaId}[?reimportar=true]` conforme D-29 (`trilhaId` de outro usuário → 404).
  - `POST /api/cadernos/{cadernoId}/sincronizar-trilha` conforme D-29; caderno sem `origemTrilhaId` → `400 VALIDATION_ERROR` com `errors.cadernoId = ["Caderno não vinculado a uma trilha."]`.
  - `GET /api/notas/busca` → `Pagina<ResultadoBuscaNotaDTO>` com `{ notaId, titulo, cadernoId, cadernoTitulo, secaoId, secaoTitulo, atualizadoEm, snippet, destaques }` (D-30).
  - Impacto de exclusão (D-33); lixeira paginada com `{ tipo, id, titulo, breadcrumb, deletedAt, expiresAt }`; restauração conforme D-34; fora do prazo → 404 `TRASH_ITEM_EXPIRED`.
  - Limites: 500 cadernos/usuário, 500 seções/caderno, 2.000 páginas/seção → `409 RESOURCE_LIMIT_EXCEEDED` (acrescentar aos requisitos).
- Hierarquia: criar nota exige seção ativa do caderno do path; mover itens entre cadernos não faz parte do MVP.
- Frontend:
  - Editor: autosave com debounce de 800 ms + D-31; `SyncStatusBadge` no cabeçalho do editor; barra de recuperação com **Restaurar rascunho não salvo** / **Descartar rascunho** (`role="region"`, `aria-label="Rascunho local encontrado"`).
  - Renderização segura (D-30) e extensão de link com validação de protocolo.
  - Busca: `BuscaModal` com debounce de 300 ms, mínimo de 2 caracteres alfanuméricos validado no cliente (mensagem inline, sem request), resultados com `<mark>` por offsets, estado vazio "Nenhuma página encontrada para “{q}”", erro → `SectionErrorCard inline`, 429 → mensagem "Muitas buscas seguidas. Aguarde alguns segundos.".
  - Caderno sem seções: `EmptyState` "Este caderno não tem seções" com as ações **Nova seção** e **Abrir lixeira**.
  - Pílula "Trilha: {nome}" no caderno vinculado; tooltip "Vinculado à trilha; use Sincronizar para atualizar nomes e novas disciplinas".
  - Importação automática: modal só se houver trilha não vinculada e a chave `trilhara:u:{id}:notas_ocultar_modal_importacao` ≠ `"true"`; ação **Importar trilha** sempre visível na sidebar; falha ao consultar trilhas → **não** abre o modal nem exibe erro intrusivo (é apenas uma sugestão); a ação da sidebar continua disponível e, ao ser usada, mostra seus próprios estados de erro/retry.
  - Exclusões: nota → toast com **Desfazer**; seção/caderno → `ConfirmDialog` com contagem; esvaziar lixeira → `danger`.

**3. Checklist Atômico de Execução**
1. Reimportação: implementar o guarda 409 + `reimportar=true` com desvinculação; título = nome da trilha.
2. Sincronização: remover a criação de notas para tópicos em seções existentes; ajustar a resposta.
3. Busca: remover a geração de HTML no snippet; calcular `destaques`; escapar nada no backend (texto puro).
4. Frontend: renderização de snippet por offsets; remover qualquer `dangerouslySetInnerHTML`.
5. Configurar Tiptap/markdown: `html: false`, allowlist de protocolos em links, `rel`/`target`.
6. Validação backend de caracteres de controle e tamanho (D-30d).
7. Remover o endpoint aninhado de leitura de nota e ajustar o frontend.
8. Implementar `impacto-exclusao` de caderno/seção e o `ConfirmDialog` com contagens.
9. Implementar `TRASH_ITEM_EXPIRED` e o caderno "Restauradas" (D-34).
10. Implementar limites e o filtro `origemTrilhaId` em `GET /api/cadernos`.
11. Rascunho completo + `seq`/`AbortController` + flush + `useUnsavedChanges` (D-31).
12. UI: estado vazio de caderno, pílula, busca, importação automática.
13. Verificar e remover vestígios do modelo embutido (`secoes`/`paginas` dentro de caderno, trava de última seção) em backend e frontend (`types/nota.ts`, `notaService.ts`).
14. Em Estudos, habilitar o link "Abrir no Notas" (R3) usando `?origemTrilhaId=`.
15. Habilitar ArchUnit para o pacote `nota`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend `NotaResourceTest` (ampliado): `importarSemReimportarComVinculoExistenteRetorna409`; `reimportarDesvinculaAntigoECriaNovoComNomeDaTrilha`; `sincronizarNaoCriaNotasEmSecoesExistentes`; `sincronizarCriaSecaoParaDisciplinaNovaComPaginas`; `buscaRetornaSnippetTextoPuroComDestaques` (sem `<`/`>` gerados pelo backend; offsets apontam para o termo); `buscaAcentoInsensivel` ("Constituição"/"constituicao"); `buscaNaoRetornaLixeiraNemOutroUsuario`; `buscaQueryCurtaRetorna400`; `listagemNaoContemConteudoMarkdown`; `markdownComCaractereDeControleRetorna400`; `markdownAcimaDe512KBRetorna400ValidationError`; `restaurarForaDoPrazoRetorna404TrashItemExpired`; `restaurarComCadernoPurgadoCriaCadernoESecaoRestauradas`; `impactoExclusaoContaSoAtivos`; `importarTrilhaDeOutroUsuarioRetorna404`; `endpointAninhadoDeNotaNaoExiste`; limites → 409.
- Frontend: `syncStatusBadge.test.tsx` (4 estados, rótulos exatos, retry); `autosave.test.tsx` (debounce 800 ms; falha → rascunho completo com `baseAtualizadoEmServidor`; resposta obsoleta de outra nota ignorada; troca de nota aborta + preserva rascunho; flush em `visibilitychange`; `beforeunload` registrado enquanto sujo); `recuperacaoRascunho.test.tsx` (conteúdo diferente → barra; aviso de servidor alterado); `buscaModal.test.tsx` (sem request com 1 caractere; `<mark>` em texto escapado: snippet `"<img src=x onerror=alert(1)>"` renderizado como texto); `cadernoVazio.test.tsx`; `importacaoAutomatica.test.tsx`.
- `rg "dangerouslySetInnerHTML" frontend/src` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-29 — Importação, reimportação e sincronização**
- *Dúvida/Lacuna:* Reimportação cria um título com sufixo e não está claro se desvincula; a sincronização cria notas para tópicos novos.
- *Deliberação e Referência:* Requisitos aplicáveis são inequívocos. Um guarda contra reimportação acidental segue o padrão do Notion "importar novamente cria cópia" com confirmação.
- *Decisão Cravada:* `POST /api/cadernos/importar-trilha/{trilhaId}` sem parâmetro: se já houver um caderno ativo vinculado → `409 TRAIL_ALREADY_IMPORTED` (`errors.cadernoId`). Com `?reimportar=true`: limpa `origemTrilhaId` do caderno antigo e `origemDisciplinaId` das seções dele; cria o novo caderno com `titulo = trilha.nome` (sem sufixo). A UI pede confirmação ("O caderno atual será mantido e desvinculado…"). Sincronização: renomeia caderno/seções vinculadas, cria seções (com páginas vazias dos tópicos) **somente para disciplinas novas**, desvincula seções de disciplinas removidas; **não cria nem altera notas de tópicos em seções existentes**. A resposta é `{ secoesCriadas, secoesRenomeadas, secoesDesvinculadas, paginasCriadas }`.

**D-30 — Busca, snippet e renderização segura de Markdown**
- *Dúvida/Lacuna:* O backend gera um snippet com tags HTML; não há política de sanitização.
- *Deliberação e Referência:* OWASP XSS Prevention Cheat Sheet (regra: dados não confiáveis nunca como HTML); GitHub/Obsidian desabilitam HTML bruto em Markdown de usuário; CSP como segunda camada.
- *Decisão Cravada:* (a) O backend retorna `snippet: string` (texto puro, ≤ 180 caracteres, sem marcação) + `destaques: [{ inicio: number, fim: number }]` (offsets em UTF-16 do snippet). (b) O frontend renderiza com nós de texto React e `<mark>`; `dangerouslySetInnerHTML` é **proibido** no repositório (regra ESLint `react/no-danger: error`). (c) Tiptap/Markdown com HTML bruto desabilitado (`html: false`), links com protocolo na allowlist `http:`, `https:`, `mailto:` e atributos `rel="noopener noreferrer nofollow" target="_blank"`; colagem passa pelo schema do Tiptap (descarta nós desconhecidos). (d) O backend armazena Markdown como texto (sem renderizar), valida tamanho (UTF-8 ≤ 524288 bytes) e rejeita caracteres de controle exceto `\t\n\r` (`400 VALIDATION_ERROR`).

**D-31 — Autosave, rascunho e perda de dados**
- *Dúvida/Lacuna:* A recuperação compara relógios; o descarte de respostas obsoletas é vago.
- *Deliberação e Referência:* Relógios de cliente divergem; o Notion/Google Docs comparam a versão-base. `AbortController` é o mecanismo padrão de cancelamento.
- *Decisão Cravada:* (a) Rascunho = `{ titulo, conteudoMarkdown, atualizadoEmLocal, baseAtualizadoEmServidor }`. (b) Ao abrir uma nota, existindo rascunho com conteúdo **diferente** do servidor → barra de recuperação. Se `servidor.atualizadoEm !== baseAtualizadoEmServidor`, ela também avisa "A versão do servidor mudou desde este rascunho". (c) Cada `PUT` carrega um `seq` local por `notaId`; respostas com `seq` menor que o último enviado, ou de outra `notaId`, são ignoradas; troca de nota aborta o request pendente (`AbortController`) **após** gravar o rascunho. (d) Flush imediato (sem debounce) em `visibilitychange: hidden`, troca de nota, `AppSwitcher` e `beforeunload`. (e) Apagar o rascunho apenas após `200` cujo conteúdo enviado seja igual ao rascunho atual. (f) Registrar em `unsavedChangesRegistry` enquanto `SyncStatusState ∈ {salvando, alteracoes-locais, erro}`.

**D-32 — Endpoint canônico de nota**
- *Decisão Cravada:* A leitura completa usa somente `GET /api/notas/{notaId}`. Remover `GET /api/cadernos/{c}/secoes/{s}/notas/{n}`.

**D-33 — Contagem de impacto antes de excluir**
- *Decisão Cravada:* `GET /api/cadernos/{id}/impacto-exclusao` → `{ secoes, paginas }`; `GET /api/secoes/{id}/impacto-exclusao` → `{ paginas }`; `GET /api/listas-tarefas/{id}/impacto-exclusao` → `{ tarefas, seriesSuspensas }`. Contam apenas itens **ativos** (os que receberão o `loteExclusaoId`). O `ConfirmDialog` exibe os números; se o fetch de impacto falhar, o dialog mostra `SectionErrorCard` compacto e o botão destrutivo fica desabilitado.

**D-34 — "Restauradas" com caderno purgado**
- *Decisão Cravada:* Se a seção estiver purgada, mas o caderno ativo/restaurável → criar/reutilizar a seção "Restauradas" nesse caderno. Se o caderno estiver purgado → criar/reutilizar o caderno "Restauradas" e, dentro dele, a seção "Restauradas". Conflito de nome → sufixo " (2)", " (3)". A resposta indica `realocadaPara: { cadernoId, secaoId }`.

---

## Contexto normativo de domínio pertinente

## 10. Notas

### 10.1 Modelo e importação de trilhas

- Coleções separadas para cadernos, seções e páginas/notas, com `usuarioId` e IDs/referências; não embutir todas as seções/páginas no caderno.
- Caderno importado guarda `trilhaId`; seção importada guarda `disciplinaId`; página originada de tópico pode guardar `topicoId` para referência, sem sincronizar tópicos no MVP.
- Importação cria cópia inicial: nome da trilha → caderno; disciplina → seção; tópico → página vazia com título do tópico.
- Uma trilha pode ter um caderno vinculado. Reimportar desvincula o caderno antigo (limpando referências de origem de suas seções), preserva conteúdo e cria novo caderno associado. O usuário pode apagar o antigo manualmente.
- Caderno com `trilhaId` exibe pílula identificadora.
- Ao abrir Notas, consultar trilhas em segun. Se houver trilha não importada e a preferência local permitir, abrir modal automaticamente. Mostrar ação destacada de importação quando houver trilha não importada, mesmo com modal dispensado.
- “Não mostrar automaticamente” é preferência local do navegador em `localStorage`; não impede consulta nem oculta a ação destacada. Não abrir automaticamente naquele navegador enquanto estiver marcada.
- Sincronização é explícita por ação do usuário: renomear caderno/seções por IDs e adicionar novas disciplinas como seções; disciplinas removidas preservam seção/páginas e desvinculam; seções criadas localmente permanecem. Não comparar/sincronizar tópicos nem alterar conteúdo de páginas.
- Se a trilha for apagada, preservar caderno e páginas, remover referências de origem e pílula.
- Um caderno pode ficar temporariamente com zero seções ativas (por exemplo, quando sua única seção é movida para a lixeira), exibindo estado vazio orientando criar uma nova seção ou restaurar da lixeira (removendo a trava legada que impedia excluir a última seção).

### 10.2 Edição, busca, projeção e lixeira

- Editor Tiptap com autosave/debounce, mostrando estados salvando/salvo/erro. Sem upload de imagens no momento.
- Autosave com debounce de `800ms`. A cada alteração ainda não confirmada pelo backend (ou quando o salvamento falhar por rede/401), gravar rascunho de contingência em `localStorage` (`trilhara:u:{usuarioId}:nota_draft:{notaId}`) contendo `{ titulo, conteudoMarkdown, atualizadoEmLocal, baseAtualizadoEmServidor }`. Em caso de erro da API, exibir estado visual `"Erro ao salvar"` com botão **"Tentar novamente"**; ao reabrir uma página que possua rascunho local mais novo que a versão do servidor, exibir barra de recuperação oferecendo **Restaurar rascunho** ou **Descartar**. Estratégia de conflito é última gravação vence. Quando trocar de página durante requisição em andamento, cancelar/ignorar resposta antiga por `notaId` e não apagar rascunho da nota nova/errada.
- Coleção `notas` armazena `conteudoMarkdown` (limite validado de até `512 KB` por página). Listagens de páginas (`GET /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`) são paginadas e projetam exclusivamente metadados (`id`, `cadernoId`, `secaoId`, `titulo`, `ordem`, `topicoOrigemId`, `criadoEm`, `atualizadoEm`), omitindo `conteudoMarkdown` no MongoDB (`Projections.exclude("conteudoMarkdown")`). Carregar Markdown ao abrir a página.
- Busca global (`GET /api/notas/busca?q=...&pagina=1&tamanho=20`) executa no backend sobre todas as páginas ativas (`deletedAt == null`) do usuário. No MongoDB 7 self-hosted, configurar o **único índice de texto da coleção** como índice composto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` com `default_language: "portuguese"` e pesos `{ titulo: 10, conteudoMarkdown: 1 }` (sem `partialFilterExpression`). Busca é token/palavra, sem garantia substring/infix/fuzzy; query menor que 2 caracteres alfanuméricos ou sem tokens pesquisáveis dá 400. Ordenação é relevância DESC, `atualizadoEm DESC`, `_id DESC`. Snippet até 180 caracteres no backend; jamais retornar conteúdo completo e escapar o snippet no frontend. Busca não pode cair em regex/scan de toda a coleção.
- Lixeira hierárquica de 30 dias para página, seção e caderno usando `deletedAt`, `expiresAt` e `loteExclusaoId`:
  1. Excluir caderno ou seção exibe modal de confirmação informando a contagem exata de seções e páginas filhas afetadas e marca toda a subárvore com o mesmo `loteExclusaoId`.
  2. Restaurar um caderno ou seção restaura os descendentes que receberam o mesmo `loteExclusaoId` (preservando no estado de lixeira itens que já haviam sido excluídos isoladamente antes).
  3. Restaurar uma página cuja seção pai (ou caderno pai) também esteja na lixeira restaura automaticamente a seção e o caderno ancestrais; se o ancestral já tiver sido purgado definitivamente após 30 dias, a página é alocada em uma seção/caderno `"Restauradas"` criado automaticamente.
  4. Permitir esvaziar a lixeira antecipadamente; após 30 dias, purgar definitivamente.

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

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

---

## Plano original da FASE 8 (referência de escopo)

## FASE 8 — Domínio Notas: Coleções Separadas, `SyncStatusBadge`, Busca PT-BR, Importação/Sync de Trilha, Autosave e Lixeira

### 8.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas. Pode ser implementada antes ou em paralelo a F6/F7 em branch isolado; F9 aguarda integração.
- **Objetivo da fase:**
  1. Implementar o componente `SyncStatusBadge` no Design System (`frontend/src/shared/design-system`).
  2. Refatorar o pacote `br.com.trilhara.nota` para usar três coleções separadas (`cadernos`, `secoes`, `notas`), projeção sem `conteudoMarkdown` nas listagens, busca global em português no backend com geração de snippets, importação/reimportação/sincronização explícita com trilhas de Estudos e lixeira hierárquica de 30 dias.
  3. Atualizar `frontend/src/modules/notas/` com carregamento sob demanda do Markdown, autosave com rascunho de contingência em `localStorage`, busca global paginada e lixeira hierárquica.

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

---

## Relato da implementação alegada — FASE 8 (hipótese a verificar)

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

