# Complemento de Arquitetura, Produto e Implementação — Reestruturação Trilhara

**Documentos-base analisados:**
- [`REQUISITOS-REESTRUTURACAO-DADOS.md`](./REQUISITOS-REESTRUTURACAO-DADOS.md)
- [`PLANO-IMPLEMENTACAO-REESTRUTURACAO.md`](./PLANO-IMPLEMENTACAO-REESTRUTURACAO.md)

**Escopo:** Revisão crítica ponta a ponta de coerência documental, modelagem de domínio e persistência (MongoDB 7 / Quarkus), contratos de API, produto/UX, acessibilidade (WCAG 2.2 AA), Design System, sincronização multi-aba/multi-dispositivo e segurança/privacidade.

---

> **Status documental:** registro histórico de revisão, mantido para rastreabilidade. Não é fonte normativa nem instrução independente de implementação. As decisões aprovadas são consolidadas nos dois documentos-base; se houver divergência, prevalecem `REQUISITOS-REESTRUTURACAO-DADOS.md` para produto/domínio e `PLANO-IMPLEMENTACAO-REESTRUTURACAO.md` para fases/execução. Não usar as propostas de redação das seções abaixo sem conferir a versão normativa atual.

## 1. Objetivo e relação com os dois documentos-base

Este documento registra todas as correções de contradições, resoluções de ambiguidades, decisões arquiteturais complementares, reorganização de fases e critérios de aceite necessários para tornar a especificação de reestruturação do **Trilhara** completa, verificável e pronta para implementação.

As propostas das seções seguintes foram revisadas e parcialmente consolidadas nos documentos-base. O complemento pode conter texto histórico superado; ele não garante alinhamento literal com os documentos normativos.

### Resumo executivo das lacunas críticas resolvidas

