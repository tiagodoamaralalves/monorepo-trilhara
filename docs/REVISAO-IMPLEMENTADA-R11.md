# R11 — Revisão Implementada (F10: Web Push de Tarefas)

> Relatório da revisão **R11**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R10.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-13 · backend Java 25/Quarkus 3.40.1 + frontend React 18/Vite 6.

---

## 0. Bloqueio de ambiente — resolvido (não era falha da revisão)

**Diagnóstico corrigido em 2026-11-13.** O registro anterior afirmava que `mongod` não estava
instalado e que não havia container alcançável. Ambas as afirmações estavam erradas: o
`trilhara_mongo` estava `Up (healthy)` o tempo todo, respondendo `{ok:1}`. O que falhava era a
*descoberta* do daemon, por dois motivos encadeados:

1. **Dois daemons Docker no WSL.** Os containers do compose vivem no `dockerd` do **snap**
   (`/snap/bin/docker`). O `docker` do `PATH`, porém, é o `docker.exe` do Docker Desktop
   (interop), que responde `could not be found in this WSL 2 distro`. O `env-gate.sh` usava
   `command -v docker` e, com `2>/dev/null`, engolia o erro — concluindo "MongoDB inalcançável".
2. **IP obsoleto.** O container está em `172.25.0.3` (rede
   `monorepo-trilhara_internal_net`), não em `172.22.0.2`. O `%test` de
   `application.properties` e os relatos anteriores citavam IPs de stacks já derrubadas
   (`172.22.0.2`, `172.25.0.2` → `ip neigh` = `FAILED` / `no route to host`).

**Correções aplicadas:**
- `scripts/env-gate.sh`: novo `resolver_docker()` valida o cliente com `docker info` antes de
  usá-lo (ordem: `$DOCKER_BIN` → `/snap/bin/docker` → `/usr/bin/docker` → `command -v docker`).
  O `inspect` agora testa **cada** IP do container e usa o primeiro que aceita conexão na 27017,
  em vez de `awk '{print $1}'`. Erros de descoberta passam a ser impressos, não silenciados.
- `scripts/mongo/reset-dev.sh`: mesma resolução de binário no `executar_mongosh` (o fallback
  anterior também caía no `docker.exe` e falhava).

**Desbloqueio confirmado:** `@QuarkusTest` conecta em `172.25.0.3:27017`, o
`MongoIndexInitializer` reporta *"Todos os índices canônicos das 20 coleções estão presentes e
compatíveis"* e o `verify` completo executa os 26 arquivos `@QuarkusTest` da suíte.

### Baseline e correções desta execução

Baseline capturado na árvore pós-R11, antes de qualquer mudança de R12/R13:
**342 testes, 3 falhas, 1 erro, 7 skipped**. As 4 falhas eram todas pré-existentes e
independentes do ambiente — o Mongo apenas as tornou visíveis.

| # | Teste | Causa raiz | Correção |
|---|---|---|---|
| 1 | `LembretePushLifecycleTest.ocorrenciaRecorrenteHerdaModoDaSerie` | Fixture usava `"DIARIA"` (contrato é `DIARIO` — `FrequenciaRecorrencia` no frontend confirma) **e** passava `EM_DATA` com `dataFim=null`, que o validador rejeita | `DIARIO` + `dataFim` coerente (`amanha()+30d`) |
| 2 | `PushDeliverySchedulerTest.falhaTransitoriaRespeitaJanela` | `Instant.now()` tem precisão de nanossegundos; o BSON trunca para milissegundos, então o round-trip não era idempotente | `Instant.now().truncatedTo(ChronoUnit.MILLIS)` |
| 3 | `AuthorizationByOwnerTest.auditarPush` | Teste usava `?endpoint=` (query), mas o contrato de `DELETE /api/push/subscriptions` recebe o endpoint no **corpo** (`RemoverInscricaoPushRequest`) → `400` em vez de `404` | `body(Map.of("endpoint", ...))` |
| 4 | `TenantIsolationArchTest.repositoriosDePushExigemUsuarioId` | 4 violações reais de D-06(a) no código de produção | ver abaixo |

