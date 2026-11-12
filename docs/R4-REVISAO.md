# R4 — Guia de Revisão da Reestruturação Trilhara

---

## Introdução e contexto comum

Este guia orienta a revisão **R4** da reestruturação já executada do Trilhara. A análise documental que originou este guia foi feita sem inspeção do código-fonte; portanto, cada achado abaixo é uma hipótese textual que deve ser confirmada ou descartada no repositório antes de qualquer correção. Registre evidência concreta (arquivo, símbolo, teste ou comando) para todo achado descartado.

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

### R4 — Revisão da F3: Sessões, histórico e estatísticas

**1. Referência e Diagnóstico**
- `editavel` dependente de página/filtro (RI-06); testes obrigatórios ausentes (RI-07); sem purga de tombstone (RI-08); série temporal sem agrupamento semanal/mensal (RI-09); streak sem definição (D-14); possível gravação de sessão fora do serviço global (DC-06); regressão de contagem de testes (META-03).

**2. Especificação Técnica, Segurança e UX**
- `POST /api/trilhas/{trilhaId}/sessoes`: `Idempotency-Key` **obrigatória** (ausente → 400 `IDEMPOTENCY_KEY_REQUIRED`); `disciplinaId` deve pertencer à trilha do path e ao usuário (senão 400 `VALIDATION_ERROR`, `errors.disciplinaId`); `dataSessao > hojeLocal` → 400 `VALIDATION_ERROR` (`errors.dataSessao`). Respostas 201/200/409 conforme o plano. Concorrência da mesma chave: o `E11000` do índice único é capturado → reler e aplicar a regra de mesmo/diferente payload.
- `requestHash` = SHA-256 de JSON canônico (chaves ordenadas) de `{trilhaId, disciplinaId, dataSessao, duracaoSegundos, questoes, acertos}`.
- `GET /api/trilhas/{trilhaId}/sessoes` → `Pagina<SessaoDTO>` com `editavel` global (D-13); `dataInicio > dataFim` → 400 `VALIDATION_ERROR`.
- `GET /api/trilhas/{trilhaId}/estatisticas`: acrescentar `granularidade: 'DIA' | 'SEMANA' | 'MES'` na resposta. Regra: período ≤ 31 dias → DIA; ≤ 180 → SEMANA (semana ISO, chave = segunda-feira); > 180 ou `tudo` → MES (chave = `yyyy-MM-01`). Totais idênticos em qualquer granularidade. `streakDias` conforme D-14.
- `PurgaTombstonesSessaoJob` (D-15).
- Frontend `DashboardView`/Histórico:
  - Skeleton `chart` + `row`; `EmptyState` "Nenhuma sessão neste período" com ação "Registrar sessão manual".
  - Grupo "Disciplina removida" com `Badge` amarelo (token `--color-warning`, contraste AA) e ação **Apagar histórico desta disciplina** → `ConfirmDialog danger` com "N sessões · X h Y min" vindos de `porDisciplina`.
  - Editar sessão (somente `editavel`): `FormModal` com guarda de alterações; 409 `LATEST_SESSION_ONLY` → toast + recarregar o histórico.
  - Excluir sessão: `ConfirmDialog danger` ("Esta ação não pode ser desfeita.").
  - Toda gravação de sessão passa pelo `sessaoGlobalService` (R6); a edição/exclusão usa `estudosService` (somente PATCH/DELETE).

**3. Checklist Atômico de Execução**
1. Implementar o cálculo global de `editavel` (consulta dedicada `findFirst` ordenada, filtrando `deletedAt: null`).
2. Garantir que todas as consultas/agregações filtrem `deletedAt: null` (tombstones).
3. Tornar `Idempotency-Key` obrigatória e tratar a corrida de `E11000`.
4. Implementar granularidade e streak (D-14) na agregação.
5. Criar `PurgaTombstonesSessaoJob` (`@SystemScope`).
6. Verificar no código e remover qualquer `POST .../sessoes` fora de `shared/services/sessaoGlobalService.ts` (`rg "/sessoes" frontend/src --glob '!**/sessaoGlobalService.ts'` deve mostrar apenas GET/PATCH/DELETE).
7. Remover tipos com `minutos`/`erros`/`tipo` persistidos no frontend.
8. Atualizar `DashboardView` com os estados D-37 e as confirmações D-38.
9. Restaurar a cobertura dos testes legados excluídos, mapeando-os na matriz.
10. Habilitar ArchUnit para o pacote `sessao`.

