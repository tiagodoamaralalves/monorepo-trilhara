# R3 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R3** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

### Hierarquia normativa incorporada neste guia

Os requisitos de domínio e produto aplicáveis a esta revisão estão reproduzidos abaixo e são a referência normativa para comportamento. As decisões `D-xx` explicitamente listadas prevalecem sobre especificações históricas quando determinam uma regra diferente; mudanças que alterem comportamento devem atualizar os documentos normativos do produto no mesmo change. O contexto de execução original da fase aparece apenas para delimitar o escopo desta revisão. O relato da implementação é uma declaração a verificar no código e nos testes, nunca prova de conformidade. Qualquer trecho histórico incluído está marcado como não normativo. O agente deve trabalhar com o contexto deste guia e com o estado atual do repositório, sem precisar consultar os documentos de origem.

### Regras de execução aplicáveis

- Dependências desta revisão: R0→R1→R2; R6 exige R4+R5; R8 exige R5+R6+R7; R9 exige R3; R10 exige R3–R9; R11 exige R7+R10; R12 e R13 exigem todas as revisões anteriores. Sequência linear segura: R0→R1→R2→R3→R4→R5→R6→R7→R8→R9→R10→R11→R12→R13.
- Faça inventário de imports, usos e call sites antes de mudanças transversais. Não introduza compatibilidade legada nem mocks/callbacks temporários destinados a fases futuras.
- Preserve os limites de escopo desta revisão. Mudanças transversais indispensáveis para compilar/testar devem ser justificadas no resumo.
- A premissa é banco de desenvolvimento/teste vazio no novo esquema; não preservar compatibilidade com modelos, rotas ou dados legados.
- Contratos comuns: rotas privadas exigem autenticação (401); falta de papel administrativo é 403; recursos inexistentes ou de outro usuário retornam 404 sem revelar existência. IDs HTTP são hexadecimais de 24 caracteres: malformado é 400 `INVALID_IDENTIFIER`. Erros usam `application/problem+json`, RFC 9457 (`type`, `title`, `status`, `detail`, `instance`) e códigos estáveis `code`/`errors`; falhas inesperadas não expõem detalhes internos ou dados pessoais. Paginação usa `pagina` 1-based, tamanho dentro do limite do endpoint (máximo 100), erro 400 `INVALID_PAGINATION`, resposta `{ itens, pagina, tamanho, totalItens, totalPaginas }` e ordenação estável com `_id` como desempate. Datas de calendário são `yyyy-MM-dd`; instantes são RFC 3339 na API e `Instant`/BSON Date no banco; o fuso vem de `X-Timezone`, com fallback `America/Sao_Paulo`, nunca do relógio/fuso implícito do servidor. Escritas idempotentes definem chave, escopo, hash funcional e política de retry; mesma chave com payload funcional diferente retorna 409 `IDEMPOTENCY_KEY_CONFLICT`. Operações compostas sem transação multi-documento documentam estados intermediários, recuperação, retry e efeitos parciais; não declarar atomicidade inexistente.
- Ao concluir, registre achados confirmados/descartados, arquivos alterados/removidos, testes completos por nome e resultado do gate em `docs/REVISAO-IMPLEMENTADA.md`; atualize `docs/RASTREABILIDADE-REESTRUTURACAO.md`.

**Gate comum de saída:** `backend/`: `./mvnw -q verify`; `frontend/`: `npm run typecheck && npm run lint -- --max-warnings 0 && npm test -- --run && npm run build && npx knip`; raiz: `bash scripts/verificar-legado.sh`. Se uma etapa ainda não estiver disponível por dependência anterior, registre claramente o bloqueio e não declare a revisão concluída.


---

## Escopo desta revisão

### R3 — Revisão da F2: Trilhas, disciplinas, tópicos e progresso

**1. Referência e Diagnóstico**
- Rota legada de progresso mantida (RI-01/DC-03); limites com status errado (RI-02); acoplamento Disciplina→caderno (RI-03/DC-04); `EditalVerticalizadoView` possivelmente não migrada (RI-04); lease ausente em exclusões de trilha/disciplina (RI-05); adaptadores e tipo `Trilha` de dupla forma (DC-05); validação de referência cruzada não comprovada (SEC-09).

**2. Especificação Técnica, Segurança e UX**
- Contratos (sem alteração em relação ao plano, exceto remoções):
  - `GET /api/trilhas` → `TrilhaResumoDTO[]` (≤ 50).
  - `POST /api/trilhas` → 201 + `Location`; `editalId` **xor** `disciplinasIniciais` (ambos → 400 `VALIDATION_ERROR`); `editalId` deve existir e estar publicado (inexistente → 400 `VALIDATION_ERROR` em `errors.editalId`).
  - `GET/PUT/DELETE /api/trilhas/{trilhaId}`; endpoints granulares de disciplinas, `reordenar` e progresso conforme os contratos de trilha, disciplina e progresso descritos neste guia.
  - `PUT .../topicos/{topicoId}/progresso`: o servidor resolve a disciplina dona do tópico **dentro da trilha do path e do usuário**; tópico inexistente → 404.
