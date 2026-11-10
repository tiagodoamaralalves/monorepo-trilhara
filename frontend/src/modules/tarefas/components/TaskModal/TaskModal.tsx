import React, { useState, useEffect } from 'react';
import {
  CheckSquare,
  Plus,
  Trash2,
  Repeat,
  AlertCircle,
  Bell,
  Smartphone
} from 'lucide-react';
import {
  DatePicker,
  FormModal,
  Input,
  Select,
  Textarea,
  Button
} from '@shared/design-system';
import {
  getPushPermissionState,
  isIOSNonStandalone,
  PushPermissionState
} from '@shared/services/pushService';
import {
  Tarefa,
  Prioridade,
  Subtarefa,
  ListaTarefas,
  EscopoRecorrencia,
  FrequenciaRecorrencia,
  ModoMensal,
  TipoTermino
} from '../../types/tarefa';
import { hojeISO } from '../../utils/date';
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
}

export const TaskModal: React.FC<TaskModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  tarefaParaEditar,
  listas = [],
  listaAtivaId
}) => {
  const [titulo, setTitulo] = useState('');
  const [observacoes, setObservacoes] = useState('');
  const [listaId, setListaId] = useState('');
  const [data, setData] = useState(hojeISO());
  const [horario, setHorario] = useState('');
  const [prioridade, setPrioridade] = useState<Prioridade>('MEDIA');
  const [subtarefas, setSubtarefas] = useState<Subtarefa[]>([]);
  const [novaSubtarefaTitulo, setNovaSubtarefaTitulo] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);

  // Lembrete Web Push (FASE 10)
  const [lembreteOpcao, setLembreteOpcao] = useState<string>('PADRAO_GLOBAL');
  const [pushStatus, setPushStatus] = useState<PushPermissionState>('default');
  const [isIOS, setIsIOS] = useState(false);
  const [dicaIOSAberta, setDicaIOSAberta] = useState(false);

  // Recorrência
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
    setPushStatus(getPushPermissionState());
    setIsIOS(isIOSNonStandalone());
    setDicaIOSAberta(false);

    if (tarefaParaEditar) {
      setTitulo(tarefaParaEditar.titulo);
      setObservacoes(tarefaParaEditar.observacoes || tarefaParaEditar.descricao || '');
      setListaId(tarefaParaEditar.listaId || '');
      setData(tarefaParaEditar.dataVencimento || tarefaParaEditar.data || hojeISO());
      setHorario(tarefaParaEditar.horario || '');
      setPrioridade((tarefaParaEditar.prioridade || 'MEDIA').toUpperCase() as Prioridade);
      setSubtarefas(tarefaParaEditar.subtarefas || []);
      setEscopoEdicao('ESTA');
      setHabilitarRecorrencia(false);

      if (tarefaParaEditar.usaPadraoGlobalLembrete !== false) {
        setLembreteOpcao('PADRAO_GLOBAL');
      } else if (
        tarefaParaEditar.lembreteMinutosAntes === null ||
        tarefaParaEditar.lembreteMinutosAntes === undefined ||
        tarefaParaEditar.lembreteMinutosAntes < 0
      ) {
        setLembreteOpcao('SEM_ALERTA');
      } else {
        setLembreteOpcao(String(tarefaParaEditar.lembreteMinutosAntes));
      }
    } else {
      setTitulo('');
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
      setLembreteOpcao('PADRAO_GLOBAL');
    }
    setNovaSubtarefaTitulo('');
    setErroTitulo('');
  }, [tarefaParaEditar, isOpen, listaAtivaId]);

  const handleAdicionarSubtarefa = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const texto = novaSubtarefaTitulo.trim();
    if (!texto) return;

    const nova: Subtarefa = {
      id: 'sub_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      titulo: texto,
      concluida: false
    };

    setSubtarefas((prev) => [...prev, nova]);
    setNovaSubtarefaTitulo('');
  };

  const handleAlternarSubtarefa = (subId: string) => {
    setSubtarefas((prev) =>
      prev.map((s) => (s.id === subId ? { ...s, concluida: !s.concluida } : s))
    );
  };

  const handleRemoverSubtarefa = (subId: string) => {
    setSubtarefas((prev) => prev.filter((s) => s.id !== subId));
  };

  const toggleDiaSemana = (dia: number) => {
    setDiasDaSemana((prev) =>
      prev.includes(dia) ? prev.filter((d) => d !== dia) : [...prev, dia].sort()
    );
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    if (!titulo.trim()) {
      setErroTitulo('Informe o título da tarefa.');
      return;
    }

    setSalvando(true);
    try {
      let lembreteMinutos: number | undefined = undefined;
      let usaPadrao = false;
      if (horario) {
        if (lembreteOpcao === 'PADRAO_GLOBAL') {
          usaPadrao = true;
          lembreteMinutos = 15;
        } else if (lembreteOpcao !== 'SEM_ALERTA') {
          usaPadrao = false;
          lembreteMinutos = Number(lembreteOpcao);
        }
      }

      const payload: SalvarTarefaPayload = {
        titulo: titulo.trim(),
        observacoes: observacoes.trim() || undefined,
        descricao: observacoes.trim() || undefined,
        listaId: listaId || undefined,
        dataVencimento: data,
        data,
        horario: horario || undefined,
        prioridade,
        subtarefas,
        lembreteMinutosAntes: lembreteMinutos,
        usaPadraoGlobalLembrete: usaPadrao,
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
          ordinalSemana: frequencia === 'MENSAL' && modoMensal === 'ORDINAL_SEMANA' ? ordinalSemana : undefined,
          diaSemanaOrdinal: frequencia === 'MENSAL' && modoMensal === 'ORDINAL_SEMANA' ? diaSemanaOrdinal : undefined,
          tipoTermino,
          dataInicio: data || hojeISO(),
          dataFim: tipoTermino === 'EM_DATA' ? dataFim : undefined,
          maxOcorrencias: tipoTermino === 'APOS_OCORRENCIAS' ? Number(maxOcorrencias) : undefined
        };
      }

      await onSalvar(payload);
      onClose();
    } finally {
      setSalvando(false);
    }
  };

  const prioridadeOptions = [
    { value: 'BAIXA', label: 'Baixa prioridade' },
    { value: 'MEDIA', label: 'Média prioridade' },
    { value: 'ALTA', label: 'Alta prioridade' }
  ];

  const listaOptions = [
    { value: '', label: 'Tarefas Gerais (Sem lista)', dotColor: undefined },
    ...listas.map((l) => ({
      value: l.id,
      label: l.nome || l.titulo || 'Sem nome',
      dotColor: l.cor || undefined
    }))
  ];

  return (
    <FormModal
      isOpen={isOpen}
      onClose={onClose}
      title={tarefaParaEditar ? 'Editar Tarefa' : 'Nova Tarefa'}
      subtitle="Defina os prazos, detalhes, subtarefas e regras de recorrência."
      icon={<CheckSquare size={20} />}
      onSubmit={handleSubmit}
      submitText={tarefaParaEditar ? 'Salvar Alterações' : 'Criar Tarefa'}
      isLoading={salvando}
    >
      <>
        {isRecorrente && (
          <div style={{ background: 'var(--bg-subtle, #f3f4f6)', padding: '12px', borderRadius: '8px', marginBottom: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', fontWeight: 600 }}>
              <Repeat size={16} />
              <span>Esta tarefa faz parte de uma série recorrente</span>
            </div>
            <label style={{ fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>
              Selecione o escopo da alteração:
            </label>
            <Select
              label=""
              value={escopoEdicao}
              onChange={(val) => setEscopoEdicao(val as EscopoRecorrencia)}
              options={[
                { value: 'ESTA', label: 'Apenas esta ocorrência (desvincula da série)' },
                { value: 'ESTA_E_FUTURAS', label: 'Esta e todas as ocorrências futuras' },
                { value: 'TODA_A_SERIE', label: 'Toda a série de tarefas' }
              ]}
            />
          </div>
        )}

        <Input
          label="Título da Tarefa *"
          placeholder="Ex: Resolver 30 questões de Português, Pagar inscrição..."
          value={titulo}
          onChange={(e) => {
            setTitulo(e.target.value);
            if (erroTitulo) setErroTitulo('');
          }}
          error={erroTitulo}
          disabled={salvando}
          autoFocus={!tarefaParaEditar}
        />

        {listas.length > 0 && (
          <Select
            label="Lista"
            value={listaId}
            onChange={(val) => setListaId(val)}
            options={listaOptions}
            disabled={salvando}
          />
        )}

        <div className={styles.row}>
          <div className={styles.col}>
            <DatePicker
              label="Data de Vencimento"
              value={data}
              onChange={setData}
              disabled={salvando}
            />
          </div>

          <div className={styles.col}>
            <Input
              label="Horário"
              type="time"
              value={horario}
              onChange={(e) => setHorario(e.target.value)}
              disabled={salvando}
            />
          </div>
        </div>

        {/* Seletor de Lembrete Web Push (FASE 10) */}
        {horario && (
          <div className={styles.row}>
            <div className={styles.col}>
              <Select
                label="Lembrete (Web Push)"
                value={lembreteOpcao}
                onChange={(val) => setLembreteOpcao(String(val))}
                options={[
                  { value: 'PADRAO_GLOBAL', label: 'Usar padrão global (15 min antes)' },
                  { value: '0', label: 'No horário da tarefa' },
                  { value: '5', label: '5 minutos antes' },
                  { value: '15', label: '15 minutos antes' },
                  { value: '30', label: '30 minutos antes' },
                  { value: '60', label: '60 minutos antes (1 hora)' },
                  { value: 'SEM_ALERTA', label: 'Sem alerta' }
                ]}
                disabled={salvando}
              />
            </div>
          </div>
        )}

        {/* Badges informativos sem bloquear operações da tarefa */}
        {horario && lembreteOpcao !== 'SEM_ALERTA' && pushStatus === 'unsupported' && (
          <div
            role="status"
            aria-live="polite"
            data-testid="badge-push-unsupported"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 10px',
              fontSize: '0.8125rem',
              borderRadius: '6px',
              backgroundColor: 'rgba(234, 179, 8, 0.12)',
              color: 'var(--text-secondary, #eab308)',
              border: '1px solid rgba(234, 179, 8, 0.25)',
              marginBottom: '10px'
            }}
          >
            <AlertCircle size={14} />
            <span>Navegador sem suporte a Web Push (notificações push desativadas).</span>
          </div>
        )}

        {horario && lembreteOpcao !== 'SEM_ALERTA' && pushStatus === 'denied' && (
          <div
            role="status"
            aria-live="polite"
            data-testid="badge-push-denied"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 10px',
              fontSize: '0.8125rem',
              borderRadius: '6px',
              backgroundColor: 'rgba(239, 68, 68, 0.12)',
              color: 'var(--text-danger, #ef4444)',
              border: '1px solid rgba(239, 68, 68, 0.25)',
              marginBottom: '10px'
            }}
          >
            <AlertCircle size={14} />
            <span>Notificações bloqueadas nas permissões do navegador.</span>
          </div>
        )}

        {horario && lembreteOpcao !== 'SEM_ALERTA' && isIOS && (
          <div
            style={{
              padding: '8px 10px',
              fontSize: '0.8125rem',
              borderRadius: '6px',
              backgroundColor: 'rgba(59, 130, 246, 0.1)',
              border: '1px solid rgba(59, 130, 246, 0.25)',
              marginBottom: '10px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Smartphone size={14} color="#3b82f6" />
                <span>Notificações no iOS Safari requerem tela de início</span>
              </span>
              <button
                type="button"
                data-testid="btn-push-ios-prompt"
                onClick={() => setDicaIOSAberta((prev) => !prev)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#3b82f6',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '0.75rem',
                  textDecoration: 'underline'
                }}
              >
                {dicaIOSAberta ? 'Ocultar' : 'Como ativar'}
              </button>
            </div>
            {dicaIOSAberta && (
              <p
                data-testid="prompt-ios-standalone"
                style={{ margin: '6px 0 0 0', color: 'var(--text-secondary, #94a3b8)', lineHeight: 1.4 }}
              >
                Para receber alertas no iOS, toque no botão <strong>Compartilhar</strong> do Safari e escolha <strong>"Adicionar à Tela de Início"</strong>.
              </p>
            )}
          </div>
        )}

        <div className={styles.row}>
          <div className={styles.col}>
            <Select
              label="Prioridade"
              value={prioridade}
              onChange={(val) => setPrioridade(val as Prioridade)}
              options={prioridadeOptions}
              disabled={salvando}
            />
          </div>
        </div>

        {/* Toggle de Recorrência ao criar nova tarefa */}
        {!tarefaParaEditar && (
          <div style={{ borderTop: '1px solid var(--border-subtle, #e5e7eb)', paddingTop: '12px', marginTop: '12px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 600 }}>
              <input
                type="checkbox"
                checked={habilitarRecorrencia}
                onChange={(e) => setHabilitarRecorrencia(e.target.checked)}
              />
              <Repeat size={16} />
              <span>Repetir tarefa (Recorrência de 90 dias)</span>
            </label>

            {habilitarRecorrencia && (
              <div style={{ marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '10px', background: 'var(--bg-subtle, #f9fafb)', padding: '12px', borderRadius: '8px' }}>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ flex: 1 }}>
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
                  </div>
                  <div style={{ flex: 1 }}>
                    <Input
                      label="Intervalo"
                      type="number"
                      min={1}
                      value={intervalo}
                      onChange={(e) => setIntervalo(Math.max(1, parseInt(e.target.value) || 1))}
                    />
                  </div>
                </div>

                {frequencia === 'SEMANAL' && (
                  <div>
                    <label style={{ fontSize: '0.85rem', fontWeight: 500, display: 'block', marginBottom: '6px' }}>
                      Dias da semana:
                    </label>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      {[
                        { dia: 1, label: 'Seg' },
                        { dia: 2, label: 'Ter' },
                        { dia: 3, label: 'Qua' },
                        { dia: 4, label: 'Qui' },
                        { dia: 5, label: 'Sex' },
                        { dia: 6, label: 'Sáb' },
                        { dia: 7, label: 'Dom' }
                      ].map(({ dia, label }) => {
                        const ativo = diasDaSemana.includes(dia);
                        return (
                          <button
                            key={dia}
                            type="button"
                            onClick={() => toggleDiaSemana(dia)}
                            style={{
                              padding: '6px 10px',
                              borderRadius: '6px',
                              border: ativo ? '2px solid var(--primary-600, #4f46e5)' : '1px solid var(--border-subtle, #d1d5db)',
                              background: ativo ? 'var(--primary-50, #eef2ff)' : '#fff',
                              fontWeight: ativo ? 600 : 400,
                              cursor: 'pointer'
                            }}
                          >
                            {label}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {frequencia === 'MENSAL' && (
                  <div style={{ display: 'flex', gap: '12px' }}>
                    <div style={{ flex: 1 }}>
                      <Select
                        label="Modo Mensal"
                        value={modoMensal}
                        onChange={(val) => setModoMensal(val as ModoMensal)}
                        options={[
                          { value: 'DIA_DO_MES', label: 'Dia específico do mês' },
                          { value: 'ORDINAL_SEMANA', label: 'Dia da semana ordinal' }
                        ]}
                      />
                    </div>
                    {modoMensal === 'DIA_DO_MES' ? (
                      <div style={{ flex: 1 }}>
                        <Input
                          label="Dia do mês (1-31)"
                          type="number"
                          min={1}
                          max={31}
                          value={diaDoMes}
                          onChange={(e) => setDiaDoMes(Math.max(1, Math.min(31, parseInt(e.target.value) || 1)))}
                        />
                      </div>
                    ) : (
                      <div style={{ flex: 1, display: 'flex', gap: '6px' }}>
                        <Select
                          label="Ordinal"
                          value={String(ordinalSemana)}
                          onChange={(val) => setOrdinalSemana(parseInt(val))}
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
                          onChange={(val) => setDiaSemanaOrdinal(parseInt(val))}
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

                <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-end' }}>
                  <div style={{ flex: 1 }}>
                    <Select
                      label="Término da Série"
                      value={tipoTermino}
                      onChange={(val) => setTipoTermino(val as TipoTermino)}
                      options={[
                        { value: 'NUNCA', label: 'Nunca encerra' },
                        { value: 'EM_DATA', label: 'Em data específica' },
                        { value: 'APOS_OCORRENCIAS', label: 'Após número de ocorrências' }
                      ]}
                    />
                  </div>
                  {tipoTermino === 'EM_DATA' && (
                    <div style={{ flex: 1 }}>
                      <DatePicker
                        label="Data Limite"
                        value={dataFim}
                        onChange={setDataFim}
                      />
                    </div>
                  )}
                  {tipoTermino === 'APOS_OCORRENCIAS' && (
                    <div style={{ flex: 1 }}>
                      <Input
                        label="Max Ocorrências"
                        type="number"
                        min={1}
                        value={maxOcorrencias}
                        onChange={(e) => setMaxOcorrencias(Math.max(1, parseInt(e.target.value) || 1))}
                      />
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        <Textarea
          label="Observações / Descrição"
          placeholder="Adicione links, detalhes da tarefa ou apontamentos..."
          value={observacoes}
          onChange={(e) => setObservacoes(e.target.value)}
          rows={3}
          disabled={salvando}
        />

        {/* Subtarefas */}
        <div className={styles.subtasksSection}>
          <label className={styles.subtasksHeader}>
            <span>Subtarefas / Checklist</span>
            {subtarefas.length > 0 && (
              <span className={styles.subtaskBadge}>
                {subtarefas.filter((s) => s.concluida).length}/{subtarefas.length}
              </span>
            )}
          </label>

          {subtarefas.length > 0 && (
            <div className={styles.subtasksList}>
              {subtarefas.map((s) => (
                <div key={s.id} className={styles.subtaskItem}>
                  <label className={styles.subtaskLabel}>
                    <input
                      type="checkbox"
                      checked={s.concluida}
                      onChange={() => handleAlternarSubtarefa(s.id)}
                    />
                    <span className={s.concluida ? styles.subtaskDone : ''}>
                      {s.titulo}
                    </span>
                  </label>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    iconOnly
                    onClick={() => handleRemoverSubtarefa(s.id)}
                    title="Remover subtarefa"
                  >
                    <Trash2 size={13} />
                  </Button>
                </div>
              ))}
            </div>
          )}

          <div className={styles.addSubtaskRow}>
            <Input
              placeholder="Nova subtarefa..."
              value={novaSubtarefaTitulo}
              onChange={(e) => setNovaSubtarefaTitulo(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAdicionarSubtarefa();
                }
              }}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => handleAdicionarSubtarefa()}
            >
              <Plus size={14} />
              <span>Adicionar</span>
            </Button>
          </div>
        </div>
      </>
    </FormModal>
  );
};