**4. Critérios de Aceite e Validação Automatizada**
- Backend (`SessaoNormalizadaIntegrationTest`): `deveExigirIdempotencyKey` (400); `deveTratarConcorrenciaDaMesmaChave` (2 threads, mesmo payload → 1 documento, respostas 201+200); `retryAposEditarRetornaSessaoAtual`; `retryAposExcluirRetorna409ResultRemoved`; `editavelEhGlobalMesmoComFiltroDeDisciplina`; `editavelFalsoNaPagina2`; `tombstoneNaoApareceEmListagemNemEstatistica`; `periodoInvertidoRetorna400`; `estatisticaVaziaRetornaZerosEPercentual0`; `granularidadeSemanalPreservaTotais`; `streakConsideraOntemQuandoSemSessaoHoje`; `streakEm23h30SaoPaulo`; `disciplinaDeOutraTrilhaRetorna400`; `PurgaTombstonesSessaoJobTest`.
- Frontend: `dashboard.test.tsx` (estados; grupo removido; confirmação com contagem/tempo); `editarSessao.test.tsx` (409 → toast + refetch).
- Contagem de testes backend ≥ baseline de R0.
- Gate comum verde.

---

---

## Decisões arquiteturais aplicáveis

**D-13 — Sessão editável**
- *Dúvida/Lacuna:* `editavel` é calculado por página/filtro.
- *Deliberação e Referência:* Regra aplicável: "nº 1 na ordenação canônica **global** da trilha".
- *Decisão Cravada:* O serviço obtém uma vez por request o `_id` da sessão global nº 1 de `{usuarioId, trilhaId, deletedAt: null}` ordenada por `(dataSessao DESC, criadoEm DESC, _id DESC)` e marca `editavel` comparando IDs, independentemente de `pagina`, `disciplinaId` ou período. Tombstones (`deletedAt != null`) nunca entram em listagens, agregações nem no cálculo da nº 1.

**D-14 — Definição de streak**
- *Dúvida/Lacuna:* `streakDias` não tem definição normativa.
- *Deliberação e Referência:* No Duolingo e no GitHub, o streak não "quebra" enquanto o dia corrente ainda não terminou.
- *Decisão Cravada:* `streakDias` = número de dias de calendário consecutivos com ≥ 1 sessão, terminando em `hojeLocal` se houver sessão hoje; caso contrário, terminando em `hojeLocal − 1`. Se não houver sessão em nenhum dos dois, vale 0. É calculado sobre **todas** as sessões da trilha (não depende do filtro de período). Teste com borda 23h30 em `America/Sao_Paulo`.

**D-15 — Tombstone de sessão**
- *Decisão Cravada:* O tombstone mantém apenas `_id, usuarioId, trilhaId, chaveIdempotencia, requestHash, deletedAt`; todos os outros campos são removidos com `$unset`. O job `PurgaTombstonesSessaoJob` (`@SystemScope`, a cada hora) remove tombstones com `deletedAt < agora − 24h`. Após a purga, a chave fica livre (o cliente nunca reutiliza chaves; está documentado no contrato).

---

## Contexto normativo de domínio pertinente

## 6. Sessões e estatísticas de Estudos

### 6.1 Fonte de verdade e documento de sessão

