import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Award,
  BarChart2,
  BookOpen,
  Clock,
  Edit2,
  ExternalLink,
  Flame,
  Target,
  Trash2,
  TrendingUp
} from 'lucide-react';
import {
  Button,
  DatePicker,
  DisciplineLabel,
  Input,
  Modal,
  Pagination,
  PageBanner,
  ProgressBar,
  useConfirm,
  useToast
} from '@shared/design-system';
import {
  Ciclo,
  DisciplinaProgramada,
  EstatisticaDisciplinaDTO,
  EstatisticasTrilhaDTO,
  Sessao,
  Trilha
} from '../types/trilhara';
import { EstudosService, PaginaSessoes } from '../services/estudosService';
import { PlanejamentoStorage } from '../services/planejamentoStorage';
import {
  formatarDataBR,
  formatarDuracaoSegundos,
  formatarMinutos,
  formatarSegundos,
  hojeISO,
  paraDataLocalISO
} from '../utils/format';
import { EmptyJourneyState } from '../components/EmptyJourneyState/EmptyJourneyState';
import styles from './DashboardView.module.css';

type Periodo = '7' | '14' | '30' | '180' | '0' | 'custom';
type FiltroDiagnostico = 'todas' | 'critico' | 'atencao';
type FiltroHoras = 'todas' | 'abaixo_media' | 'muito_abaixo_media';

interface OpcaoPeriodo {
  value: Periodo;
  label: string;
  descricao: string;
}

const OPCOES_PERIODO: OpcaoPeriodo[] = [
  { value: '7', label: '7 Dias', descricao: 'Últimos 7 dias' },
  { value: '14', label: '14 Dias', descricao: 'Últimos 14 dias' },
  { value: '30', label: '30 Dias', descricao: 'Últimos 30 dias' },
  { value: '180', label: '6 Meses', descricao: 'Últimos 6 meses' },
  { value: '0', label: 'Tudo', descricao: 'Todo o período' },
  { value: 'custom', label: 'Personalizado', descricao: 'Período personalizado' }
];

const TAMANHO_PAGINA = 20;

