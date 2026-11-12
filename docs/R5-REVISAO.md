# R5 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R5** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R5 — Revisão da F4: Cronogramas, blocos, lease e publicação

**1. Referência e Diagnóstico**
- Divergência entre os dois relatos (META-01); campos não canônicos e `duracaoRealSegundos` (RI-10/DC-08); lote com `<= hoje` (RI-11); coleção de operações (RI-12/DC-07); sem heartbeat, reconciliador e testes de falha (RI-13); padrão de período e rótulos (RI-14); política de concluídos (RI-15); contagem exata de atrasados (RI-27); terminologia "ciclo" (DC-15).

**2. Especificação Técnica, Segurança e UX**
- Modelo canônico de `cronogramas` e `blocos_cronograma` exatamente como no plano F4.2.A, com D-16. `trilhas.proximaVersaoCronograma` (contador atômico) é aceito e deve ser documentado no plano.
- Endpoints:
  - `GET /api/trilhas/{trilhaId}/cronogramas`; `POST` (cria+gera+ativa); `PUT /{id}` (altera+regenera+ativa; `DUPLICATE_MODALITY` antes de qualquer escrita); `PATCH /{id}/ativar`; `DELETE /{id}`.
  - `GET .../cronogramas/{cronogramaId}/blocos?concluido=&statusData=&dataInicio=&dataFim=&pagina=&tamanho=` → `Pagina<BlocoCronogramaItemDTO>`; `statusData` filtra pela relação com `hojeLocal` (D-17f). O cronograma inativo pode ser listado (Planejamento), mas leitura sempre sobre a sua `versaoAtiva`.
  - `GET /api/blocos/hoje` → `BlocoHojeDTO[]` com `trilhaNome` (nome **atual** da trilha) para ordenação.
  - `PATCH /api/blocos/{blocoId}/conclusao` body `{ concluido }` (somente essa propriedade); bloco de versão não ativa → 404.
  - `POST .../blocos/concluir-atrasados` conforme D-17.
- `CronogramaDTO` conforme o contrato do cronograma estabelecido neste guia (`configuracao`, `quantidadeBlocos`, `cargaMinutosPorDia`, `estadoPublicacao`).
- Validações de entrada: `disciplinasParticipantesIds` todas da trilha/usuário; `semanaModeloManual` com IDs da trilha; dias ISO 1..7 únicos; `blocosPorDia` 1..24; `duracaoBlocoMinutos` 30..240; datas estritas.
- Lease (D-18): heartbeat de 30 s; a perda do token aborta a publicação.
- `ReconciliadorCronogramasJob`; `FaultInjector` em teste.
- UI `PlanejamentoView`:
  - Terminologia "Cronograma" em 100% dos textos/tipos; abas por cronograma seguindo o APG Tabs; badge "Ativo".
  - Formulário: modalidade já usada desabilitada com tooltip "Esta trilha já possui um cronograma {modalidade}"; período padrão conforme D-20; aviso de horizonte; sobrecarga em banner não bloqueante (`role="status"`) com os dias em sobrecarga listados.
  - Botões **Criar cronograma** / **Salvar e gerar blocos** (com `ConfirmDialog` de regeneração).
  - Lista: seletor Pendentes/Concluídos (D-19); "Estudar este bloco" inicia o timer com `blocoId`; checkbox de concluir abre `GlobalSessionModal BLOCO_DIRETO`; "Concluir atrasados sem sessão (N)" com N vindo de `statusData=ATRASADO&tamanho=1`.
  - Estados: geração em andamento → botão `loading` e a seção de blocos em skeleton; 409 `SCHEDULE_GENERATION_IN_PROGRESS` → toast; `estadoPublicacao = RECUPERACAO_NECESSARIA` → `SectionErrorCard` "O cronograma está sendo recuperado" + retry.