- Sessões são a fonte de verdade para duração, questões e acertos. Não duplicar esses valores em contadores da disciplina.
- Cada sessão é um documento pequeno, editável segundo as regras abaixo; não é uma linha negativa nem um registro de ajuste para apagar métricas.
- Campos persistidos em `sessoes`: `_id` (`ObjectId`), `usuarioId`, `trilhaId`, `disciplinaId`, `trilhaNomeSnapshot`, `disciplinaNomeSnapshot`, `disciplinaCorSnapshot`, `dataSessao` (`LocalDate` / ISO `yyyy-MM-dd`), `duracaoSegundos` (`int`, mínimo `60`, máximo `86400`), `questoes` (`int >= 0`), `acertos` (`int >= 0`, `<= questoes`), `chaveIdempotencia` (`String`, máx. 128 chars), `requestHash` (SHA-256 imutável), `deletedAt?` (tombstone mínimo sem conteúdo por 24h), `criadoEm` (`Instant`) e `atualizadoEm` (`Instant`).
- Erros (`questoes - acertos`) e percentual de acerto (`acertos / questoes`) são calculados em leitura; não persistir `erros`, `minutos` nem `tipo` (remover `TipoSessao` e `AJUSTE_MANUAL`).
- Sessões não registram horário de estudo. `criadoEm` e `_id` são usados exclusivamente para desempate de ordenação técnica no mesmo `dataSessao` (`dataSessao DESC, criadoEm DESC, _id DESC`), nunca exibidos como hora de estudo na UI.
- Duração mínima para salvar: `60` segundos (`duracaoSegundos >= 60`). Questões e acertos são opcionais (default `0`).
- Exigir cabeçalho `Idempotency-Key` (ou campo `chaveIdempotencia`) por intenção de salvar, reutilizado em retries: se a chave já existir para o mesmo `usuarioId` com o mesmo payload funcional, retornar a sessão existente (`200 OK` / `201 Created`); se o payload divergir para a mesma chave, retornar `409 Conflict` (RFC 9457).

### 6.2 Gravação, edição e exclusão

- Um único modal/componente e um único serviço frontend de gravação em `frontend/shared`, reutilizados por Estudos, Tarefas e Pomodoro.
- O modal só grava ao clicar **Salvar sessão**.
- No cronômetro de Estudos, a duração inicial enviada ao modal é o foco acumulado real em segundos (`duracaoSegundos`), excluindo pausas; o alarme sonoro/visual de meta atingida nunca abre o modal nem salva sessão automaticamente. Somente clicar em **Encerrar e salvar** abre o modal.
- No modal aberto por conclusão direta de bloco, a duração inicial é `duracaoMinutos * 60` segundos, editável em horas e minutos. Se o bloco foi iniciado no cronômetro (**Estudar este bloco**), prevalece o foco real acumulado em segundos no cronômetro.
- No registro manual: usar a trilha ativa e exigir disciplina; permitir editar horas/minutos e `dataSessao`. Se não houver trilha ou disciplina, o próprio modal permite informar o nome da nova trilha e/ou nova disciplina e criá-las antes de gravar a sessão.
- `dataSessao` é data de calendário local (`yyyy-MM-dd`). Timer e conclusão de bloco usam `hojeLocal`; registro manual permite selecionar qualquer data `<= hojeLocal` (rejeitar data futura com `400 Bad Request`).
- Campos de questões: total e acertos opcionais; validar acertos ≤ total. Erros e percentual são derivados.
- **Edição da sessão mais recente:** apenas a sessão número 1 da trilha na ordenação canônica global `(dataSessao DESC, criadoEm DESC, _id DESC)` pode ser editada (`PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}`), a qualquer tempo, permitindo alterar `duracaoSegundos` (`>= 60`), `questoes` e `acertos`. Tentativa de editar qualquer outra sessão retorna `409 Conflict`.
- **Exclusão de sessão:** permitir excluir individualmente uma sessão do histórico mediante confirmação explícita (`DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`), permitindo corrigir lançamentos em disciplina ou data incorreta sem precisar apagar a disciplina inteira. Durante 24h manter tombstone mínimo de chave/hash para impedir recriação acidental em retry, sem reter conteúdo pessoal da sessão.
- Histórico ordenado exclusivamente por `(dataSessao DESC, criadoEm DESC, _id DESC)`, sem seletor de ordenação na UI, com paginação obrigatória (`20` por página por padrão, máximo `100`).
- Enquanto a disciplina existir na trilha, o histórico e as estatísticas exibem seu nome e cor atuais; se a disciplina for excluída da trilha, suas sessões são **preservadas por padrão** (sem prompt destrutivo ao excluir a disciplina) e exibidas usando `disciplinaNomeSnapshot` e `disciplinaCorSnapshot`.
- No Dashboard e no Histórico, sessões cujos `disciplinaId` não existem mais na trilha aparecem identificadas com badge/alerta amarelo (**"Disciplina removida"**) e ação explícita **"Apagar histórico desta disciplina"** (`DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`), com modal de confirmação informando a quantidade de sessões e o tempo total que serão removidos.
- Excluir uma trilha apaga seus cronogramas e sessões. Excluir a conta apaga todos os dados pessoais, lixeiras, inscrições push e dados associados.

