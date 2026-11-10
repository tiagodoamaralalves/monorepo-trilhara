# Requisitos e diretrizes para reestruturação de dados

**Status:** documento normativo de domínio e produto. O [COMPLEMENTO-REESTRUTURACAO.md](./COMPLEMENTO-REESTRUTURACAO.md) é registro histórico não normativo; em divergência, este documento define comportamento e o plano define sequência/implementação.
**Escopo:** Estudos, Tarefas, Notas, Pomodoro e suas integrações com o backend/MongoDB
**Premissa de migração:** o produto ainda não foi lançado; os dados atuais são de teste e podem ser descartados. Não é necessário manter compatibilidade de dados com o modelo legado.

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

## 2. Conceitos e limites entre os aplicativos

### 2.1 Estudos

É a fonte de verdade de trilhas, disciplinas, tópicos, cronogramas, progresso dos tópicos, sessões e estatísticas derivadas.

O cronômetro da aba **Estudar** pertence a Estudos. Exige uma trilha selecionada e uma disciplina; o registro manual pode criar uma trilha e disciplina se ainda não houver contexto adequado. Quando o cronômetro é iniciado a partir de um bloco planejado (**Estudar este bloco**), ele carrega a trilha, a disciplina e a referência da ocorrência do bloco; ao clicar em **Encerrar e salvar**, o modal global é aberto com o tempo real focado em segundos e, após salvar a sessão, conclui o bloco vinculado.

### 2.2 Tarefas

É a fonte de verdade das tarefas e listas criadas manualmente pelo usuário. Tarefas manuais são independentes de Estudos e não abrem o modal de sessão ao serem concluídas.

As listas que representam trilhas são **visões virtuais**, não documentos/listas gravados em Tarefas. Elas consultam blocos de Estudos e permitem concluir ou reabrir blocos; não permitem criar nem editar blocos como tarefas.

### 2.3 Notas

É um produto independente. Pode oferecer importação inicial de uma trilha para criar um caderno, guardando referências de origem para sincronização explícita. A importação não torna o conteúdo do caderno dependente da trilha.

### 2.4 Pomodoro

O módulo em `pomodoro.localhost` (ou `/pomodoro`) tem cronômetro próprio, independente de Estudos e sem criação automática de sessões. Para usuários autenticados, exibe ao lado do cronômetro a visão de Hoje compartilhada com Tarefas (tarefas manuais de hoje, atrasadas e sem data, mais blocos do dia dos cronogramas ativos). Para visitantes não autenticados, o cronômetro funciona localmente e o painel de tarefas exibe chamada para autenticação. Concluir tarefa manual executa a mesma ação de Tarefas; concluir bloco de cronograma aciona o modal global de sessão.

### 2.5 Navegação entre módulos (App Switcher)

Todos os quatro módulos (**Estudos**, **Tarefas**, **Notas** e **Pomodoro**) compartilham o mesmo cabeçalho/barra lateral do Design System (`AppSidebar`) com um alternador rápido de módulos (**App Switcher**), preservando a sessão autenticada (SSO) e o tema visual ativo tanto em navegação por subdomínios (`*.trilhara.com.br` / `*.localhost`) quanto por rotas (`/estudos`, `/tarefas`, `/notas`, `/pomodoro`).

## 3. Identidade, tenancy e tipos BSON

