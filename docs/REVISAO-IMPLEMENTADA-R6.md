# R6 — Revisão Implementada (F5: `GlobalSessionModal`, `sessaoGlobalService` e timers)

> Relatório da revisão **R6**. Complementa `docs/REVISAO-IMPLEMENTADA.md` (R0) e os
> relatórios R1–R5.
> Regra mantida: nenhum critério é declarado atendido sem teste automatizado nomeado.
>
> Execução: 2026-11-12 · branch `main` · base `c749c08` (R5-preparacao).

---

## 1. Achados confirmados e descartados

| Achado do guia | Veredito | Evidência antes | Correção aplicada |
|---|---|---|---|
| **RI-16/DC-14** namespace `anon` no timer de Estudos | **Descartado no código** | `StudyTimerContext.tsx:101-103` já devolvia `null` sem `usuarioId`; `grep "\|\| 'anon'"` = 0; gate `storage-inseguro` = 0 | Achado era documental. Faltava a **UX**: nenhuma indicação de "Entre para usar o cronômetro" |
| UX do timer sem autenticação | **Confirmado** | `EstudarView.tsx:308-356` renderizava os controles sempre | `ativo` no contexto; botões `disabled` + status **"Entre para usar o cronômetro"** |
| **RI-17** retry do manual duplica trilha/disciplina | **Confirmado** | `GlobalSessionModal.tsx:296-326` não guardava IDs; disciplina com `.catch(() => null)` silencioso (`:321`) | `criadosInline { trilhaId?, disciplinaId? }` no estado; erro inline; teste garante `POST /trilhas` **1×** |
| **D-22.1** pausa ao abrir / reset só após sucesso | **Parcial** | pausa no chamador (`EstudarView:152`); `reset` gravava zeros (`:400-414`) | Modal pausa ao abrir nos modos de timer; `reset()` **remove a chave** e só roda após `201/200` |
| **D-22.2** descarte com confirmação | **Confirmado** | sem dirty tracking, sem `ConfirmDialog`, overlay sem `onClick` | `sujo` + `ConfirmDialog` "Descartar registro?" (*Continuar editando* padrão) + `useUnsavedChanges('sessao:global', sujo)` |
| **D-22.3/UX-07** rótulos | **Confirmado** | `'Salvando...'`, `'Salvar Sessão'`, `'Salvar sessão e concluir bloco'` | **Salvar sessão**, **Concluir sem salvar sessão** (só com bloco), **Cancelar**, **Tentar concluir bloco novamente** |
| **D-22.5** rede/offline/401/409/400 | **Confirmado (ausente)** | `:366-394` só `toast.error` | Banner offline + **Tentar novamente**; `401` → `AUTH_REQUIRED_EVENT` + `aguardandoLogin`; `409 IDEMPOTENCY_KEY_CONFLICT` com mensagem própria; `400` → `errors` por campo com `FieldError` |
| **D-22** duração preserva segundos | **Confirmado (ausente)** | `:66-71,205` truncava para h/m; inputs sem `inputMode` | `duracaoOriginalSegundos` + `duracaoEditada`; auxiliar **"Tempo medido: 25 min 37 s"**; `inputmode="numeric"` |
| **D-22.6** `AGENDA_INVALIDADA` após salvar | **Confirmado (ausente)** | `grep AGENDA_INVALIDADA` = 0 | `publicar('AGENDA_INVALIDADA')` em `sucessoFinalizado`/`Concluir sem salvar`; `agendaHojeService` assina e recarrega |
| **UX-11** canais heterogêneos | **Confirmado** | 4 `BroadcastChannel` + 5 eventos DOM | Barramento único `BroadcastChannel("trilhara")` + eventos `trilhara:logout` / `trilhara:conta-excluida` |
| Checklist 8 (4 apps via `useGlobalSessionModal`) | **Já conforme** | `EstudarView:63`, `PlanejamentoView:91`, `TarefasApp:107`, `PomodoroApp:231`, `DashboardView:110` | Nada a alterar; permanece coberto |
| Spec do serviço (`executarFluxo`, `concluirBloco(void)`, chave obrigatória) | **Confirmado divergente** | `executarFluxoDuasEtapas`, `concluirBloco(id, bool)`, chave opcional | `registrarSessao(trilhaId, payload, chave)`, `concluirBloco(blocoId): void`, `reabrirBloco`, `executarFluxo` → `ResultadoFluxo` |


### Achados novos (não listados no guia)