**Item 4 — a única falha de produção.** `PushSubscriptionRepository` tinha três métodos sem
`usuarioId` como primeiro parâmetro. `buscarPorEndpoint` era **vazamento de tenant de fato**
(consulta só por `endpoint`, que é único globalmente) — passou a exigir `usuarioId` e a usar
`and(eq("usuarioId"), eq("endpoint"))`. Os outros dois são legítimos e foram isentados com
justificativa documentada:
- `inserirNovaVersao` / `salvarOuAtualizar` recebem a **entidade inteira**, que já carrega
  `usuarioId` — mesma semântica de `inserir`/`atualizar`, então entraram em
  `EXCECOES_POR_ENTIDADE`. Ambos existem **apenas** no pacote `push`, logo não enfraquecem as
  regras de outros domínios.
- `removerEndpointInvalido` é chamada **só** por `PushDeliveryScheduler` (`@SystemScope`); uma
  inscrição morta (404/410) precisa sair da coleção mesmo que o dispositivo tenha sido
  reatribuído a outra conta. Isenta por `EXCECOES_SISTEMA_PUSH`, no mesmo padrão de
  `EXCECOES_PURGA_TAREFA`/`EXCECOES_PURGA_NOTA`.

- **Executado e verde (sem banco):** `LembretePushCalculatorTest` (5),
  `PushPayloadFactoryTest` (6), `WebPushCriptografiaTest` (4),
  `LembretePushFilaFiltroTest` (3) → **18 testes**.
- **Compilação de toda a suíte de testes:** `./mvnw -o clean test-compile` → **OK**.
- **Executado e verde (com Mongo):** `LembretePushLifecycleTest` (9),
  `PushDeliverySchedulerTest` (7), `PushResourceTest` (6) → **22 testes**.
- **Consequência:** o bloqueio da R11 **está removido**. O comando de verificação é
  `bash scripts/env-gate.sh --exec -- bash -c 'cd backend && ./mvnw -q verify'`.


### Gate comum

| Etapa | Resultado |
|---|---|
| `frontend`: `npm run typecheck` | **verde** |
| `frontend`: `npm run lint -- --max-warnings 0` | **verde** (0 erros, 0 warnings) |
| `frontend`: `npm test -- --run` | **verde** — 52 arquivos de teste |
| `frontend`: `npm run build` | **verde** |
| `frontend`: `npx knip` | **verde** (sem saída) |
| raiz: `bash scripts/verificar-legado.sh` | **14 → 11 achados** (ver §5) |
| `backend`: `./mvnw -q verify` | **verde** — 342 testes, 0 falhas, 0 erros (baseline corrigido; ver §0) |


---

## 1. Achados confirmados e descartados

### 1.1 Descartados com evidência (já conformes)

| Hipótese do guia | Veredito | Evidência |
|---|---|---|
| `horarioDisparoUtc` / `usaPadraoGlobalLembrete` presentes (RI-37) | **Descartado** | `LembretePush` já usava `instanteEntregaUtc`; `ModoLembrete{HERDAR,SEM_ALERTA,PERSONALIZADO}` desde a R7; `rg` → 0 em `backend`/`frontend` |
| TTL `expiraEm` em `lembretes_push` | **Descartado** | `IndicesCanonicos` declara apenas `uk_(tarefaId,versaoAgendamento)`, `idx_(estado,instanteEntregaUtc)` e `idx_(usuarioId,tarefaId)`; nenhum `expireAfter` na coleção |
| Scheduler a cada 1 min | **Descartado** | `application.properties` → `trilhara.push.scheduler.interval=30s` |
| `PATCH /api/push/preferencias` | **Descartado** | `PushResource` já expunha `@PUT`; frontend já usava `PUT`; `rg "PATCH.*preferencias"` → 0 |
| `sw.js` sem `Cache-Control` | **Descartado** | `Caddyfile` já tinha `@sw_script` com `no-cache, no-store, must-revalidate` |
| Manifest PWA ausente | **Descartado** | `public/manifest.webmanifest` com `"display":"standalone"`, vinculado em `index.html` |
| `TTL: 900` / `404`/`410` remove inscrição | **Descartado** (comportamento) | `DefaultWebPushSender` enviava `TTL`/`Urgency`; scheduler removia `404`/`410` |
| `upsert` não reatribui `endpoint` | **Parcialmente descartado** | `updateOne(eq("endpoint"))` já gravava o `usuarioId` do chamador — porém o `201` devolvia um `_id` **não persistido** |