1. **Contradição na ordem de fases e retrabalho entre domínios:** A Seção 15 de `REQUISITOS-REESTRUTURACAO-DADOS.md` colocava Sessões antes da normalização de Estudos, enquanto o `PLANO-IMPLEMENTACAO-REESTRUTURACAO.md` fazia o inverso e dividia a refatoração de Tarefas e APIs de blocos em quatro fases intercaladas (F4, F5, F7, F8) usando callbacks provisórios. As fases foram reorganizadas e consolidadas (de 13 fases `F0–F12` para 12 fases `F0–F11`), eliminando código temporário descartável.
2. **Contradição na exclusão de disciplina:** O plano (F3, passo 9) previa perguntar ao usuário no momento da exclusão da disciplina se desejava apagar as sessões, contradizendo `REQUISITOS-REESTRUTURACAO-DADOS.md` (§6.2 e §16), onde a exclusão da disciplina preserva o histórico por padrão e exibe um grupo de alerta amarelo no Dashboard/Histórico para exclusão posterior opcional.
3. **Inconsistência na unidade de duração das sessões (`duracaoSegundos` vs `minutos`):** Os requisitos exigem duração em segundos (mínimo 60s), mas o modelo atual (`Sessao.java`, `SessaoService.java`, `EstudarView.tsx`) trunca/arredonda para minutos inteiros. O contrato foi padronizado em `duracaoSegundos` (`int`, `60..86400`) e `totalSegundos` nas agregações.
4. **Ambiguidade na "sessão mais recente" e impossibilidade de corrigir lançamentos errados:** Como sessões usam `LocalDate` sem horário de estudo, a definição de "sessão mais recente" foi formalizada pela ordenação determinística `(dataSessao DESC, criadoEm DESC, _id DESC)`. Além disso, foi adicionada a exclusão individual de sessão com confirmação (`DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`), pois a edição só altera duração e questões (não disciplina ou data) e, sem exclusão individual, um lançamento em disciplina errada seria irreversível.
5. **Fuso horário implícito no servidor (`LocalDate.now()` em UTC vs fuso do navegador):** O código atual usa `LocalDate.now()` da JVM (UTC) em Tarefas e Estudos e fixa `"America/Sao_Paulo"` em Sessões. Foi definido o contrato explícito via cabeçalho `X-Timezone` (IANA) enviado pelo cliente HTTP compartilhado.
6. **Staging/versionamento de cronogramas sem campo de versão nos blocos e sem trava de concorrência:** Foi especificado o campo `versaoGeracao` em `blocos_cronograma`, `versaoAtiva` em `cronogramas`, limite de horizonte de 366 dias por geração e índice único parcial `{ usuarioId: 1, trilhaId: 1 }` com `partialFilterExpression: { ativo: true }` para impedir dois cronogramas ativos na mesma trilha sob requisições concorrentes.
7. **Recorrência de tarefas vulnerável a duplicação após edição de data individual:** O índice de idempotência das ocorrências foi corrigido para `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }` (mantendo `dataOriginalOcorrencia` imutável quando `dataVencimento` muda) e a coleção `series_recorrentes` foi incluída no catálogo de coleções.
8. **Restrições reais do índice de texto do MongoDB em Notas e perda de rascunho em falha de rede:** Especificado o índice único de texto composto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` com `default_language: "portuguese"` (sem `partialFilterExpression`, que o MongoDB não suporta em índices `$text`), geração de snippets no backend e rascunho de contingência em `localStorage` (`trilhara:u:{usuarioId}:nota_draft:{notaId}`).
9. **Isolamento de SSO e logout entre subdomínios (`*.localhost` / `*.trilhara.com.br`):** Como `localStorage` e `BroadcastChannel` são isolados por `origin` (não cruzam subdomínios), foi especificado o mecanismo de cookie compartilhado no domínio base (`trilhara_rt` HttpOnly + `trilhara_session_state` legível no `visibilitychange`/`focus`) e o escopo estrito de chaves locais `trilhara:u:{usuarioId}:*`.
10. **Inventário incompleto na exclusão de conta (LGPD) e requisitos de Web Push para iOS/Safari:** Adicionadas as coleções `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email`, `series_recorrentes` e `lembretes_push` ao modelo e à cascata de exclusão de conta, além da janela numérica de 15 minutos (`TTL: 900`) para push e manifesto PWA `display: standalone` exigido pelo iOS/iPadOS.

---

## 2. Alterações por documento e seção

### 2.1 Alterações em `REQUISITOS-REESTRUTURACAO-DADOS.md`

#### Seção 2 — Conceitos e limites entre os aplicativos (`§2.1`, `§2.4` e nova `§2.5`)

- **SUBSTITUIR** em `§2.1 Estudos` (segundo parágrafo):
  - **Texto original:**
    > O cronômetro da aba **Estudar** pertence a Estudos. Exige uma trilha selecionada e uma disciplina; o registro manual pode criar uma trilha e disciplina se ainda não houver contexto adequado.
  - **Texto final:**
    > O cronômetro da aba **Estudar** pertence a Estudos. Exige uma trilha selecionada e uma disciplina; o registro manual pode criar uma trilha e disciplina se ainda não houver contexto adequado. Quando o cronômetro é iniciado a partir de um bloco planejado (**Estudar este bloco**), ele carrega a trilha, a disciplina e a referência da ocorrência do bloco; ao clicar em **Encerrar e salvar**, o modal global é aberto com o tempo real focado em segundos e, após salvar a sessão, conclui o bloco vinculado.

- **SUBSTITUIR** em `§2.4 Pomodoro`:
  - **Texto original:**
    > O app em `pomodoro.localhost` tem cronômetro próprio, independente de Estudos e sem criação automática de sessões. Ele exibe a visão de Hoje compartilhada com Tarefas. Concluir tarefa manual é a mesma ação compartilhada usada por Tarefas; concluir bloco de ciclo aciona o fluxo global de sessão.
  - **Texto final:**
    > O módulo em `pomodoro.localhost` (ou `/pomodoro`) tem cronômetro próprio, independente de Estudos e sem criação automática de sessões. Para usuários autenticados, exibe ao lado do cronômetro a visão de Hoje compartilhada com Tarefas (tarefas manuais de hoje, atrasadas e sem data, mais blocos do dia dos cronogramas ativos). Para visitantes não autenticados, o cronômetro funciona localmente e o painel de tarefas exibe chamada para autenticação. Concluir tarefa manual executa a mesma ação de Tarefas; concluir bloco de cronograma aciona o modal global de sessão.

- **ADICIONAR** `§2.5 Navegação entre módulos (App Switcher)`:
  - **Texto incluído:**
    > Todos os quatro módulos (**Estudos**, **Tarefas**, **Notas** e **Pomodoro**) compartilham o mesmo cabeçalho/barra lateral do Design System (`AppSidebar`) com um alternador rápido de módulos (**App Switcher**), preservando a sessão autenticada (SSO) e o tema visual ativo tanto em navegação por subdomínios (`*.trilhara.com.br` / `*.localhost`) quanto por rotas (`/estudos`, `/tarefas`, `/notas`, `/pomodoro`).

#### Seção 3 — Identidade, tenancy e tipos BSON

- **SUBSTITUIR** a lista de regras da `Seção 3`:
  - **Texto original:**
    > - Padronizar o campo de proprietário das coleções privadas como `usuarioId`.
    > - Usar ObjectId BSON nativo para `_id` e referências a documentos raiz; a API pode expor IDs como strings hexadecimais.
    > - IDs internos de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações. IDs removidos não devem ser reutilizados.
    > - Datas que representam instantes (`criadoEm`, `atualizadoEm`, etc.) são BSON Date no MongoDB e `Instant` no Java.
    > - Datas de calendário (`dataVencimento`, data de sessão, data de bloco, data da prova) permanecem como `LocalDate`/ISO `yyyy-MM-dd`; não converter para instante usando um fuso implícito.
    > - O fuso de calendário do app segue o fuso atual do navegador. Para notificações push, guardar o fuso IANA no momento do agendamento e calcular o instante de entrega.
    > - Converter valores para BSON com codecs/configuração tipada ou mapeadores explícitos. Evitar a ida e volta `objeto → JSON String → BSON Document → JSON String → objeto`.
  - **Texto final:**
    > - Padronizar o campo de proprietário de todas as coleções privadas como `usuarioId` gravado como `ObjectId` BSON nativo. Eliminar o fallback legado `"local"` em string; em ambiente de teste sem token explícito, usar `ObjectId` fixo determinístico (`000000000000000000000001`) ou helper de autenticação.
    > - Usar `ObjectId` BSON nativo para `_id` e para referências a documentos de coleções (`usuarioId`, `trilhaId`, `disciplinaId`, `cronogramaId`, `listaId`, `serieId`, `cadernoId`, `secaoId`, `notaId`, `origemEditalId`). A API HTTP expõe e recebe esses IDs como strings hexadecimais de 24 caracteres, validando formato antes de consultar o banco (`400 Bad Request` ou `404 Not Found` se inválido).
    > - Tópicos embutidos no documento da disciplina usam `id` string estável gerado com `new ObjectId().toHexString()`. IDs de disciplina, tópico e ocorrência de bloco permanecem estáveis durante renomeações e nunca são reutilizados após remoção.
    > - Datas que representam instantes (`criadoEm`, `atualizadoEm`, `concluidoEm`, `deletedAt`, `expiresAt`, `instanteEntregaUtc`) são `BSON Date` no MongoDB e `Instant` no Java.
    > - Datas de calendário (`dataVencimento`, `dataOriginalOcorrencia`, `dataSessao`, `dataBloco`, `dataInicio`, `dataFim`, `dataProva`) são armazenadas como string ISO `yyyy-MM-dd` (mapeadas para `LocalDate` no Java via codec BSON direto), garantindo ordenação lexicográfica e ausência de deslocamento por fuso horário.
    > - O fuso de calendário segue o fuso atual do navegador: o cliente HTTP compartilhado envia em todas as requisições o cabeçalho `X-Timezone` com o identificador IANA (ex.: `America/Sao_Paulo`, obtido via `Intl.DateTimeFormat().resolvedOptions().timeZone`). O backend valida o `X-Timezone` (com fallback seguro para `America/Sao_Paulo` se ausente/inválido) e calcula `hojeLocal = LocalDate.now(zoneId)` para visão Hoje, atrasadas, corte de blocos futuros, trava de data futura em sessão manual e cálculo de *streak*, proibindo o uso de `LocalDate.now()` no fuso UTC da JVM.
    > - Converter valores para BSON com `CodecRegistry` tipado ou mapeadores BSON diretos. É proibido serializar/desserializar via `ObjectMapper.writeValueAsString` + `Document.parse` + `documento.toJson()`.

#### Seção 4 — Estudos: trilhas, disciplinas e tópicos (`§4.1` e `§4.2`)

- **ADICIONAR** ao final de `§4.1 Estrutura e consultas`:
  - **Texto incluído:**
    > - Limites estruturais validados no backend: até 50 trilhas por usuário, até 100 disciplinas por trilha e até 500 tópicos por disciplina.
    > - A resposta de resumo do Hub (`GET /api/trilhas`) projeta metadados da trilha, `totalDisciplinas`, `totalTopicos`, `topicosConcluidos`, `percentualProgresso` e metadados básicos do cronograma ativo (`cronogramaAtivoId`, `modalidade`, `dataInicio`, `dataFim`), sem transferir os arrays de tópicos nem as ocorrências de blocos.
    > - Operações em disciplinas e tópicos usam endpoints granulares sob `/api/trilhas/{trilhaId}/disciplinas` (criação, edição de metadados/peso/tópicos, reordenação e exclusão individual), evitando enviar a trilha inteira a cada alteração.

#### Seção 5 — Estudos: cronogramas e blocos (`§5.2`, `§5.3` e `§5.4`)

- **SUBSTITUIR** em `§5.2 Criação, edição e geração` (linhas 91–92):
  - **Texto original:**
    > - Opções de período: até a data da prova, 1 semana, 1 mês e 3 meses. Com data da prova, essa opção é o padrão; sem data, fica desabilitada e o padrão é 1 mês (30 dias).
    > - Datas inicial e final são inclusivas, inclusive a data da prova.
  - **Texto final:**
    > - Opções de período: até a data da prova, 1 semana (7 dias), 1 mês (30 dias) e 3 meses (90 dias). Com data da prova futura em relação à data de início, essa opção é o padrão; sem data da prova (ou se a data da prova já passou), a opção fica desabilitada e o padrão é 1 mês (30 dias).
    > - Datas inicial e final são inclusivas, inclusive a data da prova. O horizonte máximo por geração é de **366 dias** (`dataFim <= dataInicio.plusDays(365)`, máximo de 8.784 blocos por versão). Se a data da prova estiver a mais de 366 dias da data de início, o sistema gera os primeiros 366 dias e informa que o período restante poderá ser regenerado posteriormente.

- **SUBSTITUIR** em `§5.3 Regras de geração` (linhas 99–103):
  - **Texto original:**
    > - **Ponderado:** peso inteiro de 1 a 5 define proporção global dos blocos no período. Distribuição determinística e equilibrada; empate resolvido pela ordem cadastrada. Alterar pesos não altera cronograma já gerado.
    > - **Manual:** o usuário define a quantidade de blocos por dia da semana e disciplina de cada bloco; repetir a semana-modelo no período.
    > - Excluir disciplina remove blocos futuros não concluídos a partir de hoje; blocos passados permanecem como histórico visual. Renomear disciplina atualiza seu nome no cronograma, sem trocar seu ID; alteração de peso não regenera cronograma.
    > - Perfil semanal global de disponibilidade registra horas disponíveis por dia e é apenas uma referência. Cada cronograma mantém sua própria carga; alterar o perfil não modifica cronogramas existentes.
    > - Mostrar aviso não bloqueante de sobrecarga comparando a soma de horas dos cronogramas ativos de todas as trilhas com o perfil semanal.
  - **Texto final:**
    > - **Ponderado:** peso inteiro $p_i \in \{1..5\}$ define a proporção global dos blocos no período. Para um total de $B_{\text{total}}$ blocos no período e soma de pesos $P = \sum p_k$ das disciplinas participantes, cada posição $t \in \{1..B_{\text{total}}\}$ é atribuída à disciplina $i$ que maximiza o déficit acumulado $\Delta_i(t) = \frac{t \cdot p_i}{P} - \text{alocados}_i(t-1)$, desempatando pela menor `ordem` de cadastro da disciplina na trilha. Alterar pesos na disciplina não altera cronograma já gerado.
    > - **Manual:** o usuário define a quantidade de blocos por dia da semana (0 a 24) e a disciplina de cada posição; a semana-modelo é repetida ao longo do período.
    > - Excluir disciplina remove de todos os cronogramas da trilha apenas os blocos com `data >= hojeLocal` e `concluido == false`; blocos passados (`data < hojeLocal`) ou já concluídos (`concluido == true`) permanecem inalterados como histórico visual usando `disciplinaNomeSnapshot` e `disciplinaCorSnapshot`. Renomear disciplina ou alterar sua cor atualiza o snapshot nos blocos dos cronogramas ativos da trilha, sem trocar `disciplinaId`.
    > - O perfil semanal global de disponibilidade (`disponibilidadeSemanalMinutos`, 7 inteiros de `0` a `1440` para Seg–Dom) é persistido nas preferências do usuário (`usuarios.preferencias`) como referência. Cada cronograma mantém sua própria carga; alterar o perfil não modifica cronogramas existentes.
    > - Mostrar aviso não bloqueante de sobrecarga comparando, dia a dia da semana, a soma de minutos dos cronogramas ativos de todas as trilhas do usuário com o perfil semanal de disponibilidade.

- **SUBSTITUIR** em `§5.4 Persistência e APIs` (linhas 108–111):
  - **Texto original:**
    > - Campos esperados da ocorrência: ID, usuário/trilha/cronograma, data, ordem, disciplina ID e snapshot do nome, duração, estado concluído e timestamps necessários.
    > - Índices devem atender listagem por usuário/trilha/cronograma/data/estado e lookup de ocorrência autorizada.
    > - APIs de Estudos são a fonte única de blocos: endpoint de Hoje para todos os cronogramas ativos do usuário; endpoint paginado de blocos pendentes de um cronograma/trilha; endpoints idempotentes para concluir/reabrir.
    > - Gerar/substituir grandes conjuntos em etapas versionadas (staging → publicação ativa → limpeza da versão antiga) para não expor cronograma parcialmente gerado. Usar gravação em lote e operações idempotentes; não exigir transações multi-documento como pré-requisito.
  - **Texto final:**
    > - Campos obrigatórios da ocorrência em `blocos_cronograma`: `_id` (`ObjectId`), `usuarioId`, `trilhaId`, `cronogramaId`, `versaoGeracao` (`long`), `data` (`yyyy-MM-dd`), `ordem` (`1..24`), `disciplinaId`, `disciplinaNomeSnapshot`, `disciplinaCorSnapshot`, `duracaoMinutos` (`30..240`), `concluido` (`boolean`), `concluidoEm` (`Instant` opcional), `criadoEm` e `atualizadoEm`.
    > - O documento em `cronogramas` mantém `versaoAtiva` (`long`) e `ativo` (`boolean`). A unicidade de no máximo um cronograma ativo por trilha é garantida no MongoDB por índice único parcial em `{ usuarioId: 1, trilhaId: 1 }` com `partialFilterExpression: { ativo: true }`.
    > - Para coordenar criações, regenerações e ativações concorrentes entre os documentos de `trilhas`, `cronogramas` e `blocos_cronograma` sem exigir transação multi-documento, adquirir um lock/lease atômico (`findOneAndUpdate`) no documento da trilha pai (`geracaoCronogramaEmAndamentoAte` por 30s; se já houver operação em curso, retornar `409 Conflict` com `SCHEDULE_GENERATION_IN_PROGRESS`). Na edição de um cronograma pelo lápis, se a nova modalidade escolhida já pertencer a outro cronograma da mesma trilha (`_id != cronogramaId`), rejeitar imediatamente com `409 Conflict` (`DUPLICATE_MODALITY`) antes de alterar o documento ou gerar blocos.
    > - Dentro do lock da trilha, gerar/substituir blocos usa publicação versionada em quatro passos: (1) gravar em lotes (`insertMany`) os blocos da nova geração com `versaoGeracao = novaVersao`; (2) desativar outro cronograma ativo da mesma trilha (`updateMany` com `_id != cronogramaId` e `ativo: false`); (3) atualizar o documento alvo em `cronogramas` definindo `versaoAtiva = novaVersao` e `ativo = true`; (4) remover (`deleteMany`) os blocos daquele `cronogramaId` com `versaoGeracao != novaVersao`. Leituras filtram sempre pela `versaoAtiva` do cronograma.
    > - APIs de Estudos são a fonte única de blocos: endpoint de Hoje (`GET /api/blocos/hoje`) para todos os cronogramas ativos do usuário na data `hojeLocal`; endpoint paginado (`GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos`) para blocos do cronograma/lista virtual; endpoints idempotentes para concluir/reabrir ocorrência individual (`PATCH /api/blocos/{blocoId}/conclusao`) e para concluir atrasados sem sessão (`POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados`).

#### Seção 6 — Sessões e estatísticas de Estudos (`§6.1`, `§6.2` e `§6.3`)

- **SUBSTITUIR** em `§6.1 Fonte de verdade e documento de sessão` (linhas 120–124):
  - **Texto original:**
    > - Campos funcionais: `usuarioId`, trilha ID, disciplina ID, snapshot do nome da trilha/disciplina, data local da sessão, duração em segundos, total de questões, total de acertos e timestamps técnicos.
    > - Erros = total de questões − acertos; percentual = acertos / total. Não persistir valores derivados.
    > - Sessões não precisam registrar horário de estudo nem tipo. `criadoEm` técnico pode ser usado para desempatar/ordenar no mesmo dia, mas não deve ser apresentado como hora de estudo.
    > - Duração mínima para salvar: 60 segundos. Questões/acertos são opcionais; uma sessão pode registrar somente tempo.
    > - Usar chave de idempotência por intenção de salvar, reutilizada em retries, para não duplicar uma sessão depois de timeout.
  - **Texto final:**
    > - Campos persistidos em `sessoes`: `_id` (`ObjectId`), `usuarioId`, `trilhaId`, `disciplinaId`, `trilhaNomeSnapshot`, `disciplinaNomeSnapshot`, `disciplinaCorSnapshot`, `dataSessao` (`LocalDate` / ISO `yyyy-MM-dd`), `duracaoSegundos` (`int`, mínimo `60`, máximo `86400`), `questoes` (`int >= 0`), `acertos` (`int >= 0`, `<= questoes`), `chaveIdempotencia` (`String`, máx. 128 chars), `criadoEm` (`Instant`) e `atualizadoEm` (`Instant`).
    > - Erros (`questoes - acertos`) e percentual de acerto (`acertos / questoes`) são calculados em leitura; não persistir `erros`, `minutos` nem `tipo` (remover `TipoSessao` e `AJUSTE_MANUAL`).
    > - Sessões não registram horário de estudo. `criadoEm` e `_id` são usados exclusivamente para desempate de ordenação técnica no mesmo `dataSessao` (`dataSessao DESC, criadoEm DESC, _id DESC`), nunca exibidos como hora de estudo na UI.
    > - Duração mínima para salvar: `60` segundos (`duracaoSegundos >= 60`). Questões e acertos são opcionais (default `0`).
    > - Exigir cabeçalho `Idempotency-Key` (ou campo `chaveIdempotencia`) por intenção de salvar, reutilizado em retries: se a chave já existir para o mesmo `usuarioId` com o mesmo payload funcional, retornar a sessão existente (`200 OK` / `201 Created`); se o payload divergir para a mesma chave, retornar `409 Conflict` (RFC 9457).

- **SUBSTITUIR** em `§6.2 Gravação, edição e exclusão` (linhas 130–140):
  - **Texto original:**
    > - No cronômetro de Estudos, duração inicial é o foco acumulado real em segundos, excluindo pausas; o alarme de fim não abre modal. Somente **Encerrar e salvar** abre o modal.
    > - No modal aberto por bloco, duração inicial é a duração planejada do bloco, editável.
    > - No registro manual: usar trilha ativa e exigir disciplina; permitir editar duração e data. Se não existir trilha/disciplina selecionada, o fluxo pode criar trilha e disciplina.
    > - Data de sessão é data local, sem horário/fuso. Bloco/timer usa hoje; registro manual permite data selecionável, sem data futura.
    > - Campos de questões: total e acertos opcionais; validar acertos ≤ total. Erros e percentual são derivados.
    > - Editar somente a sessão mais recente da trilha ativa, a qualquer tempo, alterando duração, total de questões e acertos. Atualizar o documento existente; não manter revisões antigas.
    > - Histórico ordenado por data; sem controle de ordenação pelo usuário.
    > - Histórico paginado obrigatoriamente: 20 por página por padrão, máximo 100.
    > - Se a disciplina for renomeada, mostrar o nome atual às sessões existentes enquanto ela existe. Guardar também snapshot para exibição quando a disciplina deixar de existir.
    > - Remover disciplina preserva sessões por padrão. O histórico/estatísticas mostram o grupo de disciplina removida com alerta amarelo; ao abrir, oferecer apagar as sessões e métricas desse grupo com confirmação. Após exclusão, o alerta/grupo desaparece.
  - **Texto final:**
    > - No cronômetro de Estudos, a duração inicial enviada ao modal é o foco acumulado real em segundos (`duracaoSegundos`), excluindo pausas; o alarme sonoro/visual de meta atingida nunca abre o modal nem salva sessão automaticamente. Somente clicar em **Encerrar e salvar** abre o modal.
    > - No modal aberto por conclusão direta de bloco, a duração inicial é `duracaoMinutos * 60` segundos, editável em horas e minutos. Se o bloco foi iniciado no cronômetro (**Estudar este bloco**), prevalece o foco real acumulado em segundos no cronômetro.
    > - No registro manual: usar a trilha ativa e exigir disciplina; permitir editar horas/minutos e `dataSessao`. Se não houver trilha ou disciplina, o próprio modal permite informar o nome da nova trilha e/ou nova disciplina e criá-las antes de gravar a sessão.
    > - `dataSessao` é data de calendário local (`yyyy-MM-dd`). Timer e conclusão de bloco usam `hojeLocal`; registro manual permite selecionar qualquer data `<= hojeLocal` (rejeitar data futura com `400 Bad Request`).
    > - **Edição da sessão mais recente:** apenas a sessão número 1 da trilha na ordenação canônica global `(dataSessao DESC, criadoEm DESC, _id DESC)` pode ser editada (`PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}`), a qualquer tempo, permitindo alterar `duracaoSegundos` (`>= 60`), `questoes` e `acertos`. Tentativa de editar qualquer outra sessão retorna `409 Conflict`.
    > - **Exclusão de sessão:** permitir excluir individualmente uma sessão do histórico mediante confirmação explícita (`DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}`), permitindo corrigir lançamentos em disciplina ou data incorreta sem precisar apagar a disciplina inteira.
    > - Histórico ordenado exclusivamente por `(dataSessao DESC, criadoEm DESC, _id DESC)`, sem seletor de ordenação na UI, com paginação obrigatória (`20` por página por padrão, máximo `100`).
    > - Enquanto a disciplina existir na trilha, o histórico e as estatísticas exibem seu nome e cor atuais; se a disciplina for excluída da trilha, suas sessões são **preservadas por padrão** (sem prompt destrutivo ao excluir a disciplina) e exibidas usando `disciplinaNomeSnapshot` e `disciplinaCorSnapshot`.
    > - No Dashboard e no Histórico, sessões cujos `disciplinaId` não existem mais na trilha aparecem identificadas com badge/alerta amarelo (**"Disciplina removida"**) e ação explícita **"Apagar histórico desta disciplina"** (`DELETE /api/trilhas/{trilhaId}/disciplinas/{disciplinaId}/sessoes`), com modal de confirmação informando a quantidade de sessões e o tempo total que serão removidos.

#### Seção 7 — Tarefas (`§7.2`, `§7.3` e `§7.4`)

- **SUBSTITUIR** em `§7.2 Recorrência` (linhas 173–180):
  - **Texto original:**
    > - Modelar regra/série separadamente das ocorrências. Ocorrências em janela móvel de 90 dias, expandida conforme necessário; não materializar séries infinitas.
    > - Recorrência por calendário, com ocorrências independentes mesmo se anteriores estiverem atrasadas.
    > - MVP: intervalos diários, semanais com dias selecionados e mensais (dia do mês ou ordinal da semana); no mês em que o dia não existe, usar o último dia válido. Não incluir recorrência anual sem requisito posterior.
    > - Série pode ser sem término, terminar numa data ou após quantidade de ocorrências.
    > - Concluir uma ocorrência não altera as seguintes nem registra sessão. Pular uma ocorrência a marca cancelada sem alterar a regra da série.
    > - Ocorrências atrasadas são preservadas individualmente e agrupadas visualmente por série.
    > - Editar/excluir: oferecer “esta ocorrência”, “esta e futuras” e “série toda”; passadas preservadas, salvo exclusão explícita permitida.
    > - Excluir série encerra a regra, envia pendentes/futuras (incluindo atrasadas) à lixeira e preserva ocorrências concluídas. Restaurar uma ocorrência não reativa a série.
  - **Texto final:**
    > - Modelar a regra em coleção própria (`series_recorrentes`) separada das ocorrências materializadas em `tarefas`. Materializar ocorrências em janela móvel de **90 dias** a partir de `hojeLocal` (ou até o fim do intervalo consultado no calendário, limitado a 366 dias), atualizando `geradoAte` na série.
    > - Cada ocorrência em `tarefas` guarda `serieId` (`ObjectId`), `dataOriginalOcorrencia` (`LocalDate`, imutável) e `estado` (`PENDENTE`, `CONCLUIDA`, `CANCELADA`). A idempotência de geração é garantida por índice único parcial `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }`. Assim, se o usuário alterar a `dataVencimento` de uma ocorrência individual, `dataOriginalOcorrencia` permanece intacta e a janela móvel jamais recria aquela data em duplicidade.
    > - Recorrência por calendário fixo (e não a partir da conclusão): intervalos `DIARIO` (a cada $N$ dias), `SEMANAL` (a cada $N$ semanas em dias da semana selecionados) e `MENSAL` (por dia do mês `1..31` ou ordinal da semana, ex.: 1ª segunda-feira / última sexta-feira; se o dia não existir no mês, usar o último dia válido do mês). Não incluir recorrência anual no MVP.
    > - Término da série: `NUNCA`, `EM_DATA` (`dataFim`) ou `APOS_OCORRENCIAS` (`maxOcorrencias`).
    > - Concluir uma ocorrência muda seu `estado` para `CONCLUIDA` sem alterar as demais e sem abrir modal de sessão. Pular uma ocorrência muda seu `estado` para `CANCELADA` (omitida das listas ativas, preservada para impedir regeneração).
    > - Na seção **Atrasadas**, múltiplas ocorrências pendentes atrasadas da mesma `serieId` são agrupadas visualmente sob a ocorrência atrasada mais recente, exibindo um expansor `"+ N ocorrências anteriores atrasadas"`.
    > - Escopos de edição e exclusão:
    >   1. **Apenas esta ocorrência:** edita ou move para a lixeira apenas o documento da ocorrência (marcando `desvinculadaDaSerie = true` se editada).
    >   2. **Esta e futuras:** encerra a série original em `dataOriginalOcorrencia.minusDays(1)`, remove ocorrências pendentes com `dataOriginalOcorrencia >= alvo` e cria uma nova série em `series_recorrentes` a partir dos novos parâmetros; ocorrências passadas e concluídas permanecem intocadas.
    >   3. **Toda a série:** encerra/move a regra em `series_recorrentes` para a lixeira, move todas as ocorrências pendentes (atrasadas, de hoje e futuras) para a lixeira com o mesmo `loteExclusaoId` e preserva ocorrências já concluídas. Restaurar uma ocorrência individual da lixeira não reativa a série encerrada.

- **ADICIONAR** ao final de `§7.3 Lixeiras e listas manuais`:
  - **Texto incluído:**
    > - Se o usuário restaurar uma tarefa individual cuja `listaId` original esteja na lixeira, o sistema restaura a lista pai (apenas o container da lista) ou, caso a lista já tenha sido purgada definitivamente, restaura a tarefa na lista geral (`listaId = null`) informando o usuário.

#### Seção 8 e Seção 9 — Pomodoro, Timers e Visão Compartilhada de Hoje

- **SUBSTITUIR** em `§8 Pomodoro e timers` (linhas 203–206):
  - **Texto original:**
    > - Cronômetro de Estudos e Pomodoro persistem estado local completo em `localStorage`, retomando pelo timestamp absoluto após refresh/fechamento. Não registrar sessão automaticamente pela passagem do tempo.
    > - Em Estudos, há no máximo um timer ativo por conta/dispositivo; iniciar outro pausa o anterior.
    > - Sincronizar estado de timer entre abas do mesmo módulo/conta; separar chaves por usuário. Logout oculta/pausa estado; ao voltar à mesma conta, restaurar. Uma conta nunca vê timers locais de outra.
    > - Se o usuário estiver offline, manter timer/rascunho local e não fingir gravação bem-sucedida; operações de escrita exigem conexão e retry explícito.
  - **Texto final:**
    > - Cronômetro de Estudos e Pomodoro persistem seu estado completo em `localStorage` sob chaves escopadas por usuário (`trilhara:u:{usuarioId}:study_timer` e `trilhara:u:{usuarioId}:pomodoro_timer`), calculando o tempo em execução por diferença de timestamp absoluto (`Date.now() - runningSince` / `targetEndAt - Date.now()`) para suportar refresh, fechamento acidental de aba e *throttling* de segundo plano sem *drift*.
    > - Em Estudos, existe no máximo **um único timer ativo ou pausado por usuário/dispositivo**; iniciar o cronômetro em outra trilha, disciplina ou aba acumula o tempo decorrido e pausa/substitui o anterior após confirmação caso haja tempo não salvo (`>= 60s`).
    > - Sincronizar estado do timer entre abas de mesma origem via eventos `storage` e `BroadcastChannel`. Ao fazer logout (ou expirar sessão), se o timer estiver rodando, acumular o tempo até o instante do logout (`elapsedMs += now - runningSince`), mudar `runningSince = null` (pausado) na chave `trilhara:u:{usuarioId}:*` e limpar os dados de sessão em memória. Ao autenticar novamente com a mesma conta, restaurar o timer pausado; outra conta jamais lê chaves de outro `usuarioId`.
    > - Se o usuário estiver offline, o cronômetro continua funcionando localmente, mas ações de escrita (salvar sessão, concluir bloco/tarefa) exibem erro claro de conectividade mantendo os dados preenchidos no modal para retry explícito.

- **SUBSTITUIR** em `§9 Visão compartilhada de Hoje e integração entre domínios` (linhas 212–214):
  - **Texto original:**
    > - Ordem em seções: atrasadas, hoje, sem data; dentro de cada seção, ordem estável definida pelo app Tarefas.
    > - Atualizar após ações locais e ao focar/retornar à aba; sem WebSocket/polling frequente no MVP.
  - **Texto final:**
    > - Ordem das seções na Visão Hoje (Tarefas e Pomodoro):
    >   1. **Atrasadas** (somente tarefas manuais pendentes com `dataVencimento < hojeLocal`, agrupadas por série quando recorrentes): ordenadas por `dataVencimento ASC, prioridade DESC, ordem ASC`.
    >   2. **Hoje** (blocos pendentes de `hojeLocal` de todos os cronogramas ativos + tarefas manuais pendentes com `dataVencimento == hojeLocal`): blocos ordenados por `trilhaNome ASC, ordem ASC` e tarefas manuais ordenadas por `horario ASC NULLS LAST, prioridade DESC, ordem ASC`.
    >   3. **Sem data** (somente tarefas manuais pendentes com `dataVencimento == null`): ordenadas por `prioridade DESC, ordem ASC`.
    > - Atualizar automaticamente após mutações locais, ao receber eventos `BroadcastChannel`/`storage` na mesma origem e nos eventos `visibilitychange` (quando `document.visibilityState === 'visible'`) e `focus` da janela (com *throttle* mínimo de 5 segundos para evitar rajadas de requisições).

#### Seção 10 — Notas (`§10.1` e `§10.2`)

- **ADICIONAR** ao final de `§10.1 Modelo e importação de trilhas`:
  - **Texto incluído:**
    > - Um caderno pode ficar temporariamente com zero seções ativas (por exemplo, quando sua única seção é movida para a lixeira), exibindo estado vazio orientando criar uma nova seção ou restaurar da lixeira (removendo a trava legada que impedia excluir a última seção).

- **SUBSTITUIR** em `§10.2 Edição, busca, projeção e lixeira` (linhas 234–238):
  - **Texto original:**
    > - Se a API falhar, preservar rascunho local e oferecer retry explícito; estratégia de conflito é última gravação vence.
    > - Coleção de páginas armazena Markdown no documento da página; listagens são paginadas e projetam resumo (ID, seção/caderno, título, ordem, datas). Carregar Markdown ao abrir a página.
    > - Busca global em todos os cadernos/páginas ativos do usuário, incluindo títulos e conteúdo; ignorar caixa e acentos, devolver paginação e snippets. A busca é backend-side, não deve baixar todos os Markdown para filtrar no navegador.
    > - MongoDB self-hosted pode usar índice de texto para o MVP; verificar idioma/acentuação e relevância com conteúdo em português. Se a busca full-text evoluir, avaliar mecanismo dedicado sem acoplar o modelo de notas a ele.
    > - Excluir página/seção/caderno move entidade e descendentes à lixeira por até 30 dias, com confirmação e contagem para exclusões de hierarquia. Restaurar hierarquia no local original; permitir esvaziar antecipadamente. A purga definitiva remove após o prazo.
  - **Texto final:**
    > - Autosave com debounce de `800ms`. A cada alteração ainda não confirmada pelo backend (ou quando o salvamento falhar por rede/401), gravar rascunho de contingência em `localStorage` (`trilhara:u:{usuarioId}:nota_draft:{notaId}`) contendo `{ titulo, conteudoMarkdown, atualizadoEmLocal, baseAtualizadoEmServidor }`. Em caso de erro da API, exibir estado visual `"Erro ao salvar"` com botão **"Tentar novamente"**; ao reabrir uma página que possua rascunho local mais novo que a versão do servidor, exibir barra de recuperação oferecendo **Restaurar rascunho** ou **Descartar**.
    > - Coleção `notas` armazena `conteudoMarkdown` (limite validado de até `512 KB` por página). Listagens de páginas (`GET /api/cadernos/{cadernoId}/secoes/{secaoId}/notas`) são paginadas e projetam exclusivamente metadados (`id`, `cadernoId`, `secaoId`, `titulo`, `ordem`, `topicoOrigemId`, `criadoEm`, `atualizadoEm`), omitindo `conteudoMarkdown` no MongoDB (`Projections.exclude("conteudoMarkdown")`).
    > - Busca global (`GET /api/notas/busca?q=...&pagina=1&tamanho=20`) executa no backend sobre todas as páginas ativas (`deletedAt == null`) do usuário. No MongoDB 7 self-hosted, configurar o **único índice de texto da coleção** como índice composto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` com `default_language: "portuguese"` e pesos `{ titulo: 10, conteudoMarkdown: 1 }` (sem `partialFilterExpression`, pois o MongoDB não permite filtro parcial em índices de texto). O backend extrai um snippet de até 180 caracteres ao redor do termo encontrado e nunca retorna o `conteudoMarkdown` completo nos resultados de busca.
    > - Lixeira hierárquica de 30 dias para página, seção e caderno usando `deletedAt`, `expiresAt` e `loteExclusaoId`:
    >   1. Excluir caderno ou seção exibe modal de confirmação informando a contagem exata de seções e páginas filhas afetadas e marca toda a subárvore com o mesmo `loteExclusaoId`.
    >   2. Restaurar um caderno ou seção restaura os descendentes que receberam o mesmo `loteExclusaoId` (preservando no estado de lixeira itens que já haviam sido excluídos isoladamente antes).
    >   3. Restaurar uma página cuja seção pai (ou caderno pai) também esteja na lixeira restaura automaticamente a seção e o caderno ancestrais; se o ancestral já tiver sido purgado definitivamente após 30 dias, a página é alocada em uma seção/caderno `"Restauradas"` criado automaticamente.

