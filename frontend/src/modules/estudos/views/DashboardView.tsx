import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Award,
  Clock,
  Flame,
  Target,
  Trash2
} from 'lucide-react';
import {
  Badge,
  Button,
  DatePicker,
  DisciplineLabel,
  EmptyState,
  Pagination,
  PageBanner,
  ProgressBar,
  SectionErrorCard,
  Skeleton,
  useConfirm,
  useGlobalSessionModal,
  useToast
} from '@shared/design-system';
import {
  EstatisticaDisciplinaDTO,
  EstatisticasTrilhaDTO,
  Sessao,
  TrilhaDetalheDTO
} from '../types/trilhara';
import type { BlocoCronogramaItemDTO } from '@shared/contracts';
import { EstudosService, PaginaSessoes } from '../services/estudosService';
import {
  formatarDataBR,
  formatarDuracao,
  hojeISO
} from '../utils/format';
import { calcularProgressoTrilha } from '../utils/progressoTrilha';
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

/**
 * Blocos planejados para a meta do período vêm do cronograma ATIVO publicado no ponteiro da
 * trilha. Sem cronograma ativo, não há meta.
 */
function cronogramaAtivoId(trilha: TrilhaDetalheDTO): string | undefined {
  return trilha.cronogramaAtivoResumo?.cronogramaAtivoId;
}

