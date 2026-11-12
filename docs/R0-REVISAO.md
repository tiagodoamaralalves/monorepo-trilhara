# R0 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R0** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R0 — Baseline, inventário real e gate anti-legado (pré-requisito; abrange F0–F11)

**1. Referência e Diagnóstico**
- Relatório não confiável (META-01..05): F4 duplicada, pacotes duplicados, contagens de teste regressivas, índices divergentes.
- Não existe gate automatizado contra padrões proibidos nem matriz de rastreabilidade.

**2. Especificação Técnica, Segurança e UX**
- Criar `scripts/verificar-legado.sh` (bash + `rg`), que falha (`exit 1`) ao encontrar qualquer padrão do Anexo C fora das allowlists declaradas no próprio script.
- Criar `docs/RASTREABILIDADE-REESTRUTURACAO.md` com uma tabela: `Critério de aceite | Teste(s) automatizado(s) | Status`.
- Criar `docs/REVISAO-IMPLEMENTADA.md` (relatório desta revisão).
- Configurar ferramentas (D-44): `knip`, flags do `tsconfig`, regras ESLint, ArchUnit (`com.tngtech.archunit:archunit-junit5`, escopo test), JaCoCo com thresholds em modo *report-only* nesta fase (passa a *enforce* em R13).

**3. Checklist Atômico de Execução**
1. Executar `./mvnw test` e `npm test -- --run`; registrar a contagem real de testes por suíte como baseline.
2. Listar todos os pacotes Java sob `br.com.trilhara` (`find backend/src/main/java -type d`) e registrar duplicidades (DC-01, DC-02).
3. Listar as rotas JAX-RS registradas (teste utilitário que lê `@Path` de todas as classes `*Resource` e imprime método + caminho) e salvar em `docs/REVISAO-IMPLEMENTADA.md` (insumo do Anexo A).
4. Subir o MongoDB de dev/test vazio, iniciar o app e capturar `listIndexes()` de todas as coleções; comparar com a tabela canônica (D-07) e registrar as divergências.
5. Ler o código da F4 e determinar qual dos dois relatos é verdadeiro (campos do bloco, filtro do lote, coleção de operações); registrar.
6. Criar `scripts/verificar-legado.sh` com os padrões do Anexo C; rodá-lo e registrar todas as ocorrências (sem corrigir ainda).
7. Adicionar `knip.json`, flags do `tsconfig` e regras ESLint; registrar a contagem de achados (sem corrigir ainda; as correções ocorrem nas fases de domínio e em R13).
8. Adicionar a dependência ArchUnit e o esqueleto `TenantIsolationArchTest` com as regras marcadas como `@ArchIgnore` comentadas com a fase que as habilitará.
9. Criar a matriz de rastreabilidade preenchida com todos os critérios de aceite transversais reproduzidos nesta matriz e os testes existentes que (supostamente) os cobrem.

**4. Critérios de Aceite e Validação Automatizada**
- `scripts/verificar-legado.sh` existe, é executável e reporta achados (pode falhar nesta fase; o modo `--report` retorna 0).
- `docs/RASTREABILIDADE-REESTRUTURACAO.md` e `docs/REVISAO-IMPLEMENTADA.md` existem com baseline (contagens, rotas, índices reais, pacotes duplicados).
- Build e testes existentes continuam verdes (nenhuma correção funcional nesta fase).

---

---

## Decisões arquiteturais aplicáveis

**D-01 — Fonte de verdade da revisão**
- *Dúvida/Lacuna:* O relatório marca 100% como "Atendido", mas se contradiz (META-01..05). Em que confiar?
- *Deliberação e Referência:* Em engenharia de releases (Google SRE, "trust but verify"), um relatório de execução não substitui evidência executável. O código e os testes automatizados são a verdade; os documentos normativos são o alvo.
- *Decisão Cravada:* Antes de corrigir, cada fase executa a **verificação** dos achados. Nenhum item fica "Atendido" sem um teste automatizado nomeado na matriz de rastreabilidade (`docs/RASTREABILIDADE-REESTRUTURACAO.md`, criada em R0). O relatório anterior de implementação permanece apenas como registro histórico; a revisão deve ser registrada em `docs/REVISAO-IMPLEMENTADA.md`.

**D-02 — Nomenclatura canônica de campos**
- *Dúvida/Lacuna:* Coexistem `data/ordem/concluido` × `dataEstudo/ordemDia/status`, `instanteEntregaUtc` × `horarioDisparoUtc`, `expiraEm` × `expiresAt` × `expiracao`, `dataFim` × `dataTermino`.
- *Deliberação e Referência:* *Ubiquitous language* (DDD). Ter um único nome por conceito evita a criação de índices para campos inexistentes (META-04).
- *Decisão Cravada:* Valem **exclusivamente** os nomes  e . Em especial: `blocos_cronograma.{data, ordem, concluido, concluidoEm}`; `lembretes_push.instanteEntregaUtc`; `refresh_tokens.expiraEm`, `tokens_*.expiraEm`; `series_recorrentes.dataFim`; `sessoes.dataSessao`. O lixo/soft-delete usa `deletedAt`/`expiresAt` (apenas lixeiras). Qualquer outro nome é legado (DC-08).