| # | Achado | Evidência | Correção |
|---|---|---|---|
| **N-1** | Configuração do Pomodoro em chaves **globais** (`trilhara_pomodoro_*`, `trilhara_alarme_*`) — viola D-21/§8 | `PomodoroApp.tsx:81-88` | Tudo migrou para o objeto da chave `pomodoro_timer` da conta; testes garantem ausência das globais |
| **N-2** | Pausas do Estudos em chaves globais compartilhadas com o Pomodoro | `EstudarView.tsx:33-34` | `pausas {short,long}` no estado do `study_timer` (`timer.setPausas`) |
| **N-3** | Troca de contexto usava `window.confirm` | `StudyTimerContext.tsx:328-331` | `useConfirm()` (`ConfirmDialog`) com *Trocar contexto* / *Continuar aqui* |
| **N-4** | Código legado sem call site (`timers`, `move`) | `StudyTimerContext.tsx:69-76, 453-475, 543` | Removido junto com o arquivo |
| **N-5** | `sessaoGlobalService` misturava etapa 1 e 2 e perdia o retry | `GlobalSessionModal.tsx:346-394` duplicava a orquestração | Orquestração única em `executarFluxo`; o modal só consome `ResultadoFluxo` |
| **N-6** | Teste por fase alimentava `E3b-arquivos-por-fase` | `fase5GlobalSessionTimer.test.tsx` | Removido; substituído por 5 arquivos normativos |
| **N-7** | `RASTREABILIDADE` tinha `## E.` duplicado (R2 e R4) e A7 apontava para o arquivo por fase | `docs/RASTREABILIDADE-REESTRUTURACAO.md:108,162` | Cabeçalho da R4 renomeado para `## F.`; A7 atualizado; nova seção `## G.` da R6 |

---

## 2. Decisões implementadas

### 2.1 Barramento único (D-21) — `frontend/src/shared/sync/barramento.ts`
- `BroadcastChannel("trilhara")` criado **lazy**; quando indisponível, fallback por evento
  `storage` na chave `trilhara:bus`.
- Envelope `{ v: 1, tipo, usuarioId: string | null, origemAbaId, payload }`.
- `definirUsuarioCorrente(id)`, `publicar(tipo, payload, usuarioId?)`, `assinar(tipo, handler)`.
- Descarta: `v !== 1`, tipo desconhecido, mensagem da própria aba e `usuarioId` diferente do
  usuário em memória (inclusive `null` × autenticado).
- Substituiu os canais `trilhara_study_timer`, `trilhara_pomodoro_timer`, `trilhara_agenda_hoje`
  e `trilhara_auth_channel`.

### 2.2 Eventos DOM unificados (D-21.d)

| Antes | Depois |
|---|---|
| `trilhara:auth:logout` | `trilhara:logout` |
| `trilhara:auth:conta-excluida` | `trilhara:conta-excluida` |
| `AUTH_SYNC`/`AUTH_LOGOUT`/`ACCOUNT_DELETED` (canal próprio) | `SESSAO_ENCERRADA`/`CONTA_EXCLUIDA` no barramento |
| `trilhara:auth-required` | mantido |
| `trilhara:auth-change` | mantido (notificação intra-aba; cross-tab vai ao barramento) |

### 2.3 Cronômetro de Estudos em `shared/` (Opção A)
`StudyTimerContext` → **`frontend/src/shared/timers/estudoTimer.tsx`**; `alarmService` →
`frontend/src/shared/services/alarmService.ts`. `StudyTimerProvider` montado em `App.tsx`
entre `AuthProvider` e `GlobalSessionModalProvider`, permitindo que o modal pause/zere o timer e
use `ConfirmDialog` sem callbacks no contrato congelado em F0.

### 2.4 `sessaoGlobalService`
```ts
registrarSessao(trilhaId, payload, chave): Promise<SessaoRegistradaDTO>  // header + body
concluirBloco(blocoId): Promise<void>                                   // PATCH { concluido: true }
reabrirBloco(blocoId): Promise<void>                                    // PATCH { concluido: false }
executarFluxo({ trilhaId, blocoId, payload, chave, sessaoSalva? }): Promise<ResultadoFluxo>
```

---

## 3. Arquivos criados / alterados / removidos

**Criados:**
```
frontend/src/shared/sync/barramento.ts
frontend/src/shared/timers/estudoTimer.tsx
frontend/src/test/sessaoGlobalService.test.ts
frontend/src/test/globalSessionModal.test.tsx
frontend/src/test/studyTimer.test.tsx
frontend/src/test/pomodoro.test.tsx
frontend/src/test/barramento.test.ts
docs/REVISAO-IMPLEMENTADA-R6.md
```