function subtrairDias(dataISO: string, dias: number): string {
  const [ano, mes, dia] = dataISO.split('-').map(Number);
  const data = new Date(ano, mes - 1, dia);
  data.setDate(data.getDate() - dias);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function diferencaEmDias(inicioISO: string, fimISO: string): number {
  const [anoInicio, mesInicio, diaInicio] = inicioISO.split('-').map(Number);
  const [anoFim, mesFim, diaFim] = fimISO.split('-').map(Number);
  const inicio = Date.UTC(anoInicio, mesInicio - 1, diaInicio);
  const fim = Date.UTC(anoFim, mesFim - 1, diaFim);
  return Math.floor((fim - inicio) / 86_400_000);
}

function formatarHoras(horas: number): string {
  return `${horas.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h`;
}

function nivelAcerto(percentual: number): string {
  if (percentual >= 80) return styles.tierGood;
  if (percentual >= 60) return styles.tierWarn;
  return styles.tierBad;
}

function corNivelAcerto(percentual: number): string {
  const nivel = nivelAcerto(percentual);
  if (nivel === styles.tierGood) return 'var(--green)';
  if (nivel === styles.tierWarn) return 'var(--yellow)';
  return 'var(--red)';
}

function cicloAtual(trilha: Trilha): Ciclo | undefined {
  return trilha.ciclos.find((ciclo) => ciclo.id === trilha.cicloAtivoId)
    || trilha.ciclos.at(-1);
}

export interface DashboardViewProps {
  trilha: Trilha;
  onIrParaDisciplinas: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  trilha,
  onIrParaDisciplinas
}) => {
  const toast = useToast();
  const { confirm } = useConfirm();

  const [periodo, setPeriodo] = useState<Periodo>('14');
  const [customDe, setCustomDe] = useState<string>(() => subtrairDias(hojeISO(), 29));
  const [customAte, setCustomAte] = useState<string>(() => hojeISO());
  const [tempCustomDe, setTempCustomDe] = useState<string>(customDe);
  const [tempCustomAte, setTempCustomAte] = useState<string>(customAte);
  const [mostrarCustomPopover, setMostrarCustomPopover] = useState(false);
  const customPopoverRef = useRef<HTMLDivElement>(null);

  const [filtroDiagnostico, setFiltroDiagnostico] = useState<FiltroDiagnostico>('todas');
  const [filtroHoras, setFiltroHoras] = useState<FiltroHoras>('todas');
  const [estatisticas, setEstatisticas] = useState<EstatisticasTrilhaDTO | null>(null);
  const [paginaSessoes, setPaginaSessoes] = useState<PaginaSessoes | null>(null);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);
  const [cronograma, setCronograma] = useState<DisciplinaProgramada[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [paginaHistorico, setPaginaHistorico] = useState(1);
  const [tentativa, setTentativa] = useState(0);

  // Estado para edição da sessão mais recente
  const [sessaoEditando, setSessaoEditando] = useState<Sessao | null>(null);
  const [editHoras, setEditHoras] = useState(0);
  const [editMinutos, setEditMinutos] = useState(0);
  const [editQuestoes, setEditQuestoes] = useState(0);
  const [editAcertos, setEditAcertos] = useState(0);
  const [salvandoEdicao, setSalvandoEdicao] = useState(false);

  const ciclo = useMemo(() => cicloAtual(trilha), [trilha]);
  const hoje = hojeISO();

  const dataFinalPeriodo = periodo === 'custom' ? customAte : hoje;
  const dataInicialPeriodo = useMemo(() => {
    if (periodo === '0') return undefined;
    if (periodo === 'custom') return customDe;
    return subtrairDias(hoje, Number(periodo) - 1);
  }, [customDe, hoje, periodo]);

  const periodoBackend = useMemo(() => {
    if (periodo === '0') return 'tudo';
    if (periodo === 'custom') return undefined;
    return `${periodo}d`;
  }, [periodo]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        customPopoverRef.current &&
        !customPopoverRef.current.contains(event.target as Node)
      ) {
        setMostrarCustomPopover(false);
      }
    }
    if (mostrarCustomPopover) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [mostrarCustomPopover]);

  useEffect(() => {
    let ativa = true;

    async function carregarDados() {
      setCarregando(true);
      setErro(null);

      const filtro = {
        ...(periodoBackend ? { periodo: periodoBackend } : {}),
        ...(dataInicialPeriodo ? { dataInicio: dataInicialPeriodo } : {}),
        ...(dataFinalPeriodo ? { dataFim: dataFinalPeriodo } : {})
      };

      try {
        const [stats, sessaoPag] = await Promise.all([
          EstudosService.obterEstatisticas(trilha.id, filtro),
          EstudosService.listarSessoesPaginado(trilha.id, {
            ...filtro,
            pagina: 1,
            tamanho: TAMANHO_PAGINA
          })
        ]);
        if (!ativa) return;
        setEstatisticas(stats);
        setPaginaSessoes(sessaoPag);
        setPaginaHistorico(1);
      } catch (err) {
        if (!ativa) return;
        const mensagem = err instanceof Error ? err.message : 'Erro ao carregar estatísticas.';
        setErro(mensagem);
        toast.error(mensagem);
      } finally {
        if (ativa) setCarregando(false);
      }
    }

    void carregarDados();
    return () => {
      ativa = false;
    };
  }, [dataFinalPeriodo, dataInicialPeriodo, periodoBackend, tentativa, toast, trilha.id]);

  async function trocarPagina(novaPagina: number) {
    setPaginaHistorico(novaPagina);
    setCarregandoHistorico(true);
    try {
      const filtro = {
        ...(dataInicialPeriodo ? { dataInicio: dataInicialPeriodo } : {}),
        ...(dataFinalPeriodo ? { dataFim: dataFinalPeriodo } : {}),
        pagina: novaPagina,
        tamanho: TAMANHO_PAGINA
      };
      const res = await EstudosService.listarSessoesPaginado(trilha.id, filtro);
      setPaginaSessoes(res);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Erro ao carregar página de sessões.');
    } finally {
      setCarregandoHistorico(false);
    }
  }

  useEffect(() => {
    if (!ciclo) {
      setCronograma([]);
      return;
    }
    let ativa = true;
    const carregarPlanejamento = async () => {
      try {
        const estado = await PlanejamentoStorage.carregar(trilha.id, ciclo.id);
        if (ativa) setCronograma(estado.cronograma);
      } catch (err) {
        if (!ativa) return;
        setCronograma([]);
        toast.error(err instanceof Error ? err : 'Não foi possível carregar o planejamento do ciclo.');
      }
    };
    void carregarPlanejamento();
    return () => {
      ativa = false;
    };
  }, [ciclo, toast, trilha.id]);

  const porDisciplinaStats = useMemo(() => {
    return estatisticas?.porDisciplina ?? [];
  }, [estatisticas]);

  const totalSegundos = estatisticas?.totalSegundos ?? 0;
  const totalMinutos = Math.round(totalSegundos / 60);
  const totalQuestoes = estatisticas?.totalQuestoes ?? 0;
  const totalAcertos = estatisticas?.totalAcertos ?? 0;
  const percentualAcerto = totalQuestoes > 0
    ? Math.round(estatisticas?.percentualAcertoGlobal ?? ((totalAcertos / totalQuestoes) * 100))
    : null;

  const cobertura = useMemo(() => {
    const total = trilha.disciplinas.reduce(
      (soma, d) => soma + (d.topicos ? d.topicos.length : 0),
      0
    );
    const concluidos = trilha.disciplinas.reduce(
      (soma, d) => soma + (d.topicos ? d.topicos.filter((t) => trilha.progresso?.[t.id]?.done || trilha.topicosConcluidosIds?.includes(t.id)).length : 0),
      0
    );
    return {
      total,
      concluidos,
      percentual: total > 0 ? Math.round((concluidos / total) * 100) : 0
    };
  }, [trilha]);

  const ritmo = useMemo(() => {
    const pendentes = Math.max(0, cobertura.total - cobertura.concluidos);
    if (!trilha.dataProva) {
      return { valor: `${pendentes} pendentes`, descricao: 'Defina a data da prova para calcular o ritmo diário.' };
    }

    const diasAteProva = diferencaEmDias(hoje, trilha.dataProva);
    if (diasAteProva < 0) {
      return { valor: 'Prova realizada', descricao: `${pendentes} assuntos ainda pendentes.` };
    }
    if (diasAteProva === 0) {
      return { valor: 'Prova hoje', descricao: `${pendentes} assuntos ainda pendentes.` };
    }
    return {
      valor: `${(pendentes / diasAteProva).toLocaleString('pt-BR', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      })} assuntos/dia`,
      descricao: `${pendentes} assuntos pendentes nos próximos ${diasAteProva} dias.`
    };
  }, [cobertura, hoje, trilha.dataProva]);

  const periodoResumo = useMemo(() => {
    const dataInicial = dataInicialPeriodo || ciclo?.dataInicio;
    const dataFinal = dataFinalPeriodo || hoje;
    const blocosPlanejados = cronograma.filter((bloco) =>
      bloco.data <= dataFinal
      && (!dataInicial || bloco.data >= dataInicial)
    );

    let metaMinutos = 0;
    let totalBlocos = 0;
    if (cronograma.length > 0) {
      totalBlocos = blocosPlanejados.length;
      metaMinutos = blocosPlanejados.reduce(
        (total, bloco) => total + bloco.duracaoMinutos,
        0
      );
    } else if (ciclo) {
      const blocosPorSemana = (ciclo.blocosSemana || [])
        .reduce((total, blocos) => total + blocos, 0);
      const inicioMeta = dataInicial
        ? (dataInicial > (ciclo.dataInicio || dataInicial) ? dataInicial : ciclo.dataInicio || dataInicial)
        : ciclo.dataInicio || dataFinal;
      const diasConsiderados = Math.max(0, diferencaEmDias(inicioMeta, dataFinal) + 1);
      const minutosPorSemana = blocosPorSemana * (ciclo.duracaoBlocoMin || 0);
      metaMinutos = (minutosPorSemana * diasConsiderados) / 7;
      totalBlocos = Math.round((blocosPorSemana * diasConsiderados) / 7);
    }

    const realizadoMinutos = totalMinutos;
    const cumprimento = metaMinutos > 0
      ? Math.round((realizadoMinutos / metaMinutos) * 100)
      : realizadoMinutos > 0 ? 100 : 0;

    return {
      horasEstudadas: realizadoMinutos / 60,
      horasMeta: metaMinutos / 60,
      cumprimento,
      totalBlocos,
      sessoes: estatisticas?.totalSessoes ?? paginaSessoes?.total ?? 0,
      diasComEstudo: estatisticas?.serieTemporal.length ?? 0
    };
  }, [
    ciclo,
    cronograma,
    dataFinalPeriodo,
    dataInicialPeriodo,
    estatisticas?.serieTemporal.length,
    estatisticas?.totalSessoes,
    hoje,
    paginaSessoes?.total,
    totalMinutos
  ]);

  const disciplinasComQuestoes = useMemo(() => {
    const comQuestoes = porDisciplinaStats
      .filter((item) => item.totalQuestoes > 0)
      .filter((item) => filtroDiagnostico === 'todas'
        || (filtroDiagnostico === 'critico' && item.percentualAcerto < 60)
        || (filtroDiagnostico === 'atencao' && item.percentualAcerto < 80));
    return comQuestoes.sort((a, b) =>
      a.percentualAcerto - b.percentualAcerto
      || b.totalQuestoes - a.totalQuestoes
      || a.nome.localeCompare(b.nome, 'pt-BR')
    );
  }, [filtroDiagnostico, porDisciplinaStats]);

  const mediaHorasDisciplinas = useMemo(() => {
    const comHoras = porDisciplinaStats.filter((item) => item.totalSegundos > 0);
    return comHoras.length > 0 ? totalMinutos / comHoras.length : 0;
  }, [porDisciplinaStats, totalMinutos]);

  const disciplinasComHoras = useMemo(() => {
    const comHoras = porDisciplinaStats.filter((item) => item.totalSegundos > 0);
    if (comHoras.length === 0) return [];

    const mediaMuitoAbaixo = mediaHorasDisciplinas * 0.5;

    const filtradas = comHoras.filter((item) => {
      const min = Math.round(item.totalSegundos / 60);
      if (filtroHoras === 'abaixo_media') {
        return min < mediaHorasDisciplinas;
      }
      if (filtroHoras === 'muito_abaixo_media') {
        return min < mediaMuitoAbaixo;
      }
      return true;
    });

    return filtradas.sort((a, b) => b.totalSegundos - a.totalSegundos);
  }, [filtroHoras, mediaHorasDisciplinas, porDisciplinaStats]);

  const maiorTempoSegundos = Math.max(1, ...porDisciplinaStats.map((item) => item.totalSegundos));
  const sessoesTabela = paginaSessoes?.itens ?? [];
  const totalPaginas = Math.max(1, paginaSessoes?.totalPaginas ?? 1);
  const paginaAtual = Math.min(paginaHistorico, totalPaginas);
  const totalSessoesTabela = paginaSessoes?.total ?? 0;

  function selecionarPeriodo(novoPeriodo: Periodo) {
    if (novoPeriodo === 'custom') {
      setTempCustomDe(customDe);
      setTempCustomAte(customAte);
      setMostrarCustomPopover(true);
      return;
    }
    setPeriodo(novoPeriodo);
    setMostrarCustomPopover(false);
  }

  function aplicarCustomizado() {
    if (tempCustomDe > tempCustomAte) {
      toast.error('A data inicial não pode ser maior que a data final.');
      return;
    }
    setCustomDe(tempCustomDe);
    setCustomAte(tempCustomAte);
    setPeriodo('custom');
    setMostrarCustomPopover(false);
  }

  // Ações de FASE 3
  function abrirEdicao(sessao: Sessao) {
    setSessaoEditando(sessao);
    const segundos = sessao.duracaoSegundos || (sessao.minutos ? sessao.minutos * 60 : 60);
    setEditHoras(Math.floor(segundos / 3600));
    setEditMinutos(Math.floor((segundos % 3600) / 60));
    setEditQuestoes(sessao.questoes || 0);
    setEditAcertos(sessao.acertos || 0);
  }

  async function handleSalvarEdicao() {
    if (!sessaoEditando) return;
    const duracaoSegundos = editHoras * 3600 + editMinutos * 60;
    if (duracaoSegundos < 60) {
      toast.error('A duração mínima da sessão é de 1 minuto (60 segundos).');
      return;
    }
    if (duracaoSegundos > 86400) {
      toast.error('A duração máxima da sessão é de 24 horas.');
      return;
    }
    if (editAcertos > editQuestoes) {
      toast.error('O número de acertos não pode ser maior que o total de questões.');
      return;
    }

    setSalvandoEdicao(true);
    try {
      await EstudosService.atualizarSessao(trilha.id, sessaoEditando.id, {
        duracaoSegundos,
        questoes: editQuestoes,
        acertos: editAcertos
      });
      toast.success('Sessão atualizada com sucesso.');
      setSessaoEditando(null);
      setTentativa((t) => t + 1);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Erro ao atualizar a sessão.');
    } finally {
      setSalvandoEdicao(false);
    }
  }

  async function handleExcluirSessao(sessao: Sessao) {
    const duracao = formatarDuracaoSegundos(sessao.duracaoSegundos || (sessao.minutos ? sessao.minutos * 60 : 0));
    const data = formatarDataBR(sessao.dataSessao || (sessao.inicio ? paraDataLocalISO(sessao.inicio) : ''));
    const confirmou = await confirm({
      title: 'Excluir sessão de estudo?',
      description: `Deseja realmente excluir esta sessão de estudo (${duracao}) realizada em ${data}? Esta ação é permanente e não poderá ser desfeita.`,
      confirmText: 'Excluir sessão',
      cancelText: 'Cancelar',
      variant: 'danger'
    });

    if (confirmou) {
      try {
        await EstudosService.excluirSessao(trilha.id, sessao.id);
        toast.success('Sessão de estudo excluída.');
        setTentativa((t) => t + 1);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : 'Erro ao excluir a sessão.');
      }
    }
  }

  async function handleExcluirHistoricoDisciplinaRemovida(disciplina: EstatisticaDisciplinaDTO) {
    const duracao = formatarDuracaoSegundos(disciplina.totalSegundos);
    const confirmou = await confirm({
      title: `Apagar histórico de "${disciplina.nome}"?`,
      description: `Esta disciplina foi removida da trilha. Esta ação excluirá permanentemente todas as ${disciplina.totalSessoes} sessões (${duracao}) registradas nesta disciplina. Deseja continuar?`,
      confirmText: 'Apagar histórico',
      cancelText: 'Cancelar',
      variant: 'danger'
    });

    if (confirmou) {
      try {
        const resp = await EstudosService.excluirSessoesDisciplina(trilha.id, disciplina.disciplinaId);
        toast.success(`Histórico removido com sucesso: ${resp.sessoesRemovidas} sessões apagadas.`);
        setTentativa((t) => t + 1);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : 'Erro ao apagar histórico da disciplina.');
      }
    }
  }

  return (
    <div className={styles.container}>
      <PageBanner
        title={`Dashboard de Estudos: ${trilha.nome}`}
        description="Acompanhe o rendimento geral, histórico detalhado de sessões e distribuição de horas por disciplina."
      />

      {/* Popover Customizado */}
      <div className={styles.summaryHeader}>
        <div className={styles.periodFilterWrapper}>
          <div className={styles.periodPills} role="group" aria-label="Selecione o período">
            {OPCOES_PERIODO.map((item) => (
              <Button
                key={item.value}
                type="button"
                variant="ghost"
                size="sm"
                selected={periodo === item.value}
                onClick={() => selecionarPeriodo(item.value)}
              >
                {item.label}
              </Button>
            ))}
          </div>

          {mostrarCustomPopover && (
            <div ref={customPopoverRef} className={styles.customDatePopover}>
              <div className={styles.customDateInputs}>
                <DatePicker
                  label="De"
                  value={tempCustomDe}
                  max={tempCustomAte}
                  onChange={(d) => setTempCustomDe(d || tempCustomDe)}
                />
                <DatePicker
                  label="Até"
                  value={tempCustomAte}
                  min={tempCustomDe}
                  max={hoje}
                  onChange={(d) => setTempCustomAte(d || tempCustomAte)}
                />
              </div>
              <div className={styles.customDateActions}>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setMostrarCustomPopover(false)}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  onClick={aplicarCustomizado}
                >
                  Aplicar
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {carregando ? (
        <div className={styles.emptyState}>Carregando métricas e histórico de sessões...</div>
      ) : erro ? (
        <div className={styles.emptyState}>
          <p>{erro}</p>
          <Button type="button" variant="secondary" size="sm" onClick={() => setTentativa((t) => t + 1)}>
            Tentar novamente
          </Button>
        </div>
      ) : (
        <>
          {/* KPI Cards */}
          <div className={styles.kpiGrid}>
            <div className={styles.kpiCard}>
              <div className={`${styles.kpiIcon} ${styles.kpiPrimary}`}>
                <Clock size={20} aria-hidden="true" />
              </div>
              <div className={styles.kpiInfo}>
                <span className={styles.kpiLabel}>Horas estudadas</span>
                <span className={styles.kpiVal}>{formatarDuracaoSegundos(totalSegundos)}</span>
                <span className={styles.kpiDescription}>
                  {periodoResumo.sessoes} {periodoResumo.sessoes === 1 ? 'sessão registrada' : 'sessões registradas'}
                </span>
              </div>
            </div>

            <div className={styles.kpiCard}>
              <div className={`${styles.kpiIcon} ${styles.kpiGreen}`}>
                <Target size={20} aria-hidden="true" />
              </div>
              <div className={styles.kpiInfo}>
                <span className={styles.kpiLabel}>Precisão global</span>
                <span className={`${styles.kpiVal} ${percentualAcerto !== null ? nivelAcerto(percentualAcerto) : ''}`}>
                  {percentualAcerto !== null ? `${percentualAcerto}%` : '—'}
                </span>
                <span className={styles.kpiDescription}>
                  {totalQuestoes > 0 ? `${totalAcertos} acertos em ${totalQuestoes} questões` : 'Sem questões registradas'}
                </span>
              </div>
            </div>

            <div className={styles.kpiCard}>
              <div className={`${styles.kpiIcon} ${styles.kpiYellow}`}>
                <Flame size={20} aria-hidden="true" />
              </div>
              <div className={styles.kpiInfo}>
                <span className={styles.kpiLabel}>Sequência ativa</span>
                <span className={styles.kpiVal}>{estatisticas?.streakDias ?? 0} dias</span>
                <span className={styles.kpiDescription}>
                  {(estatisticas?.streakDias ?? 0) > 0 ? 'Foco diário consecutivo mantido' : 'Estude hoje para iniciar sua sequência'}
                </span>
              </div>
            </div>

            <div className={styles.kpiCard}>
              <div className={`${styles.kpiIcon} ${styles.kpiPurple}`}>
                <Award size={20} aria-hidden="true" />
              </div>
              <div className={styles.kpiInfo}>
                <span className={styles.kpiLabel}>Ritmo até a prova</span>
                <span className={styles.kpiVal}>{ritmo.valor}</span>
                <span className={styles.kpiDescription}>{ritmo.descricao}</span>
              </div>
            </div>
          </div>

          {/* Horas por Disciplina */}
          <section className={styles.sectionCard} aria-labelledby="hours-chart-title">
            <div className={styles.sectionHeader}>
              <div>
                <h2 id="hours-chart-title" className={styles.sectionTitle}>Tempo por disciplina</h2>
                <p className={styles.sectionDescription}>
                  Distribuição do tempo de foco acumulado por disciplina no período selecionado.
                </p>
              </div>
              <div className={styles.diagnosticFilters} role="group" aria-label="Filtrar horas de estudo">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroHoras === 'todas'}
                  onClick={() => setFiltroHoras('todas')}
                >
                  Todas
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroHoras === 'abaixo_media'}
                  onClick={() => setFiltroHoras(filtroHoras === 'abaixo_media' ? 'todas' : 'abaixo_media')}
                >
                  Abaixo da média
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroHoras === 'muito_abaixo_media'}
                  onClick={() => setFiltroHoras(filtroHoras === 'muito_abaixo_media' ? 'todas' : 'muito_abaixo_media')}
                >
                  Muito abaixo da média
                </Button>
              </div>
            </div>

            {disciplinasComHoras.length > 0 ? (
              <div className={styles.chartList}>
                {disciplinasComHoras.map((item) => {
                  const percentual = Math.round((item.totalSegundos / maiorTempoSegundos) * 100);
                  return (
                    <div key={item.disciplinaId} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <div className={styles.chartRow}>
                        <div className={styles.disciplineInfo}>
                          <DisciplineLabel
                            name={item.nome}
                            color={item.cor}
                            className={styles.discName}
                          />
                          {item.disciplinaRemovida && (
                            <span className={styles.badgeRemovida}>Disciplina removida</span>
                          )}
                        </div>
                        <div className={styles.barCol}>
                          <ProgressBar
                            value={percentual}
                            color={item.cor || 'var(--primary)'}
                            label={formatarDuracaoSegundos(item.totalSegundos)}
                            className={styles.barProgress}
                          />
                          <strong className={styles.barText}>{formatarDuracaoSegundos(item.totalSegundos)}</strong>
                        </div>
                        <span className={styles.metricText}>
                          {totalSegundos > 0 ? `${Math.round((item.totalSegundos / totalSegundos) * 100)}% do total` : ''}
                        </span>
                      </div>

                      {/* Grupo / Alerta amarelo para disciplina removida */}
                      {item.disciplinaRemovida && (
                        <div className={styles.removidaBanner}>
                          <span className={styles.removidaBannerText}>
                            ⚠️ Esta disciplina foi excluída da trilha, mas suas sessões históricas foram preservadas ({item.totalSessoes} sessões).
                          </span>
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            onClick={() => handleExcluirHistoricoDisciplinaRemovida(item)}
                          >
                            <Trash2 size={14} style={{ marginRight: 4, color: 'var(--red)' }} />
                            Apagar histórico desta disciplina
                          </Button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className={styles.emptyState}>
                {filtroHoras === 'todas'
                  ? 'Nenhuma hora de estudo registrada neste período.'
                  : `Nenhuma disciplina com tempo de estudo ${filtroHoras === 'abaixo_media' ? 'abaixo da média' : 'muito abaixo da média'}.`}
              </div>
            )}
          </section>

          {/* Diagnóstico de Questões */}
          <section className={styles.sectionCard} aria-labelledby="diagnostic-title">
            <div className={styles.sectionHeader}>
              <div>
                <h2 id="diagnostic-title" className={styles.sectionTitle}>Diagnóstico de questões</h2>
                <p className={styles.sectionDescription}>
                  Precisão ponderada de acertos por disciplina no período.
                </p>
              </div>
              <div className={styles.diagnosticFilters} role="group" aria-label="Filtrar rendimento em questões">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroDiagnostico === 'todas'}
                  onClick={() => setFiltroDiagnostico('todas')}
                >
                  Todas
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroDiagnostico === 'critico'}
                  onClick={() => setFiltroDiagnostico(filtroDiagnostico === 'critico' ? 'todas' : 'critico')}
                >
                  Crítico (&lt; 60%)
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  selected={filtroDiagnostico === 'atencao'}
                  onClick={() => setFiltroDiagnostico(filtroDiagnostico === 'atencao' ? 'todas' : 'atencao')}
                >
                  Atenção (&lt; 80%)
                </Button>
              </div>
            </div>

            {disciplinasComQuestoes.length > 0 ? (
              <div className={styles.chartList}>
                {disciplinasComQuestoes.map((item) => (
                  <div key={item.disciplinaId} className={styles.chartRow}>
                    <div className={styles.disciplineInfo}>
                      <DisciplineLabel
                        name={item.nome}
                        color={item.cor}
                        className={styles.discName}
                      />
                      {item.disciplinaRemovida && (
                        <span className={styles.badgeRemovida}>Removida</span>
                      )}
                    </div>
                    <div className={styles.barCol}>
                      <ProgressBar
                        value={Math.round(item.percentualAcerto)}
                        color={corNivelAcerto(item.percentualAcerto)}
                        label={`${Math.round(item.percentualAcerto)}% de acerto`}
                        className={styles.barProgress}
                      />
                      <strong className={styles.barText} style={{ color: corNivelAcerto(item.percentualAcerto) }}>
                        {Math.round(item.percentualAcerto)}%
                      </strong>
                    </div>
                    <span className={styles.metricText}>
                      {item.totalAcertos}/{item.totalQuestoes} acertos
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className={styles.emptyState}>
                Nenhuma questão registrada nas disciplinas filtradas para este período.
              </div>
            )}
          </section>

          {/* Histórico de Sessões Paginado (20 por página) */}
          <section className={styles.sectionCard} aria-labelledby="history-title">
            <div className={styles.sectionHeader}>
              <div>
                <h2 id="history-title" className={styles.sectionTitle}>Histórico de sessões</h2>
                <p className={styles.sectionDescription}>
                  {totalSessoesTabela} {totalSessoesTabela === 1 ? 'sessão registrada' : 'sessões registradas'} (20 por página).
                </p>
              </div>
              <Clock size={20} className={styles.mutedIcon} aria-hidden="true" />
            </div>

            {sessoesTabela.length > 0 ? (
              <>
                <div className={styles.tableWrap} style={{ opacity: carregandoHistorico ? 0.6 : 1, transition: 'opacity 0.15s ease' }}>
                  <table className={styles.historyTable}>
                    <thead>
                      <tr>
                        <th scope="col">Data</th>
                        <th scope="col">Disciplina</th>
                        <th scope="col">Duração</th>
                        <th scope="col">Questões</th>
                        <th scope="col">Ações</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sessoesTabela.map((sessao) => {
                        const duracaoFormatada = formatarDuracaoSegundos(
                          sessao.duracaoSegundos || (sessao.minutos ? sessao.minutos * 60 : 0)
                        );
                        return (
                          <tr key={sessao.id}>
                            <td>{formatarDataBR(sessao.dataSessao || (sessao.inicio ? paraDataLocalISO(sessao.inicio) : ''))}</td>
                            <td>
                              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                                <DisciplineLabel
                                  name={sessao.disciplinaNome || 'Estudo'}
                                  color={sessao.disciplinaCor || 'var(--primary)'}
                                />
                                {sessao.disciplinaRemovida && (
                                  <span className={styles.badgeRemovida} title="Disciplina excluída da trilha">
                                    Removida
                                  </span>
                                )}
                                {sessao.editavel && (
                                  <span className={styles.badgeRecente} title="Sessão mais recente da trilha (editável)">
                                    Mais recente
                                  </span>
                                )}
                              </div>
                            </td>
                            <td><strong>{duracaoFormatada}</strong></td>
                            <td>
                              {sessao.questoes > 0
                                ? `${sessao.acertos}/${sessao.questoes} (${Math.round((sessao.acertos / sessao.questoes) * 100)}%)`
                                : '—'}
                            </td>
                            <td>
                              <div className={styles.acoesCol}>
                                {sessao.editavel && (
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => abrirEdicao(sessao)}
                                    title="Editar a sessão mais recente"
                                  >
                                    <Edit2 size={14} style={{ marginRight: 4 }} />
                                    Editar
                                  </Button>
                                )}
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleExcluirSessao(sessao)}
                                  title="Excluir sessão do histórico"
                                  style={{ color: 'var(--red)' }}
                                >
                                  <Trash2 size={14} style={{ marginRight: 4 }} />
                                  Excluir
                                </Button>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                <Pagination
                  ariaLabel="Paginação do histórico"
                  currentPage={paginaAtual}
                  totalPages={totalPaginas}
                  summary={`Página ${paginaAtual} de ${totalPaginas}`}
                  showPageLabel={false}
                  onPageChange={trocarPagina}
                />
              </>
            ) : (
              <div className={styles.emptyState}>Nenhuma sessão registrada neste período.</div>
            )}
          </section>
        </>
      )}

      {/* Modal para Edição da Sessão Mais Recente */}
      {sessaoEditando && (
        <Modal
          isOpen={true}
          onClose={() => setSessaoEditando(null)}
          title="Editar Sessão Mais Recente"
          subtitle={`Disciplina: ${sessaoEditando.disciplinaNome || 'Estudo'}`}
          icon={<Edit2 size={20} />}
          footer={
            <div className={styles.editModalFooter}>
              <Button
                type="button"
                variant="secondary"
                disabled={salvandoEdicao}
                onClick={() => setSessaoEditando(null)}
              >
                Cancelar
              </Button>
              <Button
                type="button"
                variant="primary"
                disabled={salvandoEdicao}
                onClick={handleSalvarEdicao}
              >
                {salvandoEdicao ? 'Salvando...' : 'Salvar alterações'}
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p className={styles.sectionDescription}>
              Apenas a sessão mais recente da trilha pode ter sua duração e questões ajustadas.
            </p>

            <div className={styles.editModalGrid}>
              <Input
                label="Horas de estudo"
                type="number"
                min={0}
                max={24}
                value={String(editHoras)}
                onChange={(e) => setEditHoras(Math.max(0, Number.parseInt(e.target.value, 10) || 0))}
              />
              <Input
                label="Minutos de estudo"
                type="number"
                min={0}
                max={59}
                value={String(editMinutos)}
                onChange={(e) => setEditMinutos(Math.max(0, Math.min(59, Number.parseInt(e.target.value, 10) || 0)))}
              />
            </div>

            <div className={styles.editModalGrid}>
              <Input
                label="Total de questões"
                type="number"
                min={0}
                value={String(editQuestoes)}
                onChange={(e) => setEditQuestoes(Math.max(0, Number.parseInt(e.target.value, 10) || 0))}
              />
              <Input
                label="Acertos"
                type="number"
                min={0}
                max={editQuestoes}
                value={String(editAcertos)}
                onChange={(e) => setEditAcertos(Math.max(0, Number.parseInt(e.target.value, 10) || 0))}
              />
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