---

## Contexto normativo de domínio pertinente

## 1. Objetivo e princípios

Reestruturar o modelo de dados e a integração backend/banco sem confundir os limites dos quatro produtos, mantendo somente integrações explicitamente úteis.

Princípios:

1. **Cada domínio possui sua fonte de verdade.** Tarefas manuais pertencem a Tarefas; cronogramas e blocos pertencem a Estudos; sessões pertencem ao histórico de Estudos; cadernos e páginas pertencem a Notas.
2. **Não persistir cópias de leitura como entidades de domínio.** Blocos exibidos em Tarefas/Pomodoro são consultados de Estudos, não copiados para a coleção de tarefas.
3. **Uma métrica não deve ser armazenada em duplicidade sem necessidade.** Segundos de estudo e resultados de questões são calculados a partir das sessões. O progresso de tópicos é calculado a partir das conclusões dos tópicos.
4. **Atualizações pequenas e delimitadas.** Progresso, conclusão de bloco e alterações de sessão não devem substituir documentos grandes.
5. **Dados pessoais têm proprietário explícito.** Operações de leitura, escrita e exclusão verificam o usuário autenticado no backend.
6. **Erros de integração são visíveis.** Falha de uma API não deve virar uma lista vazia com aparência de sucesso.
7. **Destruição é explícita.** Exclusões em cascata e remoção de histórico mostram o impacto antes de confirmar.

### 1.1 Convenções normativas de API

- Toda rota de recurso privado exige autenticação. O backend escopa cada consulta e mutação ao `usuarioId` autenticado; ID válido inexistente ou pertencente a outro usuário resulta em `404`, sem revelar existência. Falta de autenticação é `401`; falta de privilégio para operação administrativa é `403`.
- ID malformado (não é hexadecimal de 24 caracteres) resulta em `400 INVALID_IDENTIFIER`. Restrições de campo/payload inválidas resultam em `400 VALIDATION_ERROR`; conflito de estado/regra resulta em `409` com `code` estável.
- Erros usam `application/problem+json` conforme RFC 9457: `type`, `title`, `status`, `detail`, `instance`; extensões Trilhara `code` e `errors` são documentadas e usadas por cliente apenas como campos de máquina. Mensagem `detail` pode ser localizada e não deve ser usada como identificador.
- Listagens usam `pagina` 1-based, `tamanho` dentro dos limites do endpoint e ordenação estável que inclui `_id` como desempate. Resposta comum `{ itens, pagina, tamanho, totalItens, totalPaginas }`; `tamanho` inválido é `400 INVALID_PAGINATION`. Paginação por offset não promete snapshot enquanto dados mudam.
- Horários de API são ISO 8601/RFC 3339; datas de calendário são `yyyy-MM-dd`; propriedade ausente e `null` têm semântica definida por cada DTO. Não enviar documentos Mongo diretamente como contrato público.

### 1.1 Convenções normativas de API

- Toda rota de recurso privado exige autenticação. O backend escopa cada consulta e mutação ao `usuarioId` autenticado; ID válido inexistente ou pertencente a outro usuário resulta em `404`, sem revelar existência. Falta de autenticação é `401`; falta de privilégio para operação administrativa é `403`.
- ID malformado (não é hexadecimal de 24 caracteres) resulta em `400 INVALID_IDENTIFIER`. Restrições de campo/payload inválidas resultam em `400 VALIDATION_ERROR`; conflito de estado/regra resulta em `409` com `code` estável.
- Erros usam `application/problem+json` conforme RFC 9457: `type`, `title`, `status`, `detail`, `instance`; extensões Trilhara `code` e `errors` são documentadas e usadas por cliente apenas como campos de máquina. Mensagem `detail` pode ser localizada e não deve ser usada como identificador.
- Listagens usam `pagina` 1-based, `tamanho` dentro dos limites do endpoint e ordenação estável que inclui `_id` como desempate. Resposta comum `{ itens, pagina, tamanho, totalItens, totalPaginas }`; `tamanho` inválido é `400 INVALID_PAGINATION`. Paginação por offset não promete snapshot enquanto dados mudam.
- Horários de API são ISO 8601/RFC 3339; datas de calendário são `yyyy-MM-dd`; propriedade ausente e `null` têm semântica definida por cada DTO. Não enviar documentos Mongo diretamente como contrato público.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

