import { useState, useEffect, useCallback, useMemo } from 'react';
import { registrarFalha } from '@shared/diagnostico';
import {
  useGlobalSessionModal,
  Tabs,
  Button,
  Badge,
  Skeleton,
  SectionErrorCard,
  useConfirm,
} from '@shared/design-system';
import { useToast } from '@shared/design-system';
import { sessaoGlobalService } from '@shared/services/sessaoGlobalService';
import { EstudosService } from '../services/estudosService';
import { TrilhaDetalheDTO, TrilhaResumoDTO, RegistroBlocoPendente } from '../types/trilhara';
import { ModalidadeCronograma, CriarCronogramaPayload } from '@shared/contracts';
import type { CronogramaDTO, BlocoCronogramaItemDTO, DisponibilidadeSemanalDTO } from '@shared/contracts';
import type { Pagina } from '@shared/contracts';
import { hojeISO } from '@shared/format';
import styles from './PlanejamentoView.module.css';

const DIAS_SEMANA = [
  { id: 1, label: 'Segunda-feira' },
  { id: 2, label: 'Terça-feira' },
  { id: 3, label: 'Quarta-feira' },
  { id: 4, label: 'Quinta-feira' },
  { id: 5, label: 'Sexta-feira' },
  { id: 6, label: 'Sábado' },
  { id: 7, label: 'Domingo' },
];

const TAMANHO_PAGINA_BLOCOS = 20;
const MAX_DIAS_HORIZONTE = 366;

const ROTULO_MODALIDADE: Record<ModalidadeCronograma, string> = {
  SEQUENCIAL: 'Sequencial',
  PONDERADO: 'Ponderado',
  MANUAL: 'Manual',
};

const ROTULO_PERIODO: Record<string, string> = {
  UMA_SEMANA: '1 semana (7 dias)',
  UM_MES: '1 mês (30 dias)',
  TRES_MESES: '3 meses (90 dias)',
  ATE_PROVA: 'Até a data da prova',
};

/** Dias (1..7) representados com carga acima da disponibilidade futura restante. */
function diasEmSobrecarga(
  disponibilidade: DisponibilidadeSemanalDTO | null,
  novosMinutosPorDia: number[]
): string[] {
  if (!disponibilidade) return [];
  const rotulos: string[] = [];
  for (let i = 0; i < 7; i++) {
    const disponivel = disponibilidade.disponibilidadeSemanalMinutos[i] ?? 0;
    const carga = (disponibilidade.cargaAtivaMinutosPorDia[i] ?? 0) + (novosMinutosPorDia[i] ?? 0);
    if (disponivel > 0 && carga > disponivel) {
      rotulos.push(DIAS_SEMANA[i].label);
    }
  }
  return rotulos;
}

function diasEntre(inicioISO: string, fimISO: string): number {
  const inicio = new Date(`${inicioISO}T00:00:00`);
  const fim = new Date(`${fimISO}T00:00:00`);
  if (Number.isNaN(inicio.getTime()) || Number.isNaN(fim.getTime())) return 0;
  return Math.round((fim.getTime() - inicio.getTime()) / 86_400_000) + 1;
}

export interface PlanejamentoProps {
  trilha?: TrilhaDetalheDTO;
  onTrilhaAtualizada?: (atualizada: TrilhaDetalheDTO) => void;
  onIniciarEstudo?: (disciplinaId: string, registro: RegistroBlocoPendente) => void;
  onIrParaDisciplinas?: () => void;
}

type FiltroBlocos = 'PENDENTES' | 'CONCLUIDOS';

