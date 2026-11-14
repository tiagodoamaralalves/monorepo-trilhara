import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState
} from 'react';
import { AlarmService } from '../services/alarmService';
import { useAuth } from '../auth';
import { useConfirm } from '../design-system/ConfirmDialog/ConfirmDialog';
import { assinar, definirUsuarioCorrente, publicar } from '../sync/barramento';

type BreakType = 'short' | 'long';

/** Durações das pausas configuradas pelo usuário (persistidas com o timer). */
interface PausasEstudo {
  short: number;
  long: number;
}

interface PersistedStudyTimerState {
  trilhaId: string;
  disciplinaId: string;
  blocoId?: string;
  mode: 'progressivo' | 'regressivo';
  targetSeconds?: number;
  accumulatedElapsedMs: number;
  runningSince: number | null;
  startedAt: string | null;
  breakType: BreakType | null;
  breakEndsAt: number | null;
  pausas: PausasEstudo;
  updatedAt: number;
}

interface StudyTimerSnapshot {
  seconds: number;
  running: boolean;
  startedAt: string | null;
  breakType: BreakType | null;
  breakSeconds: number;
  trilhaId: string;
  disciplinaId: string;
  blocoId?: string;
  mode: 'progressivo' | 'regressivo';
  targetSeconds?: number;
  accumulatedElapsedMs: number;
  runningSince: number | null;
  /** `false` sem autenticação: o cronômetro de Estudos não existe no namespace anônimo. */
  ativo: boolean;
  pausas: PausasEstudo;
}

interface StartTimerOptions {
  trilhaId?: string;
  disciplinaId?: string;
  blocoId?: string;
  mode?: 'progressivo' | 'regressivo';
  targetSeconds?: number;
}

interface StudyTimerContextValue {
  state: PersistedStudyTimerState;
  now: number;
  ativo: boolean;
  start: (opts?: StartTimerOptions) => Promise<boolean>;
  pause: () => void;
  startBreak: (type: BreakType, durationMinutes: number) => void;
  endBreak: (resumeFocus: boolean) => void;
  /** Encerra o ciclo: **remove a chave** do usuário e publica o estado zerado. */
  reset: () => void;
  setTimerContext: (
    trilhaId: string,
    disciplinaId: string,
    blocoId?: string,
    mode?: 'progressivo' | 'regressivo',
    targetSeconds?: number
  ) => Promise<boolean>;
  setPausas: (pausas: PausasEstudo) => void;
}

const PAUSAS_PADRAO: PausasEstudo = { short: 5, long: 15 };
const TRILHA_VAZIA = '__sem_trilha__';
/** Tempo mínimo acumulado que exige confirmação ao trocar de contexto (§8). */
const LIMITE_CONFIRMACAO_TROCA_CONTEXTO_MS = 60_000;

const EMPTY_STUDY_TIMER_STATE: PersistedStudyTimerState = {
  trilhaId: TRILHA_VAZIA,
  disciplinaId: '',
  blocoId: undefined,
  mode: 'progressivo',
  targetSeconds: undefined,
  accumulatedElapsedMs: 0,
  runningSince: null,
  startedAt: null,
  breakType: null,
  breakEndsAt: null,
  pausas: PAUSAS_PADRAO,
  updatedAt: 0
};

function estadoVazio(): PersistedStudyTimerState {
  return { ...EMPTY_STUDY_TIMER_STATE, pausas: { ...PAUSAS_PADRAO }, updatedAt: Date.now() };
}

/**
 * Chave de persistência do cronômetro de Estudos.
 *
 * O cronômetro de Estudos **exige autenticação**: sem `usuarioId` não existe chave e nada é
 * persistido (o namespace anônimo é exclusivo do Pomodoro).
 */
export function getStudyTimerStorageKey(usuarioId?: string | null): string | null {
  return usuarioId ? `trilhara:u:${usuarioId}:study_timer` : null;
}

function normalizarPausas(valor: unknown): PausasEstudo {
  const bruto = (valor ?? {}) as Partial<PausasEstudo>;
  const curta = Number(bruto.short);
  const longa = Number(bruto.long);
  return {
    short: Number.isFinite(curta) && curta >= 1 ? curta : PAUSAS_PADRAO.short,
    long: Number.isFinite(longa) && longa >= 1 ? longa : PAUSAS_PADRAO.long
  };
}

/**
 * Lê o estado do usuário restaurando **sempre pausado**.
 *
 * O tempo decorrido entre a gravação e a leitura é somado ao acumulado (`updatedAt`), de modo
 * que recarregar a aba não perde segundos. `runningSince` nunca volta ativo: sem o usuário
 * presente não há como garantir continuidade confiável.
 */