#### Seção 11 — Notificações push de Tarefas

- **SUBSTITUIR** os três últimos itens da `Seção 11` (linhas 247–249):
  - **Texto original:**
    > - Permissão negada ou browser sem suporte não bloqueia tarefas; mostrar estado indisponível.
    > - Push é best-effort: não entregar alerta fora da janela relevante após indisponibilidade.
    > - Requisito técnico: service worker, inscrições Web Push por dispositivo, armazenamento de assinatura, rotina/agendador de entrega, chaves de aplicação e limpeza de inscrições expiradas. Incluir os alertas na política de privacidade.
  - **Texto final:**
    > - Permissão negada ou navegador sem suporte não bloqueia o uso de tarefas; exibir estado informativo claro. Em iOS/iPadOS (Safari 16.4+), onde a Web Push API exige aplicativo instalado na Tela de Início, servir `/manifest.webmanifest` com `"display": "standalone"` e exibir instrução passo a passo ("Adicionar à Tela de Início") quando o usuário tentar ativar notificações em aba comum do Safari.
    > - Push é *best-effort* com **janela útil de entrega de 15 minutos**: a rotina agendadora materializa entregas na coleção `lembretes_push` com `instanteEntregaUtc`. Se o agendador processar um registro com atraso superior a 15 minutos em relação a `instanteEntregaUtc`, marca-o como `EXPIRADO_FORA_DA_JANELA` sem disparar notificação atrasada. Nas chamadas aos provedores Web Push (RFC 8030), enviar cabeçalho `TTL: 900`.
    > - Requisito técnico: `/sw.js` servido na raiz sem cache imutável (`Cache-Control: no-cache, no-store, must-revalidate` no Caddy), autenticação VAPID (RFC 8292) configurada via variáveis de ambiente, coleção `push_subscriptions` por dispositivo e coleção `lembretes_push` com índice em `{ estado: 1, instanteEntregaUtc: 1 }`. Quando o serviço de push responder `410 Gone` ou `404 Not Found`, remover imediatamente a inscrição inválida de `push_subscriptions`.