### 1.2 Confirmados e corrigidos

| # | Achado | Evidência antes | Correção aplicada |
|---|---|---|---|
| **C1** | `lembretes_push` **não versionado**: upsert por `(usuarioId, tarefaId)` sobrescrevia a linha | `LembretePushRepository.salvarOuAtualizar` com `updateOne(..., upsert)` | **Uma linha por versão**: `inserirNovaVersao` (idempotente pelo índice único `(tarefaId, versaoAgendamento)`), `buscarVersaoVigente`, `cancelarPendentesAnteriores` |
| **C2** | `versaoAgendamento` era `int` | `LembretePush:10` | `long` (D-36a) |
| **C3** | Campos fora do contrato (`minutosAntes`, `fusoAgendamento`, `tituloTarefa`, `proximaTentativaEm`, `ultimoErro`) e nomes de lease divergentes | `LembretePush` | Record reduzido a `{_id, usuarioId, tarefaId, versaoAgendamento, instanteEntregaUtc, estado, tentativas, leaseToken, leaseAte, criadoEm, atualizadoEm}` |
| **C4** | Backoff **reescrevia `instanteEntregaUtc`**, deslocando a janela de 15 min | `remarcarFalhaTemporaria:148` | O agendamento é imutável; o backoff é derivado de `atualizadoEm + min(2^n s, 900)` no filtro da fila |
| **C5** | Backoff com teto fixo de 120 s | `Math.min(Math.pow(2,n), 120)` | Teto = `BACKOFF_MAXIMO_SEGUNDOS` (900 = janela útil), com primeira tentativa imediata (`tentativas == 0`) |
| **C6** | Elegibilidade incompleta: não exigia `estado = PENDENTE` nem `instanteEntregaUtc > agora`; tarefa **pulada** (`estado=CANCELADA`) voltava a agendar ao ser editada | `deveCancelarLembrete` só olhava `concluida`/`CONCLUIDA` | Regra completa em `calcularInstanteElegivel` conforme D-36a; teste `semAlertaNaoAgenda` |
| **C7** | Sem idempotência: toda edição criava versão nova | `int versao = existente.versao + 1` incondicional | Se a versão vigente está `PENDENTE` e o instante calculado é igual → **nenhuma** versão nova |
| **C8** | Ocorrências materializadas nunca geravam lembrete | `RecorrenciaEngine` não conhecia `LembretePushService` | `RecorrenciaEngine` recebe o serviço e agenda após inserir a ocorrência; teste `ocorrenciaRecorrenteHerdaModoDaSerie` |
| **C9** | Payload **em claro** | `Content-Type: application/json` com JSON puro | **RFC 8291 / `aes128gcm`** em `WebPushCriptografia` (ECDH P-256 + HKDF-SHA256 + AES-128-GCM); header `Content-Encoding: aes128gcm` |
| **C10** | Payload com `mensagem`, título sem truncar e `url` relativa | `montarPayloadJson` | `PushPayloadFactory`: `{lembreteId, tarefaId, titulo≤80, url absoluta}`; `trilhara.app.tarefas-url` + allowlist |
| **C11** | Payload serializado com `ObjectMapper` (gate `serializacao-json`) | `objectMapper.writeValueAsString` | Serialização manual com escaper próprio; achado saiu de 4 → 3 |
| **C12** | Scheduler varria multi-tenant **sem `@SystemScope`** | `PushDeliveryScheduler` sem anotação | `@SystemScope` + varredura movida para `LembretePushFilaRepository` (`@SystemScope`) |
| **C13** | Nenhuma regra ArchUnit para `..push..` | `TenantIsolationArchTest` sem regra de push | `repositoriosDePushExigemUsuarioId` e `schedulerDePushExigeSystemScope` |
| **C14** | `PUT /preferencias` aceitava qualquer inteiro | só `@NotNull` | Conjunto fechado `{0,5,15,30,60}` → `400 VALIDATION_ERROR` com `errors.padraoMinutosAntes` |
| **C15** | `POST /subscriptions` não validava HTTPS nem chaves | teste antigo usava `"auth_secret_test"` | `endpoint` HTTPS + `p256dh` 65 bytes (`0x04`) + `auth` 16 bytes, base64url |
| **C16** | `DELETE /subscriptions` aceitava query param **e** body | `@QueryParam endpoint` | Somente body `{ endpoint }`; sem endpoint → `400` |
| **C17** | `GET /config` marcava `habilitado` só com a public key | `pushHabilitado && pubKey != null` | Exige **par** VAPID (pública + privada) |
| **C18** | SW registrado dentro de `ativarNotificacoes` e **sem `scope`** | `serviceWorker.register('/sw.js')` | `registrarServiceWorker()` com `{ scope: '/' }` e reuso via `getRegistration('/')`; registrado **somente** em `TarefasApp` |
| **C19** | Dedupe em `Set` com TTL 60 s; `notificationclick` navegava cross-origin | `sw.js` | Dedupe LRU de **100** `lembreteId`; clique restrito à mesma origem; `navigate` tolerante a `undefined` |
| **C20** | `ConfiguracaoLembreteModal` sem estado `ios-nao-instalado` e sem textos do Anexo B | ternário de 4 estados | 5 estados com os textos literais do Anexo B |
| **C21** | Logout não removia a inscrição (SEC-12) | `AuthService.logout` só revogava a sessão | `pushService.removerInscricaoDoDispositivo()` antes de revogar, best-effort com timeout de 2 s |
| **C22** | Deep link `?tarefaId=` inexistente | `TarefasApp` só lia `pagina`/`auth` | Efeito de boot: abre `TaskModal`; `404`/`400` → toast "Tarefa não encontrada ou excluída" + limpeza do parâmetro |
| **C23** | Exclusão por escopo de série não cancelava lembretes em lote | só o caminho de tarefa avulsa | `listarIdsPorSerie` + `cancelarLembretesDaSerie` em `ESTA_E_FUTURAS` e `TODA_A_SERIE` |
| **C24** | `ZoneId.of("America/Sao_Paulo")` literal no calculator | `LembretePushCalculator:37` | `TimezoneContext.DEFAULT_ZONE`; achado de gate → **0** |
| **C25** | Testes de lifecycle com datas fixas no passado | `2026-06-15` | Reescritos com data relativa (`amanha()` em `America/Sao_Paulo`) |