- Remover `cadernoId`/vínculo de caderno de `Disciplina`, DTOs e UI. O link "Abrir no Notas" na UI de Estudos usa `GET /api/cadernos?origemTrilhaId={id}` (novo filtro opcional em Notas, implementado em R9); antes de R9, o link não é exibido.
- `DELETE disciplina` e `DELETE trilha` adquirem o lease da trilha (`409 SCHEDULE_GENERATION_IN_PROGRESS` se ocupado); a sequência e os efeitos parciais ficam documentados no Javadoc do service.
- Excluir trilha: `ConfirmDialog` com digitação do nome (D-38) informando a contagem de cronogramas e sessões (endpoint `GET /api/trilhas/{id}/impacto-exclusao` → `{ disciplinas, cronogramas, sessoes, cadernosVinculados }`).
- Frontend: tipos `TrilhaResumoDTO`/`TrilhaDetalheDTO` usados diretamente; o progresso é `Set<string>` derivado de `topicosConcluidosIds`; marcar tópico é otimista (D-39). Estados por view: Hub (skeleton `card` ×3, `EmptyState` "Crie sua primeira trilha" com ação primária), Disciplinas (skeleton `row`), erro → `SectionErrorCard`.
- Tenancy: habilitar a regra ArchUnit de assinatura `usuarioId` para o pacote `trilha`.

**3. Checklist Atômico de Execução**
1. Verificar no código e remover `PATCH /api/trilhas/{id}/progresso/{topicoId}` e o respectivo método de service/DTO.
2. Trocar as exceções de limite para `ResourceLimitExceededException`.
3. Remover o campo de caderno de `Disciplina`, `DisciplinaDTO`, `AtualizarDisciplinaRequest`, o mapeamento BSON e a UI ("atualização de caderno" em `DisciplinasView`).
4. Implementar a validação de `editalId` xor `disciplinasIniciais`.
5. Envolver as exclusões de trilha/disciplina no lease (reutilizar o serviço de lease de R5; se R5 ainda não foi feito, extrair agora `LeaseTrilhaService` para `shared` ou `trilha`).
6. Implementar `GET /api/trilhas/{id}/impacto-exclusao`.
7. Frontend: apagar `adaptarTrilhaResumo`, `adaptarTrilhaDetalhe`, o mapa de progresso legado e a interface `Trilha` dupla; migrar `HubView`, `DisciplinasView`, `EditalVerticalizadoView` e `EstudosApp` para os DTOs.
8. Aplicar `useRecurso` + `Skeleton`/`EmptyState`/`SectionErrorCard` nas três views.
9. Excluir trilha com confirmação por nome.
10. Habilitar a regra ArchUnit do pacote `trilha`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend (`TrilhaNormalizadaIntegrationTest`, ampliado): `deveRetornar409ResourceLimitAoExceder50Trilhas` / `100Disciplinas` / `500Topicos`; `deveRejeitarEditalEDisciplinasIniciaisJuntos`; `deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha`; `deveRetornar409SeLeaseOcupadoAoExcluirDisciplina`; `naoDeveExistirRotaLegadaDeProgresso` (PATCH → 404/405); `impactoExclusaoTrilhaContaCorretamente`; isolamento A×B em todas as rotas de trilhas, disciplinas e progresso.
- Frontend: `hub.test.tsx` (skeleton → cards; vazio → EmptyState; erro → SectionErrorCard + retry); `topicoOtimista.test.tsx` (falha → rollback + toast); `excluirTrilha.test.tsx` (botão destrutivo habilitado só com o nome exato).
- `rg "adaptarTrilha|progresso/\{topicoId\}|cadernoId" frontend/src/modules/estudos backend/src/main/java/br/com/trilhara/trilha` → zero (exceto em Notas).
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-06 — Isolamento estrutural por tenant**
- *Dúvida/Lacuna:* O isolamento depende de disciplina manual em cada consulta.
- *Deliberação e Referência:* *Defense in depth*; Linear e Notion aplicam o escopo de workspace na camada de acesso a dados, e não no controlador. ArchUnit permite verificar regras arquiteturais como teste.
- *Decisão Cravada:* (a) Toda classe `*Repository` de coleção privada expõe somente métodos cuja assinatura começa com `ObjectId usuarioId` e cujos filtros incluem `Filters.eq("usuarioId", usuarioId)` (ou `_id` em `usuarios`). (b) Acesso multi-tenant (purgas, scheduler de push, exclusão de conta, reconciliador de cronogramas) só existe em classes anotadas com `@SystemScope`, num pacote `..job..`, sem exposição HTTP. (c) Teste ArchUnit `TenantIsolationArchTest` garante (a) e (b) e que nenhum `*Resource` injeta `MongoClient`/`MongoCollection` diretamente. (d) Referências cruzadas recebidas em payload (`trilhaId`, `disciplinaId`, `listaId`, `cadernoId`, `secaoId`, `disciplinasParticipantesIds`, `trilhaId` de importação) são validadas por consulta com `usuarioId` **e** pai correto (ex.: disciplina pertence à trilha do path). Falha → `404 NOT_FOUND` para path e `400 VALIDATION_ERROR` para referência no body.

