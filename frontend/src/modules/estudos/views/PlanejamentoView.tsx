import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  AlertTriangle,
  Calendar,
  CalendarRange,
  Check,
  Clock,
  HelpCircle,
  Layers,
  ListOrdered,
  Minus,
  Pencil,
  Play,
  Plus,
  Scale,
  SlidersHorizontal,
  Trash2,
  Undo2,
} from 'lucide-react';
import { registrarFalha } from '@shared/diagnostico';
import {
  useGlobalSessionModal,
  Tabs,
  Button,
  Badge,
  Checkbox,
  Input,
  Modal,
  Select,
  DatePicker,
  PageBanner,
  Skeleton,
  SectionErrorCard,
  SharedCalendarView,
  criarGradeMes,
  criarGradeSemana,
  dataLocal,
  useConfirm,
  useToast,
  type CalendarBadge,
  type CalendarItem,
  type CalendarVisibleRange,
} from '@shared/design-system';
import { sessaoGlobalService } from '@shared/services/sessaoGlobalService';
import { EstudosService } from '../services/estudosService';
import { TrilhaDetalheDTO, RegistroBlocoPendente } from '../types/trilhara';
import { ModalidadeCronograma, CriarCronogramaPayload } from '@shared/contracts';
import type { CronogramaDTO, BlocoCronogramaItemDTO, DisponibilidadeSemanalDTO } from '@shared/contracts';
import { formatarDataCurta, hojeISO } from '@shared/format';
import styles from './PlanejamentoView.module.css';

const DIAS_SEMANA = [
  { id: 1, label: 'Segunda-feira', shortLabel: 'Seg' },
  { id: 2, label: 'Terça-feira', shortLabel: 'Ter' },
  { id: 3, label: 'Quarta-feira', shortLabel: 'Qua' },
  { id: 4, label: 'Quinta-feira', shortLabel: 'Qui' },
  { id: 5, label: 'Sexta-feira', shortLabel: 'Sex' },
  { id: 6, label: 'Sábado', shortLabel: 'Sáb' },
  { id: 7, label: 'Domingo', shortLabel: 'Dom' },
];

const TODOS_DIAS_SEMANA = DIAS_SEMANA.map((d) => d.id);

const BLOCOS_PADRAO_POR_DIA: Record<number, number> = {
  1: 4,
  2: 4,
  3: 4,
  4: 4,
  5: 4,
  6: 2,
  7: 2,
};

const TAMANHO_PAGINA_BLOCOS = 100;
const MAX_PAGINAS_CALENDARIO = 20;
const MAX_DIAS_HORIZONTE = 366;
const MIN_BLOCOS_DIA = 1;
const MAX_BLOCOS_DIA = 24;

interface BlocoCalendarItem extends CalendarItem {
  bloco: BlocoCronogramaItemDTO;
}

const ROTULO_MODALIDADE: Record<ModalidadeCronograma, string> = {
  SEQUENCIAL: 'Sequencial',
  PONDERADO: 'Ponderado',
  MANUAL: 'Manual',
};

const MODALIDADES_ORDEM: ModalidadeCronograma[] = ['SEQUENCIAL', 'PONDERADO', 'MANUAL'];

const MODALIDADES_INFO: Record<
  ModalidadeCronograma,
  {
    titulo: string;
    resumo: string;
    descricao: string;
    exemplo: string;
    idealPara: string;
  }
> = {
  SEQUENCIAL: {
    titulo: 'Sequencial',
    resumo: 'Alterna todas as disciplinas em fila contínua, com a mesma carga entre matérias.',
    descricao:
      'Organiza as disciplinas na ordem da trilha em um ciclo contínuo, distribuindo os blocos de forma igual entre todas as matérias.',
    exemplo: 'Ex.: Port → Dir → Mat → Port…',
    idealPara: 'Cobertura equilibrada do edital',
  },
  PONDERADO: {
    titulo: 'Ponderado',
    resumo: 'Distribui os blocos proporcionalmente ao peso (1x a 5x) de cada disciplina.',
    descricao:
      'Prioriza matérias de peso maior com mais frequência ao longo do período, sem deixar as disciplinas menores de fora.',
    exemplo: 'Ex.: Peso 3x recebe 3× mais blocos que 1x',
    idealPara: 'Focar matérias de maior peso',
  },
  MANUAL: {
    titulo: 'Manual',
    resumo: 'Você escolhe exatamente qual disciplina estudar em cada bloco de cada dia da semana.',
    descricao:
      'Permite montar sua grade fixa de segunda a domingo bloco por bloco, repetindo esse modelo durante todo o período.',
    exemplo: 'Ex.: Seg: Port + Dir · Ter: Mat',
    idealPara: 'Controle total da rotina semanal',
  },
};

function renderIconeModalidade(modalidade: ModalidadeCronograma, size = 16) {
  switch (modalidade) {
    case 'SEQUENCIAL':
      return <ListOrdered size={size} aria-hidden="true" />;
    case 'PONDERADO':
      return <Scale size={size} aria-hidden="true" />;
    case 'MANUAL':
      return <SlidersHorizontal size={size} aria-hidden="true" />;
  }
}