#### Seção 12 — Autenticação, exclusão e confiabilidade (e nova `§12.1 UX, Acessibilidade e Design System`)

- **SUBSTITUIR** os dois primeiros itens da `Seção 12` (linhas 253–254):
  - **Texto original:**
    > - SSO para os quatro módulos; logout global deve refletir em outras abas/módulos. Reautenticar em sessão expirada preservando formulário/rascunho e retomando com retry idempotente.
    > - Isolar estado local de cada conta. Limpar/ocultar dados sensíveis locais ao logout/troca; timers podem ser restaurados somente para a mesma conta.
  - **Texto final:**
    > - SSO unificado para os quatro módulos, tratando separadamente **Produção** e **Ambiente Local (`localhost` / `*.localhost`)**: em Produção (ex.: `*.trilhara.com.br`), o backend configura o atributo `Domain` compartilhado no cookie `trilhara_rt` (HttpOnly) e emite em conjunto um cookie leve não-HttpOnly `trilhara_session_state` (`Path=/`, mesmo `Domain`). Em Ambiente Local (`localhost` / `*.localhost`), o backend omite o atributo `Domain` (criando cookies *host-only* para não serem rejeitados pelos navegadores sob as regras de Public Suffix/TLD reservado) e a navegação inicial entre subdomínios/portas locais usa `gerarUrlComTokenHandoff`. Ao ganhar foco (`visibilitychange` / `focus`), cada aba verifica `trilhara_session_state` (em produção) e valida a sessão via `GET /api/auth/me` (ou `POST /api/auth/refresh`); se a sessão foi encerrada em outro subdomínio/aba (`401`), pausa timers locais em execução, limpa o estado em memória e sincroniza imediatamente a UI.
    > - Em caso de expiração de sessão (`401 Unauthorized` após tentativa de refresh), abrir o modal de reautenticação (`AuthModal`) sobre a tela atual **sem desmontar a árvore de componentes nem recarregar a página**, preservando formulários abertos, rascunhos de notas e estado de conclusão de bloco para retomar a ação após o login.