function carregarEstadoPersistido(usuarioId?: string | null): PersistedStudyTimerState {
  if (typeof window === 'undefined') return estadoVazio();
  const key = getStudyTimerStorageKey(usuarioId);
  if (!key) return estadoVazio();
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return estadoVazio();
    const parsed = JSON.parse(raw) as Partial<PersistedStudyTimerState>;
    let accumulatedElapsedMs = parsed.accumulatedElapsedMs ?? 0;
    if (typeof parsed.runningSince === 'number') {
      accumulatedElapsedMs += Math.max(0, (parsed.updatedAt || Date.now()) - parsed.runningSince);
    }
    return {
      ...estadoVazio(),
      ...parsed,
      pausas: normalizarPausas(parsed.pausas),
      accumulatedElapsedMs,
      runningSince: null
    };
  } catch {
    return estadoVazio();
  }
}

function salvarEstadoPersistido(
  usuarioId: string | null | undefined,
  state: PersistedStudyTimerState
): void {
  if (typeof window === 'undefined') return;
  const key = getStudyTimerStorageKey(usuarioId);
  // Sem autenticação não há cronômetro de Estudos persistido.
  if (!key) return;
  try {
    window.localStorage.setItem(key, JSON.stringify(state));
  } catch {
    // Storage restrito: o timer continua válido em memória.
  }
}

function removerEstadoPersistido(usuarioId: string | null | undefined): void {
  if (typeof window === 'undefined') return;
  const key = getStudyTimerStorageKey(usuarioId);
  if (!key) return;
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Storage restrito: sem chave para remover.
  }
}

const StudyTimerContext = createContext<StudyTimerContextValue | null>(null);

