import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  BellOff,
  Coffee,
  ExternalLink,
  HelpCircle,
  Pause,
  Play,
  RotateCcw,
  Settings,
  Square,
  Timer,
  Check,
  BookOpen,
  ListTodo,
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  X,
  LogIn
} from 'lucide-react';
import {
  Button,
  Checkbox,
  FormModal,
  Input,
  Modal,
  PageBanner,
  Switch,
  ThemeToggle,
  Badge,
  DisciplineLabel,
  SectionErrorCard,
  useGlobalSessionModal,
  useToast,
  EmptyState
} from '@shared/design-system';
import { useAuth } from '@shared/auth';
import { useAgendaHoje } from '@shared/services/agendaHojeService';
import { BlocoHojeDTO, TarefaDTO } from '@shared/contracts/domainContracts';
import { TarefaService } from '../tarefas/services/tarefaService';
import logoMark from '@shared/assets/logo-mark.svg';
import styles from './App.module.css';

// Ícone SVG de Tomate para representar a Técnica Pomodoro
const TomatoIcon = ({ size = 22 }: { size?: number }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    {/* Caule e Folhas Verdes */}
    <path
      d="M12 2C12 3.8 11.2 5.2 10.5 5.8C8.5 5.5 6.8 6.2 6.2 7C7.8 7.2 9.5 6.6 10.8 6.2C10.2 7.8 10.4 9.2 11.5 9.8C11.8 8.4 12.6 7.2 13.5 6.8C14.8 7.5 16.2 7.6 17.5 7.2C16.8 6.5 15.2 6 13.8 6.2C14.5 5.5 15 4 14.2 2.5C13.5 3.5 12.8 4.5 12.5 5.5C12.3 4.2 12.2 3 12 2Z"
      fill="#10b981"
    />
    {/* Corpo do Tomate */}
    <path
      d="M12 7C7.2 7 3.5 10.2 3.5 14.5C3.5 18.5 7 21.5 11.5 21.5C12.2 21.5 12.8 21.4 13.5 21.3C17.5 21 20.5 18.2 20.5 14.5C20.5 10.2 16.8 7 12 7Z"
      fill="#ef4444"
    />
    {/* Brilho suave */}
    <ellipse cx="8.5" cy="11.5" rx="2.5" ry="1.5" transform="rotate(-25 8.5 11.5)" fill="white" fillOpacity="0.35" />
  </svg>
);

type BreakType = 'short' | 'long';

interface PomodoroConfig {
  short: number;
  long: number;
}

const DEFAULT_POMODORO_MINUTES = 25;
const STORAGE_POMODORO_MINUTES_KEY = 'trilhara_pomodoro_focus_minutes';
const STORAGE_POMODORO_SECONDS_KEY = 'trilhara_pomodoro_focus_seconds';
const STORAGE_CONFIG_KEY = 'trilhara_pomodoro_config';
const STORAGE_SOUND_KEY = 'trilhara_alarme_som';
const STORAGE_NOTIF_KEY = 'trilhara_alarme_notif';
const LEGACY_STORAGE_SOUND_KEY = 'trilhara_pomodoro_sound';
const LEGACY_STORAGE_NOTIF_KEY = 'trilhara_pomodoro_notification';
const STORAGE_POMODORO_MODE_KEY = 'trilhara_pomodoro_mode_active';

function formatarSegundos(totalSegundos: number): string {
  const minutos = Math.floor(totalSegundos / 60);
  const segundos = totalSegundos % 60;
  return `${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`;
}

function lerDuracaoLocalStorage(chave: string, padrao: number, minimo: number, maximo: number): number {
  if (typeof window === 'undefined') return padrao;
  const valor = Number(window.localStorage.getItem(chave));
  return Number.isInteger(valor) && valor >= minimo && valor <= maximo ? valor : padrao;
}

function lerBooleanoLocalStorage(chave: string, padrao: boolean, chaveFallback?: string): boolean {
  if (typeof window === 'undefined') return padrao;
  const valor = window.localStorage.getItem(chave);
  if (valor !== null) return valor === 'true';
  if (chaveFallback) {
    const valorFallback = window.localStorage.getItem(chaveFallback);
    if (valorFallback !== null) return valorFallback === 'true';
  }
  return padrao;
}

class SoundEngine {
  private ctx: AudioContext | null = null;

  private getContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    try {
      if (!this.ctx) {
        const AudioCtx =
          window.AudioContext ||
          (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        if (AudioCtx) {
          this.ctx = new AudioCtx();
        }
      }
      if (this.ctx && this.ctx.state === 'suspended') {
        void this.ctx.resume();
      }
      return this.ctx;
    } catch {
      return null;
    }
  }

  playChime(notes: number[], noteDuration = 0.2, type: OscillatorType = 'sine') {
    try {
      const ctx = this.getContext();
      if (!ctx) return;

      const now = ctx.currentTime;
      notes.forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = type;
        osc.frequency.setValueAtTime(freq, now + idx * noteDuration);

        const startTime = now + idx * noteDuration;
        const endTime = startTime + noteDuration * 1.8;

        gain.gain.setValueAtTime(0.0001, startTime);
        gain.gain.exponentialRampToValueAtTime(0.35, startTime + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, endTime);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(startTime);
        osc.stop(endTime);
      });
    } catch {
      // Silencia falhas do navegador sem interromper o cronômetro
    }
  }

  playFimFoco() {
    this.playChime([523.25, 659.25, 783.99, 1046.5], 0.22);
  }

  playPausaCurta() {
    this.playChime([659.25, 880.0, 659.25], 0.22);
  }

  playPausaLonga() {
    this.playChime([587.33, 783.99, 987.77], 0.24);
  }

  playTeste() {
    this.playChime([523.25, 659.25, 783.99], 0.18);
  }
}

const soundEngine = new SoundEngine();

let titleInterval: number | null = null;
let originalTitle = typeof document !== 'undefined' ? document.title : 'Trilhara — Pomodoro';

function alertarAbaNavegador(mensagem: string): void {
  if (typeof document === 'undefined') return;
  if (titleInterval) {
    clearInterval(titleInterval);
    titleInterval = null;
  }

  originalTitle = document.title;
  let alternar = false;
  let iteracoes = 0;

  titleInterval = window.setInterval(() => {
    document.title = alternar ? `⏰ ${mensagem}` : originalTitle;
    alternar = !alternar;
    iteracoes++;

    if (iteracoes > 24) {
      if (titleInterval) clearInterval(titleInterval);
      titleInterval = null;
      document.title = originalTitle;
    }
  }, 900);

  const limparAoFocar = () => {
    if (titleInterval) {
      clearInterval(titleInterval);
      titleInterval = null;
    }
    document.title = originalTitle;
    window.removeEventListener('focus', limparAoFocar);
  };

  window.addEventListener('focus', limparAoFocar);
}

export function getPomodoroTimerStorageKey(usuarioId?: string | null): string {
  return `trilhara:u:${usuarioId || 'anon'}:pomodoro_timer`;
}