**3. Checklist Atômico de Execução**
1. Verificar e unificar o modelo do bloco: renomear/remover `dataEstudo`, `ordemDia`, `status`, `duracaoRealSegundos`; ajustar repositório, DTOs, índices e testes.
2. Corrigir o filtro do lote para `data < hojeLocal` e `versaoGeracao = versaoAtiva`.
3. Apagar `OperacaoConcluirAtrasadosRepository` e a coleção; implementar D-17.
4. Implementar o filtro `statusData`.
5. Implementar o heartbeat do lease e a verificação de token antes de cada etapa de publicação.
6. Implementar `ReconciliadorCronogramasJob` e chamá-lo no `StartupEvent`.
7. Criar `FaultInjector` (interface em `main` com implementação no-op; implementação controlável em `src/test`).
8. Garantir que snapshot de nome/cor atualize os blocos dos cronogramas **ativos** da trilha (regra normativa), sem tocar `disciplinaId`.
9. Remover as duplicidades `PlanejamentoCicloRepository` em ambos os pacotes (`rg -l PlanejamentoCiclo` → zero).
10. Atualizar `PlanejamentoView` conforme o item 2 (UI).
11. Substituir "ciclo/Ciclo" em textos, tipos e nomes (`rg -i "\bciclos?\b" frontend/src/modules/estudos frontend/src/modules/tarefas` → zero, exceto "ciclo Pomodoro" no módulo Pomodoro).
12. Habilitar ArchUnit para o pacote `cronograma`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend (`CronogramaNormalizadoIntegrationTest` + `PublicacaoCronogramaFaultInjectionTest`):
  - `geradoresDeterministicos` (snapshot dos 3 modos com seed fixa de dados); `ponderadoRespeitaDeficit` (proporções em 1.000 blocos dentro de ±1 bloco); `limite366DiasE24PorDia`.
  - `concorrenciaGeracao` (2 requests paralelos → 201 + 409; exatamente 1 `ativo:true`).
  - `duplicateModalityNaoAlteraBlocos` (contagem/IDs de blocos iguais antes e depois).
  - Para cada etapa `1..6` da publicação: `falhaNaEtapaN_leitorVeVersaoAnteriorOuNovaCompleta` e `reconciliadorConvergeIdempotente`.
  - `leaseExpiradoNaoPublica` (token roubado → worker antigo não altera ponteiro).
  - `concluirAtrasadosNaoIncluiHoje`; `concluirAtrasadosRetryNaoDuplica`; `concluirAtrasadosParaleloRetorna409`; `statusDataAtrasadoTotalItensExato`.
  - `patchConclusaoAceitaSomenteConcluido` (body com `duracaoRealSegundos` → 400).
  - `blocoDeVersaoAntigaRetorna404`.
  - `InventarioColecoesTest` (nenhuma coleção de operações).
- Frontend: `planejamento.test.tsx` (modalidade desabilitada; padrão "Até a prova" só com `dataProva` futura; aviso > 366 dias; seletor Pendentes/Concluídos; Reabrir sem modal; concluir abre modal; contagem N exata a partir de `totalItens`).
- `rg "dataEstudo|ordemDia|duracaoRealSegundos|OperacaoConcluirAtrasados|PlanejamentoCiclo" backend frontend` → zero.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-16 — Estado do bloco e vínculo com sessão**
- *Dúvida/Lacuna:* O 2º relato da F4 adicionou `status` (com `CANCELADO`) e `duracaoRealSegundos`.
- *Deliberação e Referência:* Requisitos aplicáveis (campos obrigatórios fechados) e regra normativa ("Sessão e bloco não precisam de vínculo persistente"); princípio 3 (métrica não duplicada).
- *Decisão Cravada:* O bloco tem **apenas** `concluido: boolean` + `concluidoEm?`. Remover `status`, `CANCELADO`, `duracaoRealSegundos` e qualquer referência de sessão no bloco. `PATCH /api/blocos/{id}/conclusao` aceita **somente** `{ "concluido": boolean }`.