**D-11 — Rotas de retrocompatibilidade**
- *Dúvida/Lacuna:* A F2 manteve um PATCH legado "para retrocompatibilidade".
- *Deliberação e Referência:* O plano e os requisitos regra normativa estabelecem: produto não lançado, sem compatibilidade.
- *Decisão Cravada:* Remover toda rota, DTO ou adaptador mantido "por compatibilidade". O teste de contrato `RotasPublicasContratoTest` compara a lista de rotas JAX-RS registradas com a allowlist do Anexo A e falha com rota extra.

**D-38 — Política de ações destrutivas**
- *Deliberação e Referência:* O Gmail/Todoist usam "Desfazer" para ações reversíveis (menos atrito); confirmação modal fica reservada para o irreversível ou para cascatas (NN/g: *confirmation dialogs should be rare*).
- *Decisão Cravada:* (1) **Item único para a lixeira** (tarefa, nota): sem dialog; toast "Movida para a lixeira" + **Desfazer** (chama a restauração). (2) **Cascata para a lixeira** (lista, caderno, seção, série inteira): `ConfirmDialog` com contagem (D-33). (3) **Irreversível** (excluir sessão, apagar histórico de disciplina removida, esvaziar lixeira, excluir cronograma, regenerar cronograma, excluir disciplina, excluir trilha, excluir conta): `ConfirmDialog` `danger`, com o impacto descrito em números quando disponível. Excluir trilha exige digitar o nome da trilha (padrão GitHub para recursos grandes).

**D-39 — Atualizações otimistas**
- *Decisão Cravada:* Otimista com rollback + toast de erro: marcar/desmarcar tópico, subtarefa, concluir/reabrir tarefa manual, pular ocorrência, reordenar disciplinas. **Não otimista**: conclusão de bloco (passa pelo modal), sessão, cronograma, exclusões em cascata, importação. Toda mutação emite `AGENDA_INVALIDADA` quando afeta Hoje.

---

## Contexto normativo de domínio pertinente

## 4. Estudos: trilhas, disciplinas e tópicos

### 4.1 Estrutura e consultas

- Separar metadados da trilha, disciplinas/tópicos e progresso; evitar um documento único que cresça com dezenas de milhares de tópicos e chaves de progresso.
- Recomenda-se uma coleção de disciplinas por trilha, com seus tópicos limitados por documento (o limite funcional atual é de até 500 tópicos por disciplina), e uma coleção de progresso dos tópicos concluídos.
- Uma trilha importada de edital é um snapshot. Uma nova versão do edital não altera silenciosamente a trilha do usuário.
- A listagem do Hub retorna resumo leve (metadados e contadores); o grafo de disciplinas/tópicos é carregado ao abrir uma trilha.
- Criar índices de proprietário e trilha nas coleções filhas; usar chave única para impedir duplicidade de progresso por usuário/trilha/tópico.
- Limites estruturais validados no backend: até 50 trilhas por usuário, até 100 disciplinas por trilha e até 500 tópicos por disciplina.
- A resposta de resumo do Hub (`GET /api/trilhas`) projeta metadados da trilha, `totalDisciplinas`, `totalTopicos`, `topicosConcluidos`, `percentualProgresso` e `cronogramaAtivoResumo: null | { cronogramaAtivoId, modalidade, dataInicio, dataFim }`, sem transferir arrays de tópicos nem ocorrências de blocos.
- Operações em disciplinas e tópicos usam endpoints granulares sob `/api/trilhas/{trilhaId}/disciplinas` (criação, edição de metadados/peso/tópicos, reordenação e exclusão individual), evitando enviar a trilha inteira a cada alteração.

### 4.2 Progresso de tópicos e métricas curriculares