## 16. Critérios de aceite transversais

- Usuário A nunca acessa, altera ou exclui dados do usuário B por ID conhecido.
- Datas de calendário não mudam ao converter entre navegador/backend; operações entre 21h00 e 23h59 no fuso `America/Sao_Paulo` usam a data local correta enviada via `X-Timezone`; instantes usam BSON Date.
- Trilha grande não exige transferir disciplinas/tópicos ao listar Hub.
- Marcar/desmarcar tópico afeta somente aquele progresso e seus agregados.
- Regenerar cronograma substitui integralmente blocos/conclusões via `versaoGeracao`/`versaoAtiva`, mantendo sessões e progresso; requisições simultâneas nunca deixam dois cronogramas ativos na mesma trilha.
- Tarefas/Pomodoro mostram o mesmo conjunto de tarefas manuais Hoje e os blocos do dia; nenhum bloco é gravado como tarefa.
- Concluir manual nunca cria sessão; concluir bloco abre o modal global; cancelar não conclui.
- Retry após timeout não duplica sessão nem conclusão.
- Uma sessão salva com `95` segundos persiste `duracaoSegundos = 95` e soma exatamente `95` segundos nas agregações; estatísticas por sessão conferem com somas do histórico; editar a sessão mais recente `(dataSessao DESC, criadoEm DESC, _id DESC)` ou excluir uma sessão individual recalcula naturalmente.
- Remover disciplina preserva histórico por padrão (sem prompt destrutivo na exclusão da disciplina) e permite apagar sessões/métricas da disciplina removida de forma explícita no Dashboard/Histórico.
- Alterar a `dataVencimento` de uma ocorrência recorrente mantém `dataOriginalOcorrencia` e jamais recria a ocorrência na data original ao expandir a janela de 90 dias.
- Autosave/busca/lixeira de Notas não transferem Markdown completo em listagens e preservam rascunho local (`trilhara:u:{usuarioId}:nota_draft:{notaId}`) em falha de rede.
- Tarefas/notas em lixeira podem ser restauradas no prazo e no contexto originais; após prazo, são purgadas.
- Logout ou troca de conta sincroniza na mesma origem via `storage`/`BroadcastChannel`; entre subdomínios a validação é server-side ao focar e o cookie marcador de produção é apenas um sinal opaco. Timers são sincronizados apenas na mesma origem/dispositivo; não há promessa de sincronização entre dispositivos.
- Falha de serviço aparece como erro recuperável (`SectionErrorCard` / `SyncStatusBadge`); nunca é apresentada silenciosamente como “sem dados”.
- Requisitos de acessibilidade WCAG 2.2 AA (*focus trap*, `aria-live`, alvos mínimos de toque/clique e `scroll-padding-bottom` para barras fixas) são atendidos nos quatro módulos.
- Resultados de performance são medidos em ambientes representativos; estimativas de redução de payload/latência não são aceites como garantias sem benchmark.
- Em todas as listas paginadas, ordenação inclui chave estável e filtros de tenant; erro de uma fonte na Visão Hoje não remove os dados da fonte disponível.
- Ao alternar cronograma ativo dentro da mesma trilha, leitor vê versão anterior ou versão nova completa; não vê staging incompleto nem dois ativos. Um período de recuperação sem cronograma ativo só é permitido em falha operacional e precisa ser sinalizado como seção indisponível/retry.
- Geração de cronograma é testada com falha injetada em cada etapa: nenhum leitor vê staging incompleto; estado anterior ou novo é recuperável, e lease expirado/obsoleto não permite publicação concorrente.
- Materialização recorrente é idempotente sob requisições simultâneas e os casos diários/semanais/mensais são testados em mudanças de ano, semanas ISO e meses curtos.
- Exclusão e restauração de lista/caderno/seção respeitam lotes de exclusão, itens previamente excluídos isoladamente e descendentes já purgados.
- Scheduler push não processa o mesmo item em paralelo; expirados não são enviados; falhas transitórias respeitam janela e backoff, reconhecendo que o provedor não garante exactly-once.
- Frontend instala runner automatizado Vitest + React Testing Library + jsdom em F0; cada critério de UI deste documento/plano tem teste de componente ou serviço executável por `npm test`, além de `npm run build`.
- Conta excluída retorna 202 depois de aceitar operação autenticada; worker conclui exclusão das 18 coleções sem deixar órfãos, mesmo com falhas injetadas em qualquer etapa.

---

## Contexto das fases originais

R0 é uma revisão de baseline transversal às F0–F11, sem fase original única. Use as fases  de implementação como mapa de domínios; não tente corrigir todos eles nesta revisão.

---