- **ADICIONAR** `§12.1 UX, Acessibilidade (WCAG 2.2 AA) e Design System`:
  - **Texto incluído:**
    > - **Tokens e Temas:** Manter como fonte única de estilo os tokens CSS globais em `frontend/src/shared/styles/index.css` para os temas `dark` (*Obsidian Dark UI*) e `light` (*Daylight Editorial UI*), garantindo contraste mínimo WCAG AA (4.5:1 para texto normal, 3:1 para componentes de UI e gráficos) e tipografia numérica tabular (`font-variant-numeric: tabular-nums`) em todos os cronômetros, contadores e tabelas.
    > - **Componentes compartilhados obrigatórios em `frontend/src/shared/design-system` (contratos congelados em F0; implementação junto da fase consumidora):**
    >   1. `AppSwitcher` (implementado em **F1**): seletor rápido entre Estudos, Tarefas, Notas e Pomodoro integrado à `AppSidebar` (desktop) e ao header/navegação (mobile).
    >   2. `GlobalSessionModal` (implementado em **F5**): modal único de registro de sessão e conclusão de bloco.
    >   3. `SectionErrorCard` (implementado em **F7**): bloco de erro recuperável com botão **"Tentar novamente"** para falha parcial na Visão Hoje e em listagens paginadas.
    >   4. `SyncStatusBadge` (implementado em **F8**): indicador visual padronizado para estados `salvo`, `salvando`, `alteracoes-locais` e `erro` (com ação de retry).
    > - **Acessibilidade e Responsividade (WCAG 2.2 Level AA):**
    >   1. Modais (`Modal`, `FormModal`, `ConfirmDialog`) implementam *focus trap*, fechamento por `Escape` (desabilitado enquanto `isSaving == true`) e devolução de foco ao elemento acionador.
    >   2. Cronômetros usam `role="timer"` com `aria-live="off"` nos dígitos que mudam a cada segundo, anunciando apenas mudanças de estado (início, pausa, término de foco/descanso e erros) em região `aria-live="polite"`.
    >   3. Alvos de toque e clique têm dimensão mínima de `24x24px` no desktop (WCAG 2.2 SC 2.5.8) e `40x40px` em telas touch (`@media (pointer: coarse)`).
    >   4. Listas com barra de navegação inferior fixa no mobile aplicam `scroll-padding-bottom` e espaçamento inferior seguro para que o foco de teclado e o último item da lista nunca fiquem encobertos (WCAG 2.2 SC 2.4.11).
    >   5. Respeitar `@media (prefers-reduced-motion: reduce)` desativando animações contínuas de pulso e alternância rápida do título da aba.