**D-17 — "Concluir atrasados" sem coleção de operações**
- *Dúvida/Lacuna:* Uma coleção extra guarda respostas por `operationId`; a contagem exibida vem da página carregada.
- *Deliberação e Referência:* Uma operação *naturalmente idempotente* (setar `concluido=true` onde `false`) não precisa de cache de resposta (Stripe só exige chave quando o efeito não é idempotente). O plano proíbe coleção de operações.
- *Decisão Cravada:* (a) Remover `OperacaoConcluirAtrasadosRepository`/coleção. (b) Body `{ "operationId": "<uuid>" }` mantido **apenas para correlação de log**. (c) O processamento adquire o **lease da trilha** (mesmo mecanismo da geração); se estiver ocupado, retorna `409 SCHEDULE_GENERATION_IN_PROGRESS`. (d) Filtro estrito `{usuarioId, trilhaId, cronogramaId, versaoGeracao: versaoAtiva, concluido: false, data: { $lt: hojeLocal }}`, atualização item a item por `_id` com `concluido:false` no filtro. (e) Resposta `{ operationId, totalProcessados, totalConcluidos, falhas: [{ blocoId, code }] }`; retry recalcula (o que já foi concluído não é reprocessado). (f) Novo filtro `statusData=ATRASADO|HOJE|FUTURO` no endpoint paginado de blocos, para que a UI obtenha a **contagem exata** via `totalItens` com `tamanho=1`.

**D-18 — Publicação de cronograma resiliente**
- *Decisão Cravada:* (a) Heartbeat de renovação do lease a cada 30 s durante a geração (`ScheduledExecutorService` ligado ao token; parar se a renovação falhar e abortar sem publicar). (b) `ReconciliadorCronogramasJob` (`@SystemScope`, no startup e a cada 5 min) trata `PREPARANDO` com lease expirado (remove staging) e `RECUPERACAO_NECESSARIA` (conclui flags/limpeza idempotentemente). (c) Injeção de falhas via bean `FaultInjector` **existente apenas no classpath de teste** (`src/test`), com implementação no-op em produção — nunca hooks estáticos no código de produção. (d) Reler `trilhas.cronogramaAtivoId/versaoAtivaCronograma` como fonte de leitura de blocos; o flag `ativo` nunca é usado sozinho em leitura.

**D-19 — Concluídos no Planejamento × lista virtual**
- *Dúvida/Lacuna:* Como reabrir um bloco se a lista virtual não mostra concluídos?
- *Deliberação e Referência:* No Todoist, as concluídas ficam em uma visão separada com "desmarcar"; a lista ativa só mostra pendentes.
- *Decisão Cravada:* A lista virtual em Tarefas mostra **só pendentes**. O `PlanejamentoView` tem um seletor segmentado **Pendentes** (padrão) | **Concluídos**; em "Concluídos", cada item tem a ação **Reabrir** (chama `PATCH {concluido:false}` direto, sem modal, sem apagar sessões). Marcar como concluído **sempre** abre o `GlobalSessionModal` (`BLOCO_DIRETO`).

**D-20 — Período padrão do cronograma**
- *Decisão Cravada:* O padrão "Até a data da prova" depende de `trilha.dataProva > dataInicio`, e não de edital. Se `dataProva − dataInicio > 365 dias`, o formulário mostra aviso inline (`role="status"`): "Serão gerados os primeiros 366 dias. Você poderá atualizar o cronograma depois." O botão final se chama **Criar cronograma** (criação) e **Salvar e gerar blocos** (edição pelo lápis), com `ConfirmDialog` informando que conclusões de blocos serão substituídas (sessões e progresso permanecem).

---

## Contexto normativo de domínio pertinente

## 5. Estudos: cronogramas e blocos

### 5.1 Conceito, quantidade e estado ativo