### 6.3 Cálculo das estatísticas

- Agregar a partir das sessões, filtrando por usuário/trilha/período/disciplina; não carregar milhares de registros para o frontend.
- Tempo por disciplina = soma da duração em segundos (`duracaoSegundos`) das sessões; questões/acertos = somas; erros e precisão são derivados.
- Precisão global é ponderada pelos totais de questões (`soma acertos / soma questões`), não média aritmética dos percentuais.
- Progresso de tópico/disciplina continua independente das sessões, calculado pelas flags de tópico concluído.
- Dashboard mantém filtros de período existentes (7/14/30/180 dias e intervalo personalizado); agregar por dia para séries temporais. Para consultas longas, agrupar por semana/mês quando melhorar legibilidade sem perder totais.
- Índices compostos por usuário/trilha/data e usuário/trilha/disciplina/data; avaliar os planos com `explain("executionStats")`.
- Com 6 sessões/dia, o volume é cerca de 2.190/ano; MongoDB atende esse porte com documentos e índices adequados. Fazer benchmark com volume representativo antes de estabelecer metas de latência. Materializações/cache só se medição futura justificar.

### 6.4 Falhas ao concluir bloco

- Concluir bloco abre o modal global. O bloco só muda para concluído quando o usuário salva a sessão ou escolhe explicitamente **Concluir sem salvar**.
- Cancelar/voltar/fechar retorna ao contexto anterior e deixa o bloco pendente.
- Ao salvar: gravar sessão idempotentemente e então concluir bloco. Sessão e bloco não precisam de vínculo persistente.
- Se salvar sessão funcionar e concluir bloco falhar, informar “sessão salva, bloco ainda pendente” e permitir retry apenas da conclusão, sem criar outra sessão.
- Payload/chave de idempotência permanecem estáveis durante a abertura do modal. Se aba/reload interromper a segunda etapa, sessão já salva não é revertida nem reenviada automaticamente; ao voltar, bloco continua pendente e pode ser concluído sem sessão ou iniciar novo fluxo intencional.
- Desconcluir bloco não exclui sessões. Podem existir múltiplas sessões informativas no mesmo contexto/bloco.
- Falhas de conexão não enfileiram operações no servidor no MVP; preservar formulário/dados, mostrar erro e permitir retry.

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

## Plano original da FASE 3 (referência de escopo)

## FASE 3 — Domínio Estudos (Parte 2): Sessões em Segundos, Edição/Exclusão e Estatísticas Agregadas

### 3.1 Contexto e Pré-requisitos para o Agente Construtor
- **Pré-requisitos:** FASES 0, 1 e 2 concluídas.
- **Objetivo da fase:** Refatorar completamente o pacote `br.com.trilhara.sessao` para tornar a coleção `sessoes` a fonte única de verdade de tempo (`duracaoSegundos`) e questões (`questoes`, `acertos`), implementar idempotência de criação, edição restrita à sessão mais recente, exclusão individual de sessão com confirmação, preservação de sessões ao excluir disciplina (com grupo de alerta amarelo e exclusão explícita do histórico da disciplina removida) e atualizar o Dashboard de Estudos.
- **O que NÃO fazer nesta fase:**
  - Não criar ainda o `GlobalSessionModal` em `frontend/src/shared` (será feito na FASE 5).

### 3.5 Critérios de Saída da FASE 3
- Testes de integração em `backend/src/test/java/br/com/trilhara/sessao/` validando:
  - Gravação em segundos (`duracaoSegundos = 95` soma exatamente `95` segundos).
  - Idempotência (`Idempotency-Key` igual com mesmo payload retorna a mesma sessão; com payload diferente retorna `409`).
  - Edição permitida apenas na sessão número 1 de `(dataSessao DESC, criadoEm DESC, _id DESC)` e `409` nas demais.
  - Exclusão de disciplina na trilha preserva suas sessões e marca `disciplinaRemovida = true`; chamada ao endpoint de limpeza da disciplina removida apaga apenas as sessões daquela disciplina.
- `mvn test` e `npm run build` passando sem erros.
- Testar header ausente, divergência header/body, concorrência da mesma chave, retries depois de editar/excluir, períodos invertidos e estatística vazia.