- Padronizar o campo de proprietário de todas as coleções privadas como `usuarioId` gravado como `ObjectId` BSON nativo. Eliminar o fallback legado `"local"` em string; em teste usar autenticação simulada/helper ou, somente se indispensável, ObjectId fixo no profile `%test`, nunca fora dele.
- Usar `ObjectId` BSON nativo para `_id` e referências entre coleções. A API HTTP expõe IDs como strings hexadecimais de 24 caracteres; ID malformado é sempre `400 INVALID_IDENTIFIER`, ID bem formado mas inexistente ou de outro tenant é `404 NOT_FOUND`.
- Tópicos embutidos no documento da disciplina usam `id` string estável gerado com `new ObjectId().toHexString()`. IDs de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações e nunca são reutilizados após remoção.
- Datas que representam instantes (`criadoEm`, `atualizadoEm`, `concluidoEm`, `deletedAt`, `expiresAt`, `instanteEntregaUtc`) são `BSON Date` no MongoDB e `Instant` no Java.
- Datas de calendário (`dataVencimento`, `dataOriginalOcorrencia`, `dataSessao`, `dataBloco`, `dataInicio`, `dataFim`, `dataProva`) são armazenadas como string ISO `yyyy-MM-dd` (mapeadas para `LocalDate` no Java via codec BSON direto), garantindo ordenação lexicográfica e ausência de deslocamento por fuso horário.
- O fuso de calendário segue o fuso atual do navegador: o cliente HTTP compartilhado envia em todas as requisições o cabeçalho `X-Timezone` com o identificador IANA (ex.: `America/Sao_Paulo`, obtido via `Intl.DateTimeFormat().resolvedOptions().timeZone`). O backend valida o `X-Timezone` (com fallback seguro para `America/Sao_Paulo` se ausente/inválido) e calcula `hojeLocal = LocalDate.now(zoneId)` para visão Hoje, atrasadas, corte de blocos futuros, trava de data futura em sessão manual e cálculo de *streak*, proibindo o uso de `LocalDate.now()` no fuso UTC da JVM.
- Converter valores para BSON com `CodecRegistry` tipado ou mapeadores BSON diretos. É proibido serializar/desserializar via `ObjectMapper.writeValueAsString` + `Document.parse` + `documento.toJson()`.

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

## 7. Tarefas

### 7.1 Tarefas manuais e visão Hoje