- Na interface e no modelo de domínio, tratar **cronograma** como o conceito que contém configuração, modalidade e blocos gerados; substituir gradualmente a terminologia que separa “ciclo” da sua agenda de blocos.
- Cada trilha pode ter até três cronogramas: um sequencial, um ponderado e um manual.
- Não permitir selecionar/criar outra instância de uma modalidade já existente na mesma trilha. Na escolha de modalidade, a já utilizada fica indisponível.
- No máximo um cronograma ativo por trilha. Criar um novo cronograma o torna ativo e desativa o anterior.
- Integrações de Tarefas/Pomodoro usam o cronograma ativo de cada trilha. Cronogramas ativos em trilhas distintas podem aparecer juntos.
- Se o cronograma ativo for removido/inativado, ativar o cronograma restante mais recentemente criado/atualizado; se não houver outro, não há blocos para essa trilha.

### 5.2 Criação, edição e geração

- O botão final do assistente chama-se **Criar cronograma**. Salvar configuração e gerar os blocos fazem parte de uma única operação de produto.
- **Novo cronograma** cria outra configuração/cronograma sujeito aos limites acima.
- Editar pelo lápis permite mudar modalidade e configurações. Se a nova modalidade escolhida já pertencer a outro cronograma da mesma trilha (`_id != cronogramaId`), rejeitar a operação com `409 Conflict` (`DUPLICATE_MODALITY`) antes de alterar o documento ou gerar blocos. Salvar uma edição válida descarta integralmente as ocorrências antigas e gera blocos novos. Sessões e progresso de tópicos permanecem.
- A ação **Atualizar cronograma/blocos** também substitui integralmente as ocorrências do cronograma; não preservar conclusões ou blocos antigos fora do período escolhido.
- Blocos gerados são estáticos e não editáveis individualmente. Para mudar o plano, editar configuração e gerar de novo.
- A data de início vem preenchida com hoje, mas é editável, inclusive para data futura.
- Opções de período: até a data da prova, 1 semana (7 dias), 1 mês (30 dias) e 3 meses (90 dias). Com data da prova futura em relação à data de início, essa opção é o padrão; sem data da prova (ou se a data da prova já passou), a opção fica desabilitada e o padrão é 1 mês (30 dias).
- Datas inicial e final são inclusivas, inclusive a data da prova. O horizonte máximo por geração é de **366 dias** (`dataFim <= dataInicio.plusDays(365)`, máximo de 8.784 blocos por versão). Se a data da prova estiver a mais de 366 dias da data de início, o sistema gera os primeiros 366 dias e informa que o período restante poderá ser regenerado posteriormente.
- Na modalidade manual, a revisão mostra quantidade de blocos/horas por disciplina. A mesma disciplina pode ocupar qualquer quantidade de blocos e cada bloco precisa de uma disciplina.
- Limite de 24 blocos por dia em todas as modalidades. Duração uniforme do cronograma entre 30 e 240 minutos por bloco. Exigir ao menos uma disciplina participante.

### 5.3 Regras de geração

