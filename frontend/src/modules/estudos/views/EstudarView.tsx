import React, { useEffect, useRef, useState } from 'react';
import {
  Bell,
  BellOff,
  BookOpen,
  Coffee,
  Pause,
  Play,
  Settings,
  Square
} from 'lucide-react';
import {
  Button,
  Checkbox,
  FormModal,
  Input,
  PageBanner,
  Select,
  useToast,
  useGlobalSessionModal
} from '@shared/design-system';
import {
  Trilha,
  RegistroPlanejamentoPendente
} from '../types/trilhara';
import { useStudyTimer } from '../context/StudyTimerContext';
import { formatarSegundos } from '../utils/format';
import { AlarmService } from '../services/alarmService';
import styles from './EstudarView.module.css';

const SEM_TRILHA_TIMER_ID = '__sem_trilha__';
const NOVA_DISCIPLINA = '__nova__';
const CHAVE_PAUSA_CURTA = 'trilhara_pomodoro_short';
const CHAVE_PAUSA_LONGA = 'trilhara_pomodoro_long';

interface PauseConfig {
  short: number;
  long: number;
}

export interface EstudarViewProps {
  trilha: Trilha | null;
  disciplinaInicialId?: string;
  onTrilhaCriada: (trilha: Trilha) => void;
  onTrilhaAtualizada: (trilha: Trilha) => void;
  onSessaoRegistrada: () => void;
  registroPlanejamento?: RegistroPlanejamentoPendente | null;
  onRegistroPlanejamentoConcluido?: (registro: RegistroPlanejamentoPendente) => void;
  onCancelarRegistroPlanejamento?: (requestId: string) => void;
  contextoBlocoPlanejamento?: RegistroPlanejamentoPendente | null;
  somenteModalRegistro?: boolean;
}

function lerDuracaoPausa(chave: string, padrao: number, maximo: number): number {
  const valor = Number(window.localStorage.getItem(chave));
  return Number.isInteger(valor) && valor >= 1 && valor <= maximo ? valor : padrao;
}

