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
import { useAuth } from '@shared/auth';

export type BreakType = 'short' | 'long';

export interface PersistedStudyTimerState {
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
  updatedAt: number;
}

export interface StudyTimerSnapshot {
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
}

export interface StartTimerOptions {
  trilhaId?: string;
  disciplinaId?: string;
  blocoId?: string;
  mode?: 'progressivo' | 'regressivo';
  targetSeconds?: number;
  force?: boolean;
}

export interface StudyTimerContextValue {
  state: PersistedStudyTimerState;
  now: number;
  start: (timerId?: string, opts?: StartTimerOptions) => boolean;
  pause: () => void;
  startBreak: (type: BreakType, durationMinutes: number) => void;
  endBreak: (resumeFocus: boolean) => void;
  reset: (targetId?: string) => void;
  move: (fromId: string, toId: string) => void;
  setTimerContext: (
    trilhaId: string,
    disciplinaId: string,
    blocoId?: string,
    mode?: 'progressivo' | 'regressivo',
    targetSeconds?: number
  ) => void;
  // Compatibilidade com callers legados que liam context.timers
  timers: Record<string, {
    elapsedMs: number;
    startedAt: string | null;
    runningSince: number | null;
    breakType: BreakType | null;
    breakEndsAt: number | null;
  }>;
}

const EMPTY_PERSISTED_STATE: PersistedStudyTimerState = {
  trilhaId: '__sem_trilha__',
  disciplinaId: '',
  blocoId: undefined,
  mode: 'progressivo',
  targetSeconds: undefined,
  accumulatedElapsedMs: 0,
  runningSince: null,
  startedAt: null,
  breakType: null,
  breakEndsAt: null,
  updatedAt: Date.now()
};

const BROADCAST_CHANNEL_NAME = 'trilhara_study_timer';

export function getStudyTimerStorageKey(usuarioId?: string | null): string {
  return `trilhara:u:${usuarioId || 'anon'}:study_timer`;
}

function carregarEstadoPersistido(usuarioId?: string | null): PersistedStudyTimerState {
  if (typeof window === 'undefined') return { ...EMPTY_PERSISTED_STATE };
  try {
    const key = getStudyTimerStorageKey(usuarioId);
    const raw = window.localStorage.getItem(key);
    if (!raw) return { ...EMPTY_PERSISTED_STATE };
    const parsed = JSON.parse(raw) as PersistedStudyTimerState;
    // Garante que, ao carregar de storage (após reload ou login), restaura pausado
    let accumulatedElapsedMs = parsed.accumulatedElapsedMs ?? 0;
    if (parsed.runningSince !== null && typeof parsed.runningSince === 'number') {
      accumulatedElapsedMs += Math.max(0, (parsed.updatedAt || Date.now()) - parsed.runningSince);
    }
    return {
      ...parsed,
      accumulatedElapsedMs,
      runningSince: null
    };
  } catch {
    return { ...EMPTY_PERSISTED_STATE };
  }
}

function salvarEstadoPersistido(usuarioId: string | null | undefined, state: PersistedStudyTimerState): void {
  if (typeof window === 'undefined') return;
  try {
    const key = getStudyTimerStorageKey(usuarioId);
    window.localStorage.setItem(key, JSON.stringify(state));
  } catch {
    // Ignora erro em ambientes restritos de storage
  }
}

const StudyTimerContext = createContext<StudyTimerContextValue | null>(null);