- **Sequencial circular:** repetir a ordem cadastrada das disciplinas participantes.
- **Ponderado:** peso inteiro $p_i \in \{1..5\}$ define a proporção global dos blocos no período. Para um total de $B_{\text{total}}$ blocos no período e soma de pesos $P = \sum p_k$ das disciplinas participantes, cada posição $t \in \{1..B_{\text{total}}\}$ é atribuída à disciplina $i$ que maximiza o déficit acumulado $\Delta_i(t) = \frac{t \cdot p_i}{P} - \text{alocados}_i(t-1)$, desempatando pela menor `ordem` de cadastro da disciplina na trilha. Alterar pesos na disciplina não altera cronograma já gerado.
- **Manual:** o usuário define a quantidade de blocos por dia da semana (0 a 24) e a disciplina de cada posição; a semana-modelo é repetida ao longo do período.
- Excluir disciplina remove de todos os cronogramas da trilha apenas os blocos com `data >= hojeLocal` e `concluido == false`; blocos passados (`data < hojeLocal`) ou já concluídos (`concluido == true`) permanecem inalterados como histórico visual usando `disciplinaNomeSnapshot` e `disciplinaCorSnapshot`. Renomear disciplina ou alterar sua cor atualiza o snapshot nos blocos dos cronogramas ativos da trilha, sem trocar `disciplinaId`.
- O perfil semanal global de disponibilidade (`disponibilidadeSemanalMinutos`, 7 inteiros de `0` a `1440` para Seg–Dom) é persistido nas preferências do usuário (`usuarios.preferencias`) como referência; ausência usa `[0,0,0,0,0,0,0]`. Cada cronograma mantém sua carga; alterar o perfil não modifica cronogramas existentes.
- Carga semanal por dia soma `duracaoMinutos` de cada bloco planejado por ocorrência daquele dia da semana no período futuro vigente; blocos concluídos e passados não compõem aviso de carga futura. Comparar carga futura restante de todos os cronogramas ativos com disponibilidade e informar `sobrecargaPorDia` sem bloquear salvamento.

### 5.4 Persistência e APIs

- Não guardar milhares de blocos em um array ilimitado dentro do documento do cronograma. Persistir cada ocorrência como documento separado, referenciando usuário, trilha e cronograma.
- Campos obrigatórios da ocorrência em `blocos_cronograma`: `_id` (`ObjectId`), `usuarioId`, `trilhaId`, `cronogramaId`, `versaoGeracao` (`long`), `data` (`yyyy-MM-dd`), `ordem` (`1..24`), `disciplinaId`, `disciplinaNomeSnapshot`, `disciplinaCorSnapshot`, `duracaoMinutos` (`30..240`), `concluido` (`boolean`), `concluidoEm` (`Instant` opcional), `criadoEm` e `atualizadoEm`.
- O documento em `cronogramas` mantém `versaoAtiva` (`long`) e `ativo` (`boolean`). A unicidade de no máximo um cronograma ativo por trilha é garantida no MongoDB por índice único parcial em `{ usuarioId: 1, trilhaId: 1 }` com `partialFilterExpression: { ativo: true }`.
- Para coordenar criações, regenerações, ativações, exclusões de cronograma/disciplina/trilha, usar `geracaoCronogramaLeaseToken` (`String`) e `geracaoCronogramaEmAndamentoAte` (`Instant`) no documento da trilha. Lease tem token de fencing UUID, expira em 2 minutos e renova a cada 30 segundos. Aquisição/liberação filtra token; lease expirado só pode ser retomado atomicamente; operação cujo token perdeu não publica. Operação concorrente retorna `409 SCHEDULE_GENERATION_IN_PROGRESS`.
- Publicação é staging com versão, não transação distribuída: bloco de staging não é lido até publicação completa. Persistir no cronograma `versaoAtiva`, `versaoPreparada`, `estadoPublicacao` (`ATIVO`, `PREPARANDO`, `RECUPERACAO_NECESSARIA`) e configuração ativa/preparada. Persistir em `trilhas` ponteiro `cronogramaAtivoId` + `versaoAtivaCronograma`, atualizado atomicamente; índices únicos parciais em `cronogramas.ativo` garantem **no máximo** um ativo.
- Inserir blocos em lotes idempotentes, com índice único `{ usuarioId, trilhaId, cronogramaId, versaoGeracao, data, ordem }`; verificar contagem esperada antes de publicar. Manter geração anterior até staging completo. Atualizar ponteiro da trilha em uma única operação atômica; flags são reconciliadas em seguida sob lease. Leitores veem geração anterior ou nova completa. Se falha pós-publicação, preservar versões e reconciliar idempotentemente no startup/job antes de limpar antiga.
- APIs de Estudos são a fonte única de blocos: endpoint Hoje (`GET /api/blocos/hoje`) retorna somente pendentes de todos os cronogramas ativos na data `hojeLocal`; endpoint paginado (`GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`) serve a lista virtual; endpoints idempotentes concluem/reabrem bloco individual (`PATCH /api/blocos/{blocoId}/conclusao`) e concluem atrasados sem sessão (`POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`). Conclusão seta `concluidoEm=agora`; reabertura limpa `concluidoEm`; repetição sem mudança não altera timestamps.
- Lista virtual de uma trilha pode percorrer todo o período gerado com carregamento incremental, ordenada por data. Exibir estado atrasado/hoje/futuro; blocos concluídos não aparecem nessa lista.