- Persistir um registro de progresso somente quando o tópico estiver concluído.
- Marcar concluído cria/atualiza o registro e `concluidoEm`; desmarcar remove o registro.
- Renomear tópico ou disciplina mantém os IDs e o progresso.
- Excluir tópico remove seu progresso. Sessões históricas não são apagadas por excluir tópico.
- Progresso de disciplina = tópicos concluídos / total de tópicos da disciplina.
- Progresso geral da trilha/edital = total de tópicos concluídos / total de tópicos (ponderação natural pelo número de tópicos, não média simples dos percentuais das disciplinas).

### 5.4 Persistência e APIs

- Não guardar milhares de blocos em um array ilimitado dentro do documento do cronograma. Persistir cada ocorrência como documento separado, referenciando usuário, trilha e cronograma.
- Campos obrigatórios da ocorrência em `blocos_cronograma`: `_id` (`ObjectId`), `usuarioId`, `trilhaId`, `cronogramaId`, `versaoGeracao` (`long`), `data` (`yyyy-MM-dd`), `ordem` (`1..24`), `disciplinaId`, `disciplinaNomeSnapshot`, `disciplinaCorSnapshot`, `duracaoMinutos` (`30..240`), `concluido` (`boolean`), `concluidoEm` (`Instant` opcional), `criadoEm` e `atualizadoEm`.
- O documento em `cronogramas` mantém `versaoAtiva` (`long`) e `ativo` (`boolean`). A unicidade de no máximo um cronograma ativo por trilha é garantida no MongoDB por índice único parcial em `{ usuarioId: 1, trilhaId: 1 }` com `partialFilterExpression: { ativo: true }`.
- Para coordenar criações, regenerações, ativações, exclusões de cronograma/disciplina/trilha, usar `geracaoCronogramaLeaseToken` (`String`) e `geracaoCronogramaEmAndamentoAte` (`Instant`) no documento da trilha. Lease tem token de fencing UUID, expira em 2 minutos e renova a cada 30 segundos. Aquisição/liberação filtra token; lease expirado só pode ser retomado atomicamente; operação cujo token perdeu não publica. Operação concorrente retorna `409 SCHEDULE_GENERATION_IN_PROGRESS`.
- Publicação é staging com versão, não transação distribuída: bloco de staging não é lido até publicação completa. Persistir no cronograma `versaoAtiva`, `versaoPreparada`, `estadoPublicacao` (`ATIVO`, `PREPARANDO`, `RECUPERACAO_NECESSARIA`) e configuração ativa/preparada. Persistir em `trilhas` ponteiro `cronogramaAtivoId` + `versaoAtivaCronograma`, atualizado atomicamente; índices únicos parciais em `cronogramas.ativo` garantem **no máximo** um ativo.
- Inserir blocos em lotes idempotentes, com índice único `{ usuarioId, trilhaId, cronogramaId, versaoGeracao, data, ordem }`; verificar contagem esperada antes de publicar. Manter geração anterior até staging completo. Atualizar ponteiro da trilha em uma única operação atômica; flags são reconciliadas em seguida sob lease. Leitores veem geração anterior ou nova completa. Se falha pós-publicação, preservar versões e reconciliar idempotentemente no startup/job antes de limpar antiga.
- APIs de Estudos são a fonte única de blocos: endpoint Hoje (`GET /api/blocos/hoje`) retorna somente pendentes de todos os cronogramas ativos na data `hojeLocal`; endpoint paginado (`GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`) serve a lista virtual; endpoints idempotentes concluem/reabrem bloco individual (`PATCH /api/blocos/{blocoId}/conclusao`) e concluem atrasados sem sessão (`POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`). Conclusão seta `concluidoEm=agora`; reabertura limpa `concluidoEm`; repetição sem mudança não altera timestamps.
- Lista virtual de uma trilha pode percorrer todo o período gerado com carregamento incremental, ordenada por data. Exibir estado atrasado/hoje/futuro; blocos concluídos não aparecem nessa lista.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

---

## Plano original da FASE 2 (referência de escopo)

## FASE 2 — Domínio Estudos (Parte 1): Trilhas, Disciplinas, Tópicos e Progresso Normalizados

### 2.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0 e 1 concluídas.
- **Objetivo da fase:** Desmembrar o documento monolítico legado de `Trilha` em três coleções normalizadas (`trilhas`, `disciplinas` e `progresso_topicos`), implementar os endpoints de resumo leve para o Hub, detalhe sob demanda, CRUD granular de disciplinas/tópicos e marcação pontual de progresso de tópicos, e atualizar as views correspondentes em `frontend/src/modules/estudos`.
- **O que NÃO fazer nesta fase:**
  - Não alterar ainda `SessaoResource` / `SessaoService` (FASE 3) nem `PlanejamentoView` / cronogramas (FASE 4).

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

---

## Relato da implementação alegada — FASE 2 (hipótese a verificar)

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
   - Preservação da integridade histórica de sessões de estudo ao excluir disciplinas ou tópicos (regra normativa).
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