export const StudyTimerProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { usuario } = useAuth();
  const { confirm } = useConfirm();
  const usuarioId = usuario?.id ?? null;

  const [state, setState] = useState<PersistedStudyTimerState>(() => carregarEstadoPersistido(usuarioId));
  const [now, setNow] = useState<number>(() => Date.now());

  const usuarioIdRef = useRef<string | null>(usuarioId);
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    definirUsuarioCorrente(usuarioId);
    usuarioIdRef.current = usuarioId;
  }, [usuarioId]);

  /** Publica o estado atual para as demais abas (`null` = ciclo encerrado). */
  const publicarEstado = useCallback((novoEstado: PersistedStudyTimerState | null) => {
    publicar('TIMER_ESTUDOS_ATUALIZADO', novoEstado);
  }, []);

  const persistir = useCallback(
    (novoEstado: PersistedStudyTimerState) => {
      salvarEstadoPersistido(usuarioIdRef.current, novoEstado);
      publicarEstado(novoEstado);
    },
    [publicarEstado]
  );

  /** Aplica uma transição sobre o estado corrente, persiste e sincroniza as abas. */
  const aplicar = useCallback(
    (transicao: (atual: PersistedStudyTimerState) => PersistedStudyTimerState) => {
      setState((atual) => {
        const proximo = transicao(atual);
        if (proximo === atual) return atual;
        persistir(proximo);
        return proximo;
      });
    },
    [persistir]
  );

  /** Pausa o timer acumulando o tempo decorrido até `instante`. */
  const pausarEm = useCallback((instante: number): PersistedStudyTimerState => {
    const atual = stateRef.current;
    if (atual.runningSince === null) return atual;
    return {
      ...atual,
      accumulatedElapsedMs: atual.accumulatedElapsedMs + Math.max(0, instante - atual.runningSince),
      runningSince: null,
      updatedAt: instante
    };
  }, []);

  // Mensagens de outras abas: o barramento já descarta usuários diferentes.
  useEffect(() => {
    const cancelar = assinar('TIMER_ESTUDOS_ATUALIZADO', (mensagem) => {
      const payload = mensagem.payload as PersistedStudyTimerState | null;
      setState(payload ?? estadoVazio());
    });
    return cancelar;
  }, []);

  // Logout / conta excluída: pausa, persiste sob a conta antiga e limpa a memória (§8).
  useEffect(() => {
    const aoLogout = (evento: Event) => {
      const detalhe = (evento as CustomEvent<{ usuarioId?: string }>).detail;
      const uid = detalhe?.usuarioId ?? usuarioIdRef.current;
      const pausado = pausarEm(Date.now());
      if (uid && pausado.runningSince === null && pausado.accumulatedElapsedMs > 0) {
        salvarEstadoPersistido(uid, pausado);
      }
      setState(estadoVazio());
    };
    const aoExcluirConta = () => {
      removerEstadoPersistido(usuarioIdRef.current);
      setState(estadoVazio());
    };

    window.addEventListener('trilhara:logout', aoLogout);
    window.addEventListener('trilhara:conta-excluida', aoExcluirConta);
    return () => {
      window.removeEventListener('trilhara:logout', aoLogout);
      window.removeEventListener('trilhara:conta-excluida', aoExcluirConta);
    };
  }, [pausarEm]);

  // Troca de conta: pausa sob a conta anterior e carrega a nova.
  useEffect(() => {
    if (usuarioIdRef.current === usuarioId) return;
    const anterior = usuarioIdRef.current;
    const pausado = pausarEm(Date.now());
    if (anterior && pausado.runningSince === null && pausado.accumulatedElapsedMs > 0) {
      salvarEstadoPersistido(anterior, pausado);
    }
    usuarioIdRef.current = usuarioId;
    setState(carregarEstadoPersistido(usuarioId));
  }, [pausarEm, usuarioId]);

  // Relógio: atualiza `now` apenas com timer ativo ou pausa em andamento.
  const isRunning = state.runningSince !== null || state.breakEndsAt !== null;
  useEffect(() => {
    if (!isRunning) return;
    const intervalId = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(intervalId);
  }, [isRunning]);

  // Alarme de pausa expirada (som/notificação; nunca abre modal nem grava sessão).
  useEffect(() => {
    if (state.breakEndsAt === null || state.breakEndsAt > now) return;
    if (state.breakType === 'short') {
      AlarmService.dispararAlarmeFimPausaCurta();
    } else if (state.breakType === 'long') {
      AlarmService.dispararAlarmeFimPausaLonga();
    }
    aplicar((atual) => ({
      ...atual,
      breakType: null,
      breakEndsAt: null,
      updatedAt: Date.now()
    }));
  }, [aplicar, now, state.breakEndsAt, state.breakType]);

  const pause = useCallback(() => {
    aplicar(() => pausarEm(Date.now()));
  }, [aplicar, pausarEm]);

  const start = useCallback(
    async (opts?: StartTimerOptions): Promise<boolean> => {
      // Sem autenticação o cronômetro de Estudos não existe.
      if (!usuarioIdRef.current) return false;

      const atual = stateRef.current;
      const inicio = Date.now();
      const novoTrilhaId = opts?.trilhaId ?? atual.trilhaId;
      const novoDisciplinaId = opts?.disciplinaId ?? atual.disciplinaId;
      const contextoMudou =
        novoTrilhaId !== atual.trilhaId || novoDisciplinaId !== atual.disciplinaId;
      const tempoTotalMs =
        atual.accumulatedElapsedMs +
        (atual.runningSince === null ? 0 : Math.max(0, inicio - atual.runningSince));

      if (contextoMudou && tempoTotalMs >= LIMITE_CONFIRMACAO_TROCA_CONTEXTO_MS) {
        const confirmou = await confirm({
          title: 'Trocar o contexto do cronômetro?',
          description:
            'Existe tempo não salvo nesta trilha/disciplina. O cronômetro anterior será pausado e o tempo acumulado ficará pendente de registro.',
          confirmText: 'Trocar contexto',
          cancelText: 'Continuar aqui'
        });
        if (!confirmou) return false;
      }

      if (atual.runningSince !== null && !contextoMudou) return true;

      aplicar((corrente) => ({
        ...corrente,
        trilhaId: novoTrilhaId,
        disciplinaId: novoDisciplinaId,
        blocoId: opts?.blocoId ?? corrente.blocoId,
        mode: opts?.mode ?? corrente.mode,
        targetSeconds: opts?.targetSeconds ?? corrente.targetSeconds,
        startedAt: corrente.startedAt ?? new Date(inicio).toISOString(),
        runningSince: inicio,
        breakType: null,
        breakEndsAt: null,
        updatedAt: inicio
      }));
      return true;
    },
    [aplicar, confirm]
  );

  const setTimerContext = useCallback(
    async (
      trilhaId: string,
      disciplinaId: string,
      blocoId?: string,
      mode?: 'progressivo' | 'regressivo',
      targetSeconds?: number
    ): Promise<boolean> => {
      const atual = stateRef.current;
      const contextoMudou = trilhaId !== atual.trilhaId || disciplinaId !== atual.disciplinaId;

      if (contextoMudou && atual.accumulatedElapsedMs >= LIMITE_CONFIRMACAO_TROCA_CONTEXTO_MS) {
        const confirmou = await confirm({
          title: 'Trocar o contexto do cronômetro?',
          description:
            'O tempo já acumulado nesta trilha/disciplina será pausado e mantido para registro antes de iniciar o novo contexto.',
          confirmText: 'Trocar contexto',
          cancelText: 'Continuar aqui'
        });
        if (!confirmou) return false;
      }

      aplicar((corrente) => ({
        ...corrente,
        trilhaId,
        disciplinaId,
        blocoId,
        mode: mode ?? corrente.mode,
        targetSeconds,
        updatedAt: Date.now()
      }));
      return true;
    },
    [aplicar, confirm]
  );

  const startBreak = useCallback(
    (type: BreakType, durationMinutes: number) => {
      if (!usuarioIdRef.current) return;
      const inicio = Date.now();
      aplicar(() => ({
        ...pausarEm(inicio),
        breakType: type,
        breakEndsAt: inicio + durationMinutes * 60_000,
        updatedAt: inicio
      }));
    },
    [aplicar, pausarEm]
  );

  const endBreak = useCallback(
    (resumeFocus: boolean) => {
      const retomada = Date.now();
      aplicar((corrente) => ({
        ...corrente,
        startedAt: resumeFocus
          ? corrente.startedAt ?? new Date(retomada).toISOString()
          : corrente.startedAt,
        runningSince: resumeFocus ? retomada : null,
        breakType: null,
        breakEndsAt: null,
        updatedAt: retomada
      }));
    },
    [aplicar]
  );

  /**
 * Encerra o ciclo e **remove** a chave do usuário. Só é chamado após o servidor
 * confirmar o registro da sessão (`201`/`200`).
 */
  const reset = useCallback(() => {
    removerEstadoPersistido(usuarioIdRef.current);
    setState(estadoVazio());
    publicar('TIMER_ESTUDOS_ATUALIZADO', null);
  }, []);

  const setPausas = useCallback(
    (pausas: PausasEstudo) => {
      const normalizadas = normalizarPausas(pausas);
      aplicar((corrente) => ({ ...corrente, pausas: normalizadas, updatedAt: Date.now() }));
    },
    [aplicar]
  );

  const ativo = Boolean(usuarioId);

  const value = useMemo<StudyTimerContextValue>(
    () => ({
      state,
      now,
      ativo,
      start,
      pause,
      startBreak,
      endBreak,
      reset,
      setTimerContext,
      setPausas
    }),
    [ativo, endBreak, now, pause, reset, setPausas, setTimerContext, start, startBreak, state]
  );

  return <StudyTimerContext.Provider value={value}>{children}</StudyTimerContext.Provider>;
};