### 6.4 Falhas ao concluir bloco

- Concluir bloco abre o modal global. O bloco só muda para concluído quando o usuário salva a sessão ou escolhe explicitamente **Concluir sem salvar**.
- Cancelar/voltar/fechar retorna ao contexto anterior e deixa o bloco pendente.
- Ao salvar: gravar sessão idempotentemente e então concluir bloco. Sessão e bloco não precisam de vínculo persistente.
- Se salvar sessão funcionar e concluir bloco falhar, informar “sessão salva, bloco ainda pendente” e permitir retry apenas da conclusão, sem criar outra sessão.
- Payload/chave de idempotência permanecem estáveis durante a abertura do modal. Se aba/reload interromper a segunda etapa, sessão já salva não é revertida nem reenviada automaticamente; ao voltar, bloco continua pendente e pode ser concluído sem sessão ou iniciar novo fluxo intencional.
- Desconcluir bloco não exclui sessões. Podem existir múltiplas sessões informativas no mesmo contexto/bloco.
- Falhas de conexão não enfileiram operações no servidor no MVP; preservar formulário/dados, mostrar erro e permitir retry.

### 7.4 Blocos de cronograma em Tarefas

- Não persistir bloco como tarefa. Uma lista virtual por trilha renderiza todos os blocos pendentes de todo o período gerado, em ordem de data, com indicação de atrasado/hoje/futuro.
- Filtros gerais **Futuro**, **Todas** e **Concluídas** não incluem blocos. A lista da trilha e Hoje são as exceções definidas.
- Hoje inclui somente blocos do dia atual, não blocos futuros ou atrasados.
- Blocos não podem ser criados/editados como tarefas. Concluir/reabrir usa endpoint de Estudos.
- Concluir um bloco abre o modal de sessão compartilhado.
- A ação **Concluir todas as atrasadas sem salvar** mostra confirmação com quantidade; implementar item a item/idempotentemente, reportando sucessos/falhas parciais e permitindo retry sem sessão. Não exigir transação Mongo multi-documento.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

---

## Plano original da FASE 4 (referência de escopo)

## FASE 4 — Domínio Estudos (Parte 3): Cronogramas, Coordenação de Concorrência/Modalidade, Geradores e APIs de Blocos

### 4.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas. F4 pode ocorrer em paralelo com F3 em branch isolado; F5 aguarda ambas.
- **Objetivo da fase:** Substituir o modelo legado de ciclos (`PlanejamentoCicloRepository`) pelas coleções normalizadas `cronogramas` e `blocos_cronograma`, implementar a coordenação de concorrência e mudança de modalidade, os 3 algoritmos geradores (Sequencial, Ponderado e Manual), a publicação versionada (`versaoGeracao`/`versaoAtiva`), a disponibilidade semanal do usuário, todos os endpoints de blocos de Estudos e a view `PlanejamentoView.tsx`.
- **O que NÃO fazer nesta fase:**
  - Não alterar o módulo Tarefas nem o módulo Pomodoro (eles consumirão os endpoints desta fase na FASE 7).

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

---

## Relato da implementação alegada — FASE 4 (hipótese a verificar)

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