**Movidos:**
```
frontend/src/modules/estudos/services/alarmService.ts        -> frontend/src/shared/services/alarmService.ts
frontend/src/modules/estudos/context/StudyTimerContext.tsx   -> frontend/src/shared/timers/estudoTimer.tsx
```

**Alterados:**
```
frontend/src/App.tsx                                        (StudyTimerProvider na raiz)
frontend/src/shared/auth/authStorage.ts                     (eventos + barramento)
frontend/src/shared/services/agendaHojeService.ts           (AGENDA_INVALIDADA)
frontend/src/shared/services/sessaoGlobalService.ts         (spec R6)
frontend/src/shared/design-system/GlobalSessionModal/GlobalSessionModal.tsx  (D-22 completo)
frontend/src/modules/estudos/EstudosApp.tsx                 (Provider removido)
frontend/src/modules/estudos/views/EstudarView.tsx          (ativo, pausas, rótulos, reset)
frontend/src/modules/estudos/views/PlanejamentoView.tsx     (reabrirBloco)
frontend/src/modules/estudos/components/Navbar/Navbar.tsx   (useStudyTimer sem id)
frontend/src/modules/pomodoro/PomodoroApp.tsx               (estado por conta + barramento)
frontend/src/test/fase7IntegracaoDiaria.test.tsx            (árvore de providers + rótulo D-22.3)
frontend/src/test/fase11Accessibility.test.tsx              (árvore de providers)
```

**Removidos:**
```
frontend/src/test/fase5GlobalSessionTimer.test.tsx          (nome por fase; substituído)
```

---

## 4. Testes normativos (nomes completos)

### `sessaoGlobalService.test.ts` (5)
- `envia header Idempotency-Key e body com a mesma chave`
- `executa o fluxo de duas etapas concluindo sessão e bloco`
- `falha na etapa 2 permite retry apenas do PATCH sem reenviar o POST`
- `401 dispara trilhara:auth-required e o retry reutiliza a mesma chave após login`
- `concluirBloco envia PATCH concluido true e reabrirBloco envia false`

### `globalSessionModal.test.tsx` (10)
- `abre em ESTUDOS_TIMER com título, tempo medido e rótulo fixo`
- `abre em BLOCO_DIRETO com o botão de concluir sem sessão`
- `abre em BLOCO_TIMER e MANUAL com os títulos esperados`
- `"Concluir sem salvar sessão" aparece apenas em modos com bloco`
- `cancelar mantém o cronômetro pausado com o tempo acumulado`
- `fechar com campos alterados pede confirmação de descarte`
- `40 s exibe erro inline de duração mínima`
- `acertos maiores que questões exibem erro inline`
- `offline mantém os campos preenchidos e exibe banner de conexão`
- `salvar com sucesso zera o timer e remove a chave`

### `studyTimer.test.tsx` (8)
- `sem usuário fica inativo, ignora o início e não grava nenhuma chave`
- `isola as chaves de Usuário A e Usuário B`
- `logout pausa, acumula o tempo e persiste na chave da conta`
- `ignora mensagem do barramento com usuarioId diferente`
- `aplica mensagem do barramento do mesmo usuário`
- `o alarme não abre modal de sessão`
- `mantém drift ≤ 1 s após 10 min "suspenso" (timestamp absoluto)`
- `reset remove a chave do usuário (D-22.1)`

### `pomodoro.test.tsx` (5)
- `visitante usa trilhara:anon:pomodoro_timer`
- `usuário autenticado usa trilhara:u:{usuarioId}:pomodoro_timer`
- `login não migra o estado anônimo para a chave da conta`
- `não há chaves globais de configuração do Pomodoro`
- `configuração pertence ao objeto persistido da conta (pausas, som, notificação)`

### `barramento.test.ts` (8)
- `usa um único canal nomeado "trilhara"`
- `cada aba tem um identificador estável de origem`
- `entrega mensagem v:1 ao ouvinte do tipo inscrito`
- `descarta mensagem de outro usuarioId`
- `descarta mensagem anônima quando o usuário em memória é autenticado`
- `descarta envelope com versão diferente de 1`
- `publicar usa o fallback storage quando não há BroadcastChannel`
- `publicar permite usuarioId explícito (conta excluída)`