export default function PomodoroApp() {
  const toast = useToast();
  const { usuario, isAuthenticated, abrirAuthModal } = useAuth();
  const { abrirModal: abrirModalSessao } = useGlobalSessionModal();
  const { dados: agendaHoje, carregando: carregandoAgenda, recarregar: recarregarAgenda, notificarMudanca } = useAgendaHoje();
  const [focoItem, setFocoItem] = useState<{ tipo: 'TAREFA' | 'BLOCO'; id: string; titulo: string } | null>(null);
  const [expansoresAtrasadas, setExpansoresAtrasadas] = useState<Record<string, boolean>>({});

  const usuarioId = usuario?.id || null;
  const usuarioIdRef = useRef<string | null>(usuarioId);
  const instanceIdRef = useRef<string>(`pomo_${crypto.randomUUID().slice(0, 8)}`);
  const broadcastChannelRef = useRef<BroadcastChannel | null>(null);

  const handleConcluirTarefa = async (tarefaId: string) => {
    try {
      await TarefaService.alternarConclusao(tarefaId, true);
      if (focoItem?.id === tarefaId) setFocoItem(null);
      notificarMudanca();
    } catch (err) {
      toast.error('Não foi possível concluir a tarefa.');
    }
  };

  const handleConcluirBloco = (bloco: BlocoHojeDTO) => {
    abrirModalSessao({
      modo: 'BLOCO_DIRETO',
      trilhaId: bloco.trilhaId,
      disciplinaId: bloco.disciplinaId,
      blocoId: bloco.blocoId || (bloco as any).id,
      duracaoInicialSegundos: (bloco.duracaoMinutos || 25) * 60,
      dataSessao: bloco.data,
      trilhaNome: bloco.trilhaNome,
      disciplinaNome: bloco.disciplinaNome,
      disciplinaCor: bloco.disciplinaCor,
      onSuccess: () => {
        if (focoItem?.id === (bloco.blocoId || (bloco as any).id)) setFocoItem(null);
        notificarMudanca();
      }
    });
  };

  const [pomodoroMode, setPomodoroMode] = useState<boolean>(() => {
    try {
      const raw = typeof window !== 'undefined' ? window.localStorage.getItem(getPomodoroTimerStorageKey(usuarioId)) : null;
      if (raw) {
        const parsed = JSON.parse(raw);
        if (typeof parsed.pomodoroMode === 'boolean') return parsed.pomodoroMode;
      }
    } catch {}
    return lerBooleanoLocalStorage(STORAGE_POMODORO_MODE_KEY, true);
  });

  const [infoModalAberto, setInfoModalAberto] = useState(false);
  const [pomodoroCyclesCount, setPomodoroCyclesCount] = useState(0);

  // Tempo do modo Pomodoro em segundos (regressivo, editável com minutos e segundos)
  const [pomodoroDurationSeconds, setPomodoroDurationSeconds] = useState<number>(() => {
    try {
      const raw = typeof window !== 'undefined' ? window.localStorage.getItem(getPomodoroTimerStorageKey(usuarioId)) : null;
      if (raw) {
        const parsed = JSON.parse(raw);
        if (typeof parsed.pomodoroDurationSeconds === 'number' && parsed.pomodoroDurationSeconds > 0) {
          return parsed.pomodoroDurationSeconds;
        }
      }
    } catch {}
    const secStr = typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_POMODORO_SECONDS_KEY) : null;
    if (secStr) {
      const val = Number.parseInt(secStr, 10);
      if (!Number.isNaN(val) && val > 0) return val;
    }
    const mins = lerDuracaoLocalStorage(STORAGE_POMODORO_MINUTES_KEY, DEFAULT_POMODORO_MINUTES, 1, 360);
    return mins * 60;
  });

  const pomodoroMinutes = Math.max(1, Math.round(pomodoroDurationSeconds / 60));

  // Segundos do relógio:
  // - Modo Pomodoro: regressivo (inicia no pomodoroDurationSeconds)
  // - Timer Manual: progressivo (inicia em 0)
  const [timerSeconds, setTimerSeconds] = useState<number>(() => {
    try {
      const raw = typeof window !== 'undefined' ? window.localStorage.getItem(getPomodoroTimerStorageKey(usuarioId)) : null;
      if (raw) {
        const parsed = JSON.parse(raw);
        if (typeof parsed.timerSeconds === 'number') return parsed.timerSeconds;
      }
    } catch {}
    const isPomodoroActive = lerBooleanoLocalStorage(STORAGE_POMODORO_MODE_KEY, true);
    if (isPomodoroActive) {
      const secStr = typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_POMODORO_SECONDS_KEY) : null;
      if (secStr) {
        const val = Number.parseInt(secStr, 10);
        if (!Number.isNaN(val) && val > 0) return val;
      }
      const mins = lerDuracaoLocalStorage(STORAGE_POMODORO_MINUTES_KEY, DEFAULT_POMODORO_MINUTES, 1, 360);
      return mins * 60;
    }
    return 0; // Timer Manual começa em 0
  });

  const [isRunning, setIsRunning] = useState(false);
  const [breakType, setBreakType] = useState<BreakType | null>(null);
  const [breakSeconds, setBreakSeconds] = useState(0);

  // Edição inline dos dígitos (somente no modo Pomodoro)
  const [isEditingInline, setIsEditingInline] = useState(false);
  const [editMinutesStr, setEditMinutesStr] = useState('');
  const [editSecondsStr, setEditSecondsStr] = useState('');
  const minutesInputRef = useRef<HTMLInputElement>(null);
  const secondsInputRef = useRef<HTMLInputElement>(null);
  const inlineFormRef = useRef<HTMLFormElement>(null);

  const [pausas, setPausas] = useState<PomodoroConfig>(() => ({
    short: lerDuracaoLocalStorage('trilhara_pomodoro_short', 5, 1, 60),
    long: lerDuracaoLocalStorage('trilhara_pomodoro_long', 15, 1, 120)
  }));
  const [configModalAberto, setConfigModalAberto] = useState(false);
  const [configPausas, setConfigPausas] = useState<PomodoroConfig>(pausas);

  const [somAtivo, setSomAtivo] = useState<boolean>(() =>
    lerBooleanoLocalStorage(STORAGE_SOUND_KEY, true, LEGACY_STORAGE_SOUND_KEY)
  );
  const [notificacaoAtiva, setNotificacaoAtiva] = useState<boolean>(() =>
    lerBooleanoLocalStorage(STORAGE_NOTIF_KEY, true, LEGACY_STORAGE_NOTIF_KEY)
  );

  const timerSecondsRef = useRef(timerSeconds);
  timerSecondsRef.current = timerSeconds;

  const breakSecondsRef = useRef(breakSeconds);
  breakSecondsRef.current = breakSeconds;

  const alarmeFocoDisparadoRef = useRef(false);
  const targetEndTimeRef = useRef<number | null>(null);
  const stopwatchStartTimeRef = useRef<number | null>(null);
  const stopwatchBaseSecondsRef = useRef<number>(0);
  const breakEndTimeRef = useRef<number | null>(null);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_POMODORO_MODE_KEY, String(pomodoroMode));
  }, [pomodoroMode]);

  // Persiste a duração de foco configurada do Pomodoro
  useEffect(() => {
    window.localStorage.setItem(STORAGE_POMODORO_SECONDS_KEY, String(pomodoroDurationSeconds));
    window.localStorage.setItem(STORAGE_POMODORO_MINUTES_KEY, String(pomodoroMinutes));
  }, [pomodoroDurationSeconds, pomodoroMinutes]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_CONFIG_KEY, JSON.stringify(pausas));
    window.localStorage.setItem('trilhara_pomodoro_short', String(pausas.short));
    window.localStorage.setItem('trilhara_pomodoro_long', String(pausas.long));
  }, [pausas]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_SOUND_KEY, String(somAtivo));
  }, [somAtivo]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_NOTIF_KEY, String(notificacaoAtiva));
  }, [notificacaoAtiva]);

  // Sincroniza preferências de alarme se alteradas em outra aba ou pelo módulo de Estudos
  useEffect(() => {
    const handleStorageChange = (event: StorageEvent) => {
      if (event.key === STORAGE_SOUND_KEY && event.newValue !== null) {
        setSomAtivo(event.newValue === 'true');
      } else if (event.key === STORAGE_NOTIF_KEY && event.newValue !== null) {
        setNotificacaoAtiva(event.newValue === 'true');
      } else if (event.key === getPomodoroTimerStorageKey(usuarioIdRef.current) && event.newValue) {
        try {
          const s = JSON.parse(event.newValue);
          if (s) {
            setPomodoroMode(s.pomodoroMode);
            setPomodoroDurationSeconds(s.pomodoroDurationSeconds);
            setPomodoroCyclesCount(s.pomodoroCyclesCount ?? 0);
            if (s.pausas) setPausas(s.pausas);
            if (typeof s.somAtivo === 'boolean') setSomAtivo(s.somAtivo);
            if (typeof s.notificacaoAtiva === 'boolean') setNotificacaoAtiva(s.notificacaoAtiva);

            if (s.breakType && s.breakEndTime) {
              const rest = Math.max(0, Math.ceil((s.breakEndTime - Date.now()) / 1000));
              setBreakType(s.breakType);
              setBreakSeconds(rest);
              breakEndTimeRef.current = s.breakEndTime;
              setIsRunning(false);
            } else if (s.isRunning) {
              setBreakType(null);
              setBreakSeconds(0);
              if (s.pomodoroMode && s.targetEndTime) {
                const rest = Math.max(0, Math.ceil((s.targetEndTime - Date.now()) / 1000));
                setTimerSeconds(rest);
                targetEndTimeRef.current = s.targetEndTime;
                setIsRunning(rest > 0);
              } else if (!s.pomodoroMode && s.stopwatchStartTime) {
                const elapsed = Math.floor((Date.now() - s.stopwatchStartTime) / 1000);
                setTimerSeconds(s.stopwatchBaseSeconds + elapsed);
                stopwatchStartTimeRef.current = s.stopwatchStartTime;
                stopwatchBaseSecondsRef.current = s.stopwatchBaseSeconds;
                setIsRunning(true);
              }
            } else {
              setIsRunning(false);
              setTimerSeconds(s.timerSeconds);
              setBreakType(null);
              setBreakSeconds(0);
              targetEndTimeRef.current = null;
              stopwatchStartTimeRef.current = null;
            }
          }
        } catch {}
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  // BroadcastChannel para sincronização instantânea de estado entre abas
  useEffect(() => {
    if (typeof window === 'undefined' || !('BroadcastChannel' in window)) return;
    try {
      const channel = new BroadcastChannel('trilhara_pomodoro_timer');
      broadcastChannelRef.current = channel;

      channel.onmessage = (event) => {
        const data = event.data;
        if (!data || data.senderId === instanceIdRef.current) return;
        if (data.usuarioId !== (usuarioIdRef.current || 'anon')) return;

        if (data.type === 'SYNC_POMODORO' && data.state) {
          const s = data.state;
          setPomodoroMode(s.pomodoroMode);
          setPomodoroDurationSeconds(s.pomodoroDurationSeconds);
          setPomodoroCyclesCount(s.pomodoroCyclesCount ?? 0);
          if (s.pausas) setPausas(s.pausas);
          if (typeof s.somAtivo === 'boolean') setSomAtivo(s.somAtivo);
          if (typeof s.notificacaoAtiva === 'boolean') setNotificacaoAtiva(s.notificacaoAtiva);

          if (s.breakType && s.breakEndTime) {
            const rest = Math.max(0, Math.ceil((s.breakEndTime - Date.now()) / 1000));
            setBreakType(s.breakType);
            setBreakSeconds(rest);
            breakEndTimeRef.current = s.breakEndTime;
            setIsRunning(false);
          } else if (s.isRunning) {
            setBreakType(null);
            setBreakSeconds(0);
            if (s.pomodoroMode && s.targetEndTime) {
              const rest = Math.max(0, Math.ceil((s.targetEndTime - Date.now()) / 1000));
              setTimerSeconds(rest);
              targetEndTimeRef.current = s.targetEndTime;
              setIsRunning(rest > 0);
            } else if (!s.pomodoroMode && s.stopwatchStartTime) {
              const elapsed = Math.floor((Date.now() - s.stopwatchStartTime) / 1000);
              setTimerSeconds(s.stopwatchBaseSeconds + elapsed);
              stopwatchStartTimeRef.current = s.stopwatchStartTime;
              stopwatchBaseSecondsRef.current = s.stopwatchBaseSeconds;
              setIsRunning(true);
            }
          } else {
            setIsRunning(false);
            setTimerSeconds(s.timerSeconds);
            setBreakType(null);
            setBreakSeconds(0);
            targetEndTimeRef.current = null;
            stopwatchStartTimeRef.current = null;
          }
        }
      };

      return () => {
        channel.close();
        broadcastChannelRef.current = null;
      };
    } catch {}
  }, []);

  // Logout e Troca de Conta: pausa o timer e persiste sob a chave isolada da conta
  useEffect(() => {
    const handleLogout = (e?: Event) => {
      const customEvent = e as CustomEvent<{ usuarioId?: string }>;
      const uidAntigo = customEvent?.detail?.usuarioId || usuarioIdRef.current;
      targetEndTimeRef.current = null;
      stopwatchStartTimeRef.current = null;
      setIsRunning(false);
      try {
        const key = getPomodoroTimerStorageKey(uidAntigo);
        window.localStorage.setItem(
          key,
          JSON.stringify({
            pomodoroMode,
            isRunning: false,
            targetEndTime: null,
            stopwatchStartTime: null,
            stopwatchBaseSeconds: 0,
            timerSeconds: timerSecondsRef.current,
            pomodoroDurationSeconds,
            pomodoroCyclesCount,
            breakType: null,
            breakEndTime: null,
            breakSeconds: 0,
            pausas,
            somAtivo,
            notificacaoAtiva,
            updatedAt: Date.now()
          })
        );
      } catch {}
    };

    window.addEventListener('trilhara:auth:logout', handleLogout);
    return () => window.removeEventListener('trilhara:auth:logout', handleLogout);
  }, [
    pomodoroMode,
    pomodoroDurationSeconds,
    pomodoroCyclesCount,
    pausas,
    somAtivo,
    notificacaoAtiva
  ]);

  // Ao alternar usuarioId (login/troca de usuário)
  useEffect(() => {
    if (usuarioIdRef.current !== usuarioId) {
      usuarioIdRef.current = usuarioId;
      try {
        const raw = window.localStorage.getItem(getPomodoroTimerStorageKey(usuarioId));
        if (raw) {
          const s = JSON.parse(raw);
          setPomodoroMode(s.pomodoroMode);
          setPomodoroDurationSeconds(s.pomodoroDurationSeconds);
          setTimerSeconds(s.timerSeconds);
          setIsRunning(false);
          setBreakType(null);
          setBreakSeconds(0);
          setPomodoroCyclesCount(s.pomodoroCyclesCount ?? 0);
          if (s.pausas) setPausas(s.pausas);
        } else {
          setIsRunning(false);
          setBreakType(null);
          setBreakSeconds(0);
          setTimerSeconds(pomodoroDurationSeconds);
        }
      } catch {}
    }
  }, [usuarioId, pomodoroDurationSeconds]);

  const salvarETransmitir = useCallback((overrides: Record<string, any> = {}) => {
    const key = getPomodoroTimerStorageKey(usuarioIdRef.current);
    const estadoSalvar = {
      pomodoroMode,
      isRunning,
      targetEndTime: targetEndTimeRef.current,
      stopwatchStartTime: stopwatchStartTimeRef.current,
      stopwatchBaseSeconds: stopwatchBaseSecondsRef.current,
      timerSeconds: timerSecondsRef.current,
      pomodoroDurationSeconds,
      pomodoroCyclesCount,
      breakType,
      breakEndTime: breakEndTimeRef.current,
      breakSeconds: breakSecondsRef.current,
      pausas,
      somAtivo,
      notificacaoAtiva,
      updatedAt: Date.now(),
      ...overrides
    };

    try {
      window.localStorage.setItem(key, JSON.stringify(estadoSalvar));
    } catch {}

    if (broadcastChannelRef.current) {
      try {
        broadcastChannelRef.current.postMessage({
          type: 'SYNC_POMODORO',
          senderId: instanceIdRef.current,
          usuarioId: usuarioIdRef.current || 'anon',
          state: estadoSalvar
        });
      } catch {}
    }
  }, [
    pomodoroMode,
    isRunning,
    pomodoroDurationSeconds,
    pomodoroCyclesCount,
    breakType,
    pausas,
    somAtivo,
    notificacaoAtiva
  ]);

  // Contagem do foco baseada em timestamp absoluto (imune a timer throttling e drift em segundo plano)
  useEffect(() => {
    if (!isRunning) {
      targetEndTimeRef.current = null;
      stopwatchStartTimeRef.current = null;
      return;
    }

    if (pomodoroMode) {
      if (!targetEndTimeRef.current) {
        targetEndTimeRef.current = Date.now() + timerSecondsRef.current * 1000;
      }
    } else {
      if (!stopwatchStartTimeRef.current) {
        stopwatchStartTimeRef.current = Date.now();
        stopwatchBaseSecondsRef.current = timerSecondsRef.current;
      }
    }

    const atualizarTempo = () => {
      if (pomodoroMode) {
        if (!targetEndTimeRef.current) return;
        const restanteMs = targetEndTimeRef.current - Date.now();
        const restanteSegundos = Math.max(0, Math.ceil(restanteMs / 1000));

        if (restanteSegundos <= 0) {
          targetEndTimeRef.current = null;
          setIsRunning(false);
          setTimerSeconds(0);
        } else {
          setTimerSeconds(restanteSegundos);
        }
      } else {
        if (!stopwatchStartTimeRef.current) return;
        const decorridoMs = Date.now() - stopwatchStartTimeRef.current;
        const decorridoSegundos = Math.floor(decorridoMs / 1000);
        setTimerSeconds(stopwatchBaseSecondsRef.current + decorridoSegundos);
      }
    };

    // Atualiza imediatamente e configura intervalo frequente (250ms) para renderização suave
    atualizarTempo();
    const intervalId = window.setInterval(atualizarTempo, 250);

    // Sincronização atômica ao retornar para a aba do navegador
    const handleVisibilityChange = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        atualizarTempo();
      }
    };

    window.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleVisibilityChange);
    };
  }, [isRunning, pomodoroMode]);

  // Contagem regressiva da pausa baseada em timestamp absoluto
  useEffect(() => {
    if (!breakType) {
      breakEndTimeRef.current = null;
      return;
    }

    if (!breakEndTimeRef.current) {
      breakEndTimeRef.current = Date.now() + breakSecondsRef.current * 1000;
    }

    const atualizarPausa = () => {
      if (!breakEndTimeRef.current) return;
      const restanteMs = breakEndTimeRef.current - Date.now();
      const restanteSegundos = Math.max(0, Math.ceil(restanteMs / 1000));

      if (restanteSegundos <= 0) {
        breakEndTimeRef.current = null;
        const terminouTipo = breakType;
        setBreakType(null);
        setBreakSeconds(0);

        if (pomodoroMode) {
          setTimerSeconds(pomodoroDurationSeconds);
          targetEndTimeRef.current = null;
        } else {
          setTimerSeconds(0);
          stopwatchStartTimeRef.current = null;
          stopwatchBaseSecondsRef.current = 0;
        }

        if (somAtivo) {
          if (terminouTipo === 'short') {
            soundEngine.playPausaCurta();
          } else {
            soundEngine.playPausaLonga();
          }
        }

        if (notificacaoAtiva && 'Notification' in window && Notification.permission === 'granted') {
          new Notification(
            terminouTipo === 'short' ? 'Pausa curta finalizada!' : 'Pausa longa finalizada!',
            {
              body: 'Seu descanso terminou. Pronto para voltar ao foco?',
              icon: '/favicon.svg',
              silent: true
            }
          );
        }

        alertarAbaNavegador(terminouTipo === 'short' ? 'Pausa curta finalizada!' : 'Pausa longa finalizada!');

        // Aguarda clique explícito em "Iniciar foco", evitando ciclos fantasmas sem o usuário presente
        setIsRunning(false);
      } else {
        setBreakSeconds(restanteSegundos);
      }
    };

    atualizarPausa();
    const intervalId = window.setInterval(atualizarPausa, 250);

    const handleVisibilityChange = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        atualizarPausa();
      }
    };

    window.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleVisibilityChange);
    };
  }, [breakType, notificacaoAtiva, pomodoroDurationSeconds, pomodoroMode, somAtivo]);

  // Disparo ao concluir o foco no modo Pomodoro (atingir 0)
  useEffect(() => {
    if (!pomodoroMode) return;

    if (timerSeconds > 0) {
      alarmeFocoDisparadoRef.current = false;
      return;
    }

    if (timerSeconds === 0 && !isRunning && !alarmeFocoDisparadoRef.current) {
      alarmeFocoDisparadoRef.current = true;

      if (somAtivo) {
        soundEngine.playFimFoco();
      }

      if (notificacaoAtiva && 'Notification' in window && Notification.permission === 'granted') {
        new Notification('Tempo de foco concluído!', {
          body: `Seu bloco de ${pomodoroMinutes} minutos terminou. Faça uma pausa.`,
          icon: '/favicon.svg',
          silent: true
        });
      }

      alertarAbaNavegador('Tempo de foco concluído!');

      const proximoCiclo = pomodoroCyclesCount + 1;
      setPomodoroCyclesCount(proximoCiclo);

      const ehPausaLonga = proximoCiclo % 4 === 0;
      const tipoPausa: BreakType = ehPausaLonga ? 'long' : 'short';
      setBreakType(tipoPausa);
      const duracaoSegundos = pausas[tipoPausa] * 60;
      setBreakSeconds(duracaoSegundos);
      breakEndTimeRef.current = Date.now() + duracaoSegundos * 1000;
    }
  }, [isRunning, notificacaoAtiva, pausas, pomodoroCyclesCount, pomodoroMinutes, pomodoroMode, somAtivo, timerSeconds]);

  const totalPomodoroSeconds = pomodoroDurationSeconds;

  const isTimerAtStart = pomodoroMode
    ? timerSeconds === totalPomodoroSeconds
    : timerSeconds === 0;

  const status = useMemo(() => {
    if (breakType) {
      return `Pausa ${breakType === 'short' ? 'curta' : 'longa'} em andamento`;
    }
    if (isRunning) return 'Foco em andamento';
    if (!isTimerAtStart) {
      return 'Foco pausado. O tempo não está sendo contado';
    }
    return 'Inicie o foco quando estiver pronto';
  }, [breakType, isRunning, isTimerAtStart]);

  const tempoFormatado = formatarSegundos(timerSeconds);

  const alternarModoPomodoro = () => {
    const novoModo = !pomodoroMode;
    targetEndTimeRef.current = null;
    stopwatchStartTimeRef.current = null;
    breakEndTimeRef.current = null;
    setPomodoroMode(novoModo);
    setIsEditingInline(false);
    setIsRunning(false);
    setBreakType(null);
    setBreakSeconds(0);
    setPomodoroCyclesCount(0);
    alarmeFocoDisparadoRef.current = false;

    const novosSegundos = novoModo ? pomodoroDurationSeconds : 0;
    setTimerSeconds(novosSegundos);

    salvarETransmitir({
      pomodoroMode: novoModo,
      isRunning: false,
      targetEndTime: null,
      stopwatchStartTime: null,
      breakType: null,
      breakEndTime: null,
      breakSeconds: 0,
      pomodoroCyclesCount: 0,
      timerSeconds: novosSegundos
    });
  };

  const iniciarFoco = () => {
    if (isEditingInline) {
      concluirEdicaoInline();
    }
    let tempoAtual = timerSeconds;
    if (pomodoroMode && timerSeconds === 0) {
      tempoAtual = totalPomodoroSeconds;
      setTimerSeconds(totalPomodoroSeconds);
    }
    const novoTarget = pomodoroMode ? Date.now() + tempoAtual * 1000 : null;
    const novoStopwatchStart = !pomodoroMode ? Date.now() : null;

    if (pomodoroMode) {
      targetEndTimeRef.current = novoTarget;
    } else {
      stopwatchStartTimeRef.current = novoStopwatchStart;
      stopwatchBaseSecondsRef.current = tempoAtual;
    }
    void solicitarPermissaoNotificacao();
    setIsRunning(true);
    setBreakType(null);
    breakEndTimeRef.current = null;

    salvarETransmitir({
      isRunning: true,
      targetEndTime: novoTarget,
      stopwatchStartTime: novoStopwatchStart,
      stopwatchBaseSeconds: tempoAtual,
      timerSeconds: tempoAtual,
      breakType: null,
      breakEndTime: null,
      breakSeconds: 0
    });
  };

  const pausarFoco = () => {
    targetEndTimeRef.current = null;
    stopwatchStartTimeRef.current = null;
    setIsRunning(false);

    salvarETransmitir({
      isRunning: false,
      targetEndTime: null,
      stopwatchStartTime: null,
      timerSeconds: timerSecondsRef.current
    });
  };

  const iniciarPausa = (tipo: BreakType) => {
    if (isEditingInline) {
      concluirEdicaoInline();
    }
    targetEndTimeRef.current = null;
    stopwatchStartTimeRef.current = null;
    setIsRunning(false);
    setBreakType(tipo);
    const duracaoSegundos = pausas[tipo] * 60;
    setBreakSeconds(duracaoSegundos);
    const breakEnd = Date.now() + duracaoSegundos * 1000;
    breakEndTimeRef.current = breakEnd;

    salvarETransmitir({
      isRunning: false,
      targetEndTime: null,
      stopwatchStartTime: null,
      breakType: tipo,
      breakEndTime: breakEnd,
      breakSeconds: duracaoSegundos
    });
  };

  const voltarAoFoco = () => {
    breakEndTimeRef.current = null;
    setBreakType(null);
    setBreakSeconds(0);
    const novoTarget = pomodoroMode ? Date.now() + timerSeconds * 1000 : null;
    const novoStopwatchStart = !pomodoroMode ? Date.now() : null;

    if (pomodoroMode) {
      targetEndTimeRef.current = novoTarget;
    } else {
      stopwatchStartTimeRef.current = novoStopwatchStart;
      stopwatchBaseSecondsRef.current = timerSeconds;
    }
    setIsRunning(true);

    salvarETransmitir({
      isRunning: true,
      targetEndTime: novoTarget,
      stopwatchStartTime: novoStopwatchStart,
      stopwatchBaseSeconds: timerSeconds,
      breakType: null,
      breakEndTime: null,
      breakSeconds: 0
    });
  };

  const reiniciarCronometro = () => {
    setIsEditingInline(false);
    breakEndTimeRef.current = null;
    targetEndTimeRef.current = null;
    stopwatchStartTimeRef.current = null;
    setBreakType(null);
    setBreakSeconds(0);
    setIsRunning(false);
    const resetSec = pomodoroMode ? totalPomodoroSeconds : 0;
    setTimerSeconds(resetSec);
    alarmeFocoDisparadoRef.current = false;
    if (pomodoroMode) {
      setPomodoroCyclesCount(0);
    }

    salvarETransmitir({
      isRunning: false,
      targetEndTime: null,
      stopwatchStartTime: null,
      breakType: null,
      breakEndTime: null,
      breakSeconds: 0,
      pomodoroCyclesCount: 0,
      timerSeconds: resetSec
    });
  };

  // Funções de Edição Inline (somente permitidas no modo Pomodoro quando pausado)
  const abrirEdicaoInline = (campoFoco: 'minutes' | 'seconds' = 'minutes') => {
    if (!pomodoroMode || isRunning || breakType) return;
    const mins = Math.floor(timerSeconds / 60);
    const secs = timerSeconds % 60;
    setEditMinutesStr(String(mins).padStart(2, '0'));
    setEditSecondsStr(String(secs).padStart(2, '0'));
    setIsEditingInline(true);
    setTimeout(() => {
      if (campoFoco === 'seconds') {
        secondsInputRef.current?.focus();
      } else {
        minutesInputRef.current?.focus();
      }
    }, 50);
  };

  const concluirEdicaoInline = () => {
    const mins = Math.max(0, Math.min(360, Number.parseInt(editMinutesStr, 10) || 0));
    const secs = Math.max(0, Math.min(59, Number.parseInt(editSecondsStr, 10) || 0));
    let totalSecs = mins * 60 + secs;
    if (totalSecs < 1) {
      totalSecs = 60; // Mínimo de 1 minuto se tiver zerado tudo
    }

    setPomodoroDurationSeconds(totalSecs);
    setTimerSeconds(totalSecs);
    targetEndTimeRef.current = null;
    setIsEditingInline(false);
  };

  const cancelarEdicaoInline = () => {
    setIsEditingInline(false);
  };

  // Salva edição inline ao clicar fora da área dos inputs
  useEffect(() => {
    if (!isEditingInline) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (inlineFormRef.current && !inlineFormRef.current.contains(event.target as Node)) {
        concluirEdicaoInline();
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isEditingInline, editMinutesStr, editSecondsStr]);

  const salvarConfiguracoes = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const curta = Math.min(60, Math.max(1, Math.trunc(configPausas.short)));
    const longa = Math.min(120, Math.max(1, Math.trunc(configPausas.long)));

    const novaConfig = { short: curta, long: longa };
    setPausas(novaConfig);
    setConfigPausas(novaConfig);

    if (breakType) {
      const duracaoSegundos = novaConfig[breakType] * 60;
      setBreakSeconds(duracaoSegundos);
      breakEndTimeRef.current = Date.now() + duracaoSegundos * 1000;
    }

    setConfigModalAberto(false);
  };

  const solicitarPermissaoNotificacao = async (): Promise<boolean> => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      setNotificacaoAtiva(false);
      return false;
    }

    if (Notification.permission === 'granted') {
      setNotificacaoAtiva(true);
      return true;
    }

    if (Notification.permission === 'denied') {
      setNotificacaoAtiva(false);
      return false;
    }

    try {
      const resultado = await Notification.requestPermission();
      const concedido = resultado === 'granted';
      setNotificacaoAtiva(concedido);
      return concedido;
    } catch {
      return false;
    }
  };

  return (
    <div className={styles.pomodoroPage}>
      <header className={styles.brandHeader}>
        <div className={styles.brandGroup}>
          <img className={styles.brandLogo} src={logoMark} alt="Logo Trilhara" />
          <span className={styles.brandName}>Trilhara</span>
        </div>

        <div className={styles.headerTopActions}>
          {!isAuthenticated && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={abrirAuthModal}
              leftIcon={<LogIn size={14} />}
            >
              Entrar
            </Button>
          )}
          <ThemeToggle size="sm" />
        </div>
      </header>

      <PageBanner
        title={
          <div className={styles.titleArea}>
            <span className={styles.titleText}>{pomodoroMode ? 'Pomodoro' : 'Timer Manual'}</span>
            <Switch
              checked={pomodoroMode}
              onChange={() => alternarModoPomodoro()}
              label="Modo Pomodoro"
            />
          </div>
        }
        icon={pomodoroMode ? <TomatoIcon size={24} /> : <Timer size={22} />}
        actions={
          <div className={styles.headerActions}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              leftIcon={somAtivo ? <Bell size={15} /> : <BellOff size={15} />}
              onClick={() => setSomAtivo((ativo) => !ativo)}
              title={somAtivo ? 'Silenciar alarmes sonoros' : 'Ativar alarmes sonoros'}
            >
              {somAtivo ? 'Alarmes ativos' : 'Alarmes mudos'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              leftIcon={<Settings size={20} />}
              onClick={() => {
                setConfigPausas(pausas);
                setConfigModalAberto(true);
              }}
              aria-label="Configurar pausas e alarmes"
              title="Configurar pausas e alarmes"
            />
          </div>
        }
      />

      <section className={styles.container} aria-label="Sessão de foco">
        <div className={styles.pomodoroLayout}>
          <div className={`${styles.cockpitCard} ${isRunning ? styles.running : ''}`}>
          <div className={styles.timerDisplay}>
            <div
              className={`${styles.statusText} ${
                isRunning ? styles.statusRunning : breakType || !isTimerAtStart ? styles.statusPaused : ''
              }`}
              role="status"
              aria-live="polite"
            >
              {isRunning && <span className={styles.pulseDot} />}
              {status}
            </div>

            {pomodoroMode && (
              <div className={styles.pomodoroCycleRow}>
                <div className={styles.pomodoroCycleBadge}>
                  <span>Ciclo {((pomodoroCyclesCount % 4) + 1)} de 4</span>
                  {pomodoroCyclesCount >= 4 && (
                    <span>({Math.floor(pomodoroCyclesCount / 4)} ciclo{pomodoroCyclesCount >= 8 ? 's' : ''} completo{pomodoroCyclesCount >= 8 ? 's' : ''})</span>
                  )}
                </div>
                <button
                  type="button"
                  className={styles.cycleInfoBtn}
                  onClick={() => setInfoModalAberto(true)}
                  title="Como funciona a Técnica Pomodoro?"
                  aria-label="Explicar Técnica Pomodoro"
                >
                  <HelpCircle size={14} />
                </button>
              </div>
            )}

            {isEditingInline && pomodoroMode ? (
              <div className={styles.inlineEditWrapper}>
                <form
                  ref={inlineFormRef}
                  className={styles.inlineEditForm}
                  onSubmit={(e) => {
                    e.preventDefault();
                    concluirEdicaoInline();
                  }}
                  onBlur={(e) => {
                    // Não conclui a edição se o foco apenas mudou entre minutos e segundos dentro do formulário
                    if (e.currentTarget.contains(e.relatedTarget as Node)) {
                      return;
                    }
                    concluirEdicaoInline();
                  }}
                >
                  <input
                    ref={minutesInputRef}
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={3}
                    spellCheck={false}
                    autoComplete="off"
                    autoCorrect="off"
                    autoCapitalize="off"
                    className={styles.inlineInput}
                    style={{ width: `${Math.max(2, editMinutesStr.length)}ch` }}
                    value={editMinutesStr}
                    onChange={(e) => {
                      const val = e.target.value.replace(/\D/g, '').slice(0, 3);
                      setEditMinutesStr(val);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Escape') {
                        cancelarEdicaoInline();
                      } else if (e.key === 'ArrowRight' && (e.currentTarget.selectionStart ?? 0) >= editMinutesStr.length) {
                        secondsInputRef.current?.focus();
                      }
                    }}
                    aria-label="Minutos de foco"
                  />
                  <span className={styles.inlineColon}>:</span>
                  <input
                    ref={secondsInputRef}
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={2}
                    spellCheck={false}
                    autoComplete="off"
                    autoCorrect="off"
                    autoCapitalize="off"
                    className={styles.inlineInput}
                    value={editSecondsStr}
                    onChange={(e) => {
                      const val = e.target.value.replace(/\D/g, '').slice(0, 2);
                      setEditSecondsStr(val);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Escape') {
                        cancelarEdicaoInline();
                      } else if (e.key === 'ArrowLeft' && e.currentTarget.selectionStart === 0) {
                        minutesInputRef.current?.focus();
                      } else if (e.key === 'Backspace' && editSecondsStr === '') {
                        minutesInputRef.current?.focus();
                      }
                    }}
                    aria-label="Segundos de foco"
                  />
                </form>
                <span className={styles.inlineEditHint}>Pressione Enter para salvar ou Esc para cancelar</span>
              </div>
            ) : (
              <div
                className={`${styles.clockDigits} ${pomodoroMode && !isRunning && !breakType ? styles.clockEditable : ''} ${
                  isRunning ? styles.clockRunning : breakType || !isTimerAtStart ? styles.clockPaused : ''
                }`}
                role="timer"
                aria-live="off"
                onClick={pomodoroMode && !isRunning && !breakType ? () => abrirEdicaoInline('minutes') : undefined}
                title={pomodoroMode && !isRunning && !breakType ? 'Clique para editar o tempo' : undefined}
                tabIndex={pomodoroMode && !isRunning && !breakType ? 0 : undefined}
                onKeyDown={(e) => {
                  if (pomodoroMode && !isRunning && !breakType && (e.key === 'Enter' || e.key === ' ')) {
                    e.preventDefault();
                    abrirEdicaoInline('minutes');
                  }
                }}
              >
                {tempoFormatado}
              </div>
            )}
          </div>

          {breakType && (
            <div className={styles.breakBanner} role="status" aria-live="polite">
              <div className={styles.breakDetails}>
                <strong>Pausa {breakType === 'short' ? 'curta' : 'longa'}</strong>
                <span className={styles.breakCountdown}>{formatarSegundos(breakSeconds)}</span>
              </div>
              <div className={styles.breakActions}>
                <Button
                  type="button"
                  size="sm"
                  leftIcon={<Play size={14} />}
                  onClick={voltarAoFoco}
                >
                  Continuar foco
                </Button>
              </div>
            </div>
          )}

          <div className={styles.controlsRow}>
            {!breakType && !isRunning && (
              <Button
                type="button"
                size="lg"
                leftIcon={<Play size={18} />}
                onClick={iniciarFoco}
              >
                {!isTimerAtStart ? 'Continuar foco' : 'Iniciar foco'}
              </Button>
            )}

            {!breakType && isRunning && (
              <Button
                type="button"
                variant="secondary"
                size="lg"
                leftIcon={<Pause size={18} />}
                onClick={pausarFoco}
              >
                Pausar
              </Button>
            )}

            {(!isTimerAtStart || isRunning || breakType) && (
              <Button
                type="button"
                variant="danger"
                size="lg"
                leftIcon={<Square size={16} />}
                onClick={reiniciarCronometro}
              >
                Parar
              </Button>
            )}
          </div>

          <div className={styles.breakShortcuts}>
            <Button
              type="button"
              variant="outline"
              size="sm"
              leftIcon={<Coffee size={14} />}
              onClick={() => iniciarPausa('short')}
            >
              Pausa curta ({pausas.short} min)
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              leftIcon={<Coffee size={14} />}
              onClick={() => iniciarPausa('long')}
            >
              Pausa longa ({pausas.long} min)
            </Button>
          </div>
        </div>

        {/* Coluna 2: Espelho da Visão Hoje (FASE 7) */}
        <aside className={styles.agendaColumn} aria-label="Agenda do dia">
            {!isAuthenticated ? (
              <div className={styles.visitorCard}>
                <div className={styles.visitorIconWrapper}>
                  <ListTodo size={28} />
                </div>
                <h3 className={styles.visitorTitle}>Organize seus estudos e tarefas</h3>
                <p className={styles.visitorDescription}>
                  Entre na sua conta para acompanhar tarefas diárias, blocos de cronograma e sincronizar suas sessões de foco.
                </p>
                <Button
                  type="button"
                  variant="primary"
                  size="md"
                  onClick={abrirAuthModal}
                >
                  Entrar na conta
                </Button>
              </div>
            ) : (
              <>
                <div className={styles.agendaHeader}>
                  <h3 className={styles.agendaTitle}>Agenda de Hoje</h3>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    iconOnly
                    onClick={recarregarAgenda}
                    isLoading={carregandoAgenda}
                    title="Atualizar agenda"
                    aria-label="Atualizar agenda"
                  >
                    <RotateCcw size={14} />
                  </Button>
                </div>

                {/* Destaque do Foco Selecionado */}
                {focoItem && (
                  <div className={styles.focoItemCard}>
                    <div className={styles.focoItemInfo}>
                      <span className={styles.focoItemTag}>Foco Selecionado</span>
                      <span className={styles.focoItemTitulo}>{focoItem.titulo}</span>
                    </div>
                    <button
                      type="button"
                      className={styles.removerFocoBtn}
                      onClick={() => setFocoItem(null)}
                      title="Desmarcar foco"
                      aria-label="Desmarcar foco"
                    >
                      <X size={14} />
                    </button>
                  </div>
                )}

                {/* Seção 1: Atrasadas */}
                {agendaHoje?.erroTarefas ? (
                  <SectionErrorCard
                    title="Erro ao carregar atrasadas"
                    message={agendaHoje.erroTarefas.message}
                    onRetry={recarregarAgenda}
                    isRetrying={carregandoAgenda}
                  />
                ) : (agendaHoje?.atrasadasAgrupadas || []).length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <AlertTriangle size={15} style={{ color: 'var(--danger, #ef4444)' }} />
                      <h4 style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--danger, #ef4444)', margin: 0 }}>
                        Atrasadas ({agendaHoje?.atrasadas.length || 0})
                      </h4>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {(agendaHoje?.atrasadasAgrupadas || []).map(({ principal, anteriores }) => {
                        const isFocado = focoItem?.id === principal.id;
                        const serieKey = principal.serieId || principal.id;
                        const expandido = Boolean(expansoresAtrasadas[serieKey]);

                        return (
                          <div key={principal.id}>
                            <div className={`${styles.agendaItemRow} ${isFocado ? styles.agendaItemFocado : ''}`}>
                              <button
                                type="button"
                                className={styles.agendaCheckBtn}
                                onClick={() => handleConcluirTarefa(principal.id)}
                                title="Concluir tarefa"
                                aria-label={`Concluir tarefa ${principal.titulo}`}
                              >
                                <Check size={12} />
                              </button>

                              <div
                                className={styles.agendaItemContent}
                                onClick={() =>
                                  setFocoItem({
                                    tipo: 'TAREFA',
                                    id: principal.id,
                                    titulo: principal.titulo
                                  })
                                }
                              >
                                <span className={styles.agendaItemTitulo} title={principal.titulo}>
                                  {principal.titulo}
                                </span>
                              </div>

                              <Badge variant="danger">Atrasada</Badge>
                            </div>

                            {anteriores.length > 0 && (
                              <div style={{ paddingLeft: '28px', marginTop: '2px' }}>
                                <button
                                  type="button"
                                  onClick={() =>
                                    setExpansoresAtrasadas((prev) => ({
                                      ...prev,
                                      [serieKey]: !prev[serieKey]
                                    }))
                                  }
                                  style={{
                                    background: 'none',
                                    border: 'none',
                                    color: 'var(--primary-light, #38bdf8)',
                                    fontSize: '0.78rem',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '3px',
                                    padding: '2px 0'
                                  }}
                                >
                                  {expandido ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                                  <span>
                                    {expandido
                                      ? 'Ocultar anteriores'
                                      : `+ ${anteriores.length} anterior(es)`}
                                  </span>
                                </button>

                                {expandido && (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '4px' }}>
                                    {anteriores.map((ant) => (
                                      <div
                                        key={ant.id}
                                        className={`${styles.agendaItemRow} ${
                                          focoItem?.id === ant.id ? styles.agendaItemFocado : ''
                                        }`}
                                      >
                                        <button
                                          type="button"
                                          className={styles.agendaCheckBtn}
                                          onClick={() => handleConcluirTarefa(ant.id)}
                                          title="Concluir tarefa"
                                        >
                                          <Check size={12} />
                                        </button>
                                        <div
                                          className={styles.agendaItemContent}
                                          onClick={() =>
                                            setFocoItem({
                                              tipo: 'TAREFA',
                                              id: ant.id,
                                              titulo: ant.titulo
                                            })
                                          }
                                        >
                                          <span className={styles.agendaItemTitulo} title={ant.titulo}>
                                            {ant.titulo}
                                          </span>
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ) : null}

                {/* Seção 2: Hoje */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {/* Subdivisão 1: Blocos de Estudo */}
                  {agendaHoje?.erroEstudos ? (
                    <SectionErrorCard
                      title="Erro ao carregar blocos de estudos"
                      message={agendaHoje.erroEstudos.message}
                      onRetry={recarregarAgenda}
                      isRetrying={carregandoAgenda}
                    />
                  ) : (agendaHoje?.blocosHoje || []).length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <BookOpen size={14} style={{ color: 'var(--primary-light)' }} />
                        <h4 style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-mid)', margin: 0 }}>
                          Blocos de Estudo ({agendaHoje?.blocosHoje.length})
                        </h4>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        {agendaHoje?.blocosHoje.map((bloco) => {
                          const blocoId = bloco.blocoId || (bloco as any).id || '';
                          const isFocado = focoItem?.id === blocoId;

                          return (
                            <div
                              key={blocoId}
                              className={`${styles.agendaItemRow} ${isFocado ? styles.agendaItemFocado : ''}`}
                            >
                              <button
                                type="button"
                                className={styles.agendaCheckBtn}
                                onClick={() => handleConcluirBloco(bloco)}
                                title="Concluir bloco e registrar sessão"
                                aria-label={`Concluir bloco de ${bloco.disciplinaNome}`}
                              >
                                <Check size={12} />
                              </button>

                              <div
                                className={styles.agendaItemContent}
                                onClick={() =>
                                  setFocoItem({
                                    tipo: 'BLOCO',
                                    id: blocoId,
                                    titulo: `${bloco.disciplinaNome} (${bloco.trilhaNome || 'Estudos'})`
                                  })
                                }
                              >
                                <DisciplineLabel
                                  name={bloco.disciplinaNome}
                                  color={bloco.disciplinaCor}
                                />
                                <span style={{ fontSize: '11px', color: 'var(--text-soft)' }}>
                                  {bloco.duracaoMinutos}m
                                </span>
                              </div>

                              <Badge variant="warning">Hoje</Badge>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ) : null}

                  {/* Subdivisão 2: Tarefas Manuais */}
                  {agendaHoje?.erroTarefas ? (
                    <SectionErrorCard
                      title="Erro ao carregar tarefas de hoje"
                      message={agendaHoje.erroTarefas.message}
                      onRetry={recarregarAgenda}
                      isRetrying={carregandoAgenda}
                    />
                  ) : (agendaHoje?.tarefasHoje || []).length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <ListTodo size={14} style={{ color: 'var(--primary-light)' }} />
                        <h4 style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-mid)', margin: 0 }}>
                          Tarefas Manuais ({agendaHoje?.tarefasHoje.length})
                        </h4>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        {agendaHoje?.tarefasHoje.map((tarefa) => {
                          const isFocado = focoItem?.id === tarefa.id;

                          return (
                            <div
                              key={tarefa.id}
                              className={`${styles.agendaItemRow} ${isFocado ? styles.agendaItemFocado : ''}`}
                            >
                              <button
                                type="button"
                                className={styles.agendaCheckBtn}
                                onClick={() => handleConcluirTarefa(tarefa.id)}
                                title="Concluir tarefa"
                                aria-label={`Concluir tarefa ${tarefa.titulo}`}
                              >
                                <Check size={12} />
                              </button>

                              <div
                                className={styles.agendaItemContent}
                                onClick={() =>
                                  setFocoItem({
                                    tipo: 'TAREFA',
                                    id: tarefa.id,
                                    titulo: tarefa.titulo
                                  })
                                }
                              >
                                <span className={styles.agendaItemTitulo} title={tarefa.titulo}>
                                  {tarefa.titulo}
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ) : null}
                </div>

                {/* Seção 3: Sem Data */}
                {agendaHoje?.erroTarefas ? (
                  <SectionErrorCard
                    title="Erro ao carregar tarefas sem data"
                    message={agendaHoje.erroTarefas.message}
                    onRetry={recarregarAgenda}
                    isRetrying={carregandoAgenda}
                  />
                ) : (agendaHoje?.semData || []).length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <h4 style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-mid)', margin: 0 }}>
                      Sem Data ({agendaHoje?.semData.length})
                    </h4>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {agendaHoje?.semData.map((tarefa) => {
                        const isFocado = focoItem?.id === tarefa.id;

                        return (
                          <div
                            key={tarefa.id}
                            className={`${styles.agendaItemRow} ${isFocado ? styles.agendaItemFocado : ''}`}
                          >
                            <button
                              type="button"
                              className={styles.agendaCheckBtn}
                              onClick={() => handleConcluirTarefa(tarefa.id)}
                              title="Concluir tarefa"
                              aria-label={`Concluir tarefa ${tarefa.titulo}`}
                            >
                              <Check size={12} />
                            </button>

                            <div
                              className={styles.agendaItemContent}
                              onClick={() =>
                                setFocoItem({
                                  tipo: 'TAREFA',
                                  id: tarefa.id,
                                  titulo: tarefa.titulo
                                })
                              }
                            >
                              <span className={styles.agendaItemTitulo} title={tarefa.titulo}>
                                {tarefa.titulo}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ) : null}

                {/* Estado vazio quando não houver itens em nenhuma seção */}
                {!(agendaHoje?.atrasadas?.length) &&
                  !(agendaHoje?.blocosHoje?.length) &&
                  !(agendaHoje?.tarefasHoje?.length) &&
                  !(agendaHoje?.semData?.length) &&
                  !agendaHoje?.erroEstudos &&
                  !agendaHoje?.erroTarefas && (
                    <EmptyState
                      icon={<Check size={20} />}
                      title="Tudo em dia para hoje!"
                      description="Nenhuma tarefa ou bloco de estudo pendente na sua agenda."
                    />
                  )}
              </>
            )}
          </aside>
        </div>
      </section>

      {/* Modal Explicativo da Técnica Pomodoro */}
      <Modal
        isOpen={infoModalAberto}
        onClose={() => setInfoModalAberto(false)}
        title="Como funciona a Técnica Pomodoro?"
        subtitle="Método de gerenciamento de tempo focado e intervalado."
        icon={<HelpCircle size={20} />}
        footer={
          <Button type="button" onClick={() => setInfoModalAberto(false)}>
            Entendi
          </Button>
        }
      >
        <div className={styles.explanationList}>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>1</span>
            <span className={styles.stepText}>Escolha uma tarefa que você precisa realizar.</span>
          </div>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>2</span>
            <span className={styles.stepText}>
              Defina o cronômetro para 25 minutos (esse período é chamado de pomodoro).
            </span>
          </div>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>3</span>
            <span className={styles.stepText}>
              Trabalhe com foco total na tarefa até o alarme tocar, sem interrupções ou distrações.
            </span>
          </div>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>4</span>
            <span className={styles.stepText}>
              Faça uma pausa curta de 5 minutos (para respirar, beber água ou esticar o corpo).
            </span>
          </div>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>5</span>
            <span className={styles.stepText}>Repita o ciclo quatro vezes seguidas.</span>
          </div>
          <div className={styles.explanationStep}>
            <span className={styles.stepNumber}>6</span>
            <span className={styles.stepText}>
              Após completar esses quatro blocos de 25 minutos, faça uma pausa mais longa, de 15 a 30 minutos.
            </span>
          </div>

          <div className={styles.explanationFooter}>
            <span className={styles.authorCredit}>
              Criada por <strong>Francesco Cirillo</strong> no final dos anos 1980.
            </span>
            <a
              href="https://pt.wikipedia.org/wiki/T%C3%A9cnica_pomodoro"
              target="_blank"
              rel="noopener noreferrer"
              className={styles.articleLink}
            >
              <span>Saiba mais no artigo da Wikipédia</span>
              <ExternalLink size={13} />
            </a>
          </div>
        </div>
      </Modal>

      <FormModal
        isOpen={configModalAberto}
        onClose={() => setConfigModalAberto(false)}
        title="Configurações do cronômetro"
        subtitle="Defina as pausas e os alarmes no navegador."
        icon={<Settings size={20} />}
        onSubmit={salvarConfiguracoes}
        cancelText="Cancelar"
        submitText="Salvar configurações"
      >
        <div className={styles.configModalContent}>
          <div className={styles.configSectionTitle}>Duração das pausas</div>
          <div className={styles.formGrid}>
            <Input
              label="Pausa curta (minutos)"
              type="number"
              min={1}
              max={60}
              required
              value={configPausas.short}
              onChange={(event) =>
                setConfigPausas((atual) => ({ ...atual, short: Number(event.target.value) || 1 }))
              }
            />
            <Input
              label="Pausa longa (minutos)"
              type="number"
              min={1}
              max={120}
              required
              value={configPausas.long}
              onChange={(event) =>
                setConfigPausas((atual) => ({ ...atual, long: Number(event.target.value) || 1 }))
              }
            />
          </div>

          <div className={styles.configSectionTitle}>Alarmes no navegador</div>
          <div className={styles.alarmsList}>
            <Checkbox
              label="Alarme sonoro"
              description="Toca sinal sonoro harmonioso ao fim das pausas e do foco"
              checked={somAtivo}
              onChange={(e) => setSomAtivo(e.target.checked)}
            />
            <Checkbox
              label="Notificações na área de trabalho"
              description="Exibe notificação do navegador quando o tempo esgotar"
              checked={notificacaoAtiva}
              onChange={async (e) => {
                const checked = e.target.checked;
                if (checked) {
                  const concedido = await solicitarPermissaoNotificacao();
                  if (!concedido) {
                    setNotificacaoAtiva(false);
                    return;
                  }
                }
                setNotificacaoAtiva(checked);
              }}
            />
          </div>

          <div className={styles.alarmInfoBox}>
            <div className={styles.alarmInfoTitle}>
              <Bell size={15} />
              <span>Alarmes automáticos configurados:</span>
            </div>
            <ul className={styles.alarmInfoItems}>
              <li>Ao fim da pausa curta ({pausas.short} min)</li>
              <li>Ao fim da pausa longa ({pausas.long} min)</li>
              <li>Ao fim do tempo de foco ({pomodoroMinutes} min)</li>
            </ul>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              leftIcon={<Bell size={14} />}
              onClick={() => soundEngine.playTeste()}
              style={{ marginTop: '4px', alignSelf: 'flex-start' }}
            >
              Testar som do alarme
            </Button>
          </div>
        </div>
      </FormModal>
    </div>
  );
}