export interface DashboardViewProps {
  trilha: TrilhaDetalheDTO;
  onIrParaDisciplinas: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  trilha
}) => {
  const toast = useToast();
  const { confirm } = useConfirm();
  const { abrirModal } = useGlobalSessionModal();

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
  const [blocosCronograma, setBlocosCronograma] = useState<BlocoCronogramaItemDTO[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [paginaHistorico, setPaginaHistorico] = useState(1);
  const [tentativa, setTentativa] = useState(0);

  const cronogramaId = useMemo(() => cronogramaAtivoId(trilha), [trilha]);
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
    if (!cronogramaId) {
      setBlocosCronograma([]);
      return;
    }
    let ativa = true;
    const carregarBlocos = async () => {
      try {
        // Percorre todas as páginas do cronograma ativo (máx. 100 por página) para a meta do período.
        const acumulado: BlocoCronogramaItemDTO[] = [];
        let pagina = 1;
        let totalPaginas = 1;
        do {
          const res = await EstudosService.listarBlocosCronograma(trilha.id, cronogramaId, {
            pagina,
            tamanho: 100
          });
          acumulado.push(...res.itens);
          totalPaginas = res.totalPaginas || 1;
          pagina += 1;
        } while (pagina <= totalPaginas);
        if (ativa) setBlocosCronograma(acumulado);
      } catch (err) {
        if (!ativa) return;
        setBlocosCronograma([]);
        toast.error(err instanceof Error ? err : 'Não foi possível carregar os blocos do cronograma.');
      }
    };
    void carregarBlocos();
    return () => {
      ativa = false;
    };
  }, [cronogramaId, toast, trilha.id]);

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

  const cobertura = useMemo(
    () =>
      calcularProgressoTrilha(
        trilha.disciplinas,
        new Set(trilha.topicosConcluidosIds ?? [])
      ),
    [trilha]
  );

  const ritmo = useMemo(() => {
    const apenasSemTopicos = cobertura.totalTopicos === 0;
    const usarDisciplinas =
      apenasSemTopicos ||
      (cobertura.topicosConcluidos === cobertura.totalTopicos &&
        cobertura.disciplinasSemTopicoPendentes > 0);
    const pendentes = usarDisciplinas
      ? cobertura.disciplinasSemTopicoPendentes
      : Math.max(0, cobertura.totalTopicos - cobertura.topicosConcluidos);
    const unidade = usarDisciplinas ? 'disciplinas' : 'assuntos';

    if (!trilha.dataProva) {
      return {
        valor: `${pendentes} pendentes`,
        descricao: 'Defina a data da prova para calcular o ritmo diário.'
      };
    }

    const diasAteProva = diferencaEmDias(hoje, trilha.dataProva);
    if (diasAteProva < 0) {
      return { valor: 'Prova realizada', descricao: `${pendentes} ${unidade} ainda pendentes.` };
    }
    if (diasAteProva === 0) {
      return { valor: 'Prova hoje', descricao: `${pendentes} ${unidade} ainda pendentes.` };
    }
    return {
      valor: `${(pendentes / diasAteProva).toLocaleString('pt-BR', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      })} ${unidade}/dia`,
      descricao: `${pendentes} ${unidade} pendentes nos próximos ${diasAteProva} dias.`
    };
  }, [cobertura, hoje, trilha.dataProva]);

  const periodoResumo = useMemo(() => {
    const dataInicial = dataInicialPeriodo || undefined;
    const dataFinal = dataFinalPeriodo || hoje;
    const blocosPlanejados = blocosCronograma.filter(
      (bloco) => bloco.data <= dataFinal && (!dataInicial || bloco.data >= dataInicial)
    );

    const totalBlocos = blocosPlanejados.length;
    const metaMinutos = blocosPlanejados.reduce(
      (total, bloco) => total + bloco.duracaoMinutos,
      0
    );

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
    blocosCronograma,
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

  // Ações
  async function handleExcluirSessao(sessao: Sessao) {
    const duracao = formatarDuracao(sessao.duracaoSegundos ?? 0);
    const data = formatarDataBR(sessao.dataSessao ?? '');
    const confirmou = await confirm({
      title: 'Excluir sessão de estudo?',
      description: `Deseja realmente excluir esta sessão de estudo (${duracao}) realizada em ${data}? Esta ação não pode ser desfeita.`,
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
    const duracao = formatarDuracao(disciplina.totalSegundos);
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
        <div className={styles.dashboardSkeleton} data-testid="dashboard-skeleton">
          <Skeleton variant="chart" />
          <Skeleton variant="row" linhas={5} />
        </div>
      ) : erro ? (
        <SectionErrorCard
          titulo="Não foi possível carregar"
          descricao="Verifique sua conexão e tente novamente."
          onRetry={() => setTentativa((t) => t + 1)}
        />
      ) : totalSessoesTabela === 0 && porDisciplinaStats.length === 0 ? (
        <EmptyState
          icone={<Clock size={44} aria-hidden="true" />}
          titulo="Nenhuma sessão neste período"
          descricao="Registre uma sessão de estudo para começar a acompanhar seu rendimento."
          acaoPrimaria={
            <Button
              type="button"
              variant="primary"
              size="sm"
              onClick={() => abrirModal({ modo: 'MANUAL', trilhaId: trilha.id })}
            >
              Registrar sessão manual
            </Button>
          }
        />
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
                <span className={styles.kpiVal}>{formatarDuracao(totalSegundos)}</span>
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
                            <Badge variant="warning" size="sm">Disciplina removida</Badge>
                          )}
                        </div>
                        <div className={styles.barCol}>
                          <ProgressBar
                            value={percentual}
                            color={item.cor || 'var(--primary)'}
                            label={formatarDuracao(item.totalSegundos)}
                            className={styles.barProgress}
                          />
                          <strong className={styles.barText}>{formatarDuracao(item.totalSegundos)}</strong>
                        </div>
                        <span className={styles.metricText}>
                          {totalSegundos > 0 ? `${Math.round((item.totalSegundos / totalSegundos) * 100)}% do total` : ''}
                        </span>
                      </div>

                      {/* Grupo de disciplina removida: badge amarelo + ação destrutiva explícita */}
                      {item.disciplinaRemovida && (
                        <div className={styles.removidaBanner}>
                          <span className={styles.removidaBannerText}>
                            <Badge variant="warning" size="sm">Disciplina removida</Badge>
                            {' '}As sessões foram preservadas ({item.totalSessoes} sessões · {formatarDuracao(item.totalSegundos)}).
                          </span>
                          <Button
                            type="button"
                            variant="danger"
                            size="md"
                            leftIcon={<Trash2 size={15} />}
                            onClick={() => handleExcluirHistoricoDisciplinaRemovida(item)}
                          >
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
                        <Badge variant="warning" size="sm">Disciplina removida</Badge>
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
                        const duracaoFormatada = formatarDuracao(sessao.duracaoSegundos ?? 0);
                        return (
                          <tr key={sessao.id}>
                            <td>{formatarDataBR(sessao.dataSessao ?? '')}</td>
                            <td>
                              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                                <DisciplineLabel
                                  name={sessao.disciplinaNome || 'Estudo'}
                                  color={sessao.disciplinaCor || 'var(--primary)'}
                                />
                                {sessao.disciplinaRemovida && (
                                  <Badge variant="warning" size="sm" title="Disciplina excluída da trilha">
                                    Removida
                                  </Badge>
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
                                <Button
                                  type="button"
                                  variant="danger"
                                  size="md"
                                  leftIcon={<Trash2 size={15} />}
                                  onClick={() => handleExcluirSessao(sessao)}
                                  title="Excluir sessão do histórico"
                                >
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
              <div className={styles.emptyState}>Nenhuma sessão neste período.</div>
            )}
          </section>
        </>
      )}
    </div>
  );
};