<!-- ===================================================================== -->
<!-- FIM DO BLOCO DA FASE 3                                                -->
<!-- ===================================================================== -->

---

<!-- ===================================================================== -->
<!-- INÍCIO DO BLOCO AUTOCONTIDO DA FASE 4                                 -->
<!-- ===================================================================== -->

---

## Relato da implementação alegada — FASE 3 (hipótese a verificar)

## FASE 3 — Domínio Estudos (Parte 2): Sessões em Segundos, Edição/Exclusão e Estatísticas Agregadas

### 1. Resumo Executivo da Implementação

A **FASE 3** consolidou a coleção `sessoes` como a **única fonte da verdade** para métricas de tempo de estudo, questões resolvidas e acertos na plataforma Trilhara, eliminando campos legados em minutos e contadores manuais desacoplados.

Principais entregas realizadas:
1. **Modelo BSON de Sessão Normalizado e Integridade Temporal**:
   - Duração estritamente em segundos (`duracaoSegundos` no intervalo `60..86400`, equivalente a 1 minuto até 24 horas).
   - Data da sessão padronizada (`dataSessao` em `yyyy-MM-dd`), com validação rigorosa proibindo datas futuras no fuso horário do usuário.
   - Questões e acertos consolidados na própria sessão (`questoes >= 0`, `acertos` entre `0` e `questoes`), com cálculo automático de erros e taxa de acerto.
   - Remoção completa de classes e estruturas legadas: `AjusteQuestoes`, `TipoSessao`, `TipoRegistroSessao`, `NovaSessao` e `EstatisticaDisciplina`.

2. **Idempotência Estrita com Hash Funcional SHA-256 e Tombstone**:
   - Endpoint `POST /api/trilhas/{trilhaId}/sessoes` com suporte a `Idempotency-Key` (via Header e Body).
   - Em caso de repetição idêntica de payload, retorna `200 OK` com o registro existente.
   - Em caso de conflito funcional com a mesma chave, rejeita com `409 IDEMPOTENCY_KEY_CONFLICT`.
   - Se o valor do header divergir do corpo, rejeita com `400 IDEMPOTENCY_KEY_MISMATCH`.
   - Exclusão via `DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}` grava tombstone de 24 horas retendo a chave e hash para evitar re-criação indevida (`409 IDEMPOTENCY_RESULT_REMOVED`).

3. **Ordenação Canônica e Regra de Edição da Última Sessão**:
   - Ordenação canônica padronizada em todas as consultas e relatórios: `(dataSessao DESC, criadoEm DESC, _id DESC)`.
   - Endpoint `PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}` restrito estritamente à **sessão #1 mais recente global** da trilha. Qualquer tentativa de alteração em sessões anteriores é rejeitada com `409 LATEST_SESSION_ONLY`.
   - A listagem paginada (`GET /api/trilhas/{trilhaId}/sessoes`) sinaliza o atributo booleano `editavel: true` exclusivamente na primeira sessão da primeira página.

4. **Preservação de Histórico e Endpoint Dedicado de Exclusão por Disciplina**:
   - Ao excluir uma disciplina da trilha, as sessões de estudo associadas são integralmente preservadas com a flag `disciplinaRemovida: true` e snapshots imutáveis de nome e cor.
   - Implementado endpoint dedicado `DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes` permitindo a purga voluntária do histórico, retornando `{ sessoesRemovidas, totalSegundosRemovidos }`.

5. **Estatísticas Agregadas e Cálculo de Sequência de Dias (Streak)**:
   - Endpoint `GET /api/trilhas/{trilhaId}/estatisticas` operando via pipeline de agregação BSON com suporte a períodos pré-definidos (`7d`, `14d`, `30d`, `180d`, `tudo`) ou intervalo customizado (`dataInicio` e `dataFim`).
   - Retorno detalhado contendo métricas globais (`totalSegundos`, `totalSessoes`, `totalQuestoes`, `totalAcertos`, `totalErros`, `percentualAcertoGlobal`), métricas agrupadas `porDisciplina`, distribuição cronológica `serieTemporal` e cálculo contínuo de dias consecutivos de estudo (`streakDias`).