#### Seção 13 — Modelo de coleções recomendado

- **SUBSTITUIR** a tabela da `Seção 13`:
  - **Texto original:** Tabela com 13 coleções (sem `series_recorrentes`, `lembretes_push`, `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email` e `editais`, e sem `versaoGeracao` em `blocos_cronograma`).
  - **Texto final:**
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

#### Seção 15 — Sequência de implementação sugerida

- **SUBSTITUIR** a lista numerada da `Seção 15`:
  - **Texto original:** Lista de 9 itens onde o item 2 (Sessões/métricas) antecedia o item 3 (Estudos normalizado).
  - **Texto final:** Lista alinhada exatamente às 12 fases (`F0` a `F11`) do `PLANO-IMPLEMENTACAO-REESTRUTURACAO.md` (descritas na Seção 4 deste complemento).

---

### 2.2 Alterações em `PLANO-IMPLEMENTACAO-REESTRUTURACAO.md`

- **SUBSTITUIR** `Seção 3 — Dependências entre fases`:
  - Reorganização do grafo de `F0–F12` para `F0–F11` (veja o novo diagrama e detalhamento completo na **Seção 4** deste documento).
- **REMOVER / CONSOLIDAR** a antiga separação artificial entre `Fase 4 (Cronogramas)`, `Fase 5 (APIs de bloco e integração virtual em Tarefas)` e `Fase 8 (Visão Hoje compartilhada e Pomodoro)`:
  - **Justificativa:** Na estrutura anterior, a Fase 4 criava a persistência de blocos, a Fase 5 criava os endpoints de blocos em Estudos e mexia na UI de Tarefas usando um "callback provisório" para conclusão de bloco antes que a Fase 6 refatorasse Tarefas e antes que a Fase 7 criasse o modal global de sessão; depois, a Fase 8 mexia novamente em Tarefas para criar a visão Hoje compartilhada. Na nova estrutura:
    - **Fase 4** entrega o domínio completo de Cronogramas e Blocos no backend/Estudos (geradores, staging versionado, disponibilidade semanal e todos os endpoints de blocos de Estudos).
    - **Fase 5** entrega o `GlobalSessionModal`, o serviço de sessão em `frontend/shared` e os timers locais sincronizados (`StudyTimer` e `PomodoroTimer`) isolados por `usuarioId`.
    - **Fase 6** entrega Tarefas manuais, `series_recorrentes` e lixeira.
    - **Fase 7** integra de uma só vez a Visão Hoje compartilhada (`AgendaHojeService`), as listas virtuais de trilha em Tarefas e o espelhamento no Pomodoro, já consumindo as APIs prontas de F4/F6 e o modal real de F5, sem mocks nem retrabalho.
- **SUBSTITUIR** na `Fase 3 (Sessões como fonte única de métricas)`, passo 6 e passo 9:
  - Passo 6: especificar a ordenação canônica `(dataSessao DESC, criadoEm DESC, _id DESC)` para validar a edição da sessão mais recente da trilha e adicionar o endpoint `DELETE /api/trilhas/{trilhaId}/sessoes/{sessaoId}` para exclusão individual com confirmação.
  - Passo 9: corrigir a contradição com os requisitos — ao excluir uma disciplina da trilha, **preservar** as sessões automaticamente por padrão (sem pergunta destrutiva no ato de excluir a disciplina), exibindo-as no Dashboard/Histórico sob o grupo de alerta amarelo com ação explícita para excluir o histórico daquela disciplina removida.
- **ADICIONAR** na `Fase 0` e `Fase 1`:
  - Em `F0`, congelar contratos de erro **RFC 9457** (`Problem Details`), cabeçalho `X-Timezone` e interfaces TypeScript compartilhadas (sem antecipar a implementação visual dos componentes, que são implementados cada um na fase que os consome: `AppSwitcher` em F1, `GlobalSessionModal` em F5, `SectionErrorCard` em F7 e `SyncStatusBadge` em F8).
  - Em `F1`, separar explicitamente a configuração de cookies entre **Produção** (`Domain` compartilhado + `trilhara_session_state`) e **Ambiente Local** (`localhost` / `*.localhost` sem atributo `Domain`, com handoff SSO e validação em `visibilitychange`/`focus`).
- **ADICIONAR** nas `Fases 4, 8, 9, 10 e 11`:
  - Na `Fase 4`, adicionar o lock/lease atômico na trilha (`geracaoCronogramaEmAndamentoAte`) para coordenar criações/regenerações concorrentes entre documentos e a validação `409 Conflict` (`DUPLICATE_MODALITY`) ao tentar mudar a modalidade de um cronograma para outra já existente na trilha.
  - Especificação do índice composto de texto em português e rascunho de contingência em Notas (F8); inventário completo das **18 coleções privadas** (das 19 coleções totais, sendo `editais` a única de catálogo público) na exclusão de conta (F9); entrega tardia e isolada de Push com coleção `lembretes_push`, janela de 15 minutos (`TTL: 900`), ajuste do `Caddyfile` para `/sw.js` e `/manifest.webmanifest` com `display: standalone` para iOS (F10); e auditoria de acessibilidade WCAG 2.2 AA (F11).

---

## 3. Decisões novas e justificativas (com fontes e evidências)

Para transparência metodológica, cada decisão abaixo explicita sua base entre:
- **[Evidência Científica]** — literatura revisada por pares em psicologia cognitiva/educacional;
- **[Padrão Técnico Oficial]** — RFCs da IETF, recomendações W3C e documentação oficial do fabricante (MongoDB, Apple/WebKit);
- **[Prática de Mercado]** — padrões consolidados de produtos de referência (Todoist, Things 3, Notion, OneNote);
- **[Recomendação Arquitetural]** — decisão de engenharia para eliminar bugs e acoplamentos identificados no código atual do repositório.

### 3.1 Prática distribuída, prática de recuperação e flexibilidade de blocos (Estudos e Sessões)

- **Decisão:** Manter a duração dos blocos de cronograma configurável entre 30 e 240 minutos (e o timer do Pomodoro editável), desacoplar o alarme de término do salvamento automático e unificar no modal de sessão o registro de tempo real focado (`duracaoSegundos`) e o desempenho em questões (`questoes`, `acertos`).
- **Fundamentação:**
  - **[Evidência Científica]** Na revisão sistemática de Dunlosky et al. (2013), *Improving Students' Learning With Effective Learning Techniques: Promising Directions from Cognitive and Educational Psychology* (*Psychological Science in the Public Interest*, 14(1), 4–58, [DOI: 10.1177/1529100612453266](https://doi.org/10.1177/1529100612453266)), e na meta-análise de Cepeda et al. (2006), *Distributed practice in verbal recall tasks: A review and quantitative synthesis* (*Psychological Bulletin*, 132(3), 354–380, [DOI: 10.1037/0033-2909.132.3.354](https://doi.org/10.1037/0033-2909.132.3.354)), as duas estratégias com maior utilidade empírica e generalização para retenção de longo prazo são a **prática distribuída** (*distributed/spaced practice*) e a **prática de recuperação/teste** (*practice testing*). A literatura demonstra que a distribuição de sessões ao longo de dias/semanas e a resolução ativa de questões com feedback de acerto/erro superam amplamente a releitura passiva. Ao mesmo tempo, não há evidência neurocientífica de que intervalos fixos de 25 minutos sejam universalmente superiores para qualquer tarefa ou indivíduo: tarefas de leitura densa ou simulados exigem blocos maiores (50–120 min), enquanto tarefas com alta resistência inicial se beneficiam de blocos curtos (25–30 min).
  - **[Recomendação Arquitetural]** Persistir `duracaoSegundos` em vez de arredondar para minutos inteiros preserva a fidelidade do tempo medido pelo cronômetro e evita discrepâncias entre o relógio visto pelo estudante e a soma exibida no Dashboard.

### 3.2 Prevenção do efeito "bola de neve" na Visão Hoje e na regeneração de cronogramas