### 1.3 Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | Backoff baseado em `instanteEntregaUtc` tornaria a retentativa **imediatamente** elegível (o instante já está no passado) | primeira versão do filtro `$expr` | Base passou a ser `atualizadoEm` (com `$ifNull` de fallback); primeira tentativa não espera backoff |
| **N-2** | Lembrete com janela fechada ficaria **órfão** em `PENDENTE` | filtro só considerava `instanteEntregaUtc <= agora` | Ramo próprio `janelaFechada`; o scheduler o visita e marca `EXPIRADO_FORA_DA_JANELA` |
| **N-3** | Sem inscrições, o lembrete era marcado `ENVIADO` | condição invertida em `estadoFinal` | `FALHA_FINAL` explícito; `ENVIADO` só com entrega efetiva |
| **N-4** | Título denormalizado entregava título velho | `tituloTarefa` no documento | Scheduler lê a tarefa no envio; tarefa removida → `CANCELADO` |
| **N-5** | `salvarOuAtualizar` devolvia `void` e o resource inventava o `_id` | `PushResource:82-100` | `findOneAndUpdate` com `ReturnDocument.AFTER` devolve o `_id` persistido |
| **N-6** | `RecorrenciaEngine` instanciado em testes com 2 argumentos | `RecorrenciaEngineTest` | Construtor de 2 argumentos preservado; novo parâmetro é opcional |
| **N-7** | Payload cifrado exigia chaves EC reais nos testes | inscrições de teste usavam strings | Helpers geram par P-256 real; o round-trip decifra o corpo |