- `GET /api/tarefas/hoje` é a consulta de visão diária e retorna `{ hojeLocal, atrasadas, hoje, semData }`, somente tarefas manuais pendentes. Tarefas sem `dataVencimento` ficam em `semData`; atrasadas ordenam por `dataVencimento ASC, prioridade DESC, ordem ASC`; tarefas hoje por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`. DTO não contém blocos de Estudos.
- Tarefas manuais têm origem própria e são persistidas; campos de data e horário são locais/calendário, horário opcional.
- Hoje inclui tarefas manuais vencidas hoje, atrasadas e sem data. Tarefas concluídas somem de Hoje/Pomodoro e permanecem consultáveis em Concluídas/histórico, sem expiração automática.
- Tarefas manuais sem data não são lembrete/cronograma; continuam aparecendo em Hoje conforme decisão do produto.
- Subtarefas são checklist marcável vinculado à tarefa pai, sem data/prioridade/recorrência próprias; marcar todas não conclui automaticamente o pai.
- Concluir uma tarefa manual não abre modal de sessão.

### 7.2 Recorrência

- Modelar a regra em coleção própria (`series_recorrentes`) separada das ocorrências materializadas em `tarefas`. Materializar ocorrências em janela móvel de **90 dias** a partir de `hojeLocal` (ou até o fim do intervalo consultado no calendário, limitado a 366 dias), atualizando `geradoAte` na série.
- Cada ocorrência em `tarefas` guarda `serieId` (`ObjectId`), `dataOriginalOcorrencia` (`LocalDate`, imutável) e `estado` (`PENDENTE`, `CONCLUIDA`, `CANCELADA`). A idempotência de geração é garantida por índice único parcial `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }`. Assim, se o usuário alterar a `dataVencimento` de uma ocorrência individual, `dataOriginalOcorrencia` permanece intacta e a janela móvel jamais recria aquela data em duplicidade.
- Recorrência por calendário fixo ancorada em `dataInicio` inclusiva: `DIARIO` gera `dataInicio + k*intervalo dias`; `SEMANAL` divide semanas ISO segunda–domingo, considera semana de `dataInicio` índice zero e gera dias selecionados somente em semanas cujo índice é múltiplo de `intervalo`, nunca antes de `dataInicio`; `MENSAL` avança meses de calendário em `intervalo`, por dia do mês `1..31` ou ordinal semanal (`1..4` ou última `-1`). Dia do mês ausente vira último dia válido. Não incluir recorrência anual no MVP.
- Para regra MENSAL `ORDINAL_SEMANA`, ordinal `1..4` que não existe no mês é inválido para aquela data e não gera ocorrência; ordinal `-1` seleciona a última ocorrência do dia da semana daquele mês.
- Término da série: `NUNCA`, `EM_DATA` (`dataFim`, inclusiva) ou `APOS_OCORRENCIAS` (`maxOcorrencias`). `APOS_OCORRENCIAS` conta as posições de datas originalmente programadas desde `dataInicio`, incluindo as que foram concluídas, canceladas, movidas à lixeira ou editadas individualmente; reagendar vencimento não altera a posição/contagem. Campos incompatíveis com `tipoTermino` são rejeitados.
- `geradoAte` é cursor inclusivo da última data calendário examinada; avanço da janela é idempotente por índice único. Consultas que ampliem a janela não materializam além de `hojeLocal + 366 dias` nem do término.
- Concluir uma ocorrência muda seu `estado` para `CONCLUIDA` sem alterar as demais e sem abrir modal de sessão. Pular uma ocorrência muda seu `estado` para `CANCELADA` (omitida das listas ativas, preservada para impedir regeneração).
- Na seção **Atrasadas**, múltiplas ocorrências pendentes atrasadas da mesma `serieId` são agrupadas visualmente sob a ocorrência atrasada mais recente, exibindo um expansor `"+ N ocorrências anteriores atrasadas"`.
- A API `GET /api/tarefas/hoje` retorna `{ atrasadas, hoje, semData }` de tarefas manuais pendentes e metadados de `hojeLocal`; recorrências são materializadas antes de responder. Repetição do GET não altera estado funcional fora de `geradoAte`/criação idempotente de ocorrências.
- Escopos de edição e exclusão:
  1. **Apenas esta ocorrência:** edição altera apenas campos editáveis da ocorrência e define `desvinculadaDaSerie = true`; exclusão move apenas ocorrência para lixeira. A ocorrência não é recriada e a série continua.
  2. **Esta e futuras:** encerra a série original em `dataOriginalOcorrencia.minusDays(1)`, inativando-a se o corte preceder `dataInicio`; remove definitivamente apenas ocorrências pendentes não concluídas com data original `>= alvo`. Concluídas permanecem no histórico. Em edição, cria nova série com `dataInicio = alvo`, novos parâmetros e reinício de contagem de término relativo ao alvo. Exige `operationId` idempotente para retries; não há opção de exclusão sem edição que apague automaticamente ocorrências concluídas.
  3. **Toda a série:** encerra/move a regra em `series_recorrentes` para a lixeira, move todas as ocorrências pendentes (atrasadas, de hoje e futuras) para a lixeira com o mesmo `loteExclusaoId` e preserva ocorrências já concluídas. Restaurar uma ocorrência individual da lixeira não reativa a série encerrada.

### 7.3 Lixeiras e listas manuais

- Campos de tarefa manual: `_id`, `usuarioId`, `listaId?`, `titulo` (1..200), `descricao?`, `prioridade` (`BAIXA|MEDIA|ALTA`), `dataVencimento?` (`LocalDate`), `horario?` (`HH:mm`), `estado` (`PENDENTE|CONCLUIDA|CANCELADA`), `concluida` compatível com estado, `concluidaEm?`, `ordem`, `subtarefas` (ID/título/concluída/ordem), preferência de lembrete, campos de soft-delete e timestamps. Subtarefas não concluem automaticamente a tarefa pai.
- Coleções `listas_tarefas`, `series_recorrentes` e `tarefas` têm todos os campos definidos no plano; `deletedAt` ausente ou `null` significa ativo. Tarefa concluída não expira automaticamente.
- Excluir tarefa ou lista move o conteúdo à lixeira por 30 dias; usuário pode esvaziar antecipadamente. Excluir pai marca a subárvore ativa com mesmo `loteExclusaoId`; não muda itens que já estavam na lixeira por exclusão isolada.
- Excluir lista move lista e tarefas relacionadas juntas. Confirmar mostrando quantidade; restaurar hierarquia e ordem originais.
- Séries recorrentes vinculadas a lista excluída ficam suspensas (não materializam enquanto a lista estiver na lixeira); ao restaurar a lista, retomam a geração. Ao purgar a lista, séries que sobrevivem passam a `listaId = null` (Lista Geral).
- Se o usuário restaurar uma tarefa individual cuja `listaId` original esteja na lixeira, o sistema restaura a lista pai (apenas o container da lista) ou, caso a lista já tenha sido purgada definitivamente, restaura a tarefa na lista geral (`listaId = null`) informando o usuário.
- Depois de 30 dias, purgar definitivamente. Implementar purga em rotina controlada; índice TTL do MongoDB, se usado, é eventual e não deve ser tratado como relógio exato.
- Rotas públicas completas de CRUD, conclusão, pular, subtarefa, séries, Hoje e lixeira estão enumeradas na FASE 6 do plano; todas usam DTOs, checam tenant e retornam 404 para ID válido fora da posse.

### 7.4 Blocos de cronograma em Tarefas

- Não persistir bloco como tarefa. Uma lista virtual por trilha renderiza todos os blocos pendentes de todo o período gerado, em ordem de data, com indicação de atrasado/hoje/futuro.
- Filtros gerais **Futuro**, **Todas** e **Concluídas** não incluem blocos. A lista da trilha e Hoje são as exceções definidas.
- Hoje inclui somente blocos do dia atual, não blocos futuros ou atrasados.
- Blocos não podem ser criados/editados como tarefas. Concluir/reabrir usa endpoint de Estudos.
- Concluir um bloco abre o modal de sessão compartilhado.
- A ação **Concluir todas as atrasadas sem salvar** mostra confirmação com quantidade; implementar item a item/idempotentemente, reportando sucessos/falhas parciais e permitindo retry sem sessão. Não exigir transação Mongo multi-documento.

## 8. Pomodoro e timers

- Pomodoro independente mantém timer/configuração local e não registra sessão ao terminar, parar ou concluir uma tarefa manual.
- Pomodoro consulta e espelha exatamente a visão Hoje de Tarefas: tarefas manuais de hoje, atrasadas e sem data, e blocos de ciclo apenas de hoje. Não cria cópia/importação persistida.
- Exibir pílula da trilha nos blocos. Concluir tarefa manual chama a mesma ação de Tarefas; concluir bloco chama o modal de sessão compartilhado.
- Selecionar tarefa no Pomodoro é apenas contexto visual; não associa sessão/timer à tarefa. Concluir tarefa não pausa o timer.
- Cronômetro de Estudos e Pomodoro persistem seu estado completo em `localStorage` sob chaves escopadas por usuário (`trilhara:u:{usuarioId}:study_timer` e `trilhara:u:{usuarioId}:pomodoro_timer`), calculando o tempo em execução por diferença de timestamp absoluto (`Date.now() - runningSince` / `targetEndAt - Date.now()`) para suportar refresh, fechamento acidental de aba e *throttling* de segundo plano sem *drift*.
- Em Estudos, existe no máximo **um único timer ativo ou pausado por usuário/dispositivo**; iniciar o cronômetro em outra trilha, disciplina ou aba acumula o tempo decorrido e pausa/substitui o anterior após confirmação caso haja tempo não salvo (`>= 60s`).
- Sincronizar estado do timer entre abas de mesma origem via eventos `storage` e `BroadcastChannel`. Ao fazer logout (ou expirar sessão), se o timer estiver rodando, acumular o tempo até o instante do logout (`elapsedMs += now - runningSince`), mudar `runningSince = null` (pausado) na chave `trilhara:u:{usuarioId}:*` e limpar os dados de sessão em memória. Ao autenticar novamente com a mesma conta, restaurar o timer pausado; outra conta jamais lê chaves de outro `usuarioId`.
- Se o usuário estiver offline, o cronômetro continua funcionando localmente, mas ações de escrita (salvar sessão, concluir bloco/tarefa) exibem erro claro de conectividade mantendo os dados preenchidos no modal para retry explícito.

## 9. Visão compartilhada de Hoje e integração entre domínios

- Manter APIs backend separadas: Tarefas fornece tarefas manuais; Estudos fornece blocos do dia de todos os cronogramas ativos em todas as trilhas.
- Um serviço frontend compartilhado agrega os dois resultados para Tarefas e Pomodoro, sem mover lógica de domínio para uma coleção agregadora.
- Ordem das seções na Visão Hoje (Tarefas e Pomodoro):
  1. **Atrasadas** (somente tarefas manuais pendentes com `dataVencimento < hojeLocal`, agrupadas por série quando recorrentes): ordenadas por `dataVencimento ASC, prioridade DESC, ordem ASC`.
  2. **Hoje** (blocos pendentes de `hojeLocal` de todos os cronogramas ativos + tarefas manuais pendentes com `dataVencimento == hojeLocal`): blocos ordenados por `trilhaNome ASC, ordem ASC` e tarefas manuais ordenadas por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`.
  3. **Sem data** (somente tarefas manuais pendentes com `dataVencimento == null`): ordenadas por `prioridade DESC, ordem ASC`.