6. **Modernização do Frontend**:
   - Atualização de contratos em `domainContracts.ts` e tipagens em `trilhara.ts`.
   - Modernização do serviço `EstudosService` para consumir os novos endpoints paginados e agregados.
   - Adaptação do `EstudarView.tsx` para persistir tempo em segundos e data local ISO.
   - Atualização do `DashboardView.tsx` com paginação de 20 itens, edição rápida da última sessão, confirmação de exclusão, alerta para disciplinas removidas com ação de limpeza definitiva, e formatação amigável de horas/minutos/segundos.
   - Ajuste em `DisciplinasView.tsx` tornando a visualização de questões informativa a partir do pipeline de sessões.

---


### 3. Checklist de Validação da FASE 3

| Item | Requisito / Premissa | Situação | Evidência de Validação |
| :--- | :--- | :---: | :--- |
| **3.1** | Coleção `sessoes` como única fonte da verdade de tempo, questões e acertos | **Atendido** | `Sessao.java`, BSON aggregation em `SessaoRepository` e remoção de endpoints de ajuste manual |
| **3.2** | Duração estritamente em segundos com validação no intervalo `60..86400` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRegistrarSessaoNormalizadaEmSegundos` |
| **3.3** | Proibição de registro de sessão com data futura no fuso horário do usuário | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRejeitarSessaoComDataFutura` |
| **3.4** | Idempotência estrita via Header/Body `Idempotency-Key` com SHA-256 funcional (201/200/409) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveGarantirIdempotenciaEstritaComHashFuncional` |
| **3.5** | Rejeição por divergência entre Header `Idempotency-Key` e Body com `400 IDEMPOTENCY_KEY_MISMATCH` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveRejeitarDivergenciaEntreHeaderECorpoIdempotencyKey` |
| **3.6** | Ordenação canônica `(dataSessao DESC, criadoEm DESC, _id DESC)` em todas as listagens | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveOrdenarSessoesPelaOrdemCanonica` |
| **3.7** | Edição via `PATCH` restrita estritamente à sessão #1 mais recente global (`409 LATEST_SESSION_ONLY` nas demais) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePermitirEditarApenasAUltimaSessaoGlobalDaTrilha` |
| **3.8** | Exclusão de sessão com gravação de Tombstone retido por 24h (`409 IDEMPOTENCY_RESULT_REMOVED` em replay) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveGravarTombstoneNaExclusaoERejeitarReplayComIdempotencia` |
| **3.9** | Preservação de sessões históricas ao excluir disciplina (`disciplinaRemovida: true`) | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePreservarSessoesHistoricasAoExcluirDisciplina` |
| **3.10** | Endpoint dedicado para purga em lote de sessões de disciplina excluída | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePermitirExcluirHistoricoDeDisciplinaRemovidaViaEndpointDedicado` |
| **3.11** | Paginação obrigatória em `GET /api/trilhas/{id}/sessoes` com sinalização de `editavel: true` | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#devePaginarSessoesComSinalizacaoDeEditavelApenasNaPrimeira` |
| **3.12** | Pipeline de estatísticas agregadas por período, disciplina, série temporal e streak contínuo | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveCalcularEstatisticasAgregadasComSerieTemporalESubmeterFiltrosDePeriodo` |
| **3.13** | Isolamento estrito por `usuarioId` em todas as operações de sessões e agregações | **Atendido** | Validado em `SessaoNormalizadaIntegrationTest#deveIsolarSessoesEEstatisticasPorUsuarioId` |
| **3.14** | Exclusão de trilha limpa em cascata todas as suas sessões e tombstones no MongoDB | **Atendido** | Validado no `TrilhaService.excluirTrilha` invocando `sessaoRepository.excluirPorTrilha` |
| **3.15** | Contratos de frontend alinhados e testes unitários Vitest | **Atendido** | 34 testes vitest aprovados em `frontend/src/test/` incluindo `sessaoNormalizada.test.ts` |
| **3.16** | Sucesso no build do frontend com checagem rigorosa do TypeScript (`tsc -b && vite build`) | **Atendido** | Bundle de produção gerado com sucesso sem erros |
| **3.17** | Sucesso na suíte de testes backend com Quarkus e MongoDB (`mvn test`) | **Atendido** | 47 testes executados com 0 falhas e 0 erros |

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