---

## 2. Contratos congelados (D-36)

### 2.1 `lembretes_push` (D-36a)

```
{ _id, usuarioId, tarefaId, versaoAgendamento: long, instanteEntregaUtc,
  estado, tentativas, leaseToken?, leaseAte?, criadoEm, atualizadoEm }
```

Índices (já canônicos desde a R2, sem TTL):

| Índice | Chaves |
|---|---|
| `uk_lembretes_push_tarefaId_versaoAgendamento` (único) | `{ tarefaId: 1, versaoAgendamento: 1 }` |
| `idx_lembretes_push_estado_instanteEntregaUtc` | `{ estado: 1, instanteEntregaUtc: 1 }` |
| `idx_lembretes_push_usuarioId_tarefaId` | `{ usuarioId: 1, tarefaId: 1 }` |

```
elegível = estado == PENDENTE ∧ deletedAt == null ∧ dataVencimento != null
           ∧ horario != null ∧ modoLembrete != SEM_ALERTA ∧ instanteEntregaUtc > agora
```

- Mudança relevante (ou volta a ser elegível) → `versaoAgendamento + 1`, `PENDENTE`
  anteriores → `CANCELADO`, nova versão `PENDENTE`.
- Nada mudou (mesma versão `PENDENTE`, mesmo instante) → **nenhuma** versão nova.
- Deixou de ser elegível → todas as `PENDENTE` → `CANCELADO`; a suspensão **preserva**
  `modoLembrete`/`lembreteMinutosAntes` na tarefa.

### 2.2 Rotas

| Método | Rota | Contrato |
|---|---|---|
| GET | `/api/push/config` | `{ habilitado, publicKey?, padraoMinutosAntes }`; `habilitado` só com o **par** VAPID |
| POST | `/api/push/subscriptions` | body `{ endpoint, keys:{p256dh,auth}, userAgent? }`; `201` com o `_id` **persistido**; `endpoint` existente é **reatribuído** ao usuário atual |
| DELETE | `/api/push/subscriptions` | body `{ endpoint }`; `200 {removido:true}` / `404` (alheio ou inexistente) / `400` (sem endpoint) |
| PUT | `/api/push/preferencias` | body `{ padraoMinutosAntes ∈ {0,5,15,30,60} }`; recalcula **somente** `HERDAR` |

### 2.3 Scheduler (30 s, lease 60 s)

- Claim atômico `PENDENTE → PROCESSANDO` com `leaseToken` (UUID por ciclo) e `leaseAte = agora + 60 s`.
- **Somente o detentor do lease conclui** (filtro por `leaseToken`).
- `atraso > 15 min` → `EXPIRADO_FORA_DA_JANELA` **sem envio**.
- Falha transitória → `PENDENTE` com `tentativas + 1`; backoff `min(2^n s, restante da janela)`
  derivado de `atualizadoEm`; `instanteEntregaUtc` **nunca** é reescrito.
- Janela esgotada → `FALHA_FINAL`. `404`/`410` → remove a inscrição e continua.
- Envio: `TTL: 900`, `Urgency: normal`, `Content-Encoding: aes128gcm`.

### 2.4 Payload (D-36d)

```json
{ "lembreteId": "<hex24>", "tarefaId": "<hex24>",
  "titulo": "≤ 80 code points", "url": "https://<app-tarefas>/?tarefaId=<hex24>" }
```

- Cifrado por RFC 8291 (`aes128gcm`): `salt(16) ‖ rs=4096 ‖ idlen=65 ‖ as_public(65) ‖ AES-128-GCM(payload ‖ 0x02)`.
- `url` montada de `trilhara.app.tarefas-url` e validada contra
  `trilhara.app.tarefas-url-allowlist`; URL fora da allowlist **falha no boot**.

---

## 3. Arquivos alterados

**Criados**