- Atualizar automaticamente após mutações locais, ao receber eventos `BroadcastChannel`/`storage` na mesma origem e nos eventos `visibilitychange` (quando `document.visibilityState === 'visible'`) e `focus` da janela (com *throttle* mínimo de 5 segundos para evitar rajadas de requisições).
- Se uma fonte falhar, renderizar a seção disponível e erro explícito/retry para a seção indisponível; não substituir falha por lista vazia.
- APIs de blocos devem oferecer consulta eficiente para todos os cronogramas ativos do usuário no dia e consultas paginadas para listas por trilha.
- API de blocos em Hoje retorna apenas blocos pendentes (`concluido == false`) no `hojeLocal`, com `trilhaId`, `cronogramaId`, `blocoId`, `disciplinaId`, nomes/cores snapshot, `data`, `ordem`, `duracaoMinutos`; a união de tela diferencia explicitamente item manual e bloco. Na seção Hoje, tarefas e blocos são grupos visuais separados, cada qual com sua ordenação própria.

## 10. Notas

### 10.1 Modelo e importação de trilhas

- Coleções separadas para cadernos, seções e páginas/notas, com `usuarioId` e IDs/referências; não embutir todas as seções/páginas no caderno.
- Caderno importado guarda `trilhaId`; seção importada guarda `disciplinaId`; página originada de tópico pode guardar `topicoId` para referência, sem sincronizar tópicos no MVP.
- Importação cria cópia inicial: nome da trilha → caderno; disciplina → seção; tópico → página vazia com título do tópico.
- Uma trilha pode ter um caderno vinculado. Reimportar desvincula o caderno antigo (limpando referências de origem de suas seções), preserva conteúdo e cria novo caderno associado. O usuário pode apagar o antigo manualmente.
- Caderno com `trilhaId` exibe pílula identificadora.
- Ao abrir Notas, consultar trilhas em segundo plano. Se houver trilha não importada e a preferência local permitir, abrir modal automaticamente. Mostrar ação destacada de importação quando houver trilha não importada, mesmo com modal dispensado.
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