export const EstudarView: React.FC<EstudarViewProps> = ({
  trilha,
  disciplinaInicialId,
  onSessaoRegistrada,
  registroPlanejamento,
  onRegistroPlanejamentoConcluido,
  onCancelarRegistroPlanejamento,
  contextoBlocoPlanejamento,
  somenteModalRegistro = false
}) => {
  const toast = useToast();
  const { abrirModal } = useGlobalSessionModal();

  const [timerId, setTimerId] = useState(trilha?.id || SEM_TRILHA_TIMER_ID);
  const timer = useStudyTimer(timerId);
  const [disciplinaId, setDisciplinaId] = useState(trilha?.disciplinas[0]?.id || '');
  const [modalPausasAberto, setModalPausasAberto] = useState(false);
  const [pausas, setPausas] = useState<PauseConfig>({ short: 5, long: 15 });
  const [configPausas, setConfigPausas] = useState<PauseConfig>({ short: 5, long: 15 });

  const duracaoMinutos = contextoBlocoPlanejamento?.duracaoMinutos
    || registroPlanejamento?.duracaoMinutos
    || 0;

  const alarmeCicloDisparadoRef = useRef(false);
  const [alarmeSomAtivo, setAlarmeSomAtivo] = useState(() => AlarmService.isSomAtivado());
  const [alarmeNotifAtiva, setAlarmeNotifAtiva] = useState(() => AlarmService.isNotificacaoAtivada());

  // Alarme ao atingir a meta pré-definida: toca áudio/visual mas NUNCA abre modal automaticamente
  useEffect(() => {
    if (!timer.running || duracaoMinutos <= 0) return;

    const metaSegundos = duracaoMinutos * 60;
    if (timer.seconds >= metaSegundos && !alarmeCicloDisparadoRef.current) {
      alarmeCicloDisparadoRef.current = true;
      AlarmService.dispararAlarmeFimCiclo(duracaoMinutos);
      toast.success(`Meta do ciclo concluída! (${duracaoMinutos} min)`);
    }
  }, [duracaoMinutos, timer.running, timer.seconds, toast]);

  useEffect(() => {
    if (timer.seconds === 0 || !timer.startedAt) {
      alarmeCicloDisparadoRef.current = false;
    }
  }, [timer.seconds, timer.startedAt]);

  useEffect(() => {
    setTimerId(trilha?.id || SEM_TRILHA_TIMER_ID);
    setDisciplinaId(trilha?.disciplinas[0]?.id || '');
  }, [trilha?.id, trilha?.disciplinas]);

  useEffect(() => {
    if (!disciplinaInicialId || !trilha?.disciplinas.some((disciplina) => disciplina.id === disciplinaInicialId)) {
      return;
    }
    setDisciplinaId(disciplinaInicialId);
  }, [disciplinaInicialId, trilha?.id, trilha?.disciplinas]);

  useEffect(() => {
    if (contextoBlocoPlanejamento) {
      setDisciplinaId(contextoBlocoPlanejamento.disciplinaId);
      timer.setTimerContext(
        contextoBlocoPlanejamento.trilhaId,
        contextoBlocoPlanejamento.disciplinaId,
        contextoBlocoPlanejamento.ocorrenciaId,
        'regressivo',
        contextoBlocoPlanejamento.duracaoMinutos * 60
      );
    }
  }, [contextoBlocoPlanejamento, timer]);

  useEffect(() => {
    try {
      setPausas({
        short: lerDuracaoPausa(CHAVE_PAUSA_CURTA, 5, 60),
        long: lerDuracaoPausa(CHAVE_PAUSA_LONGA, 15, 120)
      });
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível carregar as configurações de pausa.');
    }
  }, [toast]);

  const disciplinaOptions = [
    { value: '', label: 'Selecione uma disciplina' },
    ...(trilha?.disciplinas || []).map((disciplina) => ({
      value: disciplina.id,
      label: disciplina.nome,
      dotColor: disciplina.cor
    })),
    { value: NOVA_DISCIPLINA, label: '+ Nova disciplina' }
  ];

  const disciplinaAtual = trilha?.disciplinas.find((d) => d.id === disciplinaId);

  // Abertura do GlobalSessionModal ao encerrar o cronômetro
  const encerrarESalvar = () => {
    if (timer.seconds < 60) {
      timer.reset(timerId);
      if (registroPlanejamento && onCancelarRegistroPlanejamento) {
        onCancelarRegistroPlanejamento(registroPlanejamento.requestId);
      }
      toast.info('Sessões com menos de 1 minuto não são salvas. O cronômetro foi encerrado.');
      return;
    }

    if (timer.running) timer.pause();

    const blocoId = timer.blocoId || contextoBlocoPlanejamento?.ocorrenciaId || registroPlanejamento?.ocorrenciaId;

    abrirModal({
      modo: blocoId ? 'BLOCO_TIMER' : 'ESTUDOS_TIMER',
      trilhaId: trilha?.id,
      disciplinaId: disciplinaId || trilha?.disciplinas[0]?.id,
      blocoId,
      duracaoInicialSegundos: timer.seconds,
      disciplinaNome: disciplinaAtual?.nome,
      disciplinaCor: disciplinaAtual?.cor,
      trilhaNome: trilha?.nome,
      onSuccess: () => {
        timer.reset(timerId);
        onSessaoRegistrada();
        if (registroPlanejamento && onRegistroPlanejamentoConcluido) {
          onRegistroPlanejamentoConcluido(registroPlanejamento);
        }
      }
    });
  };

  // Abertura do GlobalSessionModal no modo manual
  const abrirRegistroManual = () => {
    abrirModal({
      modo: 'MANUAL',
      trilhaId: trilha?.id,
      disciplinaId: disciplinaId || trilha?.disciplinas[0]?.id,
      disciplinaNome: disciplinaAtual?.nome,
      disciplinaCor: disciplinaAtual?.cor,
      trilhaNome: trilha?.nome,
      duracaoInicialSegundos: 0,
      onSuccess: () => {
        onSessaoRegistrada();
      }
    });
  };

  // Suporte a chamada direta somenteModalRegistro (legado)
  useEffect(() => {
    if (somenteModalRegistro && registroPlanejamento) {
      abrirModal({
        modo: 'BLOCO_DIRETO',
        trilhaId: registroPlanejamento.trilhaId,
        disciplinaId: registroPlanejamento.disciplinaId,
        blocoId: registroPlanejamento.ocorrenciaId,
        duracaoInicialSegundos: (registroPlanejamento.duracaoMinutos || 0) * 60,
        trilhaNome: trilha?.nome,
        onSuccess: () => {
          if (onRegistroPlanejamentoConcluido) {
            onRegistroPlanejamentoConcluido(registroPlanejamento);
          }
          onSessaoRegistrada();
        }
      });
    }
  }, [somenteModalRegistro, registroPlanejamento, abrirModal, onRegistroPlanejamentoConcluido, onSessaoRegistrada, trilha?.nome]);

  const salvarConfiguracaoPausas = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const curta = Math.min(60, Math.max(1, Math.trunc(configPausas.short)));
    const longa = Math.min(120, Math.max(1, Math.trunc(configPausas.long)));
    try {
      window.localStorage.setItem(CHAVE_PAUSA_CURTA, String(curta));
      window.localStorage.setItem(CHAVE_PAUSA_LONGA, String(longa));
      setPausas({ short: curta, long: longa });
      setConfigPausas({ short: curta, long: longa });
      setModalPausasAberto(false);
      toast.success('Durações das pausas atualizadas.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar as configurações de pausa.');
    }
  };

  const iniciarPausa = (tipo: 'short' | 'long') => {
    timer.startBreak(tipo, pausas[tipo]);
  };

  const tempoFormatado = formatarSegundos(timer.seconds);
  const status = timer.running
    ? 'Foco em andamento'
    : timer.seconds > 0 || timer.startedAt
      ? 'Foco pausado. O tempo não está sendo contado'
      : 'Inicie o foco quando estiver pronto';

  if (somenteModalRegistro) {
    return null;
  }

  return (
    <div className={styles.page}>
      <PageBanner
        title="Estudar"
        description="Acompanhe seu foco e registre cada sessão de estudo."
        icon={<BookOpen size={22} />}
        actions={
          <div className={styles.headerActions}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              leftIcon={alarmeSomAtivo ? <Bell size={15} /> : <BellOff size={15} />}
              onClick={() => {
                const novo = !alarmeSomAtivo;
                setAlarmeSomAtivo(novo);
                AlarmService.setSomAtivado(novo);
                toast.info(novo ? 'Alarmes sonoros ativados.' : 'Alarmes sonoros silenciados.');
              }}
              title={alarmeSomAtivo ? 'Silenciar alarmes sonoros' : 'Ativar alarmes sonoros'}
            >
              {alarmeSomAtivo ? 'Alarmes ativos' : 'Alarmes mudos'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              leftIcon={<Settings size={20} />}
              onClick={() => {
                setConfigPausas(pausas);
                setModalPausasAberto(true);
              }}
              aria-label="Configurar pausas e alarmes"
              title="Configurar pausas e alarmes"
            />
          </div>
        }
      />

      <section className={styles.container} aria-label="Sessão de foco">
        <div className={`${styles.cockpitCard} ${timer.running ? styles.running : ''}`}>
          {trilha && (
            <div style={{ marginBottom: 16, width: '100%', maxWidth: 360 }}>
              <Select
                value={disciplinaId}
                aria-label="Disciplina da sessão"
                onChange={(val) => {
                  setDisciplinaId(val);
                  timer.setTimerContext(trilha.id, val);
                }}
                options={disciplinaOptions}
              />
            </div>
          )}

          <div className={styles.timerDisplay}>
            <div
              className={`${styles.statusText} ${timer.running ? styles.statusRunning : timer.seconds > 0 ? styles.statusPaused : ''}`}
              role="status"
              aria-live="polite"
            >
              {timer.running && <span className={styles.pulseDot} />}
              {status}
            </div>
            <div
              className={`${styles.clockDigits} ${timer.running ? styles.clockRunning : timer.seconds > 0 ? styles.clockPaused : ''}`}
              role="timer"
              aria-live="off"
            >
              {tempoFormatado}
            </div>
          </div>

          {timer.breakType && (
            <div className={styles.breakBanner} role="status" aria-live="polite">
              <div className={styles.breakDetails}>
                <strong>Pausa {timer.breakType === 'short' ? 'curta' : 'longa'}</strong>
                <span className={styles.breakCountdown}>
                  {String(Math.floor(timer.breakSeconds / 60)).padStart(2, '0')}:
                  {String(timer.breakSeconds % 60).padStart(2, '0')}
                </span>
              </div>
              <div className={styles.breakActions}>
                <Button type="button" size="sm" onClick={() => timer.endBreak(true)}>
                  Voltar ao foco
                </Button>
                <Button type="button" variant="ghost" size="sm" onClick={() => timer.endBreak(false)}>
                  Encerrar pausa
                </Button>
              </div>
            </div>
          )}

          <div className={styles.controlsRow}>
            {!timer.running && (
              <Button
                type="button"
                size="lg"
                leftIcon={<Play size={18} />}
                onClick={() => {
                  void AlarmService.solicitarPermissaoNotificacao();
                  timer.start();
                }}
              >
                {timer.seconds > 0 || timer.startedAt ? 'Continuar foco' : 'Iniciar foco'}
              </Button>
            )}
            {timer.running && (
              <Button
                type="button"
                variant="secondary"
                size="lg"
                leftIcon={<Pause size={18} />}
                onClick={() => timer.pause()}
              >
                Pausar
              </Button>
            )}
            {(timer.startedAt !== null || timer.running) && (
              <Button
                type="button"
                variant="danger"
                size="lg"
                leftIcon={<Square size={16} />}
                onClick={encerrarESalvar}
              >
                Encerrar e salvar
              </Button>
            )}
          </div>

          <div className={styles.breakShortcuts}>
            <Button type="button" variant="outline" size="sm" leftIcon={<Coffee size={14} />} onClick={() => iniciarPausa('short')}>
              Pausa curta ({pausas.short} min)
            </Button>
            <Button type="button" variant="outline" size="sm" leftIcon={<Coffee size={14} />} onClick={() => iniciarPausa('long')}>
              Pausa longa ({pausas.long} min)
            </Button>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={abrirRegistroManual}>
            Registrar tempo manualmente
          </Button>
        </div>
      </section>

      <FormModal
        isOpen={modalPausasAberto}
        onClose={() => setModalPausasAberto(false)}
        title="Configurações do cronômetro"
        subtitle="Defina as pausas e os alarmes no navegador."
        icon={<Settings size={20} />}
        onSubmit={salvarConfiguracaoPausas}
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
              onChange={(event) => setConfigPausas((atual) => ({ ...atual, short: Number(event.target.value) || 1 }))}
            />
            <Input
              label="Pausa longa (minutos)"
              type="number"
              min={1}
              max={120}
              required
              value={configPausas.long}
              onChange={(event) => setConfigPausas((atual) => ({ ...atual, long: Number(event.target.value) || 1 }))}
            />
          </div>

          <div className={styles.configSectionTitle}>Alarmes no navegador</div>
          <div className={styles.alarmsList}>
            <Checkbox
              label="Alarme sonoro"
              description="Toca sinal sonoro harmonioso ao fim das pausas e do ciclo"
              checked={alarmeSomAtivo}
              onChange={(e) => {
                const checked = e.target.checked;
                setAlarmeSomAtivo(checked);
                AlarmService.setSomAtivado(checked);
              }}
            />
            <Checkbox
              label="Notificações na área de trabalho"
              description="Exibe notificação do navegador quando o tempo esgotar"
              checked={alarmeNotifAtiva}
              onChange={async (e) => {
                const checked = e.target.checked;
                if (checked) {
                  const concedido = await AlarmService.solicitarPermissaoNotificacao();
                  if (!concedido) {
                    toast.warning('Permissão de notificação não foi concedida pelo navegador.');
                  }
                }
                setAlarmeNotifAtiva(checked);
                AlarmService.setNotificacaoAtivada(checked);
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
              <li>
                Ao fim do tempo predefinido no ciclo atual
                {duracaoMinutos > 0 ? ` (${duracaoMinutos} min)` : ''}
              </li>
            </ul>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              leftIcon={<Bell size={14} />}
              onClick={() => AlarmService.tocarTeste()}
              style={{ marginTop: '4px', alignSelf: 'flex-start' }}
            >
              Testar som do alarme
            </Button>
          </div>
        </div>
      </FormModal>
    </div>
  );
};