- **Decisão:** (1) Na visão **Hoje** (Tarefas e Pomodoro), exibir apenas os blocos planejados para `hojeLocal`, mantendo os blocos atrasados exclusivamente na lista virtual da trilha com a ação em lote **"Concluir todas as atrasadas sem salvar"**; (2) agrupar visualmente ocorrências atrasadas de uma mesma série recorrente; (3) permitir regenerar o cronograma a partir de hoje sem afetar sessões e tópicos já concluídos.
- **Fundamentação:**
  - **[Evidência Científica]** Pesquisas sobre a **Falácia do Planejamento** (*Planning Fallacy* — Buehler, Griffin & Ross, 1994, *Exploring the "planning fallacy": Why people underestimate their task completion times*, *Journal of Personality and Social Psychology*, 67(3), 366–381, [DOI: 10.1037/0022-3514.67.3.366](https://doi.org/10.1037/0022-3514.67.3.366)) mostram que estudantes sistematicamente superestimam sua disponibilidade futura ao montar cronogramas. Quando imprevistos ocorrem e dias de estudo são perdidos, sistemas que acumulam automaticamente dezenas de blocos passados na lista de "Hoje" geram sobrecarga visual e acionam o abandono da meta (documentado na literatura de autorregulação como *goal disengagement* / efeito *what-the-hell* — Gollwitzer & Sheeran, 2006, *Implementation Intentions and Goal Achievement: A Meta-Analysis of Effects and Processes*, *Advances in Experimental Social Psychology*, 38, 69–119, [DOI: 10.1016/S0065-2601(06)38002-1](https://doi.org/10.1016/S0065-2601(06)38002-1)).
  - **[Prática de Mercado]** Aplicativos consolidados de produtividade (como *Things 3* e *Todoist*) separam a fila executável do dia de itens recorrentes acumulados ou permitem reagendar/limpar o passivo em uma única ação, mantendo a lista diária acionável.

### 3.3 Limitações e configuração de índices no MongoDB 7 (Text Search, Partial Indexes e TTL)

- **Decisão:**
  1. Usar índice único parcial `{ usuarioId: 1, trilhaId: 1 }` com `partialFilterExpression: { ativo: true }` em `cronogramas`.
  2. Usar índice único parcial `{ usuarioId: 1, serieId: 1, dataOriginalOcorrencia: 1 }` em `tarefas`.
  3. Configurar na coleção `notas` um único índice composto de texto `{ usuarioId: 1, deletedAt: 1, titulo: "text", conteudoMarkdown: "text" }` com `default_language: "portuguese"` e pesos `{ titulo: 10, conteudoMarkdown: 1 }`, **sem** `partialFilterExpression`.
  4. Tratar o índice TTL como limpeza secundária e filtrar sempre `deletedAt == null` (ou `expiresAt > agora` na lixeira) nas queries da aplicação, além de rotina explícita de purga.
- **Fundamentação:**
  - **[Padrão Técnico Oficial — MongoDB 7.0 Documentation]:**
    - [Text Indexes](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-text/) e [Text Index Restrictions](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-text/index-text-restrictions/): Uma coleção pode ter **no máximo um índice de texto**, e o MongoDB **não suporta combinar `partialFilterExpression` com índices de texto** (tentar criar gera erro de comando no servidor). Em compensação, um índice de texto composto cujo prefixo contenha chaves de igualdade (`usuarioId`, `deletedAt`) particiona o índice textual por usuário e por status de lixeira (`deletedAt: null`), garantindo isolamento e performance sem varrer documentos de outros tenants. Além disso, por padrão, índices de texto versão 3+ são *case-insensitive* e *diacritic-insensitive* (tratando `a`, `á`, `ã`, `â` e `c`, `ç` como equivalentes), mas exigem `default_language: "portuguese"` para aplicar *stemming* e *stop words* da língua portuguesa em vez do inglês padrão.
    - [Partial Indexes](https://www.mongodb.com/docs/manual/core/index-partial/): Índices parciais com `unique: true` indexam apenas documentos que satisfazem o predicado (`{ ativo: true }`), sendo o mecanismo oficial do MongoDB para impor que exista no máximo um registro ativo por `(usuarioId, trilhaId)` enquanto permite múltiplos registros com `ativo: false`.
    - [TTL Indexes](https://www.mongodb.com/docs/manual/core/index-ttl/): A thread de background do `mongod` que remove documentos expirados executa uma vez a cada 60 segundos e pode sofrer atraso sob carga; por isso a aplicação deve filtrar `expiresAt > agora` ao listar/restaurar itens da lixeira.

### 3.4 Padronização de erros HTTP (RFC 9457) e `Idempotency-Key` (IETF)

- **Decisão:** Padronizar todas as respostas de erro da API no formato JSON estruturado da **RFC 9457** (`type`, `title`, `status`, `detail`, `instance` e extensão `errors` para violações de campos) e adotar a semântica do draft IETF para `Idempotency-Key` na criação de sessões (`POST /api/trilhas/{trilhaId}/sessoes`).
- **Fundamentação:**
  - **[Padrão Técnico Oficial]:**
    - [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) (que atualiza e substitui a RFC 7807): Já parcialmente adotada nos `ExceptionMapper` atuais do projeto (`detail`, `title`, `status` lidos em `frontend/src/shared/auth/api.ts`), precisando apenas ser formalizada para incluir códigos de erro semânticos de domínio (ex.: `IDEMPOTENCY_KEY_CONFLICT`, `LATEST_SESSION_ONLY`, `SCHEDULE_LIMIT_EXCEEDED`).
    - [IETF `Idempotency-Key` Header Field (`draft-ietf-httpapi-idempotency-key-header`)](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/): Especifica que o cliente gera uma chave única por intenção de operação e a reenvia em retries; o servidor devolve o mesmo resultado em caso de repetição idêntica ou `409 Conflict` / `422 Unprocessable Entity` se a mesma chave for reutilizada com parâmetros diferentes.

### 3.5 Web Push (RFC 8030, RFC 8292) e compatibilidade com iOS/iPadOS Safari

- **Decisão:** Configurar autenticação VAPID (RFC 8292), definir cabeçalho `TTL: 900` (15 minutos) nos envios Web Push, remover assinaturas que retornem HTTP `410 Gone` ou `404 Not Found` (RFC 8030), corrigir o `Caddyfile` para servir `/sw.js` sem cache e adicionar `/manifest.webmanifest` com `"display": "standalone"`.
- **Fundamentação:**
  - **[Padrão Técnico Oficial]:**
    - [RFC 8030 — Generic Event Delivery Using HTTP Push](https://www.rfc-editor.org/rfc/rfc8030.html) (§5.2 `TTL` header e §5.4 remoção de assinatura em `410 Gone` / `404 Not Found`) e [RFC 8292 — Voluntary Application Server Identification (VAPID) for Web Push](https://www.rfc-editor.org/rfc/rfc8292.html).
    - [WebKit Documentation — Web Push for Web Apps on iOS and iPadOS](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/): No iOS/iPadOS (16.4+), a API `PushManager` só é habilitada para Web Apps adicionados à Tela de Início cujo Web App Manifest declare `"display": "standalone"` (ou `"fullscreen"`), e a solicitação de permissão (`Notification.requestPermission()`) deve ocorrer dentro de um evento de gesto direto do usuário (`click`).

### 3.6 Acessibilidade (WCAG 2.2 AA)

- **Decisão:** Incorporar critérios de aceite de acessibilidade nos componentes do Design System e nas views dos 4 módulos.
- **Fundamentação:**
  - **[Padrão Técnico Oficial — W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/):**
    - *SC 2.4.7 Focus Visible* e *SC 2.4.11 Focus Not Obscured (Minimum)*: Garantir que barras fixas (como `mobileBottomNav` e headers sticky) não cubram elementos focados via teclado.
    - *SC 2.5.8 Target Size (Minimum)*: Tamanho mínimo de `24x24` CSS pixels (e `40x40px` em telas de toque) para controles de ação em listas de tarefas, tópicos e blocos.
    - *SC 4.1.3 Status Messages*: Uso de `role="status"` / `aria-live="polite"` para feedbacks de salvamento (`SyncStatusBadge`) e transições de fase do cronômetro, mantendo `aria-live="off"` na contagem regressiva segundo a segundo.

---

## 4. Ajustes necessários em dependências, ordem de fases e critérios de aceite

### 4.1 Nova ordem de fases e grafo de dependências (`F0` a `F11`)

```text
F0 Contratos, RFC 9457, Design System/UX e baseline de testes
 └─ F1 Persistência BSON, codecs, ObjectId/usuarioId, X-Timezone, SSO multi-subdomínio e índices
     ├─ F2 Estudos: trilhas, disciplinas, tópicos e progresso normalizados
     │   ├─ F3 Estudos: sessões (duracaoSegundos), edição/exclusão e estatísticas agregadas
     │   └─ F4 Estudos: cronogramas, geradores, staging versionado e APIs de blocos
     │       └─ F5 Frontend Shared: GlobalSessionModal, serviço de sessão e timers sincronizados (depende de F3 e F4)
     ├─ F6 Tarefas: tarefas manuais, séries recorrentes (janela 90d) e lixeira 30d
     │   └─ F7 Integração Hoje compartilhada (AgendaHojeService), listas virtuais de trilha e Pomodoro (depende de F4, F5 e F6)
     └─ F8 Notas: coleções separadas, busca PT-BR no backend, importação/sync de trilha, autosave com rascunho local e lixeira (depende de F1 e F2)

F9  Exclusão de conta em cascata completa (18 coleções privadas) e consistência multi-aba/subdomínio (depende de F2..F8)
F10 Push notifications de tarefas (tardia e isolada): Service Worker, PWA manifest, VAPID, lembretes_push e janela de 15 min (depende de F6 e F9)
F11 Hardening, acessibilidade WCAG 2.2 AA, benchmark explain("executionStats"), observabilidade e limpeza de legado
```

### 4.2 Resumo das mudanças estruturais entre as fases

| Fase Nova | Fase Antiga | Mudança aplicada e justificativa |
|---|---|---|
| **F0** | F0 | Congela os contratos de erro RFC 9457, o cabeçalho `X-Timezone` e as interfaces TypeScript dos contratos compartilhados, deixando a implementação visual de cada componente compartilhado para a fase que efetivamente o consome. |
| **F1** | F1 | Implementa codecs diretos de `LocalDate` (`yyyy-MM-dd`)/`Instant`/`ObjectId`, remove o fallback `"local"`, inicializa os índices das 19 coleções, separa o comportamento de cookies entre Produção (`Domain` compartilhado) e Ambiente Local (`localhost`/`*.localhost` sem `Domain`) e implementa o `AppSwitcher`. |
| **F2** | F2 | Alinhada a ordem em ambos os documentos (F2 antes de F3) e definidos os endpoints granulares de disciplinas/tópicos e contadores do resumo no Hub. |
| **F3** | F3 | Padronizado `duracaoSegundos` (`60..86400`), definida a regra determinística `(dataSessao DESC, criadoEm DESC, _id DESC)` para editar a sessão mais recente, adicionada exclusão individual de sessão com confirmação e corrigida a preservação padrão de sessões ao excluir disciplina. |
| **F4** | F4 + parte backend de F5 | Consolida na Fase 4 o lock atômico na trilha (`geracaoCronogramaEmAndamentoAte`) para coordenar gerações/ativações concorrentes, a validação `409 DUPLICATE_MODALITY` na mudança de modalidade, o campo `versaoGeracao`, o índice único parcial de cronograma ativo, o limite de 366 dias e todos os endpoints de blocos de Estudos. |
| **F5** | F7 | Implementa o `GlobalSessionModal`, o serviço único `sessaoGlobalService` com `Idempotency-Key` e conclusão de bloco em duas etapas, além dos timers de Estudos e Pomodoro persistidos em `trilhara:u:{usuarioId}:*`. |
| **F6** | F6 | Adicionada a coleção `series_recorrentes`, o campo imutável `dataOriginalOcorrencia` para idempotência da janela móvel de 90 dias, o *split* de série em "Esta e futuras" e a regra de restauração de tarefa órfã na lixeira. |
| **F7** | Parte frontend de F5 + F8 | Implementa o `SectionErrorCard` e unifica a integração da Visão Hoje (`AgendaHojeService`), as listas virtuais de trilha em Tarefas e o espelhamento no Pomodoro, usando F4, F5 e F6 já prontos, sem callbacks provisórios. |
| **F8** | F9 | Implementa o `SyncStatusBadge`, o índice de texto composto em português sem `partialFilterExpression`, extração de snippets no backend, rascunho de contingência em `localStorage` no autosave e restauração hierárquica com `loteExclusaoId`. |
| **F9** | F10 | Amplia o inventário executável de exclusão de conta para cobrir nominalmente todas as **18 coleções privadas** (das 19 coleções totais, excetuando apenas `editais`), além da limpeza das chaves `trilhara:u:{usuarioId}:*`. |
| **F10** | F11 | Mantida como entrega tardia e 100% isolada: especifica a coleção `lembretes_push`, a janela de 15 minutos (`TTL: 900`), remoção automática em `410 Gone` / `404 Not Found`, ajuste do `Caddyfile` para `/sw.js` e `/manifest.webmanifest` (`display: standalone`) para suporte ao iOS/iPadOS. |
| **F11** | F12 | Inclui verificação dos critérios de acessibilidade WCAG 2.2 AA, testes de foco/teclado/responsividade e remoção de classes/rotas legadas (`AjusteQuestoes`, `TipoSessao`, `PlanejamentoCicloRepository`). |

### 4.3 Critérios de aceite atualizados e adicionados

Além dos critérios originais da Seção 16 de `REQUISITOS-REESTRUTURACAO-DADOS.md`, passam a vigorar os seguintes critérios verificáveis:

1. **Precisão de tempo e fuso horário (`X-Timezone`):**
   - Operações realizadas entre 21h00 e 23h59 no fuso `America/Sao_Paulo` (quando o relógio UTC já virou para o dia seguinte) utilizam a data local correta do navegador enviado em `X-Timezone` tanto na visão Hoje quanto no cálculo de *streak* e validação de data de sessão.
   - Uma sessão salva a partir do cronômetro com `95` segundos persiste `duracaoSegundos = 95` e soma exatamente `95` segundos nas agregações da disciplina e da trilha.
2. **Sessão mais recente e correção de erros:**
   - O endpoint `PATCH /api/trilhas/{trilhaId}/sessoes/{sessaoId}` permite editar apenas a primeira sessão retornada por `(dataSessao DESC, criadoEm DESC, _id DESC)` e rejeita com `409 Conflict` qualquer outra sessão.
   - O usuário consegue excluir individualmente uma sessão lançada por engano mediante confirmação, recalculando imediatamente as estatísticas.
3. **Concorrência, mudança de modalidade e limites em cronogramas:**
   - Duas requisições simultâneas de criação/regeneração/ativação de cronograma na mesma trilha são coordenadas pelo lock atômico na trilha (`geracaoCronogramaEmAndamentoAte`, retornando `409 SCHEDULE_GENERATION_IN_PROGRESS` para a requisição concorrente) e pelo índice único parcial `{ ativo: true }`, nunca resultando em dois documentos com `ativo: true`.
   - Editar um cronograma tentando mudar sua modalidade para outra já ocupada na mesma trilha retorna `409 DUPLICATE_MODALITY` antes de gerar blocos.
   - Geração de cronograma com período superior a 366 dias é limitada a 366 dias (máximo 8.784 blocos) e concluída sem expor estado intermediário.
4. **Idempotência de recorrência após reagendamento:**
   - Alterar a `dataVencimento` de uma ocorrência específica de tarefa recorrente mantém seu `dataOriginalOcorrencia`; recarregar a lista ou avançar a janela móvel de 90 dias não recria a ocorrência na data original.
5. **Isolamento multi-conta e sincronização entre subdomínios (Produção e `*.localhost`):**
   - Em ambiente local (`localhost` / `*.localhost`), `Set-Cookie` nunca envia atributo `Domain` (evitando rejeição pelo navegador), e em produção envia `Domain` compartilhado + `trilhara_session_state`.
   - Fazer logout em `estudos.localhost` (ou `estudos.trilhara.com.br`) pausa o timer em execução salvando o tempo acumulado em `trilhara:u:{usuarioId}:*` e, ao focar uma aba aberta em `tarefas.*`, `notas.*` ou `pomodoro.*`, a aba valida a sessão (`trilhara_session_state` / `GET /api/auth/me`) e encerra a sessão local sem vazar dados para outro usuário.
6. **Resiliência de autosave em Notas:**
   - Se a API de Notas falhar durante a edição, o texto permanece salvo em `trilhara:u:{usuarioId}:nota_draft:{notaId}`, o indicador mostra `"Erro ao salvar"` com botão de retry e recarregar a página oferece restaurar o rascunho local.
7. **Acessibilidade e responsividade (WCAG 2.2 AA):**
   - Navegação completa por teclado (`Tab`, `Shift+Tab`, `Enter`, `Escape`) em todos os modais com *focus trap*; barra inferior mobile não encobre o último item de listas nem o foco de teclado; controles de ação possuem área mínima de `24x24px` (desktop) e `40x40px` (touch).

---

## 5. Checklist histórico da revisão original (não constitui aceite atual)

- [x] **Fontes de verdade únicas:** Nenhum bloco de estudo é persistido na coleção `tarefas`; nenhum contador de tempo ou questões é duplicado na coleção `disciplinas`; nenhuma sessão negativa (`AJUSTE_MANUAL`) é criada.
- [x] **Modelo de coleções completo (19 coleções = 18 privadas + 1 de catálogo):** Todas as 18 coleções privadas de negócio, autenticação (`usuarios`, `refresh_tokens`, `tokens_recuperacao_senha`, `tokens_confirmacao_email`) e notificações (`push_subscriptions`, `lembretes_push`) mais a coleção pública `editais` estão mapeadas com seus respectivos índices, e as 18 coleções privadas estão nominalmente incluídas na rotina de exclusão de conta (Fase 9).
- [x] **Compatibilidade com MongoDB 7 Standalone/Self-Hosted:** Nenhuma operação depende de transações multi-documento ou de recursos exclusivos do MongoDB Atlas (como Atlas Search ou múltiplos índices de texto por coleção).
- [x] **Tipos BSON e fuso horário sem ambiguidade:** `ObjectId` para IDs de documentos e `usuarioId`; `BSON Date` (`Instant`) para timestamps técnicos; string ISO `yyyy-MM-dd` (`LocalDate`) para datas de calendário; `X-Timezone` para resolução determinística de `hojeLocal`.
- [ ] **Alinhamento entre Requisitos, Plano e Complemento:** revisar nos documentos normativos; esta caixa histórica não atesta consistência atual.