## 11. Notificações push de Tarefas

- Push somente para tarefas manuais com horário explícito; blocos de cronograma não têm lembretes próprios.
- Preferência global padrão: 15 minutos antes. Cada tarefa pode sobrescrever para sem alerta, no horário ou 5/15/30/60 minutos antes. Herdeiras acompanham alteração do padrão; override local permanece.
- Permitir push em todos os dispositivos/navegadores autorizados do usuário. Clique abre a tarefa no app Tarefas; não a conclui.
- Capturar fuso IANA quando o lembrete é configurado e materializar instante UTC. Se retirar o horário, suspender lembrete preservando escolha; quando voltar a definir horário, reagendar.
- Concluir/excluir cancela lembrete; alterar horário/data recalcula entrega. Ocorrências recorrentes herdam lembrete e permitem override.
- Permissão negada ou navegador sem suporte não bloqueia o uso de tarefas; exibir estado informativo claro. Em iOS/iPadOS (Safari 16.4+), onde a Web Push API exige aplicativo instalado na Tela de Início, servir `/manifest.webmanifest` com `"display": "standalone"` e exibir instrução passo a passo ("Adicionar à Tela de Início") quando o usuário tentar ativar notificações em aba comum do Safari.
- Push é *best-effort* com **janela útil de entrega de 15 minutos**: a rotina agendadora materializa entregas na coleção `lembretes_push` com `instanteEntregaUtc`. Se o agendador processar um registro com atraso superior a 15 minutos em relação a `instanteEntregaUtc`, marca-o como `EXPIRADO_FORA_DA_JANELA` sem disparar notificação atrasada. Nas chamadas aos provedores Web Push (RFC 8030), enviar cabeçalho `TTL: 900`.
- Um lembrete tem estado `PENDENTE`, `PROCESSANDO`, `ENVIADO`, `CANCELADO`, `EXPIRADO_FORA_DA_JANELA` ou `FALHA_FINAL`; cada agendamento tem `lembreteId`/versão imutável por alteração da tarefa, chave idempotente por `(tarefaId, versãoAgendamento)`, lease de processamento e `tentativas`. O scheduler adquire cada item atomicamente; apenas o dono do lease pode concluir. Falhas temporárias tentam novamente com backoff exponencial limitado ao prazo de 15 minutos; expirado não envia. Envio externo pode ter resultado ambíguo após timeout; Web Push não garante exactly-once, portanto payload inclui `lembreteId` e o service worker deduplica notificações recentes localmente.
- Se a data/hora local ocorrer no intervalo de horário inexistente por mudança de fuso (gap DST), rejeitar configuração com `400 INVALID_LOCAL_DATETIME`; se ocorrer duas vezes (overlap DST), escolher a ocorrência anterior (offset de verão) deterministicamente e retornar `instanteEntregaUtc` calculado no DTO.
- Requisito técnico: `/sw.js` servido na raiz sem cache imutável (`Cache-Control: no-cache, no-store, must-revalidate` no Caddy), autenticação VAPID (RFC 8292) configurada via variáveis de ambiente, coleção `push_subscriptions` por dispositivo e coleção `lembretes_push` com índice em `{ estado: 1, instanteEntregaUtc: 1 }`. Quando o serviço de push responder `410 Gone` ou `404 Not Found`, remover imediatamente a inscrição inválida de `push_subscriptions`. Incluir os alertas na política de privacidade.

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

