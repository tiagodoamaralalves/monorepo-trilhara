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
  TrilhaDetalheDTO,
  RegistroBlocoPendente
} from '../types/trilhara';
import { useStudyTimer } from '@shared/timers/estudoTimer';
import { formatarSegundos } from '../utils/format';
import { AlarmService } from '@shared/services/alarmService';
import styles from './EstudarView.module.css';

const NOVA_DISCIPLINA = '__nova__';

/** Pausas do cronômetro de Estudos: persistidas na chave escopada por usuário. */
const PAUSAS_PADRAO = { short: 5, long: 15 };

interface PauseConfig {
  short: number;
  long: number;
}

export interface EstudarViewProps {
  trilha: TrilhaDetalheDTO | null;
  disciplinaInicialId?: string;
  onTrilhaCriada: (trilha: TrilhaDetalheDTO) => void;
  onTrilhaAtualizada: (trilha: TrilhaDetalheDTO) => void;
  onSessaoRegistrada: () => void;
  /** Bloco escolhido em "Estudar este bloco" (Planejamento). */
  contextoBlocoPlanejamento?: RegistroBlocoPendente | null;
}

export const EstudarView: React.FC<EstudarViewProps> = ({
  trilha,
  disciplinaInicialId,
  onSessaoRegistrada,
  contextoBlocoPlanejamento
}) => {
  const toast = useToast();
  const { abrirModal } = useGlobalSessionModal();

  const timer = useStudyTimer();
  const [disciplinaId, setDisciplinaId] = useState(trilha?.disciplinas[0]?.id || '');
  const [modalPausasAberto, setModalPausasAberto] = useState(false);
  const [pausas, setPausas] = useState<PauseConfig>(timer.pausas ?? PAUSAS_PADRAO);
  const [configPausas, setConfigPausas] = useState<PauseConfig>(timer.pausas ?? PAUSAS_PADRAO);


  const duracaoMinutos = contextoBlocoPlanejamento?.duracaoMinutos || 0;

  const alarmeCicloDisparadoRef = useRef(false);
  const [alarmeSomAtivo, setAlarmeSomAtivo] = useState(() => AlarmService.isSomAtivado());
  const [alarmeNotifAtiva, setAlarmeNotifAtiva] = useState(() => AlarmService.isNotificacaoAtivada());

  // Alarme ao atingir a meta pré-definida: toca áudio/visual mas NUNCA abre modal automaticamente
  useEffect(() => {
    if (!timer.running || duracaoMinutos <= 0) return;

    const metaSegundos = duracaoMinutos * 60;
    if (timer.seconds >= metaSegundos && !alarmeCicloDisparadoRef.current) {
      alarmeCicloDisparadoRef.current = true;
      AlarmService.dispararAlarmeFimMeta(duracaoMinutos);
      toast.success(`Meta de estudo concluída! (${duracaoMinutos} min)`);
    }
  }, [duracaoMinutos, timer.running, timer.seconds, toast]);

  useEffect(() => {
    if (timer.seconds === 0 || !timer.startedAt) {
      alarmeCicloDisparadoRef.current = false;
    }
  }, [timer.seconds, timer.startedAt]);

  useEffect(() => {
    setDisciplinaId(trilha?.disciplinas[0]?.id || '');
  }, [trilha?.id, trilha?.disciplinas]);

  useEffect(() => {
    if (!disciplinaInicialId || !trilha?.disciplinas.some((disciplina) => disciplina.id === disciplinaInicialId)) {
      return;
    }
    setDisciplinaId(disciplinaInicialId);
  }, [disciplinaInicialId, trilha?.id, trilha?.disciplinas]);

  const definirContextoBloco = timer.setTimerContext;
  useEffect(() => {
    if (!contextoBlocoPlanejamento) return;
    setDisciplinaId(contextoBlocoPlanejamento.disciplinaId);
    void definirContextoBloco(
      contextoBlocoPlanejamento.trilhaId,
      contextoBlocoPlanejamento.disciplinaId,
      contextoBlocoPlanejamento.blocoId,
      'regressivo',
      contextoBlocoPlanejamento.duracaoMinutos * 60
    );
  }, [contextoBlocoPlanejamento, definirContextoBloco]);

  // Pausas configuradas vivem no estado do timer (chave escopada por usuário, ).
  useEffect(() => {
    setPausas({ ...timer.pausas });
  }, [timer.pausas]);


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

  // Abertura do GlobalSessionModal ao encerrar o cronômetro.
  // o timer é apenas **pausado** aqui; o reset só ocorre após 201/200 da sessão,
  // informado pelo modal via `onSuccess`.
  const encerrarESalvar = async () => {
    if (timer.seconds < 60) {
      timer.reset();
      toast.info('Sessões com menos de 1 minuto não são salvas. O cronômetro foi encerrado.');
      return;
    }

    if (timer.running) timer.pause();

    const blocoId = timer.blocoId || contextoBlocoPlanejamento?.blocoId;

    abrirModal({
      modo: blocoId ? 'BLOCO_TIMER' : 'ESTUDOS_TIMER',
      trilhaId: trilha?.id,
      disciplinaId: disciplinaId || trilha?.disciplinas[0]?.id,
      blocoId,
      duracaoInicialSegundos: timer.seconds,
      disciplinaNome: disciplinaAtual?.nome,
      disciplinaCor: disciplinaAtual?.cor,
      trilhaNome: trilha?.nome,
      onBlocoObsoleto: () => {
        timer.reset();
        onSessaoRegistrada();
      },
      onSuccess: () => {
        timer.reset();
        onSessaoRegistrada();
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

  const salvarConfiguracaoPausas = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const curta = Math.min(60, Math.max(1, Math.trunc(configPausas.short)));
    const longa = Math.min(120, Math.max(1, Math.trunc(configPausas.long)));
    try {
      timer.setPausas({ short: curta, long: longa });
      setPausas({ short: curta, long: longa });
      setConfigPausas({ short: curta, long: longa });
      setModalPausasAberto(false);
      toast.success('Durações das pausas atualizadas.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar as configurações de pausa.');
    }
  };


  const podeIniciarPausa =
    timer.ativo && (timer.running || timer.startedAt !== null) && !timer.breakType;

  const iniciarPausa = (tipo: 'short' | 'long') => {
    if (!podeIniciarPausa) return;
    timer.startBreak(tipo, pausas[tipo]);
  };

  const tempoFormatado = formatarSegundos(timer.seconds);
  const status = !timer.ativo
    ? 'Entre para usar o cronômetro'
    : timer.running
      ? 'Foco em andamento'
      : timer.seconds > 0 || timer.startedAt
        ? 'Foco pausado. O tempo não está sendo contado'
        : 'Inicie o foco quando estiver pronto';

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
                disabled={!timer.ativo}
                title={timer.ativo ? undefined : 'Entre para usar o cronômetro'}
                onClick={() => {
                  void AlarmService.solicitarPermissaoNotificacao();
                  void timer.start();
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
                disabled={!timer.ativo}
                onClick={() => void encerrarESalvar()}
              >
                Encerrar e salvar
              </Button>
            )}
          </div>

          <div className={styles.breakShortcuts}>
            <Button
              type="button"
              variant="outline"
              size="sm"
              leftIcon={<Coffee size={14} />}
              disabled={!podeIniciarPausa}
              title={
                podeIniciarPausa
                  ? undefined
                  : timer.ativo
                    ? 'Inicie o cronômetro para usar as pausas'
                    : 'Entre para usar o cronômetro'
              }
              onClick={() => iniciarPausa('short')}
            >
              Pausa curta ({pausas.short} min)
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              leftIcon={<Coffee size={14} />}
              disabled={!podeIniciarPausa}
              title={
                podeIniciarPausa
                  ? undefined
                  : timer.ativo
                    ? 'Inicie o cronômetro para usar as pausas'
                    : 'Entre para usar o cronômetro'
              }
              onClick={() => iniciarPausa('long')}
            >
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
              description="Toca sinal sonoro harmonioso ao fim das pausas e do bloco"
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
                Ao fim do tempo predefinido do bloco atual
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