- `backend/src/main/java/br/com/trilhara/push/LembretePushFilaRepository.java`
- `backend/src/main/java/br/com/trilhara/push/PushPayloadFactory.java`
- `backend/src/main/java/br/com/trilhara/push/WebPushCriptografia.java`
- `backend/src/main/java/br/com/trilhara/push/dto/RemoverInscricaoPushRequest.java`
- `backend/src/test/java/br/com/trilhara/push/PushPayloadFactoryTest.java`
- `backend/src/test/java/br/com/trilhara/push/WebPushCriptografiaTest.java`
- `backend/src/test/java/br/com/trilhara/push/LembretePushFilaFiltroTest.java`
- `frontend/src/test/pushService.test.ts`
- `frontend/src/test/sw.test.ts`
- `frontend/src/test/logoutPush.test.ts`
- `frontend/src/test/deepLinkTarefa.test.tsx`
- `frontend/src/test/manifestPwa.test.ts`

**Alterados**

- `push/`: `LembretePush`, `LembretePushRepository`, `LembretePushService`,
  `LembretePushCalculator`, `PushDeliveryScheduler`, `PushResource`,
  `PushSubscriptionRepository`, `WebPushSender`, `DefaultWebPushSender`,
  `dto/AtualizarPreferenciasPushRequest`
- `tarefa/`: `TarefaRepository` (`listarIdsPorSerie`), `TarefaService` (fuso materializado e
  cancelamento em lote por série), `RecorrenciaEngine` (agenda lembrete da ocorrência)
- `shared/mongo/IndicesCanonicos` (comentário canônico), `application.properties`, `.env.example`
- `arquitetura/TenantIsolationArchTest` (+2 regras)
- Frontend: `public/sw.js`, `shared/services/pushService.ts`, `shared/auth/authService.ts`,
  `modules/tarefas/TarefasApp.tsx`,
  `modules/tarefas/components/ConfiguracaoLembreteModal/ConfiguracaoLembreteModal.tsx`
- Testes backend: `LembretePushCalculatorTest`, `LembretePushLifecycleTest`,
  `PushDeliverySchedulerTest`, `PushResourceTest`

**Renomeado**

- `frontend/src/test/fase10WebPush.test.tsx` → `frontend/src/test/lembretesUi.test.tsx`
  (remove um achado `E3b-arquivos-por-fase`)

---

## 4. Testes por nome

### Backend — executados e verdes (sem Mongo)

| Arquivo | Testes |
|---|---|
| `LembretePushCalculatorTest` (5) | `deveCalcularInstanteEntregaUtcCorretamente`, `horarioEmGapDstDeveLancarExcecao`, `horarioEmOverlapDstDeveEscolherOcorrenciaAnterior`, `overlapDstEmLisboaEscolheOffsetDeVerao`, `fusosDiferentesProduzemInstantesDiferentes` |
| `PushPayloadFactoryTest` (6) | `payloadTemExatamenteOsQuatroCampos`, `tituloTruncadoEm80CodePoints`, `tituloVazioUsaPadrao`, `escapaCaracteresEspeciais`, `urlForaDaAllowlistFalha`, `esquemaInvalidoEhRejeitado` |
| `WebPushCriptografiaTest` (4) | `roundTripRecuperaOPayload`, `cabecalhoSegueARfc8188`, `cadaEnvioEhUnico`, `chaveMalformadaEhRejeitada` |
| `LembretePushFilaFiltroTest` (3) | `filtroCobreOsTresEstados`, `backoffUsaAtualizadoEmEPermitePrimeiraTentativa`, `janelaFechadaUsaLimiteEstrito` |

### Backend — escritos, aguardando ambiente com Mongo