export function PlanejamentoView({
  trilha: trilhaProp,
  onTrilhaAtualizada,
  onIniciarEstudo,
  onIrParaDisciplinas,
}: PlanejamentoProps = {}) {
  const { abrirModal } = useGlobalSessionModal();
  const { confirm } = useConfirm();
  const toast = useToast();

  const [trilhas, setTrilhas] = useState<TrilhaResumoDTO[]>([]);
  const [selectedTrilhaId, setSelectedTrilhaId] = useState<string>(trilhaProp ? trilhaProp.id : '');
  const [selectedTrilhaDetalhe, setSelectedTrilhaDetalhe] = useState<TrilhaDetalheDTO | null>(trilhaProp || null);
  const [loadingTrilhas, setLoadingTrilhas] = useState(!trilhaProp);

  // Cronogramas + aba selecionada
  const [cronogramas, setCronogramas] = useState<CronogramaDTO[]>([]);
  const [selectedCronogramaId, setSelectedCronogramaId] = useState<string>('');
  const [loadingCronogramas, setLoadingCronogramas] = useState(false);

  // Blocos paginados
  const [paginaBlocos, setPaginaBlocos] = useState<Pagina<BlocoCronogramaItemDTO> | null>(null);
  const [page, setPage] = useState(1);
  const [filtroBlocos, setFiltroBlocos] = useState<FiltroBlocos>('PENDENTES');
  const [loadingBlocos, setLoadingBlocos] = useState(false);
  const [erroBlocos, setErroBlocos] = useState<Error | null>(null);
  const [totalAtrasados, setTotalAtrasados] = useState(0);

  // Formulário (criação e edição pelo lápis)
  const [showForm, setShowForm] = useState(false);
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [modalidade, setModalidade] = useState<ModalidadeCronograma>('SEQUENCIAL');
  const [tipoPeriodo, setTipoPeriodo] = useState<string>('UM_MES');
  const [diasAtivosSemana, setDiasAtivosSemana] = useState<number[]>([1, 2, 3, 4, 5]);
  const [blocosPorDia, setBlocosPorDia] = useState<number>(4);
  const [duracaoBlocoMinutos, setDuracaoBlocoMinutos] = useState<number>(50);
  const [dataInicio, setDataInicio] = useState<string>(() => hojeISO());
  const [savingCronograma, setSavingCronograma] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [disponibilidade, setDisponibilidade] = useState<DisponibilidadeSemanalDTO | null>(null);

  // ── "Até a data da prova" só é oferecido com dataProva > dataInicio ──
  const dataProvaFutura = useMemo(() => {
    const dataProva = selectedTrilhaDetalhe?.dataProva;
    if (!dataProva || !dataInicio) return false;
    return diasEntre(dataInicio, dataProva) > 1;
  }, [selectedTrilhaDetalhe?.dataProva, dataInicio]);

  const avisoHorizonte = useMemo(() => {
    const dataProva = selectedTrilhaDetalhe?.dataProva;
    if (!dataProva || !dataInicio) return null;
    const dias = diasEntre(dataInicio, dataProva);
    return dias > MAX_DIAS_HORIZONTE
      ? `Serão gerados os primeiros ${MAX_DIAS_HORIZONTE} dias. Você poderá atualizar o cronograma depois.`
      : null;
  }, [selectedTrilhaDetalhe?.dataProva, dataInicio]);

  const modalidadesUsadas = useMemo(
    () => new Set(cronogramas.map((c) => c.modalidade)),
    [cronogramas]
  );

  const minutosNovosPorDia = useMemo(() => {
    const porDia = new Array<number>(7).fill(0);
    for (const dia of diasAtivosSemana) {
      const indice = Math.min(6, Math.max(0, dia - 1));
      porDia[indice] += blocosPorDia * duracaoBlocoMinutos;
    }
    return porDia;
  }, [diasAtivosSemana, blocosPorDia, duracaoBlocoMinutos]);

  const sobrecarga = useMemo(
    () => diasEmSobrecarga(disponibilidade, minutosNovosPorDia),
    [disponibilidade, minutosNovosPorDia]
  );

  const cronogramaSelecionado = useMemo(
    () => cronogramas.find((c) => c.id === selectedCronogramaId) ?? null,
    [cronogramas, selectedCronogramaId]
  );

  // ── Carregamento de trilhas ──
  useEffect(() => {
    async function carregarTrilhas() {
      if (trilhaProp) {
        setLoadingTrilhas(false);
        return;
      }
      try {
        const lista = await EstudosService.listarTrilhas();
        setTrilhas(lista);
        if (!selectedTrilhaId && lista.length > 0) {
          setSelectedTrilhaId(lista[0].id);
        }
      } catch (err) {
        registrarFalha('planejamento.trilhas', err);
        setErrorMessage('Não foi possível carregar suas trilhas.');
      } finally {
        setLoadingTrilhas(false);
      }
    }
    void carregarTrilhas();
  }, [trilhaProp, selectedTrilhaId]);

  // ── Detalhe da trilha (disciplinas e dataProva) ──
  useEffect(() => {
    async function carregarDetalhe() {
      if (!selectedTrilhaId) {
        setSelectedTrilhaDetalhe(null);
        return;
      }
      if (trilhaProp && trilhaProp.id === selectedTrilhaId && trilhaProp.disciplinas?.length) {
        setSelectedTrilhaDetalhe(trilhaProp);
        return;
      }
      try {
        setSelectedTrilhaDetalhe(await EstudosService.buscarTrilha(selectedTrilhaId));
      } catch (err) {
        registrarFalha('planejamento.detalheTrilha', err);
      }
    }
    void carregarDetalhe();
  }, [selectedTrilhaId, trilhaProp]);

  // ── Disponibilidade semanal (aviso não bloqueante de sobrecarga) ──
  useEffect(() => {
    async function carregarDisponibilidade() {
      try {
        setDisponibilidade(await EstudosService.obterDisponibilidadeSemanal());
      } catch (err) {
        registrarFalha('planejamento.disponibilidade', err);
      }
    }
    void carregarDisponibilidade();
  }, []);

  // ── período padrão depende da data da prova ──
  useEffect(() => {
    setTipoPeriodo((atual) => {
      if (atual === 'ATE_PROVA' && !dataProvaFutura) return 'UM_MES';
      if (!showForm) return dataProvaFutura ? 'ATE_PROVA' : 'UM_MES';
      return atual;
    });
  }, [dataProvaFutura, showForm]);

  const carregarCronogramas = useCallback(async (trilhaId: string) => {
    if (!trilhaId) {
      setCronogramas([]);
      setSelectedCronogramaId('');
      return;
    }
    try {
      setLoadingCronogramas(true);
      const lista = await EstudosService.listarCronogramas(trilhaId);
      setCronogramas(lista);
      setSelectedCronogramaId((atual) => {
        if (atual && lista.some((c) => c.id === atual)) return atual;
        const ativo = lista.find((c) => c.ativo);
        return ativo ? ativo.id : (lista[0]?.id ?? '');
      });
    } catch (err) {
      registrarFalha('planejamento.cronogramas', err);
      setErrorMessage('Erro ao buscar cronogramas da trilha.');
    } finally {
      setLoadingCronogramas(false);
    }
  }, []);

  useEffect(() => {
    void carregarCronogramas(selectedTrilhaId);
  }, [selectedTrilhaId, carregarCronogramas]);


  const carregarBlocos = useCallback(
    async (trilhaId: string, cronogramaId: string, p: number, filtro: FiltroBlocos) => {
      if (!trilhaId || !cronogramaId) {
        setPaginaBlocos(null);
        return;
      }
      try {
        setLoadingBlocos(true);
        setErroBlocos(null);
        const res = await EstudosService.listarBlocosCronograma(trilhaId, cronogramaId, {
          pagina: p,
          tamanho: TAMANHO_PAGINA_BLOCOS,
          concluido: filtro === 'CONCLUIDOS'
        });
        setPaginaBlocos(res);
      } catch (err) {
        registrarFalha('planejamento.blocos', err);
        setErroBlocos(err instanceof Error ? err : new Error('Erro ao listar blocos do cronograma.'));
      } finally {
        setLoadingBlocos(false);
      }
    },
    []
  );

  useEffect(() => {
    if (selectedTrilhaId && selectedCronogramaId) {
      void carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, filtroBlocos);
    } else {
      setPaginaBlocos(null);
    }
  }, [selectedTrilhaId, selectedCronogramaId, page, filtroBlocos, carregarBlocos]);

  // ── contagem exata de atrasados via totalItens (statusData=ATRASADO&tamanho=1) ──
  const carregarTotalAtrasados = useCallback(async () => {
    if (!selectedTrilhaId || !selectedCronogramaId) {
      setTotalAtrasados(0);
      return;
    }
    try {
      setTotalAtrasados(await EstudosService.contarBlocosAtrasados(selectedTrilhaId, selectedCronogramaId));
    } catch (err) {
      registrarFalha('planejamento.contagemAtrasados', err);
      setTotalAtrasados(0);
    }
  }, [selectedTrilhaId, selectedCronogramaId]);

  useEffect(() => {
    void carregarTotalAtrasados();
  }, [carregarTotalAtrasados]);

  const recarregarTudo = useCallback(async () => {
    if (!selectedTrilhaId || !selectedCronogramaId) return;
    await carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, filtroBlocos);
    await carregarTotalAtrasados();
  }, [selectedTrilhaId, selectedCronogramaId, page, filtroBlocos, carregarBlocos, carregarTotalAtrasados]);

  const handleToggleDia = (diaId: number) => {
    setDiasAtivosSemana((prev) =>
      prev.includes(diaId) ? prev.filter((d) => d !== diaId) : [...prev, diaId].sort((a, b) => a - b)
    );
  };

  const abrirCriacao = () => {
    setEditandoId(null);
    setModalidade(
      (['SEQUENCIAL', 'PONDERADO', 'MANUAL'] as ModalidadeCronograma[]).find((m) => !modalidadesUsadas.has(m))
        ?? 'SEQUENCIAL'
    );
    setTipoPeriodo(dataProvaFutura ? 'ATE_PROVA' : 'UM_MES');
    setDataInicio(hojeISO());
    setErrorMessage(null);
    setShowForm(true);
  };

  const abrirEdicao = (cronograma: CronogramaDTO) => {
    setEditandoId(cronograma.id);
    setModalidade(cronograma.modalidade);
    setTipoPeriodo(cronograma.tipoPeriodo);
    setDiasAtivosSemana(cronograma.diasAtivosSemana ?? [1, 2, 3, 4, 5]);
    setBlocosPorDia(cronograma.blocosPorDia ?? 4);
    setDuracaoBlocoMinutos(cronograma.duracaoBlocoMinutos);
    setDataInicio(cronograma.dataInicio);
    setErrorMessage(null);
    setShowForm(true);
  };

  const montarPayload = (): CriarCronogramaPayload => ({
    modalidade,
    duracaoBlocoMinutos,
    tipoPeriodo,
    dataInicio,
    diasAtivosSemana,
    blocosPorDia,
    disciplinasParticipantesIds: (selectedTrilhaDetalhe?.disciplinas ?? []).map((d) => d.id)
  });

  const tratarErro = (err: unknown, padrao: string) => {
    const mensagem = err instanceof Error ? err.message : '';
    if (mensagem.includes('DUPLICATE_MODALITY')) {
      toast.error('Esta trilha já possui um cronograma com a modalidade selecionada.');
      return;
    }
    if (mensagem.includes('SCHEDULE_GENERATION_IN_PROGRESS')) {
      toast.error('Existe outra operação em andamento para esta trilha. Tente novamente em alguns segundos.');
      return;
    }
    toast.error(mensagem || padrao);
  };


  const handleSalvarCronograma = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);

    if (!selectedTrilhaDetalhe?.disciplinas?.length) {
      setErrorMessage('Esta trilha não possui disciplinas cadastradas para compor o cronograma.');
      return;
    }
    if (diasAtivosSemana.length === 0) {
      setErrorMessage('Selecione ao menos um dia da semana.');
      return;
    }
    if (blocosPorDia < 1 || blocosPorDia > 24) {
      setErrorMessage('A quantidade de blocos por dia deve ser entre 1 e 24.');
      return;
    }
    if (duracaoBlocoMinutos < 30 || duracaoBlocoMinutos > 240) {
      setErrorMessage('A duração do bloco deve estar entre 30 e 240 minutos.');
      return;
    }

    try {
      setSavingCronograma(true);
      if (editandoId) {
        const confirmado = await confirm({
          title: 'Gerar blocos novamente?',
          description:
            'Os blocos atuais e suas conclusões serão substituídos. Sessões e progresso de tópicos são mantidos.',
          confirmText: 'Salvar e gerar blocos',
          cancelText: 'Cancelar'
        });
        if (!confirmado) return;
        const atualizado = await EstudosService.atualizarCronograma(selectedTrilhaId, editandoId, montarPayload());
        setShowForm(false);
        setEditandoId(null);
        await carregarCronogramas(selectedTrilhaId);
        setSelectedCronogramaId(atualizado.id);
        toast.success('Cronograma atualizado e blocos gerados.');
      } else {
        const criado = await EstudosService.criarCronograma(selectedTrilhaId, montarPayload());
        setShowForm(false);
        await carregarCronogramas(selectedTrilhaId);
        setSelectedCronogramaId(criado.id);
        toast.success('Cronograma criado e ativado.');
      }
      setPage(1);
      await recarregarTudo();
      if (onTrilhaAtualizada) {
        onTrilhaAtualizada(await EstudosService.buscarTrilha(selectedTrilhaId));
      }
    } catch (err) {
      registrarFalha('planejamento.salvar', err);
      tratarErro(err, 'Erro ao salvar o cronograma.');
    } finally {
      setSavingCronograma(false);
    }
  };

  const handleAtivarCronograma = async (cronogramaId: string) => {
    try {
      await EstudosService.ativarCronograma(selectedTrilhaId, cronogramaId);
      await carregarCronogramas(selectedTrilhaId);
      toast.success('Cronograma ativado.');
      if (onTrilhaAtualizada) {
        onTrilhaAtualizada(await EstudosService.buscarTrilha(selectedTrilhaId));
      }
    } catch (err) {
      tratarErro(err, 'Erro ao ativar cronograma.');
    }
  };

  const handleExcluirCronograma = async (cronograma: CronogramaDTO) => {
    const confirmado = await confirm({
      title: 'Excluir cronograma?',
      description: `Todos os blocos do cronograma ${ROTULO_MODALIDADE[cronograma.modalidade]} serão removidos. Sessões já registradas permanecem.`,
      confirmText: 'Excluir',
      cancelText: 'Cancelar',
      variant: 'danger'
    });
    if (!confirmado) return;
    try {
      await EstudosService.excluirCronograma(selectedTrilhaId, cronograma.id);
      await carregarCronogramas(selectedTrilhaId);
      toast.success('Cronograma excluído.');
      if (onTrilhaAtualizada) {
        onTrilhaAtualizada(await EstudosService.buscarTrilha(selectedTrilhaId));
      }
    } catch (err) {
      tratarErro(err, 'Erro ao excluir cronograma.');
    }
  };

  /** concluir SEMPRE abre o modal global; reabrir é direto, sem modal e sem apagar sessões. */
  const handleConcluirBloco = (bloco: BlocoCronogramaItemDTO) => {
    abrirModal({
      modo: 'BLOCO_DIRETO',
      trilhaId: bloco.trilhaId,
      disciplinaId: bloco.disciplinaId,
      blocoId: bloco.id,
      duracaoInicialSegundos: bloco.duracaoMinutos * 60,
      disciplinaNome: bloco.disciplinaNome,
      disciplinaCor: bloco.disciplinaCor,
      trilhaNome: selectedTrilhaDetalhe?.nome,
      onSuccess: () => {
        void recarregarTudo();
      }
    });
  };

  const handleReabrirBloco = async (bloco: BlocoCronogramaItemDTO) => {
    try {
      await sessaoGlobalService.reabrirBloco(bloco.id);
      await recarregarTudo();
    } catch (err) {
      tratarErro(err, 'Erro ao reabrir o bloco.');
    }
  };

  const handleConcluirAtrasados = async () => {
    if (!selectedCronogramaId || totalAtrasados === 0) return;
    const confirmado = await confirm({
      title: 'Concluir blocos atrasados',
      description: `Concluir ${totalAtrasados} blocos atrasados sem registrar sessão de estudo?`,
      confirmText: 'Concluir sem sessão',
      cancelText: 'Cancelar'
    });
    if (!confirmado) return;
    try {
      const res = await EstudosService.concluirBlocosAtrasados(selectedTrilhaId, selectedCronogramaId);
      if (res.falhas.length > 0) {
        toast.error(
          `Foram concluídos ${res.totalConcluidos} de ${res.totalProcessados} blocos. ${res.falhas.length} falharam.`
        );
      } else {
        toast.success(`${res.totalConcluidos} blocos atrasados concluídos.`);
      }
      await recarregarTudo();
    } catch (err) {
      tratarErro(err, 'Erro ao concluir blocos atrasados.');
    }
  };

  const handleIniciarEstudoBloco = (bloco: BlocoCronogramaItemDTO) => {
    if (!onIniciarEstudo) return;
    const registro: RegistroBlocoPendente = {
      requestId: `bloco-${bloco.id}-${Date.now()}`,
      trilhaId: bloco.trilhaId,
      cronogramaId: bloco.cronogramaId,
      blocoId: bloco.id,
      disciplinaId: bloco.disciplinaId,
      duracaoMinutos: bloco.duracaoMinutos
    };
    onIniciarEstudo(bloco.disciplinaId, registro);
  };


  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Planejamento de Cronogramas</h1>
          <p className={styles.subtitle}>
            Gere e gerencie cronogramas de estudo estruturados, com controle de modalidade e blocos diários.
          </p>
        </div>
        <div className={styles.headerActions}>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={handleConcluirAtrasados}
            disabled={!selectedCronogramaId || totalAtrasados === 0}
            loading={savingCronograma}
          >
            Concluir atrasados sem sessão ({totalAtrasados})
          </Button>
          <Button
            type="button"
            variant="primary"
            size="sm"
            onClick={showForm ? () => setShowForm(false) : abrirCriacao}
            disabled={!showForm && cronogramas.length >= 3}
          >
            {showForm ? 'Cancelar' : 'Novo cronograma'}
          </Button>
        </div>
      </header>

      {errorMessage && <div className={styles.errorMessage} role="alert">{errorMessage}</div>}

      <section className={styles.sectionCard}>
        <div className={styles.filterRow}>
          <label htmlFor="trilhaSelect" className={styles.label}>Trilha:</label>
          <select
            id="trilhaSelect"
            className={styles.select}
            value={selectedTrilhaId}
            onChange={(e) => setSelectedTrilhaId(e.target.value)}
            disabled={loadingTrilhas}
          >
            {trilhas.map((t) => (
              <option key={t.id} value={t.id}>{t.nome} ({t.totalDisciplinas} disciplinas)</option>
            ))}
          </select>
          {selectedTrilhaDetalhe && (
            <span className={styles.badgeMuted}>{cronogramas.length} de 3 cronogramas</span>
          )}
          {onIrParaDisciplinas && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={onIrParaDisciplinas}
            >
              Gerenciar disciplinas
            </Button>
          )}
        </div>
      </section>


      {/* Formulário de criação/edição — */}
      {showForm && (
        <section className={styles.formCard}>
          <h2 className={styles.formTitle}>
            {editandoId ? 'Editar cronograma' : 'Novo cronograma'}
          </h2>
          <form onSubmit={handleSalvarCronograma} className={styles.assistantForm}>
            {sobrecarga.length > 0 && (
              <div className={styles.overloadAlert} role="status">
                Sobrecarga de carga futura em: {sobrecarga.join(', ')}. Você pode salvar mesmo assim.
              </div>
            )}
            {avisoHorizonte && (
              <div className={styles.overloadAlertContent} role="status">{avisoHorizonte}</div>
            )}

            <div className={styles.formGrid}>
              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="cronograma-modalidade">Modalidade</label>
                <select
                  className={styles.select}
                  id="cronograma-modalidade"
                  value={modalidade}
                  onChange={(e) => setModalidade(e.target.value as ModalidadeCronograma)}
                >
                  {(['SEQUENCIAL', 'PONDERADO', 'MANUAL'] as ModalidadeCronograma[]).map((m) => {
                    const usada = modalidadesUsadas.has(m) && editandoId !== cronogramas.find((c) => c.modalidade === m)?.id;
                    return (
                      <option
                        key={m}
                        value={m}
                        disabled={usada}
                        title={usada ? `Esta trilha já possui um cronograma ${ROTULO_MODALIDADE[m]}` : undefined}
                      >
                        {ROTULO_MODALIDADE[m]}
                      </option>
                    );
                  })}
                </select>
                {modalidadesUsadas.has(modalidade) && (
                  <p className={styles.fieldLabel}>
                    Esta trilha já possui um cronograma {ROTULO_MODALIDADE[modalidade]}
                  </p>
                )}
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="cronograma-periodo">Período</label>
                <select
                  className={styles.select}
                  id="cronograma-periodo"
                  value={tipoPeriodo}
                  onChange={(e) => setTipoPeriodo(e.target.value)}
                >
                  <option value="ATE_PROVA" disabled={!dataProvaFutura}>
                    {ROTULO_PERIODO.ATE_PROVA}
                  </option>
                  <option value="UMA_SEMANA">{ROTULO_PERIODO.UMA_SEMANA}</option>
                  <option value="UM_MES">{ROTULO_PERIODO.UM_MES}</option>
                  <option value="TRES_MESES">{ROTULO_PERIODO.TRES_MESES}</option>
                </select>
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="cronograma-blocos-dia">Blocos por dia (máx 24)</label>
                <input
                  type="number"
                  className={styles.input}
                  min={1}
                  max={24}
                  id="cronograma-blocos-dia"
                  value={blocosPorDia}
                  onChange={(e) => setBlocosPorDia(Number.parseInt(e.target.value, 10) || 1)}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="cronograma-duracao-bloco">Duração do bloco (minutos)</label>
                <input
                  type="number"
                  className={styles.input}
                  min={30}
                  max={240}
                  step={5}
                  id="cronograma-duracao-bloco"
                  value={duracaoBlocoMinutos}
                  onChange={(e) => setDuracaoBlocoMinutos(Number.parseInt(e.target.value, 10) || 30)}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="cronograma-data-inicio">Data de início</label>
                <input
                  type="date"
                  className={styles.input}
                  id="cronograma-data-inicio"
                  value={dataInicio}
                  onChange={(e) => setDataInicio(e.target.value)}
                />
              </div>
            </div>

            <fieldset className={styles.formGroup}>
              <legend className={styles.label}>Dias da semana para estudo</legend>
              <div className={styles.diasCheckboxGroup}>
                {DIAS_SEMANA.map((dia) => (
                  <label key={dia.id} className={styles.diaCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={diasAtivosSemana.includes(dia.id)}
                      onChange={() => handleToggleDia(dia.id)}
                    />
                    <span>{dia.label}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            <div className={styles.formActions}>
              <Button
                type="button"
                variant="secondary"
                onClick={() => setShowForm(false)}
                disabled={savingCronograma}
              >
                Cancelar
              </Button>
              <Button type="submit" variant="primary" loading={savingCronograma}>
                {editandoId ? 'Salvar e gerar blocos' : 'Criar cronograma'}
              </Button>
            </div>
          </form>
        </section>
      )}


      {/* Abas por cronograma (APG Tabs) + badge Ativo */}
      <section className={styles.sectionCard}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>Cronogramas da trilha</h2>
          {cronogramaSelecionado?.ativo && <Badge variant="success" size="sm">Ativo</Badge>}
        </div>

        {loadingCronogramas ? (
          <Skeleton variant="row" linhas={1} />
        ) : cronogramas.length === 0 ? (
          <p className={styles.emptyText}>
            Nenhum cronograma cadastrado nesta trilha. Use &quot;Novo cronograma&quot; para gerar o primeiro.
          </p>
        ) : (
          <>
            <Tabs
              items={cronogramas.map((c) => ({
                id: c.id,
                label: c.ativo ? `${ROTULO_MODALIDADE[c.modalidade]} · Ativo` : ROTULO_MODALIDADE[c.modalidade]
              }))}
              activeId={selectedCronogramaId}
              onChange={(id) => {
                setSelectedCronogramaId(id);
                setPage(1);
              }}
            />

            {cronogramaSelecionado && (
              <div className={styles.cronogramaMeta}>
                <span>{ROTULO_PERIODO[cronogramaSelecionado.tipoPeriodo] ?? cronogramaSelecionado.tipoPeriodo}</span>
                <span>{cronogramaSelecionado.dataInicio} → {cronogramaSelecionado.dataFim}</span>
                <span>{cronogramaSelecionado.quantidadeBlocos} blocos</span>
                <span>{cronogramaSelecionado.duracaoBlocoMinutos} min por bloco</span>
                <div className={styles.cronogramaActions}>
                  {!cronogramaSelecionado.ativo && (
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => void handleAtivarCronograma(cronogramaSelecionado.id)}
                    >
                      Ativar
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => abrirEdicao(cronogramaSelecionado)}
                    aria-label="Editar cronograma"
                  >
                    ✏️
                  </Button>
                  <Button
                    type="button"
                    variant="danger"
                    size="sm"
                    onClick={() => void handleExcluirCronograma(cronogramaSelecionado)}
                  >
                    Excluir
                  </Button>
                </div>
                {cronogramaSelecionado.estadoPublicacao === 'RECUPERACAO_NECESSARIA' && (
                  <SectionErrorCard
                    titulo="Recuperação necessária na publicação do cronograma"
                    descricao="Ocorreu uma interrupção ao publicar este cronograma. Regenere ou ative novamente para restaurar a consistência."
                    onRetry={() => void handleAtivarCronograma(cronogramaSelecionado.id)}
                  />
                )}
              </div>
            )}
          </>
        )}
      </section>


      {/* Blocos do cronograma selecionado */}
      {selectedCronogramaId && (
        <section className={styles.blocosSection}>
          <div className={styles.blocosToolbar}>
            <div className={styles.blocosFiltros} role="group" aria-label="Filtrar blocos">
              <Button
                type="button"
                size="sm"
                variant={filtroBlocos === 'PENDENTES' ? 'primary' : 'secondary'}
                aria-pressed={filtroBlocos === 'PENDENTES'}
                onClick={() => { setFiltroBlocos('PENDENTES'); setPage(1); }}
              >
                Pendentes
              </Button>
              <Button
                type="button"
                size="sm"
                variant={filtroBlocos === 'CONCLUIDOS' ? 'primary' : 'secondary'}
                aria-pressed={filtroBlocos === 'CONCLUIDOS'}
                onClick={() => { setFiltroBlocos('CONCLUIDOS'); setPage(1); }}
              >
                Concluídos
              </Button>
            </div>
          </div>

          {loadingBlocos ? (
            <div className={styles.blocosTableWrapper}>
              <Skeleton variant="row" linhas={1} />
              <Skeleton variant="row" linhas={1} />
              <Skeleton variant="row" linhas={1} />
            </div>
          ) : erroBlocos ? (
            <SectionErrorCard
              titulo="Não foi possível carregar"
              descricao="Verifique sua conexão e tente novamente."
              onRetry={() => void recarregarTudo()}
              recarregando={loadingBlocos}
            />
          ) : !paginaBlocos || paginaBlocos.itens.length === 0 ? (
            <p className={styles.emptyText}>
              {filtroBlocos === 'CONCLUIDOS'
                ? 'Nenhum bloco concluído neste cronograma.'
                : 'Nenhum bloco pendente neste cronograma.'}
            </p>
          ) : (
            <div className={styles.blocosTableWrapper}>
              <table className={styles.blocosTable}>
                <thead>
                  <tr>
                    <th scope="col">Data</th>
                    <th scope="col">#</th>
                    <th scope="col">Disciplina</th>
                    <th scope="col">Duração</th>
                    <th scope="col">Situação</th>
                    <th scope="col">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {paginaBlocos.itens.map((bloco) => (
                    <tr key={bloco.id} className={bloco.concluido ? styles.blocoConcluidoRow : undefined}>
                      <td>{bloco.data}</td>
                      <td>{bloco.ordem}</td>
                      <td className={styles.blocoDisciplinaCell}>
                        <span
                          className={styles.blocoColorDot}
                          style={{ backgroundColor: bloco.disciplinaCor || '#6366f1' }}
                          aria-hidden="true"
                        />
                        <strong>{bloco.disciplinaNome}</strong>
                      </td>
                      <td>{bloco.duracaoMinutos} min</td>
                      <td>
                        <span className={bloco.concluido ? styles.statusBadgeConcluido : styles.statusBadgePendente}>
                          {bloco.concluido ? 'CONCLUÍDO' : bloco.statusData}
                        </span>
                      </td>
                      <td>
                        <div className={styles.blockActions}>
                          <input
                            type="checkbox"
                            aria-label={`Concluir bloco ${bloco.ordem} de ${bloco.data}`}
                            checked={bloco.concluido}
                            onChange={() => {
                              if (bloco.concluido) {
                                void handleReabrirBloco(bloco);
                              } else {
                                handleConcluirBloco(bloco);
                              }
                            }}
                          />
                          {!bloco.concluido && filtroBlocos === 'PENDENTES' && (
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => handleIniciarEstudoBloco(bloco)}
                              disabled={!onIniciarEstudo}
                            >
                              Estudar este bloco
                            </Button>
                          )}
                          {bloco.concluido && (
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => void handleReabrirBloco(bloco)}
                            >
                              Reabrir
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}


          {paginaBlocos && paginaBlocos.totalPaginas > 1 && (
            <div className={styles.paginationBar}>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                disabled={page <= 1}
                onClick={() => setPage((prev) => Math.max(1, prev - 1))}
              >
                Anterior
              </Button>
              <span className={styles.paginationLabel}>
                Página {page} de {paginaBlocos.totalPaginas}
              </span>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                disabled={page >= paginaBlocos.totalPaginas}
                onClick={() => setPage((prev) => prev + 1)}
              >
                Próxima
              </Button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