export interface UseStudyTimerResultado extends StudyTimerSnapshot {
  start: (opts?: StartTimerOptions) => Promise<boolean>;
  pause: () => void;
  startBreak: (type: BreakType, durationMinutes: number) => void;
  endBreak: (resumeFocus: boolean) => void;
  reset: () => void;
  setTimerContext: (
    trilhaId: string,
    disciplinaId: string,
    blocoId?: string,
    mode?: 'progressivo' | 'regressivo',
    targetSeconds?: number
  ) => Promise<boolean>;
  setPausas: (pausas: PausasEstudo) => void;
}

export function useStudyTimer(): UseStudyTimerResultado {
  const contexto = useContext(StudyTimerContext);
  if (!contexto) throw new Error('useStudyTimer deve ser usado dentro de StudyTimerProvider.');

  const { state, now } = contexto;

  const elapsedMs =
    state.accumulatedElapsedMs +
    (state.runningSince === null ? 0 : Math.max(0, now - state.runningSince));

  const breakSeconds =
    state.breakEndsAt === null ? 0 : Math.max(0, Math.ceil((state.breakEndsAt - now) / 1000));

  return {
    seconds: Math.floor(elapsedMs / 1000),
    running: state.runningSince !== null,
    startedAt: state.startedAt,
    breakType: breakSeconds > 0 ? state.breakType : null,
    breakSeconds,
    trilhaId: state.trilhaId,
    disciplinaId: state.disciplinaId,
    blocoId: state.blocoId,
    mode: state.mode,
    targetSeconds: state.targetSeconds,
    accumulatedElapsedMs: state.accumulatedElapsedMs,
    runningSince: state.runningSince,
    ativo: contexto.ativo,
    pausas: state.pausas,
    start: contexto.start,
    pause: contexto.pause,
    startBreak: contexto.startBreak,
    endBreak: contexto.endBreak,
    reset: contexto.reset,
    setTimerContext: contexto.setTimerContext,
    setPausas: contexto.setPausas
  };
}