## 13. Modelo de coleções recomendado

Nomes e responsabilidades das **19 coleções** do sistema (**18 coleções privadas** de usuário + **1 coleção de catálogo público** `editais`):

| Coleção | Unidade de documento | Índices principais |
|---|---|---|
| `usuarios` | Conta + subdocumento `preferencias` (disponibilidade semanal e padrão de push) | `email` único normalizado; `criadoEm DESC` |
| `refresh_tokens` | Sessão/dispositivo autenticado (família de rotação) | `tokenHash` único; `usuarioId + familiaId`; `expiraEm` (TTL/limpeza) |
| `tokens_recuperacao_senha` | Token de uso único para redefinição de senha | `tokenHash` único; `usuarioId`; `expiraEm` |
| `tokens_confirmacao_email` | Token de verificação de e-mail da conta | `tokenHash` único; `usuarioId`; `expiraEm` |
| `editais` | Catálogo público de editais (gerido por ADMIN) | `status + esfera + atualizadoEm DESC` |
| `trilhas` | Metadados da trilha do usuário | `usuarioId + atualizadoEm DESC`; `usuarioId + origemEditalId` |
| `disciplinas` | Disciplina da trilha + lista limitada de tópicos (máx. 500) | `usuarioId + trilhaId + ordem` |
| `progresso_topicos` | Um tópico concluído | único `usuarioId + trilhaId + topicoId` |
| `cronogramas` | Configuração, modalidade, período, `ativo` e `versaoAtiva` | único `usuarioId + trilhaId + modalidade`; único parcial `usuarioId + trilhaId` onde `ativo == true` |
| `blocos_cronograma` | Uma ocorrência gerada vinculada a `versaoGeracao` | `usuarioId + trilhaId + cronogramaId + versaoGeracao + data + ordem`; `usuarioId + data + concluido` (para visão Hoje) |
| `sessoes` | Um registro de sessão (`duracaoSegundos`, `questoes`, `acertos`) | `usuarioId + trilhaId + dataSessao DESC + criadoEm DESC`; `usuarioId + trilhaId + disciplinaId + dataSessao DESC`; único parcial `usuarioId + chaveIdempotencia` |
| `listas_tarefas` | Lista manual de tarefas (com suporte a lixeira) | `usuarioId + deletedAt + ordem` |
| `series_recorrentes` | Regra de recorrência e cursor `geradoAte` | `usuarioId + deletedAt + ativa` |
| `tarefas` | Tarefa manual ou ocorrência materializada de série | `usuarioId + deletedAt + listaId + concluida + ordem`; `usuarioId + deletedAt + concluida + dataVencimento`; único parcial `usuarioId + serieId + dataOriginalOcorrencia` |
| `cadernos` | Caderno de notas | `usuarioId + deletedAt + atualizadoEm DESC`; único parcial `usuarioId + origemTrilhaId` quando vinculado e ativo |
| `secoes` | Seção de caderno | `usuarioId + cadernoId + deletedAt + ordem`; `usuarioId + cadernoId + origemDisciplinaId` |
| `notas` | Página com `conteudoMarkdown` | `usuarioId + secaoId + deletedAt + ordem`; `usuarioId + cadernoId + deletedAt`; índice de texto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` (`default_language: "portuguese"`) |
| `push_subscriptions` | Inscrição Web Push por navegador/dispositivo | `usuarioId`; `endpoint` único |
| `lembretes_push` | Entrega de notificação agendada (`instanteEntregaUtc`) | `estado + instanteEntregaUtc`; `usuarioId + tarefaId` |

Use soft delete com `deletedAt`, `expiresAt`, `loteExclusaoId`/rotina de purga e estado hierárquico para lixeiras. TTL Mongo pode auxiliar, mas sua execução é assíncrona; restauração e prazo devem ser validados pela aplicação.

## 14. Migração sem compatibilidade legada

1. Criar backup dos dados de teste apenas se houver necessidade de diagnóstico; os dados não são fonte de verdade de produção.
2. Parar aplicações que escrevem no banco.
3. Recriar coleções/índices em ambiente de desenvolvimento/teste com os tipos e nomes padronizados.
4. Não executar `deleteMany` destrutivo em construtores/inicialização de repositório. Migrações e limpeza devem ser comandos/etapas explícitos.
5. Criar índices fora do fluxo de cada request; falha de índice deve impedir readiness ou ser reportada, nunca ignorada silenciosamente.
6. Rodar testes de contrato, segurança por proprietário, round-trip BSON, concorrência, retries/idempotência e casos de exclusão/restauração.
7. Medir baseline e resultados de payload/latência/`explain` com dados sintéticos representativos antes de afirmar ganhos.

## 15. Sequência e dependências oficiais (F0 a F11)

Fases são numeradas para referência, não implicam dependência linear. Dependências normativas:

```text
F0 → F1
F1 → F2
F2 → F3 → F5
F2 → F4 → F5
F1 → F6
F4 + F5 + F6 → F7
F1 + F2 → F8
F2..F8 → F9
F6 + F9 → F10
F0..F10 → F11
```

Sequência linear segura para execução em chats/branches únicos: `F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10 → F11`. Para paralelizar, F2 e F6 podem ocorrer após F1 em branches separados; depois de F2, F3, F4 e F8 podem avançar em paralelo. F5 aguarda F3/F4; F7 aguarda F4/F5/F6; F9/F10 aguardam integração dos pré-requisitos listados. Ver plano para arquivos e critérios por fase.

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

## 17. Decisões técnicas assumidas e fora de escopo

- Cronograma (máx. 366 dias) e recorrência (janela operacional 90 dias, consulta máxima 366 dias) persistem ocorrências em documentos separados, com paginação e idempotência; arrays sem limite são proibidos.
- Em geração e lista virtual de cronograma, `blocosPorDia` é quantidade por dia ativo; o total por dia nunca excede 24. Horizonte de 366 dias inclusivo limita ocorrências a 8.784.
- Agregações de sessões são a fonte de totais. Não criar contadores duplicados nem sessões negativas.
- Conclusão em lote e exclusões multi-coleção não prometem atomicidade MongoDB; usam operação idempotente, progresso/reconciliação e resultado parcial explícito.
- Índice de texto MongoDB 7 é a busca MVP de Notas, sem substring/infix garantido; snippets são montados no backend.
- Lixeiras têm prazo validado pela aplicação e purga agendada; TTL é opcional e nunca é o relógio de negócio.
- MVP não inclui upload de imagens, sincronização offline de escritas, WebSocket, timers cross-device, exactly-once de Web Push nem latência absoluta garantida.
- Antes de adicionar cache/materialização, medir consultas com massa representativa e guardar baseline.
- Contratos de request/response e cenários de falha detalhados estão no plano por fase; qualquer alteração posterior exige atualizar ambos os documentos no mesmo change.
