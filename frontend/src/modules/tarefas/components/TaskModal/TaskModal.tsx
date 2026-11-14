import React, { useEffect, useId, useRef, useState } from 'react';
import {
  AlertCircle,
  Check,
  CircleCheck,
  Pencil,
  Repeat,
  Smartphone,
  Trash2,
  Undo2
} from 'lucide-react';
import {
  Badge,
  Button,
  Checkbox,
  CheckCircleButton,
  DatePicker,
  Input,
  Modal,
  QuickAddInput,
  Select,
  Textarea,
  ToggleGroup,
  TopicRow
} from '@shared/design-system';
import {
  getPushPermissionState,
  isIOSNonStandalone,
  PushPermissionState,
  pushService
} from '@shared/services/pushService';
import {
  EscopoRecorrencia,
  FrequenciaRecorrencia,
  ListaTarefas,
  ModoLembrete,
  ModoMensal,
  Prioridade,
  Subtarefa,
  Tarefa,
  TipoTermino
} from '../../types/tarefa';
import { hojeISO } from '@shared/format';
import styles from './TaskModal.module.css';

export interface SalvarTarefaPayload extends Partial<Tarefa> {
  escopo?: EscopoRecorrencia;
  recorrencia?: {
    frequencia: FrequenciaRecorrencia;
    intervalo: number;
    diasDaSemana: number[];
    modoMensal?: ModoMensal;
    diaDoMes?: number;
    ordinalSemana?: number;
    diaSemanaOrdinal?: number;
    tipoTermino: TipoTermino;
    dataInicio: string;
    dataFim?: string;
    maxOcorrencias?: number;
  };
}

export interface TaskModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (dados: SalvarTarefaPayload) => Promise<boolean | void> | boolean | void;
  tarefaParaEditar?: Tarefa | null;
  listas?: ListaTarefas[];
  listaAtivaId?: string;
  onExcluir?: (tarefa: Tarefa) => void;
  onToggleConclusao?: (id: string, concluida: boolean) => void;
}

const OPCOES_PRIORIDADE = [
  { value: 'BAIXA', label: 'Baixa' },
  { value: 'MEDIA', label: 'Média' },
  { value: 'ALTA', label: 'Alta' }
] as const;

const DIAS_DA_SEMANA = [
  { dia: 1, label: 'Seg' },
  { dia: 2, label: 'Ter' },
  { dia: 3, label: 'Qua' },
  { dia: 4, label: 'Qui' },
  { dia: 5, label: 'Sex' },
  { dia: 6, label: 'Sáb' },
  { dia: 7, label: 'Dom' }
] as const;

const InlineSubtaskInput: React.FC<{
  value: string;
  onChange: (value: string) => void;
  onCommit: () => void;
  onCancel: () => void;
}> = ({ value, onChange, onCommit, onCancel }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.focus();
    const len = el.value.length;
    el.setSelectionRange(len, len);
  }, []);

  return (
    <input
      ref={inputRef}
      type="text"
      className={styles.inlineEditInput}
      aria-label="Nome da subtarefa"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={onCommit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          onCommit();
        } else if (e.key === 'Escape') {
          e.preventDefault();
          onCancel();
        }
      }}
      onClick={(e) => e.stopPropagation()}
    />
  );
};