const ROTULO_PERIODO: Record<string, string> = {
  UMA_SEMANA: '1 semana (7 dias)',
  UM_MES: '1 mês (30 dias)',
  TRES_MESES: '3 meses (90 dias)',
  ATE_PROVA: 'Até a data da prova',
};

/** Formata a carga diária no padrão compacto: 150 min -> "2h30m", 120 min -> "2h", 50 min -> "50min". */
function formatarHorasDia(minutosTotais: number): string {
  if (!Number.isFinite(minutosTotais) || minutosTotais <= 0) {
    return '0h';
  }
  const total = Math.floor(minutosTotais);
  const horas = Math.floor(total / 60);
  const minutos = total % 60;
  if (horas === 0) return `${minutos}min`;
  if (minutos === 0) return `${horas}h`;
  return `${horas}h${String(minutos).padStart(2, '0')}m`;
}

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

function construirBlocosPadrao(base?: number): Record<number, number> {
  if (base !== undefined) {
    return { 1: base, 2: base, 3: base, 4: base, 5: base, 6: base, 7: base };
  }
  return { ...BLOCOS_PADRAO_POR_DIA };
}

function ajustarListaManual(
  atual: string[] | undefined,
  quantidade: number,
  disciplinasIds: string[]
): string[] {
  if (disciplinasIds.length === 0 || quantidade <= 0) return [];
  const base = atual ?? [];
  return Array.from({ length: quantidade }, (_, i) => {
    const existente = base[i];
    return existente && disciplinasIds.includes(existente)
      ? existente
      : disciplinasIds[i % disciplinasIds.length];
  });
}

export interface PlanejamentoProps {
  trilha?: TrilhaDetalheDTO;
  onTrilhaAtualizada?: (atualizada: TrilhaDetalheDTO) => void;
  onIniciarEstudo?: (disciplinaId: string, registro: RegistroBlocoPendente) => void;
  onIrParaDisciplinas?: () => void;
}