## Relatos duplicados da FASE 4 — contexto para reconciliação

O relatório tem dois blocos intitulados FASE 4. Ambos são autodeclarados, não evidência. Compare as diferenças com o código e registre qual contrato está efetivamente implementado.

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


---

## Anexos pertinentes  de revisão

## Anexo C — Padrões proibidos verificados por `scripts/verificar-legado.sh`

| Padrão (`rg`) | Escopo | Motivo |
|---|---|---|
| `writeValueAsString\|Document\.parse\|\.toJson\(\)` | `backend/src/main` | Serialização via JSON proibida (regra normativa) |
| `LocalDate\.now\(\)` | `backend/src/main` | Usar `TimezoneContext.hojeLocal()` |
| `ZoneId\.of\("America/Sao_Paulo"\)` | `backend/src/main` exceto `TimezoneContext.java` | Fuso implícito |
| `deleteMany\(new Document\(\)\)` | `backend/src/main` | Destrutivo |
| `USUARIO_LOCAL_PADRAO\|"local"` (em `UsuarioAtual`/auth) | `backend/src/main` | Fallback inseguro |
| `AjusteQuestoes\|TipoSessao\|TipoRegistroSessao\|AJUSTE_MANUAL\|NovaSessao\|PlanejamentoCiclo\|DisciplinaProgramada\|CicloAtivoResponse\|OrigemTarefa\|OperacaoConcluirAtrasados\|removerTarefasDeCicloLegadas` | `backend`, `frontend` | Legado |
| `dataEstudo\|ordemDia\|duracaoRealSegundos\|horarioDisparoUtc\|dataTermino\|usaPadraoGlobalLembrete\|toObjectIdOrNotFound\|PAYLOAD_TOO_LARGE\|testFailOnCollectionHook` | `backend`, `frontend` | Nomes não canônicos (D-02) |
| `package br\.com\.trilhara\.(estudos\|tarefas\|notas)\b` | `backend/src` | Pacotes legados |
| `trilhara_auth_token\|trilhara_user\|trilhara:u:anon\|\|\| 'anon'` | `frontend/src` | Armazenamento inseguro (D-09/D-21) |
| `trilhara_study_timer\|trilhara_pomodoro_timer\|trilhara_agenda_hoje` | `frontend/src` | Canais antigos (D-21) |
| `dangerouslySetInnerHTML\|window\.location\.reload\(` | `frontend/src` | XSS / perda de estado |
| `console\.(log\|debug\|info)` | `frontend/src` (exceto testes) | Higiene |
| `adaptarTrilhaResumo\|adaptarTrilhaDetalhe` | `frontend/src` | Adaptadores legados |
| `\bciclos?\b` (case-insensitive) | `frontend/src/modules/{estudos,tarefas}`, `backend/src/main/java/br/com/trilhara/{trilha,cronograma,tarefa}` | Terminologia (regra normativa) |
| `describe\(['"]fase\|class Fase[0-9]` e arquivos `fase*.test.*` | testes | Nomenclatura por fase (D-43) |

---

## Anexo D — Resumo de rastreabilidade Achado → Decisão → Fase

| Achados | Decisões | Fase |
|---|---|---|
| META-01..05 | D-01, D-02 | R0 |
| RI-02, SEC-05, SEC-07, SEC-10, UX-01..05, UX-12, DC-11 | D-03, D-04, D-05, D-37, D-42 | R1 |
| SEC-01, SEC-06, SEC-08, SEC-13, UX-06, UX-08, DC-02, DC-14, DC-17 | D-06, D-07, D-08, D-09, D-10, D-12, D-41 | R2 |
| RI-01..05, SEC-09, DC-03..05 | D-06, D-11, D-38, D-39 | R3 |
| RI-06..09, DC-06 | D-13, D-14, D-15 | R4 |
| RI-10..15, RI-27, DC-07, DC-08, DC-15 | D-16, D-17, D-18, D-19, D-20 | R5 |
| RI-16, RI-17, UX-07, UX-11 | D-21, D-22 | R6 |
| RI-18..25, RI-38, SEC-16, DC-09 | D-23..D-28 | R7 |
| RI-26, RI-27, SEC-02, DC-10, DC-11 | D-23, D-46, D-48 | R8 |
| RI-28..33, SEC-03, SEC-04, DC-12, DC-13 | D-29..D-34 | R9 |
| RI-34..36, SEC-11, SEC-15 | D-08, D-35 | R10 |
| RI-37, SEC-12 | D-26, D-36 | R11 |
| RI-39, RI-40, UX-09, UX-10 | D-37, D-40, D-43 | R12 |
| DC-01..18, SEC-04, SEC-07, SEC-08, SEC-14 | D-44, D-45, D-47, D-49 | R13 |

---