export const TaskModal: React.FC<TaskModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  tarefaParaEditar,
  listas = [],
  listaAtivaId,
  onExcluir,
  onToggleConclusao
}) => {
  const formId = useId();
  const [titulo, setTitulo] = useState('');
  const [concluidaLocal, setConcluidaLocal] = useState(false);
  const [observacoes, setObservacoes] = useState('');
  const [listaId, setListaId] = useState('');
  const [data, setData] = useState(hojeISO());
  const [horario, setHorario] = useState('');
  const [prioridade, setPrioridade] = useState<Prioridade>('MEDIA');
  const [subtarefas, setSubtarefas] = useState<Subtarefa[]>([]);
  const [novaSubtarefaTitulo, setNovaSubtarefaTitulo] = useState('');
  const [editandoSubtarefaId, setEditandoSubtarefaId] = useState<string | null>(null);
  const [nomeSubtarefaEditado, setNomeSubtarefaEditado] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);

  const prevIsOpenRef = useRef(false);
  const prevTarefaIdRef = useRef<string | null>(null);

  // Lembrete Web Push
  const [modoLembrete, setModoLembrete] = useState<ModoLembrete>('HERDAR');
  const [lembreteMinutos, setLembreteMinutos] = useState<number>(15);
  const [padraoGlobalMinutos, setPadraoGlobalMinutos] = useState<number>(15);
  const [pushStatus, setPushStatus] = useState<PushPermissionState>('default');
  const [isIOS, setIsIOS] = useState(false);
  const [dicaIOSAberta, setDicaIOSAberta] = useState(false);

  // Recorrência
  const isEditMode = Boolean(tarefaParaEditar);
  const isRecorrente = Boolean(tarefaParaEditar?.serieId && !tarefaParaEditar?.desvinculadaDaSerie);
  const [escopoEdicao, setEscopoEdicao] = useState<EscopoRecorrencia>('ESTA');
  const [habilitarRecorrencia, setHabilitarRecorrencia] = useState(false);
  const [frequencia, setFrequencia] = useState<FrequenciaRecorrencia>('DIARIO');
  const [intervalo, setIntervalo] = useState(1);
  const [diasDaSemana, setDiasDaSemana] = useState<number[]>([1]);
  const [modoMensal, setModoMensal] = useState<ModoMensal>('DIA_DO_MES');
  const [diaDoMes, setDiaDoMes] = useState(1);
  const [ordinalSemana, setOrdinalSemana] = useState(1);
  const [diaSemanaOrdinal, setDiaSemanaOrdinal] = useState(1);
  const [tipoTermino, setTipoTermino] = useState<TipoTermino>('NUNCA');
  const [dataFim, setDataFim] = useState('');
  const [maxOcorrencias, setMaxOcorrencias] = useState(10);

  useEffect(() => {
    const justOpened = isOpen && !prevIsOpenRef.current;
    const currentTaskId = tarefaParaEditar?.id ?? null;
    const switchedTask = isOpen && currentTaskId !== prevTarefaIdRef.current;

    if (justOpened) {
      setPushStatus(getPushPermissionState());
      setIsIOS(isIOSNonStandalone());
      setDicaIOSAberta(false);

      pushService
        .obterConfig()
        .then((config) => {
          if (typeof config.padraoMinutosAntes === 'number') {
            setPadraoGlobalMinutos(config.padraoMinutosAntes);
          }
        })
        .catch(() => {
          /* preferência global é informativa; a ausência não bloqueia o modal */
        });
    }

    if (justOpened || switchedTask) {
      if (tarefaParaEditar) {
        setTitulo(tarefaParaEditar.titulo);
        setConcluidaLocal(Boolean(tarefaParaEditar.concluida));
        setObservacoes(tarefaParaEditar.observacoes || tarefaParaEditar.descricao || '');
        setListaId(tarefaParaEditar.listaId || '');
        setData(tarefaParaEditar.dataVencimento || tarefaParaEditar.data || hojeISO());
        setHorario(tarefaParaEditar.horario || '');
        setPrioridade((tarefaParaEditar.prioridade || 'MEDIA').toUpperCase() as Prioridade);
        setSubtarefas(tarefaParaEditar.subtarefas || []);
        setEscopoEdicao('ESTA');
        setHabilitarRecorrencia(false);

        if (tarefaParaEditar.modoLembrete === 'SEM_ALERTA') {
          setModoLembrete('SEM_ALERTA');
        } else if (tarefaParaEditar.modoLembrete === 'PERSONALIZADO') {
          setModoLembrete('PERSONALIZADO');
          setLembreteMinutos(tarefaParaEditar.lembreteMinutosAntes ?? 15);
        } else {
          setModoLembrete('HERDAR');
          setLembreteMinutos(tarefaParaEditar.lembreteMinutosAntes ?? 15);
        }
      } else {
        setTitulo('');
        setConcluidaLocal(false);
        setObservacoes('');
        setListaId(listaAtivaId || '');
        setData(hojeISO());
        setHorario('');
        setPrioridade('MEDIA');
        setSubtarefas([]);
        setEscopoEdicao('ESTA');
        setHabilitarRecorrencia(false);
        setFrequencia('DIARIO');
        setIntervalo(1);
        setDiasDaSemana([1]);
        setModoMensal('DIA_DO_MES');
        setDiaDoMes(1);
        setOrdinalSemana(1);
        setDiaSemanaOrdinal(1);
        setTipoTermino('NUNCA');
        setDataFim('');
        setMaxOcorrencias(10);
        setModoLembrete('HERDAR');
        setLembreteMinutos(15);
      }

      setNovaSubtarefaTitulo('');
      setEditandoSubtarefaId(null);
      setNomeSubtarefaEditado('');
      setErroTitulo('');
    }

    prevIsOpenRef.current = isOpen;
    prevTarefaIdRef.current = currentTaskId;
  }, [tarefaParaEditar, isOpen, listaAtivaId]);

  const tarefaIdAtual = tarefaParaEditar?.id;
  const tarefaConcluidaAtual = tarefaParaEditar?.concluida;

  useEffect(() => {
    if (!isOpen || !tarefaIdAtual) return;
    setConcluidaLocal(Boolean(tarefaConcluidaAtual));
  }, [isOpen, tarefaIdAtual, tarefaConcluidaAtual]);

  const criarSubtarefaItem = (texto: string, indiceExtra = 0): Subtarefa => {
    const sufixoUnico =
      typeof globalThis.crypto?.randomUUID === 'function'
        ? globalThis.crypto.randomUUID().slice(0, 8)
        : String(subtarefas.length + 1 + indiceExtra);
    return {
      id: `sub_${Date.now().toString(36)}_${sufixoUnico}`,
      titulo: texto,
      concluida: false
    };
  };

  const handleAdicionarSubtarefaRapida = (texto: string): boolean => {
    const limpo = texto.trim();
    if (!limpo) return false;
    setSubtarefas((prev) => [...prev, criarSubtarefaItem(limpo)]);
    return true;
  };

  const handleAlternarSubtarefa = (subId: string) => {
    setSubtarefas((prev) =>
      prev.map((s) => (s.id === subId ? { ...s, concluida: !s.concluida } : s))
    );
  };

  const handleRenomearSubtarefa = () => {
    if (!editandoSubtarefaId) return;
    const texto = nomeSubtarefaEditado.trim();
    if (texto) {
      setSubtarefas((prev) =>
        prev.map((s) => (s.id === editandoSubtarefaId ? { ...s, titulo: texto } : s))
      );
    }
    setEditandoSubtarefaId(null);
    setNomeSubtarefaEditado('');
  };

  const handleRemoverSubtarefa = (subId: string) => {
    setSubtarefas((prev) => prev.filter((s) => s.id !== subId));
    if (editandoSubtarefaId === subId) {
      setEditandoSubtarefaId(null);
      setNomeSubtarefaEditado('');
    }
  };

  const toggleDiaSemana = (dia: number) => {
    setDiasDaSemana((prev) =>
      prev.includes(dia) ? prev.filter((d) => d !== dia) : [...prev, dia].sort((a, b) => a - b)
    );
  };

  const handleAlternarConclusaoModal = () => {
    if (!tarefaParaEditar || !onToggleConclusao) return;
    const proximo = !concluidaLocal;
    setConcluidaLocal(proximo);
    onToggleConclusao(tarefaParaEditar.id, proximo);
  };

  const handleSubmit = async (e?: React.FormEvent<HTMLFormElement>) => {
    e?.preventDefault();

    if (!titulo.trim()) {
      setErroTitulo('Informe o título da tarefa.');
      return;
    }

    let subtarefasFinais = subtarefas;
    if (editandoSubtarefaId && nomeSubtarefaEditado.trim()) {
      subtarefasFinais = subtarefasFinais.map((s) =>
        s.id === editandoSubtarefaId ? { ...s, titulo: nomeSubtarefaEditado.trim() } : s
      );
    }
    const pendenteRapida = novaSubtarefaTitulo.trim();
    if (pendenteRapida) {
      subtarefasFinais = [...subtarefasFinais, criarSubtarefaItem(pendenteRapida, 1)];
      setNovaSubtarefaTitulo('');
    }

    setSalvando(true);
    try {
      const modoFinal: ModoLembrete = modoLembrete;
      const minutosFinal = modoFinal === 'PERSONALIZADO' ? lembreteMinutos : undefined;

      const payload: SalvarTarefaPayload = {
        titulo: titulo.trim(),
        observacoes: observacoes.trim() || undefined,
        descricao: observacoes.trim() || undefined,
        listaId: listaId || undefined,
        dataVencimento: data,
        data,
        horario: horario || undefined,
        prioridade,
        subtarefas: subtarefasFinais,
        modoLembrete: modoFinal,
        lembreteMinutosAntes: minutosFinal,
        fusoAgendamento: Intl.DateTimeFormat().resolvedOptions().timeZone
      };

      if (isRecorrente) {
        payload.escopo = escopoEdicao;
      } else if (!tarefaParaEditar && habilitarRecorrencia) {
        payload.recorrencia = {
          frequencia,
          intervalo,
          diasDaSemana: frequencia === 'SEMANAL' ? diasDaSemana : [],
          modoMensal: frequencia === 'MENSAL' ? modoMensal : undefined,
          diaDoMes: frequencia === 'MENSAL' && modoMensal === 'DIA_DO_MES' ? diaDoMes : undefined,
          ordinalSemana:
            frequencia === 'MENSAL' && modoMensal === 'ORDINAL_SEMANA' ? ordinalSemana : undefined,
          diaSemanaOrdinal:
            frequencia === 'MENSAL' && modoMensal === 'ORDINAL_SEMANA'
              ? diaSemanaOrdinal
              : undefined,
          tipoTermino,
          dataInicio: data || hojeISO(),
          dataFim: tipoTermino === 'EM_DATA' ? dataFim : undefined,
          maxOcorrencias: tipoTermino === 'APOS_OCORRENCIAS' ? Number(maxOcorrencias) : undefined
        };
      }

      const salvo = await onSalvar(payload);
      if (salvo !== false) {
        onClose();
      }
    } finally {
      setSalvando(false);
    }
  };

  const listaOptions = [
    { value: '', label: 'Tarefas Gerais', dotColor: undefined },
    ...listas.map((l) => ({
      value: l.id,
      label: l.nome || l.titulo || 'Sem nome',
      dotColor: l.cor || undefined
    }))
  ];

  const subtarefasConcluidas = subtarefas.filter((s) => s.concluida).length;
  const totalSubtarefas = subtarefas.length;
  const percentSubtarefas =
    totalSubtarefas > 0 ? Math.round((subtarefasConcluidas / totalSubtarefas) * 100) : 0;

  const modalIcon =
    isEditMode && tarefaParaEditar && onToggleConclusao ? (
      <CheckCircleButton
        size="md"
        checked={concluidaLocal}
        disabled={salvando}
        className={styles.headerCheck}
        onClick={handleAlternarConclusaoModal}
        aria-label={concluidaLocal ? 'Marcar tarefa como pendente' : 'Marcar tarefa como concluída'}
        title={concluidaLocal ? 'Marcar como pendente' : 'Concluir tarefa'}
      />
    ) : (
      <CircleCheck size={20} className={styles.headerCreateIcon} />
    );

  const footerContent = (
    <div className={styles.modalFooter}>
      {isEditMode && tarefaParaEditar && onExcluir && (
        <div className={styles.modalFooterLeft}>
          <Button
            type="button"
            variant="danger"
            size="md"
            leftIcon={<Trash2 size={15} />}
            disabled={salvando}
            onClick={() => {
              onClose();
              onExcluir(tarefaParaEditar);
            }}
          >
            Excluir tarefa
          </Button>
        </div>
      )}
      <div className={styles.modalFooterRight}>
        <Button
          type="button"
          variant="secondary"
          size="md"
          disabled={salvando}
          onClick={onClose}
        >
          Cancelar
        </Button>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          size="md"
          loading={salvando}
        >
          {isEditMode ? 'Salvar' : 'Criar tarefa'}
        </Button>
      </div>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={salvando ? () => {} : onClose}
      title={isEditMode ? 'Editar tarefa' : 'Nova tarefa'}
      icon={modalIcon}
      iconBadgeStyle={{
        width: '30px',
        height: '30px',
        marginTop: '0',
        borderRadius: '50%',
        backgroundColor: 'transparent',
        borderColor: 'transparent'
      }}
      maxWidth="lg"
      focoInicial={!isEditMode ? '[data-task-title-input="true"]' : undefined}
      isSaving={salvando}
      footer={footerContent}
    >
      <form id={formId} className={styles.modalForm} onSubmit={(e) => void handleSubmit(e)}>
        <Input
          className={styles.titleHeroInput}
          placeholder="O que precisa ser feito?"
          aria-label="Título da tarefa"
          data-task-title-input="true"
          value={titulo}
          onChange={(e) => {
            setTitulo(e.target.value);
            if (erroTitulo) setErroTitulo('');
          }}
          error={erroTitulo}
          disabled={salvando}
        />

        <div className={styles.metaStrip}>
          {isEditMode && onToggleConclusao && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className={`${styles.completionPill} ${
                concluidaLocal ? styles.completionPillUndo : styles.completionPillDone
              }`}
              aria-pressed={concluidaLocal}
              disabled={salvando}
              onClick={handleAlternarConclusaoModal}
              leftIcon={concluidaLocal ? <Undo2 size={14} /> : <Check size={14} />}
            >
              {concluidaLocal ? 'Reabrir tarefa' : 'Concluir tarefa'}
            </Button>
          )}

          <ToggleGroup
            value={prioridade}
            onChange={(val) => setPrioridade(val as Prioridade)}
            options={[...OPCOES_PRIORIDADE]}
            ariaLabel="Prioridade da tarefa"
          />

          {listas.length > 0 && (
            <Select
              aria-label="Lista"
              className={styles.inlineListaSelect}
              value={listaId}
              onChange={(val) => setListaId(val)}
              options={listaOptions}
              disabled={salvando}
            />
          )}

          {totalSubtarefas > 0 && (
            <Badge
              variant={subtarefasConcluidas === totalSubtarefas ? 'success' : 'secondary'}
              size="md"
            >
              {`${subtarefasConcluidas} / ${totalSubtarefas} ${
                totalSubtarefas === 1 ? 'subtarefa' : 'subtarefas'
              } (${percentSubtarefas}%)`}
            </Badge>
          )}
        </div>

        {isRecorrente && (
          <div className={styles.recurrenceBanner}>
            <div className={styles.recurrenceBannerHeader}>
              <Repeat size={15} />
              <span>Esta tarefa faz parte de uma série recorrente</span>
            </div>
            <Select
              label="Escopo da alteração"
              value={escopoEdicao}
              onChange={(val) => setEscopoEdicao(val as EscopoRecorrencia)}
              options={[
                { value: 'ESTA', label: 'Apenas esta ocorrência (desvincula da série)' },
                { value: 'ESTA_E_FUTURAS', label: 'Esta e todas as ocorrências futuras' },
                { value: 'TODA_A_SERIE', label: 'Toda a série de tarefas' }
              ]}
              disabled={salvando}
            />
          </div>
        )}

        <div className={styles.scheduleGrid}>
          <div>
            <DatePicker
              label="Data"
              value={data}
              onChange={setData}
              disabled={salvando}
            />
          </div>

          <div>
            <Input
              label="Horário"
              type="time"
              value={horario}
              onChange={(e) => setHorario(e.target.value)}
              disabled={salvando}
            />
          </div>

          <div>
            <Select
              label="Lembrete"
              value={
                horario
                  ? modoLembrete === 'PERSONALIZADO'
                    ? String(lembreteMinutos)
                    : modoLembrete
                  : 'SEM_HORARIO'
              }
              onChange={(val) => {
                const valor = String(val);
                if (valor === 'HERDAR' || valor === 'SEM_ALERTA') {
                  setModoLembrete(valor);
                  return;
                }
                setModoLembrete('PERSONALIZADO');
                setLembreteMinutos(Number(valor));
              }}
              options={
                horario
                  ? [
                      { value: 'HERDAR', label: `Padrão (${padraoGlobalMinutos} min antes)` },
                      { value: 'SEM_ALERTA', label: 'Sem alerta' },
                      { value: '0', label: 'No horário' },
                      { value: '5', label: '5 min antes' },
                      { value: '15', label: '15 min antes' },
                      { value: '30', label: '30 min antes' },
                      { value: '60', label: '60 min antes' }
                    ]
                  : [{ value: 'SEM_HORARIO', label: 'Defina um horário' }]
              }
              disabled={salvando || !horario}
            />
            {!horario && (
              <p className={styles.mutedHint}>Sem horário, a tarefa não dispara alertas.</p>
            )}
          </div>
        </div>

        {horario && modoLembrete !== 'SEM_ALERTA' && pushStatus === 'unsupported' && (
          <div
            role="status"
            aria-live="polite"
            data-testid="badge-push-unsupported"
            className={`${styles.statusNotice} ${styles.statusNoticeWarning}`}
          >
            <span className={styles.statusNoticeLead}>
              <AlertCircle size={14} />
              <span>Navegador sem suporte a Web Push (notificações push desativadas).</span>
            </span>
          </div>
        )}

        {horario && modoLembrete !== 'SEM_ALERTA' && pushStatus === 'denied' && (
          <div
            role="status"
            aria-live="polite"
            data-testid="badge-push-denied"
            className={`${styles.statusNotice} ${styles.statusNoticeDanger}`}
          >
            <span className={styles.statusNoticeLead}>
              <AlertCircle size={14} />
              <span>Notificações bloqueadas nas permissões do navegador.</span>
            </span>
          </div>
        )}

        {horario && modoLembrete !== 'SEM_ALERTA' && isIOS && (
          <div className={`${styles.statusNotice} ${styles.statusNoticeInfo}`}>
            <div className={styles.statusNoticeRow}>
              <span className={styles.statusNoticeLead}>
                <Smartphone size={14} />
                <span>Notificações no iOS Safari requerem tela de início</span>
              </span>
              <button
                type="button"
                data-testid="btn-push-ios-prompt"
                className={styles.iosHelpBtn}
                onClick={() => setDicaIOSAberta((prev) => !prev)}
              >
                {dicaIOSAberta ? 'Ocultar' : 'Como ativar'}
              </button>
            </div>
            {dicaIOSAberta && (
              <p data-testid="prompt-ios-standalone" className={styles.iosHelpText}>
                Para receber alertas no iOS, toque no botão <strong>Compartilhar</strong> do Safari
                e escolha <strong>&quot;Adicionar à Tela de Início&quot;</strong>.
              </p>
            )}
          </div>
        )}

        <Textarea
          label="Observações"
          placeholder="Adicione links, detalhes da tarefa ou apontamentos..."
          value={observacoes}
          onChange={(e) => setObservacoes(e.target.value)}
          rows={3}
          disabled={salvando}
        />

        {!isEditMode && (
          <div className={styles.recurrenceCard}>
            <Checkbox
              checked={habilitarRecorrencia}
              onChange={(e) => setHabilitarRecorrencia(e.target.checked)}
              label={
                <span className={styles.recurrenceToggleLabel}>
                  <Repeat size={15} />
                  <span>Repetir tarefa (recorrência de 90 dias)</span>
                </span>
              }
            />

            {habilitarRecorrencia && (
              <div className={styles.recurrenceBody}>
                <div className={styles.twoColGrid}>
                  <Select
                    label="Frequência"
                    value={frequencia}
                    onChange={(val) => setFrequencia(val as FrequenciaRecorrencia)}
                    options={[
                      { value: 'DIARIO', label: 'Diário' },
                      { value: 'SEMANAL', label: 'Semanal' },
                      { value: 'MENSAL', label: 'Mensal' }
                    ]}
                  />
                  <Input
                    label="Intervalo"
                    type="number"
                    min={1}
                    value={intervalo}
                    onChange={(e) => setIntervalo(Math.max(1, parseInt(e.target.value, 10) || 1))}
                  />
                </div>

                {frequencia === 'SEMANAL' && (
                  <div className={styles.daysGroup}>
                    <span className={styles.daysLabel}>Dias da semana</span>
                    <div className={styles.daysRow}>
                      {DIAS_DA_SEMANA.map(({ dia, label }) => {
                        const ativo = diasDaSemana.includes(dia);
                        return (
                          <button
                            key={dia}
                            type="button"
                            aria-pressed={ativo}
                            onClick={() => toggleDiaSemana(dia)}
                            className={`${styles.dayChip} ${ativo ? styles.dayChipActive : ''}`}
                          >
                            {label}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {frequencia === 'MENSAL' && (
                  <div className={styles.twoColGrid}>
                    <Select
                      label="Modo mensal"
                      value={modoMensal}
                      onChange={(val) => setModoMensal(val as ModoMensal)}
                      options={[
                        { value: 'DIA_DO_MES', label: 'Dia específico do mês' },
                        { value: 'ORDINAL_SEMANA', label: 'Dia da semana ordinal' }
                      ]}
                    />
                    {modoMensal === 'DIA_DO_MES' ? (
                      <Input
                        label="Dia do mês (1-31)"
                        type="number"
                        min={1}
                        max={31}
                        value={diaDoMes}
                        onChange={(e) =>
                          setDiaDoMes(Math.max(1, Math.min(31, parseInt(e.target.value, 10) || 1)))
                        }
                      />
                    ) : (
                      <div className={styles.ordinalPair}>
                        <Select
                          label="Ordinal"
                          value={String(ordinalSemana)}
                          onChange={(val) => setOrdinalSemana(parseInt(val, 10))}
                          options={[
                            { value: '1', label: '1º' },
                            { value: '2', label: '2º' },
                            { value: '3', label: '3º' },
                            { value: '4', label: '4º' },
                            { value: '-1', label: 'Último(a)' }
                          ]}
                        />
                        <Select
                          label="Dia"
                          value={String(diaSemanaOrdinal)}
                          onChange={(val) => setDiaSemanaOrdinal(parseInt(val, 10))}
                          options={[
                            { value: '1', label: 'Seg' },
                            { value: '2', label: 'Ter' },
                            { value: '3', label: 'Qua' },
                            { value: '4', label: 'Qui' },
                            { value: '5', label: 'Sex' },
                            { value: '6', label: 'Sáb' },
                            { value: '7', label: 'Dom' }
                          ]}
                        />
                      </div>
                    )}
                  </div>
                )}

                <div className={styles.twoColGrid}>
                  <Select
                    label="Término da série"
                    value={tipoTermino}
                    onChange={(val) => setTipoTermino(val as TipoTermino)}
                    options={[
                      { value: 'NUNCA', label: 'Nunca encerra' },
                      { value: 'EM_DATA', label: 'Em data específica' },
                      { value: 'APOS_OCORRENCIAS', label: 'Após número de ocorrências' }
                    ]}
                  />
                  {tipoTermino === 'EM_DATA' && (
                    <DatePicker label="Data limite" value={dataFim} onChange={setDataFim} />
                  )}
                  {tipoTermino === 'APOS_OCORRENCIAS' && (
                    <Input
                      label="Máx. ocorrências"
                      type="number"
                      min={1}
                      value={maxOcorrencias}
                      onChange={(e) =>
                        setMaxOcorrencias(Math.max(1, parseInt(e.target.value, 10) || 1))
                      }
                    />
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </form>

      <div className={styles.subtasksSection}>
        <div className={styles.subtasksHeader}>
          <span className={styles.subtasksTitle}>Subtarefas</span>
        </div>

        <QuickAddInput
          className={styles.quickAddSubtask}
          value={novaSubtarefaTitulo}
          onValueChange={setNovaSubtarefaTitulo}
          onAdd={handleAdicionarSubtarefaRapida}
          placeholder="Adicionar uma subtarefa... (Pressione Enter para salvar)"
          buttonTitle="Adicionar subtarefa (Enter)"
          buttonAriaLabel="Adicionar subtarefa"
          inputAriaLabel="Adicionar uma subtarefa"
          disabled={salvando}
        />

        {subtarefas.length > 0 ? (
          <ul className={styles.subtaskList}>
            {subtarefas.map((sub, index) => {
              const emEdicao = editandoSubtarefaId === sub.id;
              return (
                <TopicRow
                  key={sub.id}
                  index={index}
                  name={sub.titulo}
                  className={sub.concluida && !emEdicao ? styles.subtaskDoneRow : ''}
                  leading={
                    <Checkbox
                      checked={sub.concluida}
                      disabled={salvando}
                      aria-label={`Marcar subtarefa ${sub.titulo} como ${
                        sub.concluida ? 'pendente' : 'concluída'
                      }`}
                      onChange={() => handleAlternarSubtarefa(sub.id)}
                    />
                  }
                  content={
                    emEdicao ? (
                      <InlineSubtaskInput
                        value={nomeSubtarefaEditado}
                        onChange={setNomeSubtarefaEditado}
                        onCommit={handleRenomearSubtarefa}
                        onCancel={() => {
                          setNomeSubtarefaEditado('');
                          setEditandoSubtarefaId(null);
                        }}
                      />
                    ) : undefined
                  }
                  actions={
                    <>
                      {!emEdicao && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          iconOnly
                          title="Editar subtarefa"
                          aria-label={`Editar subtarefa ${sub.titulo}`}
                          onMouseDown={(event) => event.preventDefault()}
                          onClick={() => {
                            if (editandoSubtarefaId) handleRenomearSubtarefa();
                            setEditandoSubtarefaId(sub.id);
                            setNomeSubtarefaEditado(sub.titulo);
                          }}
                          leftIcon={<Pencil size={14} />}
                        />
                      )}
                      <Button
                        type="button"
                        variant="danger"
                        size="sm"
                        iconOnly
                        title="Remover subtarefa"
                        aria-label={`Remover subtarefa ${sub.titulo}`}
                        onMouseDown={(event) => event.preventDefault()}
                        onClick={() => handleRemoverSubtarefa(sub.id)}
                        leftIcon={<Trash2 size={14} />}
                      />
                    </>
                  }
                />
              );
            })}
          </ul>
        ) : (
          <p className={styles.emptySubtasks}>Nenhuma subtarefa cadastrada.</p>
        )}
      </div>
    </Modal>
  );
};