export function PlanejamentoView({
  trilha: trilhaProp,
  onTrilhaAtualizada,
  onIniciarEstudo,
  onIrParaDisciplinas,
}: PlanejamentoProps = {}) {
  const { abrirModal } = useGlobalSessionModal();
  const { confirm } = useConfirm();
  const toast = useToast();

  const selectedTrilhaId = trilhaProp?.id ?? '';
  const [selectedTrilhaDetalhe, setSelectedTrilhaDetalhe] = useState<TrilhaDetalheDTO | null>(trilhaProp ?? null);

  // Cronogramas + aba selecionada
  const [cronogramas, setCronogramas] = useState<CronogramaDTO[]>([]);
  const [selectedCronogramaId, setSelectedCronogramaId] = useState<string>('');
  const [loadingCronogramas, setLoadingCronogramas] = useState(false);

  // Blocos no intervalo visível do calendário
  const [blocos, setBlocos] = useState<BlocoCronogramaItemDTO[]>([]);
  const [visibleRange, setVisibleRange] = useState<CalendarVisibleRange>(() => {
    const isMobile =
      typeof window !== 'undefined' && window.matchMedia('(max-width: 760px)').matches;
    if (isMobile) {
      const dias = criarGradeSemana(hojeISO());
      return {
        dataInicio: dataLocal(dias[0]),
        dataFim: dataLocal(dias[dias.length - 1]),
        modo: 'semanal',
      };
    }
    const agora = new Date();
    const dias = criarGradeMes(new Date(agora.getFullYear(), agora.getMonth(), 1));
    return {
      dataInicio: dataLocal(dias[0]),
      dataFim: dataLocal(dias[dias.length - 1]),
      modo: 'mensal',
    };
  });
  const [loadingBlocos, setLoadingBlocos] = useState(false);
  const [erroBlocos, setErroBlocos] = useState<Error | null>(null);
  const [totalAtrasados, setTotalAtrasados] = useState(0);
  const blocosRequestSeqRef = useRef(0);

  // Formulário (criação e edição pelo lápis)
  const [showForm, setShowForm] = useState(false);
  const [editandoId, setEditandoId] = useState<string | null>(null);
  const [modalidade, setModalidade] = useState<ModalidadeCronograma>('SEQUENCIAL');
  const [tipoPeriodo, setTipoPeriodo] = useState<string>('UM_MES');
  const [diasAtivosSemana, setDiasAtivosSemana] = useState<number[]>(() => [...TODOS_DIAS_SEMANA]);
  const [blocosPorDiaSemana, setBlocosPorDiaSemana] = useState<Record<number, number>>(
    () => construirBlocosPadrao()
  );
  const [semanaModeloManual, setSemanaModeloManual] = useState<Record<number, string[]>>({});
  const [duracaoBlocoMinutos, setDuracaoBlocoMinutos] = useState<number>(50);
  const [dataInicio, setDataInicio] = useState<string>(() => hojeISO());
  const [savingCronograma, setSavingCronograma] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [disponibilidade, setDisponibilidade] = useState<DisponibilidadeSemanalDTO | null>(null);
  const [showModalidadesGuia, setShowModalidadesGuia] = useState(false);

  const disciplinasParticipantesIds = useMemo(
    () => (selectedTrilhaDetalhe?.disciplinas ?? []).map((d) => d.id),
    [selectedTrilhaDetalhe?.disciplinas]
  );

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
      const blocosNoDia = blocosPorDiaSemana[dia] ?? 0;
      porDia[indice] += blocosNoDia * duracaoBlocoMinutos;
    }
    return porDia;
  }, [diasAtivosSemana, blocosPorDiaSemana, duracaoBlocoMinutos]);

  const totalBlocosSemana = useMemo(
    () => diasAtivosSemana.reduce((acc, dia) => acc + (blocosPorDiaSemana[dia] ?? 0), 0),
    [diasAtivosSemana, blocosPorDiaSemana]
  );

  const totalMinutosSemana = useMemo(
    () => totalBlocosSemana * duracaoBlocoMinutos,
    [totalBlocosSemana, duracaoBlocoMinutos]
  );

  const sobrecarga = useMemo(
    () => diasEmSobrecarga(disponibilidade, minutosNovosPorDia),
    [disponibilidade, minutosNovosPorDia]
  );

  const cronogramaSelecionado = useMemo(
    () => cronogramas.find((c) => c.id === selectedCronogramaId) ?? null,
    [cronogramas, selectedCronogramaId]
  );

  // ── Detalhe da trilha (disciplinas e dataProva) ──
  useEffect(() => {
    if (trilhaProp) {
      setSelectedTrilhaDetalhe(trilhaProp);
      return;
    }
    setSelectedTrilhaDetalhe(null);
  }, [trilhaProp]);

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
    async (trilhaId: string, cronogramaId: string, dataInicioRange: string, dataFimRange: string) => {
      const requestSeq = ++blocosRequestSeqRef.current;
      if (!trilhaId || !cronogramaId) {
        setBlocos([]);
        return;
      }
      try {
        setLoadingBlocos(true);
        setErroBlocos(null);
        const primeiraPagina = await EstudosService.listarBlocosCronograma(trilhaId, cronogramaId, {
          dataInicio: dataInicioRange,
          dataFim: dataFimRange,
          pagina: 1,
          tamanho: TAMANHO_PAGINA_BLOCOS,
        });
        if (requestSeq !== blocosRequestSeqRef.current) return;

        const acumulados = [...(primeiraPagina.itens ?? [])];
        const totalPaginas = Math.min(primeiraPagina.totalPaginas ?? 1, MAX_PAGINAS_CALENDARIO);
        for (let p = 2; p <= totalPaginas; p++) {
          const proxima = await EstudosService.listarBlocosCronograma(trilhaId, cronogramaId, {
            dataInicio: dataInicioRange,
            dataFim: dataFimRange,
            pagina: p,
            tamanho: TAMANHO_PAGINA_BLOCOS,
          });
          if (requestSeq !== blocosRequestSeqRef.current) return;
          acumulados.push(...(proxima.itens ?? []));
        }
        setBlocos(acumulados);
      } catch (err) {
        if (requestSeq !== blocosRequestSeqRef.current) return;
        registrarFalha('planejamento.blocos', err);
        setErroBlocos(err instanceof Error ? err : new Error('Erro ao listar blocos do cronograma.'));
      } finally {
        if (requestSeq === blocosRequestSeqRef.current) {
          setLoadingBlocos(false);
        }
      }
    },
    []
  );

  useEffect(() => {
    if (selectedTrilhaId && selectedCronogramaId) {
      void carregarBlocos(
        selectedTrilhaId,
        selectedCronogramaId,
        visibleRange.dataInicio,
        visibleRange.dataFim
      );
    } else {
      setBlocos([]);
    }
  }, [selectedTrilhaId, selectedCronogramaId, visibleRange.dataInicio, visibleRange.dataFim, carregarBlocos]);

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
    await carregarBlocos(
      selectedTrilhaId,
      selectedCronogramaId,
      visibleRange.dataInicio,
      visibleRange.dataFim
    );
    await carregarTotalAtrasados();
  }, [
    selectedTrilhaId,
    selectedCronogramaId,
    visibleRange.dataInicio,
    visibleRange.dataFim,
    carregarBlocos,
    carregarTotalAtrasados,
  ]);

  const handleToggleDia = (diaId: number) => {
    const jaAtivo = diasAtivosSemana.includes(diaId);
    if (jaAtivo) {
      setDiasAtivosSemana((prev) => prev.filter((d) => d !== diaId));
      return;
    }

    const blocosAtuais = blocosPorDiaSemana[diaId] ?? BLOCOS_PADRAO_POR_DIA[diaId] ?? 2;
    const blocosGarantidos = Math.max(MIN_BLOCOS_DIA, Math.min(MAX_BLOCOS_DIA, blocosAtuais));
    setDiasAtivosSemana((prev) => [...prev, diaId].sort((a, b) => a - b));
    setBlocosPorDiaSemana((prev) => ({ ...prev, [diaId]: blocosGarantidos }));
    setSemanaModeloManual((prev) => ({
      ...prev,
      [diaId]: ajustarListaManual(prev[diaId], blocosGarantidos, disciplinasParticipantesIds),
    }));
  };

  const handleAlterarBlocosDia = (diaId: number, novoValor: number) => {
    const clamped = Math.max(MIN_BLOCOS_DIA, Math.min(MAX_BLOCOS_DIA, Math.round(novoValor) || MIN_BLOCOS_DIA));
    setBlocosPorDiaSemana((prev) => ({ ...prev, [diaId]: clamped }));
    setSemanaModeloManual((prev) => ({
      ...prev,
      [diaId]: ajustarListaManual(prev[diaId], clamped, disciplinasParticipantesIds),
    }));
  };

  const handleAlterarDisciplinaManual = (diaId: number, indiceBloco: number, disciplinaId: string) => {
    setSemanaModeloManual((prev) => {
      const qtd = blocosPorDiaSemana[diaId] ?? MIN_BLOCOS_DIA;
      const lista = ajustarListaManual(prev[diaId], qtd, disciplinasParticipantesIds);
      lista[indiceBloco] = disciplinaId;
      return { ...prev, [diaId]: lista };
    });
  };

  const abrirCriacao = () => {
    setEditandoId(null);
    setModalidade(
      (['SEQUENCIAL', 'PONDERADO', 'MANUAL'] as ModalidadeCronograma[]).find((m) => !modalidadesUsadas.has(m))
        ?? 'SEQUENCIAL'
    );
    setTipoPeriodo(dataProvaFutura ? 'ATE_PROVA' : 'UM_MES');
    setDiasAtivosSemana([...TODOS_DIAS_SEMANA]);
    const mapaPadrao = construirBlocosPadrao();
    setBlocosPorDiaSemana(mapaPadrao);
    const manualPadrao: Record<number, string[]> = {};
    for (const dia of TODOS_DIAS_SEMANA) {
      manualPadrao[dia] = ajustarListaManual(undefined, mapaPadrao[dia], disciplinasParticipantesIds);
    }
    setSemanaModeloManual(manualPadrao);
    setDuracaoBlocoMinutos(50);
    setDataInicio(hojeISO());
    setErrorMessage(null);
    setShowForm(true);
  };

  const carregarCamposDoCronograma = (cronograma: CronogramaDTO) => {
    setEditandoId(cronograma.id);
    setModalidade(cronograma.modalidade);
    setTipoPeriodo(cronograma.tipoPeriodo);

    if (cronograma.modalidade === 'MANUAL' && cronograma.semanaModeloManual) {
      const diasManuais = Object.entries(cronograma.semanaModeloManual)
        .filter(([, ids]) => Array.isArray(ids) && ids.length > 0)
        .map(([dia]) => Number(dia))
        .filter((dia) => dia >= 1 && dia <= 7)
        .sort((a, b) => a - b);
      const ativos = diasManuais.length > 0 ? diasManuais : [...TODOS_DIAS_SEMANA];
      setDiasAtivosSemana(ativos);

      const mapa = construirBlocosPadrao();
      const manual: Record<number, string[]> = {};
      for (const dia of TODOS_DIAS_SEMANA) {
        const idsDia = cronograma.semanaModeloManual[dia];
        if (Array.isArray(idsDia) && idsDia.length > 0) {
          mapa[dia] = Math.min(MAX_BLOCOS_DIA, idsDia.length);
          manual[dia] = ajustarListaManual(idsDia, mapa[dia], disciplinasParticipantesIds);
        } else {
          mapa[dia] = cronograma.blocosPorDiaSemana?.[dia] ?? cronograma.blocosPorDia ?? BLOCOS_PADRAO_POR_DIA[dia];
          manual[dia] = ajustarListaManual(undefined, mapa[dia], disciplinasParticipantesIds);
        }
      }
      setBlocosPorDiaSemana(mapa);
      setSemanaModeloManual(manual);
    } else {
      const ativos = cronograma.diasAtivosSemana?.length
        ? [...cronograma.diasAtivosSemana].sort((a, b) => a - b)
        : [...TODOS_DIAS_SEMANA];
      setDiasAtivosSemana(ativos);

      const mapa = construirBlocosPadrao();
      const manual: Record<number, string[]> = {};
      for (const dia of TODOS_DIAS_SEMANA) {
        const qtd = cronograma.blocosPorDiaSemana?.[dia]
          ?? (ativos.includes(dia) ? (cronograma.blocosPorDia ?? BLOCOS_PADRAO_POR_DIA[dia]) : BLOCOS_PADRAO_POR_DIA[dia]);
        mapa[dia] = Math.max(MIN_BLOCOS_DIA, Math.min(MAX_BLOCOS_DIA, qtd));
        manual[dia] = ajustarListaManual(undefined, mapa[dia], disciplinasParticipantesIds);
      }
      setBlocosPorDiaSemana(mapa);
      setSemanaModeloManual(manual);
    }

    setDuracaoBlocoMinutos(cronograma.duracaoBlocoMinutos);
    setDataInicio(cronograma.dataInicio);
    setErrorMessage(null);
  };

  const abrirEdicao = (cronograma: CronogramaDTO) => {
    carregarCamposDoCronograma(cronograma);
    setShowForm(true);
  };

  const handleSelecionarModalidadeForm = (novaModalidade: ModalidadeCronograma) => {
    const existente = cronogramas.find((c) => c.modalidade === novaModalidade);
    if (existente) {
      carregarCamposDoCronograma(existente);
      return;
    }

    setEditandoId(null);
    setModalidade(novaModalidade);
    if (novaModalidade === 'MANUAL') {
      setSemanaModeloManual((prev) => {
        const atualizado: Record<number, string[]> = { ...prev };
        for (const dia of diasAtivosSemana) {
          atualizado[dia] = ajustarListaManual(
            prev[dia],
            blocosPorDiaSemana[dia] ?? MIN_BLOCOS_DIA,
            disciplinasParticipantesIds
          );
        }
        return atualizado;
      });
    }
    setErrorMessage(null);
  };

  const montarPayload = (): CriarCronogramaPayload => {
    const diasOrdenados = [...diasAtivosSemana].sort((a, b) => a - b);
    const mapaAtivos = Object.fromEntries(
      diasOrdenados.map((dia) => [
        dia,
        Math.max(MIN_BLOCOS_DIA, Math.min(MAX_BLOCOS_DIA, blocosPorDiaSemana[dia] ?? MIN_BLOCOS_DIA)),
      ])
    );

    if (modalidade === 'MANUAL') {
      const manualPayload: Record<number, string[]> = {};
      for (const dia of diasOrdenados) {
        manualPayload[dia] = ajustarListaManual(
          semanaModeloManual[dia],
          mapaAtivos[dia],
          disciplinasParticipantesIds
        );
      }
      return {
        modalidade,
        duracaoBlocoMinutos,
        tipoPeriodo,
        dataInicio,
        diasAtivosSemana: diasOrdenados,
        blocosPorDiaSemana: mapaAtivos,
        disciplinasParticipantesIds,
        semanaModeloManual: manualPayload,
      };
    }

    return {
      modalidade,
      duracaoBlocoMinutos,
      tipoPeriodo,
      dataInicio,
      diasAtivosSemana: diasOrdenados,
      blocosPorDiaSemana: mapaAtivos,
      disciplinasParticipantesIds,
    };
  };

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
    const diaInvalido = diasAtivosSemana.some((dia) => {
      const qtd = blocosPorDiaSemana[dia] ?? 0;
      return qtd < MIN_BLOCOS_DIA || qtd > MAX_BLOCOS_DIA;
    });
    if (diaInvalido) {
      setErrorMessage('A quantidade de blocos por dia ativo deve ser entre 1 e 24.');
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
          title: 'Substituir cronograma existente?',
          description: `O cronograma ${ROTULO_MODALIDADE[modalidade]} existente e seus blocos atuais serão substituídos. Sessões e progresso de tópicos são mantidos.`,
          confirmText: 'Substituir e gerar blocos',
          cancelText: 'Cancelar',
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
      await carregarCronogramas(selectedTrilhaId);
    }
  };

  const handleSelecionarCronograma = (cronogramaId: string) => {
    setSelectedCronogramaId(cronogramaId);
    const selecionado = cronogramas.find((c) => c.id === cronogramaId);
    if (selecionado && !selecionado.ativo) {
      void handleAtivarCronograma(cronogramaId);
    }
  };

  const handleExcluirCronograma = async (cronograma: CronogramaDTO) => {
    const confirmado = await confirm({
      title: 'Excluir cronograma?',
      description: `Todos os blocos do cronograma ${ROTULO_MODALIDADE[cronograma.modalidade]} serão removidos. Sessões já registradas permanecem.`,
      confirmText: 'Excluir',
      cancelText: 'Cancelar',
      variant: 'danger',
    });
    if (!confirmado) return;
    try {
      await EstudosService.excluirCronograma(selectedTrilhaId, cronograma.id);
      if (editandoId === cronograma.id) {
        setShowForm(false);
        setEditandoId(null);
      }
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
      },
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
      cancelText: 'Cancelar',
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
      duracaoMinutos: bloco.duracaoMinutos,
    };
    onIniciarEstudo(bloco.disciplinaId, registro);
  };

  const modalidadeOptions = useMemo(
    () =>
      MODALIDADES_ORDEM.map((m) => ({
        value: m,
        label: ROTULO_MODALIDADE[m],
      })),
    []
  );

  const cronogramaExistenteSelecionado = useMemo(
    () => cronogramas.find((c) => c.modalidade === modalidade) ?? null,
    [cronogramas, modalidade]
  );

  const periodoOptions = useMemo(
    () => [
      { value: 'ATE_PROVA', label: ROTULO_PERIODO.ATE_PROVA, disabled: !dataProvaFutura },
      { value: 'UMA_SEMANA', label: ROTULO_PERIODO.UMA_SEMANA },
      { value: 'UM_MES', label: ROTULO_PERIODO.UM_MES },
      { value: 'TRES_MESES', label: ROTULO_PERIODO.TRES_MESES },
    ],
    [dataProvaFutura]
  );

  const handleVisibleRangeChange = useCallback((range: CalendarVisibleRange) => {
    setVisibleRange((prev) =>
      prev.dataInicio === range.dataInicio &&
      prev.dataFim === range.dataFim &&
      prev.modo === range.modo
        ? prev
        : range
    );
  }, []);

  const calendarItems = useMemo<BlocoCalendarItem[]>(() => {
    return blocos.map((bloco) => {
      const badges: CalendarBadge[] = [];
      if (!bloco.concluido && bloco.statusData === 'ATRASADO') {
        badges.push({ label: 'Atrasado', variant: 'danger' });
      } else if (!bloco.concluido && bloco.statusData === 'HOJE') {
        badges.push({ label: 'Hoje', variant: 'primary' });
      }
      badges.push({ label: `${bloco.duracaoMinutos} min`, variant: 'secondary' });

      return {
        id: bloco.id,
        data: bloco.data,
        titulo: bloco.disciplinaNome,
        cor: bloco.disciplinaCor || '#6366f1',
        concluido: bloco.concluido,
        badges,
        metadados: [`Bloco #${bloco.ordem}`],
        bloco,
      };
    });
  }, [blocos]);

  const disciplinaOptions = useMemo(
    () =>
      (selectedTrilhaDetalhe?.disciplinas ?? []).map((d) => ({
        value: d.id,
        label: d.nome,
        dotColor: d.cor,
      })),
    [selectedTrilhaDetalhe?.disciplinas]
  );

  const botaoEntenderModalidades = (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      leftIcon={<HelpCircle size={14} />}
      onClick={() => setShowModalidadesGuia(true)}
    >
      Entender modalidades
    </Button>
  );

  return (
    <div className={styles.container}>
      <PageBanner
        title="Planejamento de Cronogramas"
        description="Gere e gerencie cronogramas de estudo estruturados, com controle de modalidade e blocos diários."
        icon={<Calendar size={26} />}
        actions={
          <div className={styles.pageActions}>
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
        }
      />

      {errorMessage && <div className={styles.errorMessage} role="alert">{errorMessage}</div>}

      {/* Formulário de criação/edição */}
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
                <Select
                  id="cronograma-modalidade"
                  label="Modalidade"
                  value={modalidade}
                  onChange={(valor) => handleSelecionarModalidadeForm(valor as ModalidadeCronograma)}
                  options={modalidadeOptions}
                />
              </div>

              <div className={styles.formGroup}>
                <Select
                  id="cronograma-periodo"
                  label="Período"
                  value={tipoPeriodo}
                  onChange={setTipoPeriodo}
                  options={periodoOptions}
                />
              </div>

              <div className={styles.formGroup}>
                <Input
                  id="cronograma-duracao-bloco"
                  label="Duração do bloco (minutos)"
                  type="number"
                  min={30}
                  max={240}
                  step={5}
                  value={duracaoBlocoMinutos}
                  onChange={(e) => setDuracaoBlocoMinutos(Number.parseInt(e.target.value, 10) || 30)}
                />
              </div>

              <div className={styles.formGroup}>
                <DatePicker
                  id="cronograma-data-inicio"
                  label="Data de início"
                  value={dataInicio}
                  onChange={(valor) => {
                    if (valor) setDataInicio(valor);
                  }}
                />
              </div>
            </div>

            <div className={styles.modalityGuideGrid} role="group" aria-label="Explicação das modalidades de cronograma">
              {MODALIDADES_ORDEM.map((m) => {
                const info = MODALIDADES_INFO[m];
                const jaExiste = modalidadesUsadas.has(m);
                const selecionada = modalidade === m;
                return (
                  <button
                    key={m}
                    type="button"
                    aria-pressed={selecionada}
                    onClick={() => handleSelecionarModalidadeForm(m)}
                    className={[
                      styles.modalityGuideCard,
                      selecionada ? styles.modalityGuideCardSelected : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    <div className={styles.modalityGuideHeader}>
                      <span className={styles.modalityGuideTitle}>
                        {renderIconeModalidade(m, 15)}
                        <strong>{info.titulo}</strong>
                      </span>
                      <Badge
                        variant={selecionada ? 'primary' : jaExiste ? 'warning' : 'secondary'}
                        size="sm"
                      >
                        {selecionada ? 'Selecionada' : jaExiste ? 'Já existe' : 'Disponível'}
                      </Badge>
                    </div>
                    <p className={styles.modalityGuideDesc}>{info.descricao}</p>
                    <span className={styles.modalityGuideExample}>{info.exemplo}</span>
                  </button>
                );
              })}
            </div>

            {cronogramaExistenteSelecionado && (
              <div className={styles.replaceAlert} role="status">
                <AlertTriangle size={16} aria-hidden="true" />
                <span>
                  Já existe um cronograma <strong>{ROTULO_MODALIDADE[modalidade]}</strong> nesta trilha. Ao
                  salvar, o existente será substituído (sessões e progresso de tópicos são mantidos).
                </span>
              </div>
            )}

            <fieldset className={styles.daysFieldset}>
              <div className={styles.daysFieldsetHeader}>
                <legend className={styles.label}>Dias da semana e blocos por dia (máx 24)</legend>
                <span className={styles.weekSummary}>
                  {totalBlocosSemana} {totalBlocosSemana === 1 ? 'bloco' : 'blocos'}/semana ·{' '}
                  {formatarHorasDia(totalMinutosSemana)}
                </span>
              </div>

              <div className={styles.weekBlocksGrid}>
                {DIAS_SEMANA.map((dia) => {
                  const ativo = diasAtivosSemana.includes(dia.id);
                  const blocosDia = ativo ? (blocosPorDiaSemana[dia.id] ?? MIN_BLOCOS_DIA) : 0;
                  const minutosDia = blocosDia * duracaoBlocoMinutos;
                  const horasFormatadas = formatarHorasDia(minutosDia);

                  return (
                    <div
                      key={dia.id}
                      className={`${styles.dayBlockCard} ${ativo ? styles.dayBlockCardActive : ''}`}
                    >
                      <Checkbox
                        label={dia.label}
                        checked={ativo}
                        onChange={() => handleToggleDia(dia.id)}
                      />

                      <div className={styles.dayStepperRow}>
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          iconOnly
                          leftIcon={<Minus size={14} />}
                          aria-label={`Diminuir blocos em ${dia.label}`}
                          disabled={!ativo || blocosDia <= MIN_BLOCOS_DIA}
                          onClick={() => handleAlterarBlocosDia(dia.id, blocosDia - 1)}
                        />
                        <Input
                          type="number"
                          min={MIN_BLOCOS_DIA}
                          max={MAX_BLOCOS_DIA}
                          disabled={!ativo}
                          aria-label={`Blocos em ${dia.label}`}
                          className={styles.dayBlocksInput}
                          value={ativo ? blocosDia : 0}
                          onChange={(e) =>
                            handleAlterarBlocosDia(dia.id, Number.parseInt(e.target.value, 10) || MIN_BLOCOS_DIA)
                          }
                        />
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          iconOnly
                          leftIcon={<Plus size={14} />}
                          aria-label={`Aumentar blocos em ${dia.label}`}
                          disabled={!ativo || blocosDia >= MAX_BLOCOS_DIA}
                          onClick={() => handleAlterarBlocosDia(dia.id, blocosDia + 1)}
                        />
                      </div>

                      <span
                        className={`${styles.dayHours} ${!ativo ? styles.dayHoursMuted : ''}`}
                        data-testid={`horas-dia-${dia.id}`}
                      >
                        {horasFormatadas}
                      </span>
                    </div>
                  );
                })}
              </div>
            </fieldset>

            {modalidade === 'MANUAL' && diasAtivosSemana.length > 0 && disciplinaOptions.length > 0 && (
              <div className={styles.manualSection}>
                <span className={styles.label}>Distribuição manual de disciplinas por dia</span>
                <div className={styles.manualDaysGrid}>
                  {[...diasAtivosSemana]
                    .sort((a, b) => a - b)
                    .map((diaId) => {
                      const diaInfo = DIAS_SEMANA.find((d) => d.id === diaId)!;
                      const qtd = blocosPorDiaSemana[diaId] ?? MIN_BLOCOS_DIA;
                      const selecionadas = ajustarListaManual(
                        semanaModeloManual[diaId],
                        qtd,
                        disciplinasParticipantesIds
                      );
                      return (
                        <div key={diaId} className={styles.manualDayCard}>
                          <strong className={styles.manualDayTitle}>
                            {diaInfo.label} ({qtd} {qtd === 1 ? 'bloco' : 'blocos'})
                          </strong>
                          <div className={styles.manualSlots}>
                            {selecionadas.map((discId, idx) => (
                              <Select
                                key={`${diaId}-${idx}`}
                                aria-label={`${diaInfo.label} - Bloco ${idx + 1}`}
                                value={discId}
                                onChange={(val) => handleAlterarDisciplinaManual(diaId, idx, val)}
                                options={disciplinaOptions}
                              />
                            ))}
                          </div>
                        </div>
                      );
                    })}
                </div>
              </div>
            )}

            <div className={styles.formActions}>
              {editandoId && (
                <div className={styles.formActionsLeading}>
                  <Button
                    type="button"
                    variant="danger"
                    leftIcon={<Trash2 size={15} />}
                    disabled={savingCronograma}
                    onClick={() => {
                      const alvo = cronogramas.find((c) => c.id === editandoId);
                      if (alvo) void handleExcluirCronograma(alvo);
                    }}
                  >
                    Excluir cronograma
                  </Button>
                </div>
              )}
              <div className={styles.formActionsTrailing}>
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
            </div>
          </form>
        </section>
      )}

      {/* Abas por cronograma (alternar ativa imediatamente) + resumo intuitivo */}
      <section className={styles.sectionCard}>
        <h2 className={styles.sectionTitle}>Cronogramas da trilha</h2>

        {loadingCronogramas ? (
          <Skeleton variant="row" linhas={1} />
        ) : cronogramas.length === 0 ? (
          <div className={styles.cronogramasTabsRow}>
            <p className={styles.emptyText}>
              Nenhum cronograma cadastrado nesta trilha. Use &quot;Novo cronograma&quot; para gerar o primeiro.
            </p>
            {botaoEntenderModalidades}
          </div>
        ) : (
          <>
            <div className={styles.cronogramasTabsRow}>
              <Tabs
                items={cronogramas.map((c) => ({
                  id: c.id,
                  label: c.ativo ? `${ROTULO_MODALIDADE[c.modalidade]} · Ativo` : ROTULO_MODALIDADE[c.modalidade],
                }))}
                activeId={selectedCronogramaId}
                onChange={handleSelecionarCronograma}
              />
              {botaoEntenderModalidades}
            </div>

            {cronogramaSelecionado && (
              <div className={styles.cronogramaSummaryCard}>
                <div className={styles.cronogramaSummaryTop}>
                  <div className={styles.cronogramaSummaryContent}>
                    <p className={styles.cronogramaModalitySummary}>
                      <span className={styles.cronogramaModalityIcon}>
                        {renderIconeModalidade(cronogramaSelecionado.modalidade, 15)}
                      </span>
                      <span>
                        <strong>Modo {ROTULO_MODALIDADE[cronogramaSelecionado.modalidade]}:</strong>{' '}
                        {MODALIDADES_INFO[cronogramaSelecionado.modalidade].resumo}
                      </span>
                    </p>
                    <div className={styles.cronogramaMetaInfo}>
                      <span className={styles.cronogramaMetaChip}>
                        <CalendarRange size={13} aria-hidden="true" />
                        {ROTULO_PERIODO[cronogramaSelecionado.tipoPeriodo] ?? cronogramaSelecionado.tipoPeriodo}
                      </span>
                      <span className={styles.cronogramaMetaChip}>
                        {formatarDataCurta(cronogramaSelecionado.dataInicio)} →{' '}
                        {formatarDataCurta(cronogramaSelecionado.dataFim)}
                      </span>
                      <span className={styles.cronogramaMetaChip}>
                        <Layers size={13} aria-hidden="true" />
                        {cronogramaSelecionado.quantidadeBlocos} blocos
                      </span>
                      <span className={styles.cronogramaMetaChip}>
                        <Clock size={13} aria-hidden="true" />
                        {cronogramaSelecionado.duracaoBlocoMinutos} min por bloco
                      </span>
                    </div>
                  </div>
                  <div className={styles.cronogramaSummaryActions}>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      leftIcon={<Pencil size={14} />}
                      onClick={() => abrirEdicao(cronogramaSelecionado)}
                      aria-label="Editar cronograma"
                    >
                      Editar
                    </Button>
                  </div>
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

      <Modal
        isOpen={showModalidadesGuia}
        onClose={() => setShowModalidadesGuia(false)}
        title="Como funcionam as modalidades?"
        subtitle="Cada modalidade organiza sua fila de estudos de um jeito diferente. Você pode ter até um cronograma de cada tipo nesta trilha."
        icon={<HelpCircle size={20} />}
        maxWidth="lg"
        footer={
          <Button type="button" variant="secondary" onClick={() => setShowModalidadesGuia(false)}>
            Fechar
          </Button>
        }
      >
        <div className={styles.modalityModalList}>
          {MODALIDADES_ORDEM.map((m) => {
            const info = MODALIDADES_INFO[m];
            const isAtiva = cronogramaSelecionado?.modalidade === m;
            return (
              <article
                key={m}
                className={`${styles.modalityModalItem} ${isAtiva ? styles.modalityModalItemActive : ''}`}
              >
                <div className={styles.modalityModalItemHeader}>
                  <span className={styles.modalityGuideTitle}>
                    {renderIconeModalidade(m, 17)}
                    <strong>{info.titulo}</strong>
                  </span>
                  <div className={styles.modalityModalBadges}>
                    <Badge variant="secondary" size="sm">{info.idealPara}</Badge>
                    {isAtiva && <Badge variant="success" size="sm">Em uso</Badge>}
                  </div>
                </div>
                <p className={styles.modalityGuideDesc}>{info.descricao}</p>
                <span className={styles.modalityGuideExample}>{info.exemplo}</span>
              </article>
            );
          })}
        </div>
      </Modal>

      {/* Calendário semanal / mensal de blocos do cronograma selecionado */}
      {selectedCronogramaId && (
        <>
          {erroBlocos && (
            <SectionErrorCard
              titulo="Não foi possível carregar"
              descricao="Verifique sua conexão e tente novamente."
              onRetry={() => void recarregarTudo()}
              recarregando={loadingBlocos}
            />
          )}
          <SharedCalendarView<BlocoCalendarItem>
            items={calendarItems}
            onVisibleRangeChange={handleVisibleRangeChange}
            ariaLabel="Calendário de blocos do cronograma"
            labels={{
              itemSingular: 'bloco',
              itemPlural: 'blocos',
              concluidoSingular: 'concluído',
              concluidoPlural: 'concluídos',
              pendenteLabel: 'Pendente',
              concluidoLabel: 'Concluído',
              emptyWeekLabel: 'Sem blocos planejados',
              emptyDayLabel: 'Nenhum bloco agendado para este dia.',
              defaultModalTitle: 'Blocos do dia',
            }}
            renderItemActions={(item, { fecharModalDia }) =>
              item.bloco.concluido ? (
                <Button
                  type="button"
                  variant="warning"
                  size="md"
                  leftIcon={<Undo2 size={16} />}
                  onClick={() => void handleReabrirBloco(item.bloco)}
                >
                  Reabrir
                </Button>
              ) : (
                <>
                  <Button
                    type="button"
                    variant="success"
                    size="md"
                    leftIcon={<Check size={16} />}
                    onClick={() => {
                      fecharModalDia();
                      handleConcluirBloco(item.bloco);
                    }}
                  >
                    Concluir
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    size="md"
                    leftIcon={<Play size={16} />}
                    onClick={() => {
                      fecharModalDia();
                      handleIniciarEstudoBloco(item.bloco);
                    }}
                    disabled={!onIniciarEstudo}
                  >
                    Estudar este bloco
                  </Button>
                </>
              )
            }
          />
        </>
      )}
    </div>
  );
}