export const StudyTimerProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { usuario } = useAuth();
  const usuarioId = usuario?.id || null;
  const usuarioIdRef = useRef<string | null>(usuarioId);
  const instanceIdRef = useRef<string>(`tab_${crypto.randomUUID().slice(0, 8)}`);

  const [state, setState] = useState<PersistedStudyTimerState>(() => carregarEstadoPersistido(usuarioId));
  const [now, setNow] = useState(Date.now);
  const stateRef = useRef(state);
  stateRef.current = state;

  const broadcastChannelRef = useRef<BroadcastChannel | null>(null);

  // Inicializa BroadcastChannel
  useEffect(() => {
    if (typeof window === 'undefined' || !('BroadcastChannel' in window)) return;
    try {
      const channel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
      broadcastChannelRef.current = channel;

      channel.onmessage = (event) => {
        const data = event.data;
        if (!data || data.senderId === instanceIdRef.current) return;
        if (data.usuarioId !== (usuarioIdRef.current || 'anon')) return;

        if (data.type === 'SYNC_STATE' && data.state) {
          setState(data.state);
        }
      };

      return () => {
        channel.close();
        broadcastChannelRef.current = null;
      };
    } catch {
      // Ignora indisponibilidade do BroadcastChannel
    }
  }, []);

  const broadcastState = useCallback((novoEstado: PersistedStudyTimerState) => {
    salvarEstadoPersistido(usuarioIdRef.current, novoEstado);
    if (broadcastChannelRef.current) {
      try {
        broadcastChannelRef.current.postMessage({
          type: 'SYNC_STATE',
          senderId: instanceIdRef.current,
          usuarioId: usuarioIdRef.current || 'anon',
          state: novoEstado
        });
      } catch {
        // Ignora erro de postMessage
      }
    }
  }, []);

  // Monitora evento `storage` entre abas da mesma origem
  useEffect(() => {
    const handleStorage = (e: StorageEvent) => {
      const expectedKey = getStudyTimerStorageKey(usuarioIdRef.current);
      if (e.key === expectedKey && e.newValue) {
        try {
          const parsed = JSON.parse(e.newValue) as PersistedStudyTimerState;
          setState(parsed);
        } catch {
          // Ignora erro de parse
        }
      }
    };

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  // Logout e Troca de Conta: pausa timer ativo, acumula tempo decorrido, salva sob a conta antiga e limpa memória
  useEffect(() => {
    const handleLogout = (e?: Event) => {
      const customEvent = e as CustomEvent<{ usuarioId?: string }>;
      const uidAntigo = customEvent?.detail?.usuarioId || usuarioIdRef.current;
      const s = stateRef.current;
      if (s.runningSince !== null) {
        const elapsedDelta = Math.max(0, Date.now() - s.runningSince);
        const acumuladoFinal = s.accumulatedElapsedMs + elapsedDelta;
        const pausedState: PersistedStudyTimerState = {
          ...s,
          accumulatedElapsedMs: acumuladoFinal,
          runningSince: null,
          updatedAt: Date.now()
        };
        salvarEstadoPersistido(uidAntigo, pausedState);
      }
      setState({ ...EMPTY_PERSISTED_STATE });
    };

    window.addEventListener('trilhara:auth:logout', handleLogout);
    return () => window.removeEventListener('trilhara:auth:logout', handleLogout);
  }, []);

  // Quando o usuarioId mudar (troca de conta ou login pós logout)
  useEffect(() => {
    if (usuarioIdRef.current !== usuarioId) {
      // Se a conta anterior tinha timer rodando, pausa e persiste nela
      const prevUid = usuarioIdRef.current;
      const s = stateRef.current;
      if (s.runningSince !== null) {
        const elapsedDelta = Math.max(0, Date.now() - s.runningSince);
        const acumulado = s.accumulatedElapsedMs + elapsedDelta;
        const pausedState: PersistedStudyTimerState = {
          ...s,
          accumulatedElapsedMs: acumulado,
          runningSince: null,
          updatedAt: Date.now()
        };
        salvarEstadoPersistido(prevUid, pausedState);
      }

      usuarioIdRef.current = usuarioId;
      // Carrega estado da nova conta
      const novoEstado = carregarEstadoPersistido(usuarioId);
      setState(novoEstado);
    }
  }, [usuarioId]);

  // Intervalo para atualizar timestamp do relógio e disparar alarmes de pausa
  const isRunning = state.runningSince !== null || state.breakEndsAt !== null;

  useEffect(() => {
    if (!isRunning) return;

    const intervalId = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(intervalId);
  }, [isRunning]);

  // Alarme de pausa expirada
  useEffect(() => {
    if (state.breakEndsAt !== null && state.breakEndsAt <= now) {
      if (state.breakType === 'short') {
        AlarmService.dispararAlarmeFimPausaCurta();
      } else if (state.breakType === 'long') {
        AlarmService.dispararAlarmeFimPausaLonga();
      }

      setState((current) => {
        const next: PersistedStudyTimerState = {
          ...current,
          breakType: null,
          breakEndsAt: null,
          updatedAt: Date.now()
        };
        broadcastState(next);
        return next;
      });
    }
  }, [now, state.breakEndsAt, state.breakType, broadcastState]);

  const pause = useCallback(() => {
    setState((current) => {
      if (current.runningSince === null) return current;
      const pausedAt = Date.now();
      const elapsedDelta = Math.max(0, pausedAt - current.runningSince);
      const next: PersistedStudyTimerState = {
        ...current,
        accumulatedElapsedMs: current.accumulatedElapsedMs + elapsedDelta,
        runningSince: null,
        updatedAt: pausedAt
      };
      broadcastState(next);
      return next;
    });
  }, [broadcastState]);

  const start = useCallback((_timerId?: string, opts?: StartTimerOptions): boolean => {
    const startedAt = Date.now();
    let permitiu = true;

    setState((current) => {
      // Se estiver mudando de contexto e houver tempo acumulado >= 60s, pede confirmação
      const novoTrilhaId = opts?.trilhaId || current.trilhaId;
      const novoDisciplinaId = opts?.disciplinaId || current.disciplinaId;
      const contextoMudou =
        (opts?.trilhaId && opts.trilhaId !== current.trilhaId) ||
        (opts?.disciplinaId && opts.disciplinaId !== current.disciplinaId);

      const tempoTotalAcumuladoMs =
        current.accumulatedElapsedMs + (current.runningSince ? Math.max(0, startedAt - current.runningSince) : 0);

      if (contextoMudou && tempoTotalAcumuladoMs >= 60_000 && !opts?.force) {
        if (typeof window !== 'undefined' && typeof window.confirm === 'function') {
          const confirmou = window.confirm(
            'Existe uma sessão com mais de 1 minuto em outra disciplina. Deseja pausar o cronômetro anterior e iniciar este?'
          );
          if (!confirmou) {
            permitiu = false;
            return current;
          }
        }
      }

      // Se já estava rodando e não mudou contexto, apenas mantém
      if (current.runningSince !== null && !contextoMudou) {
        return current;
      }

      // Se mudou de contexto, pausa o anterior e redefine contexto
      const next: PersistedStudyTimerState = {
        ...current,
        trilhaId: novoTrilhaId,
        disciplinaId: novoDisciplinaId,
        blocoId: opts?.blocoId !== undefined ? opts.blocoId : current.blocoId,
        mode: opts?.mode || current.mode || 'progressivo',
        targetSeconds: opts?.targetSeconds !== undefined ? opts.targetSeconds : current.targetSeconds,
        startedAt: current.startedAt || new Date(startedAt).toISOString(),
        runningSince: startedAt,
        breakType: null,
        breakEndsAt: null,
        updatedAt: startedAt
      };
      broadcastState(next);
      return next;
    });

    return permitiu;
  }, [broadcastState]);

  const startBreak = useCallback((type: BreakType, durationMinutes: number) => {
    const breakStartedAt = Date.now();
    setState((current) => {
      const elapsedDelta = current.runningSince === null ? 0 : Math.max(0, breakStartedAt - current.runningSince);
      const next: PersistedStudyTimerState = {
        ...current,
        accumulatedElapsedMs: current.accumulatedElapsedMs + elapsedDelta,
        runningSince: null,
        breakType: type,
        breakEndsAt: breakStartedAt + durationMinutes * 60_000,
        updatedAt: breakStartedAt
      };
      broadcastState(next);
      return next;
    });
  }, [broadcastState]);

  const endBreak = useCallback((resumeFocus: boolean) => {
    const resumedAt = Date.now();
    setState((current) => {
      const next: PersistedStudyTimerState = {
        ...current,
        startedAt: resumeFocus
          ? current.startedAt || new Date(resumedAt).toISOString()
          : current.startedAt,
        runningSince: resumeFocus ? resumedAt : null,
        breakType: null,
        breakEndsAt: null,
        updatedAt: resumedAt
      };
      broadcastState(next);
      return next;
    });
  }, [broadcastState]);

  const reset = useCallback((_targetId?: string) => {
    setState((current) => {
      const next: PersistedStudyTimerState = {
        ...current,
        accumulatedElapsedMs: 0,
        runningSince: null,
        startedAt: null,
        breakType: null,
        breakEndsAt: null,
        updatedAt: Date.now()
      };
      broadcastState(next);
      return next;
    });
  }, [broadcastState]);

  const move = useCallback((_fromId: string, toId: string) => {
    setState((current) => {
      const next: PersistedStudyTimerState = {
        ...current,
        trilhaId: toId,
        updatedAt: Date.now()
      };
      broadcastState(next);
      return next;
    });
  }, [broadcastState]);

  const setTimerContext = useCallback(
    (
      trilhaId: string,
      disciplinaId: string,
      blocoId?: string,
      mode: 'progressivo' | 'regressivo' = 'progressivo',
      targetSeconds?: number
    ) => {
      setState((current) => {
        const next: PersistedStudyTimerState = {
          ...current,
          trilhaId,
          disciplinaId,
          blocoId,
          mode,
          targetSeconds,
          updatedAt: Date.now()
        };
        broadcastState(next);
        return next;
      });
    },
    [broadcastState]
  );

  // Mapa de compatibilidade para acessos legados via context.timers[id]
  const timers = useMemo(() => {
    const timerMap: Record<string, {
      elapsedMs: number;
      startedAt: string | null;
      runningSince: number | null;
      breakType: BreakType | null;
      breakEndsAt: number | null;
    }> = {};

    const activeTimer = {
      elapsedMs: state.accumulatedElapsedMs,
      startedAt: state.startedAt,
      runningSince: state.runningSince,
      breakType: state.breakType,
      breakEndsAt: state.breakEndsAt
    };

    timerMap[state.trilhaId] = activeTimer;
    timerMap['__sem_trilha__'] = activeTimer;

    return timerMap;
  }, [state]);

  const value = useMemo<StudyTimerContextValue>(
    () => ({
      state,
      now,
      start,
      pause,
      startBreak,
      endBreak,
      reset,
      move,
      setTimerContext,
      timers
    }),
    [state, now, start, pause, startBreak, endBreak, reset, move, setTimerContext, timers]
  );

  return <StudyTimerContext.Provider value={value}>{children}</StudyTimerContext.Provider>;
};

export function useStudyTimer(_timerId?: string): StudyTimerSnapshot & {
  start: (opts?: StartTimerOptions) => boolean;
  pause: () => void;
  startBreak: (type: BreakType, durationMinutes: number) => void;
  endBreak: (resumeFocus: boolean) => void;
  reset: (targetId?: string) => void;
  move: (toId: string) => void;
  setTimerContext: (
    trilhaId: string,
    disciplinaId: string,
    blocoId?: string,
    mode?: 'progressivo' | 'regressivo',
    targetSeconds?: number
  ) => void;
} {
  const context = useContext(StudyTimerContext);
  if (!context) throw new Error('useStudyTimer deve ser usado dentro de StudyTimerProvider.');

  const { state, now } = context;

  const elapsedMs =
    state.accumulatedElapsedMs +
    (state.runningSince === null ? 0 : Math.max(0, now - state.runningSince));

  const breakSeconds =
    state.breakEndsAt === null
      ? 0
      : Math.max(0, Math.ceil((state.breakEndsAt - now) / 1000));

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
    start: (opts?: StartTimerOptions) => context.start(_timerId, opts),
    pause: () => context.pause(),
    startBreak: (type, durationMinutes) => context.startBreak(type, durationMinutes),
    endBreak: (resumeFocus) => context.endBreak(resumeFocus),
    reset: (targetId) => context.reset(targetId || _timerId),
    move: (toId) => context.move(_timerId || state.trilhaId, toId),
    setTimerContext: context.setTimerContext
  };
}