| Arquivo | Testes |
|---|---|
| `LembretePushLifecycleTest` (9) | `semAlertaNaoAgenda`, `herdarAcompanhaPadrao`, `personalizadoNaoAcompanhaPadrao`, `removerHorarioCancelaEPreservaModo`, `redefinirHorarioReagendaNovaVersao`, `concluirCancela`, `lixeiraCancela`, `restaurarReagendaSeFuturo`, `ocorrenciaRecorrenteHerdaModoDaSerie` |
| `PushDeliverySchedulerTest` (7) | `doisWorkersNaoProcessamOMesmoItem`, `atrasoMaiorQue15MinExpiraSemEnviar`, `falhaTransitoriaRespeitaJanela`, `falhaSemTempoNaJanelaTerminaEmFalhaFinal`, `gone410RemoveInscricao`, `versaoSubstituidaNaoEhEntregue`, `ttl900NoEnvio` |
| `PushResourceTest` (7) | `configRetornaPadraoPadrao`, `upsertReatribuiEndpoint`, `deleteEndpointAlheioRetorna404`, `endpointSemHttpsRetorna400`, `putPreferenciasRecalculaSoHerdar`, `putPreferenciasComValorForaDoConjuntoRetorna400`, `deleteSemEndpointRetorna400` |

### Frontend — executados e verdes

| Arquivo | Testes |
|---|---|
| `pushService.test.ts` (4) | suporte ausente → `unsupported`; `granted`/`denied`/`default`; iOS fora de standalone → `ios-nao-instalado`; permissão só no clique |
| `sw.test.ts` (4) | dedupe por `lembreteId`; ids distintos exibem normalmente; clique usa `url` e não chama `fetch`; foca aba da mesma origem |
| `logoutPush.test.ts` (3) | `DELETE` da inscrição chamado; falha não bloqueia o logout; timeout próprio de 2 s |
| `deepLinkTarefa.test.tsx` (3) | abre `TaskModal` e limpa o parâmetro; `404` → toast + limpeza; sem `?tarefaId=` nenhuma busca individual |
| `lembretesUi.test.tsx` (4) | badge `unsupported`; badge `denied`; prompt iOS só no clique; salvar preferência global |
| `manifestPwa.test.ts` (3) | manifest existe; `display: standalone`; vinculado no `index.html` |

---

## 5. Gate anti-legado

`bash scripts/verificar-legado.sh` → **14 → 11 achados**:

| Padrão | Antes | Depois | Situação |
|---|---|---|---|
| `serializacao-json` | 4 | **3** | O do scheduler foi eliminado; restam `EditalRepository`×2 e `EmailService` (**dívida R12/R13**) |
| `ZoneId-America/Sao_Paulo` | 1 | **0** | Corrigido nesta revisão |
| `E3b-arquivos-por-fase` | 3 | **2** | `fase10WebPush` renomeado; restam `fase6Tarefas` e `fase11Accessibility` (**dívida R12/R13**) |
| `LocalDate.now` | 1 | 1 | `EditalRepository` (**dívida R2/R10**) |
| `termo-ciclo-frontend` | 5 | 5 | `PomodoroApp` (**dívida R12/R13**) |

Os 11 restantes estão em outros pacotes e fora do escopo da R11 — registrados como bloqueio
herdado, **sem** declarar o gate global verde.

`rg "horarioDisparoUtc|usaPadraoGlobalLembrete|PATCH.*preferencias" backend frontend` → **0**.

---

## 6. Riscos residuais e próximos passos

1. **Criptografia própria (RFC 8291).** Sem biblioteca externa por decisão de projeto
   (aderência às regras de dependências do repositório); o round-trip em
   `WebPushCriptografiaTest` cobre cabeçalho, HKDF e delimitador de registro. Recomenda-se um
   teste contra um provedor real antes do go-live.
2. **Filtro `$expr` da fila** é a peça mais sensível (`$toLong`, `$pow`, `$min`). Coberto por
   teste de BSON (`LembretePushFilaFiltroTest`) e pelo teste de integração pendente; a R12 deve
   medir `explain` com dados sintéticos.
3. **`RecorrenciaEngine` agora depende de `LembretePushService`**: a expansão de séries passa a
   escrever em `lembretes_push` durante listagens. A idempotência por versão evita churn, mas a
   R12 deve medir o custo em séries grandes.
4. **Pendências de execução:** os três arquivos `@QuarkusTest` do pacote `push` precisam rodar
   com Mongo disponível:
   `bash scripts/env-gate.sh --exec -- bash -c 'cd backend && ./mvnw -q verify'`.
   Sem isso, a R11 permanece **não concluída**.