A etapa 2 nunca rejeita: devolve `{ sessaoSalva: true, sessaoId, blocoConcluido: false, erro }`.


---

## 5. Gate de saída

| Etapa | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw -o verify` (via `scripts/env-gate.sh`) | **BUILD SUCCESS** — **244 testes**, 0 falhas, 0 erros, 7 skipped |
| Frontend typecheck | `npm run typecheck` | **SUCCESS** |
| Frontend lint | `npm run lint -- --max-warnings 0` | **SUCCESS** (exit 0) |
| Frontend testes | `npm test -- --run` | **32 arquivos / 213 testes** (R5: 28/190) |
| Frontend build | `npm run build` | **SUCCESS** (`built in 5.36s`) |
| Frontend dead code | `npx knip` | **SUCCESS** (exit 0) |
| Gate anti-legado | `bash scripts/verificar-legado.sh --quiet` | **63 achados** (R5: 67) |

### Movimento do gate anti-legado

| Padrão | Antes (R5) | Depois (R6) |
|---|---|---|
| `canais-antigos` | 3 | **0** |
| `E3b-arquivos-por-fase` | 7 | **6** |
| `storage-inseguro` | 0 | 0 |
| `nomes-nao-canonicos-d02` | 46 | 46 (→ R8) |
| `termo-ciclo-frontend` | 5 | 5 ("ciclo Pomodoro", preservado) |
| **Total** | 67 | **63** |

### Critério de grep da R6 §4
```bash
rg "trilhara_study_timer|trilhara_pomodoro_timer|trilhara_agenda_hoje|'trilhara:auth'|\|\| 'anon'" frontend/src
```
→ **0 ocorrências** (`grep -rn` exit 1).

---

## 6. Desvios de escopo justificados (mudanças transversais)

| Mudança | Por que foi necessária | Arquivos |
|---|---|---|
| `StudyTimerProvider` movido para `App.tsx` | D-22 exige que o modal pause/zere o timer; o provider vivia dentro de `EstudosApp`, abaixo do modal na árvore | `App.tsx`, `EstudosApp.tsx`, `EstudarView.tsx`, `Navbar.tsx` |
| `alarmService` movido para `shared/services` | O provider do timer subiu para `shared/` e não pode importar `modules/` (regra de camadas) | `shared/services/alarmService.ts`, `EstudarView.tsx`, `estudoTimer.tsx` |
| `ConfirmProvider` + `StudyTimerProvider` nas árvores de `fase7`/`fase11` | O modal passou a exigir ambos os contextos, como em produção | `fase7IntegracaoDiaria.test.tsx`, `fase11Accessibility.test.tsx` |
| Rótulo `Salvar Sessão e Concluir Bloco` → `Salvar sessão` no teste da fase 7 | D-22.3 fixa o rótulo do botão principal | `fase7IntegracaoDiaria.test.tsx` |
| `__receberParaTeste` no barramento | Sem `BroadcastChannel` no jsdom não é possível simular outra aba; o envio local é descartado por design | `shared/sync/barramento.ts` |

---

## 7. Bloqueios herdados (não pertencem à R6)

| Item | Dono | Evidência |
|---|---|---|
| `nomes-nao-canonicos-d02` (46): `usaPadraoGlobalLembrete`, `dataTermino` | **R8** | `grep -rInE` em `tarefa/*` |
| `E3b-arquivos-por-fase` (6) e gate estrito `verificar-legado.sh` → exit 0 | **R12/R13** | D-43 |
| `serializacao-json` (4) em `EditalRepository`/`MongoIndexInitializer` | R9/R13 | allowlist da R0 |
| `termo-ciclo-frontend` (5) — "ciclo Pomodoro" | preservado pelo guia | `PomodoroApp.tsx` |

---

## 8. Observações de implementação

- **`ConfirmDialog` no descarte**: o foco inicial é sempre o botão seguro (*Continuar editando*),
  conforme D-37.
- **`409 IDEMPOTENCY_KEY_CONFLICT`**: o modal exibe mensagem própria; a nova chave só é gerada
  em uma nova abertura (D-22.4), nunca silenciosamente.
- **`401`**: o `AuthModal` abre por cima via `AUTH_REQUIRED_EVENT`; o formulário permanece
  montado com os dados e a mesma chave, e o retry reenvia o mesmo payload.
- **Desconcluir bloco** (`reabrirBloco`) não exclui sessões (§6.4), coberto por
  `concluirBloco envia PATCH concluido true e reabrirBloco envia false`.
