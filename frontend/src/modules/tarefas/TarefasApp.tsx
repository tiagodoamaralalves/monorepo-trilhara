import { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Calendar,
  CalendarDays,
  CheckCircle2,
  Clock,
  ListTodo,
  Filter,
  ChevronDown,
  ChevronRight,
  Folder,
  ArrowUpDown,
  Trash2,
  RotateCcw,
  BookOpen
} from 'lucide-react';

import {
  PageBanner,
  Tabs,
  TabItem,
  Button,
  EmptyState,
  DisciplineLabel,
  Select,
  useConfirm,
  useToast,
  SectionErrorCard,
  Skeleton,
  useGlobalSessionModal,
  AvisoContaExcluida
} from '@shared/design-system';
import { useAuth, AuthView, AuthMode } from '@shared/auth';
import { request, ApiError } from '@shared/auth/api';
import { pushService, isPushSupported } from '@shared/services/pushService';
import {
  AgendaHoje,
  BlocoAgendaItem,
  ExcluirRecorrenteModal,
  QuickAddTask,
  TarefaAgendaItem,
  useAgendaHoje
} from '@shared/agenda';
import { hojeISO } from '@shared/format';
import {
  BlocoCronogramaItemDTO,
  BlocoHojeDTO,
  ConcluirAtrasadosResponse,
  TrilhaResumoDTO
} from '@shared/contracts/domainContracts';
import type { Pagina } from '@shared/contracts';

import { Navbar } from './components/Navbar/Navbar';
import { TrilhaComCronogramaInfo } from './components/Navbar/MinhasListasNav';
import { TaskModal, SalvarTarefaPayload } from './components/TaskModal/TaskModal';
import { ListaModal } from './components/ListaModal/ListaModal';
import { ConfiguracaoLembreteModal } from './components/ConfiguracaoLembreteModal/ConfiguracaoLembreteModal';
import { CalendarView } from './components/CalendarView/CalendarView';
import { LandingView } from './views/LandingView';

import {
  Tarefa,
  ListaTarefas,
  FiltroVisualizacao,
  EscopoRecorrencia,
  LixeiraTarefasDTO
} from './types/tarefa';
import { TarefaService } from './services/tarefaService';
import styles from './App.module.css';


type AuthScreen = 'none' | 'login' | 'cadastro' | 'recuperar';
type Ordenacao = 'vencimento' | 'prioridade' | 'manual';

function obterTelaAuthInicial(): AuthScreen {
  if (typeof window === 'undefined') return 'none';
  const p = new URLSearchParams(window.location.search).get('pagina');
  if (p === 'login' || p === 'cadastro' || p === 'recuperar') return p;
  const a = new URLSearchParams(window.location.search).get('auth');
  if (a === 'login' || a === 'cadastro' || a === 'recuperar') return a;
  return 'none';
}

const PESO_PRIORIDADE: Record<string, number> = {
  ALTA: 3,
  alta: 3,
  URGENTE: 3,
  urgente: 3,
  MEDIA: 2,
  media: 2,
  BAIXA: 1,
  baixa: 1,
  NENHUMA: 0,
  nenhuma: 0
};

const OPCOES_ORDENACAO = [
  { value: 'vencimento', label: 'Data de Vencimento' },
  { value: 'prioridade', label: 'Nível de Prioridade' },
  { value: 'manual', label: 'Ordem Padrão' }
];

function TarefasApp() {
  const toast = useToast();
  const { confirm } = useConfirm();
  const { usuario, isAuthenticated, carregando } = useAuth();
  const [telaAuth, setTelaAuth] = useState<AuthScreen>(obterTelaAuthInicial);
  const { abrirModal: abrirModalSessao } = useGlobalSessionModal();
  const {
    dados: agendaHoje,
    recarregarTarefas: recarregarAgendaTarefas,
    recarregarBlocos: recarregarAgendaBlocos,
    notificarMudanca
  } = useAgendaHoje();

  // Estados de dados
  const [tarefasManuais, setTarefasManuais] = useState<Tarefa[]>([]);
  const [listas, setListas] = useState<ListaTarefas[]>([]);
  const [listaAtivaId, setListaAtivaId] = useState<string | undefined>(undefined);
  const [filtro, setFiltro] = useState<FiltroVisualizacao>('hoje');
  const [ordenacao, setOrdenacao] = useState<Ordenacao>('vencimento');
  const [mostrarConcluidas, setMostrarConcluidas] = useState(true);

  // Trilhas de Estudo com cronograma ativo e Listas Virtuais
  const [trilhasComCronograma, setTrilhasComCronograma] = useState<TrilhaComCronogramaInfo[]>([]);
  const [trilhaAtivaId, setTrilhaAtivaId] = useState<string | undefined>(undefined);
  const [blocosTrilha, setBlocosTrilha] = useState<BlocoCronogramaItemDTO[]>([]);
  const [paginaBlocos, setPaginaBlocos] = useState<number>(1);
  const [totalPaginasBlocos, setTotalPaginasBlocos] = useState<number>(1);
  /** contagem exata de blocos atrasados, vinda de `totalItens` (não da página). */
  const [totalAtrasados, setTotalAtrasados] = useState<number>(0);
  // paginação de tarefas (50/100) com "Carregar mais".
  const [paginaTarefas, setPaginaTarefas] = useState<number>(1);
  const [totalPaginasTarefas, setTotalPaginasTarefas] = useState<number>(1);
  const [carregandoBlocos, setCarregandoBlocos] = useState<boolean>(false);
  const [erroBlocos, setErroBlocos] = useState<Error | null>(null);
  const [concluindoAtrasados, setConcluindoAtrasados] = useState<boolean>(false);

  // Lixeira
  const [dadosLixeira, setDadosLixeira] = useState<LixeiraTarefasDTO | null>(null);

  // Modais
  const [modalAberto, setModalAberto] = useState(false);
  const [tarefaEditando, setTarefaEditando] = useState<Tarefa | null>(null);
  const [modalListaAberto, setModalListaAberto] = useState(false);
  const [listaEditando, setListaEditando] = useState<ListaTarefas | null>(null);
  const [modalPushAberto, setModalPushAberto] = useState(false);

  // Modal de exclusão com escopo de recorrência
  const [tarefaParaExcluirRecorrente, setTarefaParaExcluirRecorrente] = useState<Tarefa | null>(null);
  const [escopoExclusao, setEscopoExclusao] = useState<EscopoRecorrencia>('ESTA');

  const [carregandoDados, setCarregandoDados] = useState(false);
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);
  const [carregandoMaisLixeira, setCarregandoMaisLixeira] = useState(false);
  const hoje = hojeISO();

  // ── CARREGAMENTO DE DADOS ──

  const carregarDados = useCallback(async () => {
    if (!isAuthenticated) return;
    setCarregandoDados(true);
    setErroCarregamento(null);
    try {
      const [listasApi, tarefasApi] = await Promise.all([
        TarefaService.listarListas(),
        TarefaService.listarTarefas()
      ]);

      setListas(listasApi);
      setTarefasManuais(tarefasApi.itens);
      setPaginaTarefas(tarefasApi.pagina);
      setTotalPaginasTarefas(tarefasApi.totalPaginas);

      // Carrega trilhas com cronograma ativo
      try {
        const trilhasApi = await request<TrilhaResumoDTO[]>('/trilhas');
        const comCronograma: TrilhaComCronogramaInfo[] = (trilhasApi || [])
          .filter((t): t is TrilhaResumoDTO & { cronogramaAtivoResumo: { cronogramaAtivoId: string } } =>
            Boolean(t.cronogramaAtivoResumo?.cronogramaAtivoId)
          )
          .map((t) => ({
            id: t.id,
            nome: t.nome,
            cor: t.cor,
            cronogramaAtivoId: t.cronogramaAtivoResumo.cronogramaAtivoId
          }));
        setTrilhasComCronograma(comCronograma);
      } catch {
        // Trilhas mantidas como estavam se falhar
      }

      if (filtro === 'lixeira') {
        const lixeiraRes = await TarefaService.listarLixeira();
        setDadosLixeira(lixeiraRes);
      }
    } catch (err) {
      const mensagemErro = err instanceof Error ? err.message : 'Não foi possível carregar as tarefas.';
      setErroCarregamento(mensagemErro);
      toast.error(err instanceof Error ? err : mensagemErro);
    } finally {
      setCarregandoDados(false);
    }
  }, [isAuthenticated, filtro, toast]);

  useEffect(() => {
    carregarDados();
  }, [carregarDados]);

  /**
 * o Service Worker pertence **somente** à origem do app Tarefas. O registro é
 * guardado por suporte e nunca pede permissão — o prompt só existe no clique do usuário.
 */
  useEffect(() => {
    if (!isPushSupported()) return;
    pushService.registrarServiceWorker().catch(() => {
      // Push é best-effort: falha de registro nunca afeta o módulo de Tarefas.
    });
  }, []);

  /**
 * deep link `?tarefaId=`. Abre o `TaskModal`; com 404 (inexistente/excluída) exibe o
 * toast normativo e limpa o parâmetro para não repetir o alerta ao recarregar.
 */
  useEffect(() => {
    if (!isAuthenticated) return;
    const params = new URLSearchParams(window.location.search);
    const tarefaId = params.get('tarefaId');
    if (!tarefaId) return;

    const limparParametro = () => {
      const url = new URL(window.location.href);
      url.searchParams.delete('tarefaId');
      window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
    };

    let cancelado = false;
    TarefaService.buscarTarefa(tarefaId)
      .then((tarefa) => {
        if (cancelado) return;
        setTarefaEditando(tarefa);
        setModalAberto(true);
        limparParametro();
      })
      .catch((err) => {
        if (cancelado) return;
        const inexistente = err instanceof ApiError && (err.status === 404 || err.status === 400);
        if (inexistente) {
          toast.error('Tarefa não encontrada ou excluída');
        } else {
          toast.error('Não foi possível abrir a tarefa do lembrete.');
        }
        limparParametro();
      });

    return () => {
      cancelado = true;
    };
  }, [isAuthenticated, toast]);

  /** "Carregar mais" de tarefas: acrescenta a próxima página à lista atual. */
  const carregarMaisTarefas = useCallback(async () => {
    if (!isAuthenticated || paginaTarefas >= totalPaginasTarefas) return;
    try {
      const proxima = await TarefaService.listarTarefas({
        pagina: paginaTarefas + 1,
        tamanho: 50
      });
      setTarefasManuais((prev) => [...prev, ...proxima.itens]);
      setPaginaTarefas(proxima.pagina);
      setTotalPaginasTarefas(proxima.totalPaginas);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível carregar mais tarefas.');
    }
  }, [isAuthenticated, paginaTarefas, totalPaginasTarefas, toast]);

  // Carregamento da Lista Virtual da Trilha
  const carregarBlocosTrilha = useCallback(
    async (pagina = 1) => {
      if (!trilhaAtivaId) return;
      const trilha = trilhasComCronograma.find((t) => t.id === trilhaAtivaId);
      if (!trilha || !trilha.cronogramaAtivoId) return;

      setCarregandoBlocos(true);
      setErroBlocos(null);
      try {
        const base = `/trilhas/${encodeURIComponent(trilha.id)}/cronogramas/${encodeURIComponent(
          trilha.cronogramaAtivoId
        )}/blocos`;

        // a contagem N vem de `totalItens` com `tamanho=1`, nunca da página carregada.
        const [res, contagem] = await Promise.all([
          request<Pagina<BlocoCronogramaItemDTO>>(
            `${base}?concluido=false&pagina=${pagina}&tamanho=50`
          ),
          request<Pagina<BlocoCronogramaItemDTO>>(
            `${base}?concluido=false&statusData=ATRASADO&pagina=1&tamanho=1`
          )
        ]);

        if (pagina === 1) {
          setBlocosTrilha(res.itens || []);
        } else {
          setBlocosTrilha((prev) => [...prev, ...(res.itens || [])]);
        }
        setPaginaBlocos(res.pagina || pagina);
        setTotalPaginasBlocos(res.totalPaginas || 1);
        setTotalAtrasados(contagem.totalItens ?? 0);
      } catch (err) {
        setErroBlocos(err instanceof Error ? err : new Error(String(err)));
      } finally {
        setCarregandoBlocos(false);
      }
    },
    [trilhaAtivaId, trilhasComCronograma]
  );

  useEffect(() => {
    if (trilhaAtivaId) {
      carregarBlocosTrilha(1);
    } else {
      setBlocosTrilha([]);
      setErroBlocos(null);
      setTotalAtrasados(0);
    }
  }, [trilhaAtivaId, carregarBlocosTrilha]);

  const trilhaAtiva = useMemo(() => {
    if (!trilhaAtivaId) return null;
    return trilhasComCronograma.find((t) => t.id === trilhaAtivaId) || null;
  }, [trilhasComCronograma, trilhaAtivaId]);

  // Concluir todas as atrasadas da trilha sem salvar sessão
  const handleConcluirTodasAtrasadasSemSalvar = async () => {
    if (!trilhaAtiva || totalAtrasados === 0) return;

    const confirmed = await confirm({
      title: 'Concluir blocos atrasados',
      description: `Deseja concluir ${totalAtrasados} blocos atrasados desta trilha sem registrar sessão de estudo? Esta ação não pode ser desfeita.`,
      confirmText: 'Concluir atrasados',
      variant: 'danger'
    });

    if (!confirmed) return;

    const url = `/trilhas/${encodeURIComponent(trilhaAtiva.id)}/cronogramas/${encodeURIComponent(
      trilhaAtiva.cronogramaAtivoId
    )}/blocos/concluir-atrasados`;

    const executarLote = async () => {
      setConcluindoAtrasados(true);
      try {
        const opId =
          typeof crypto !== 'undefined' && crypto.randomUUID
            ? crypto.randomUUID()
            : `op-${Date.now()}`;
        const res = await request<ConcluirAtrasadosResponse>(url, {
          method: 'POST',
          body: JSON.stringify({ operationId: opId })
        });

        if (res.falhas && res.falhas.length > 0) {
          // Resultado parcial: sucesso/falha por bloco, com retry explícito (sem sessão).
          toast.warning(
            `${res.totalConcluidos} concluídos, ${res.falhas.length} falharam`,
            { acao: { rotulo: 'Tentar novamente', aoAcionar: () => void executarLote() } }
          );
        } else {
          toast.success(`${res.totalConcluidos} blocos atrasados concluídos com sucesso!`);
        }

        await carregarBlocosTrilha(1);
        notificarMudanca();
      } catch (err) {
        toast.error(err instanceof Error ? err : 'Falha ao concluir blocos atrasados.');
      } finally {
        setConcluindoAtrasados(false);
      }
    };

    await executarLote();
  };

  /**
 * o bloco pertence a uma versão substituída do cronograma. A lista e a agenda são
 * recarregadas com toast normativo; nenhuma sessão é reenviada.
 */
  const tratarBlocoObsoleto = useCallback(() => {
    toast.info('O cronograma foi atualizado. A lista foi recarregada.');
    void carregarBlocosTrilha(1);
    notificarMudanca();
  }, [carregarBlocosTrilha, notificarMudanca, toast]);

  // Concluir bloco abrindo GlobalSessionModal
  const handleConcluirBloco = (bloco: BlocoCronogramaItemDTO | BlocoHojeDTO) => {
    const blocoId = 'blocoId' in bloco ? bloco.blocoId : bloco.id;
    const trilhaNome = 'trilhaNome' in bloco ? bloco.trilhaNome : undefined;
    abrirModalSessao({
      modo: 'BLOCO_DIRETO',
      trilhaId: bloco.trilhaId || trilhaAtiva?.id,
      disciplinaId: bloco.disciplinaId,
      blocoId,
      duracaoInicialSegundos: (bloco.duracaoMinutos || 25) * 60,
      dataSessao: bloco.data,
      trilhaNome: trilhaNome || trilhaAtiva?.nome,
      disciplinaNome: bloco.disciplinaNome,
      disciplinaCor: bloco.disciplinaCor,
      onBlocoObsoleto: tratarBlocoObsoleto,
      onSuccess: () => {
        if (trilhaAtivaId) {
          setBlocosTrilha((prev) => prev.filter((b) => b.id !== blocoId));
        }
        notificarMudanca();
      }
    });
  };

  // ── MANIPULADORES DE TAREFAS (CRUD) ──

  const handleCriarTarefaRapida = async (titulo: string): Promise<boolean> => {
    try {
      const dataVencimento = filtro === 'hoje' ? hoje : undefined;
      const criada = await TarefaService.criarTarefa({
        titulo,
        listaId: listaAtivaId,
        dataVencimento,
        data: dataVencimento,
        prioridade: 'MEDIA'
      });
      setTarefasManuais((prev) => [criada, ...prev]);
      notificarMudanca();
      toast.success('Tarefa adicionada!');
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível criar a tarefa.');
      return false;
    }
  };

  const handleSalvarTarefa = async (dados: SalvarTarefaPayload): Promise<boolean> => {
    try {
      if (tarefaEditando && tarefaEditando.id) {
        const atualizada = await TarefaService.atualizarTarefa(
          tarefaEditando.id,
          dados,
          dados.escopo
        );
        setTarefasManuais((prev) => prev.map((t) => (t.id === atualizada.id ? atualizada : t)));
        toast.success('Tarefa atualizada com sucesso.');
      } else if (dados.recorrencia) {
        await TarefaService.criarSerie({
          ...dados.recorrencia,
          titulo: dados.titulo || '',
          descricao: dados.descricao || dados.observacoes,
          prioridade: dados.prioridade || 'MEDIA',
          horario: dados.horario,
          subtarefasModelo: (dados.subtarefas || []).map((s, idx) => ({
            id: s.id,
            titulo: s.titulo,
            concluida: false,
            ordem: idx
          })),
          listaId: dados.listaId || listaAtivaId
        });
        toast.success('Série recorrente criada com sucesso! Ocorrências materializadas.');
        await carregarDados();
      } else {
        const criada = await TarefaService.criarTarefa({
          ...dados,
          listaId: dados.listaId || listaAtivaId
        });
        setTarefasManuais((prev) => [criada, ...prev]);
        toast.success('Tarefa criada com sucesso.');
      }
      setTarefaEditando(null);
      notificarMudanca();
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar a tarefa.');
      return false;
    }
  };

  const handleAlternarTarefa = async (id: string, concluida: boolean) => {
    const atualizarCampoConclusao = (lista: Tarefa[], estadoConcluido: boolean) =>
      lista.map((t) =>
        t.id === id
          ? {
              ...t,
              concluida: estadoConcluido,
              estado: (estadoConcluido ? 'CONCLUIDA' : 'PENDENTE') as Tarefa['estado'],
              concluidaEm: estadoConcluido ? new Date().toISOString() : null
            }
          : t
      );

    // Atualização otimista
    setTarefasManuais((prev) => atualizarCampoConclusao(prev, concluida));
    setTarefaEditando((prev) =>
      prev && prev.id === id
        ? {
            ...prev,
            concluida,
            estado: concluida ? 'CONCLUIDA' : 'PENDENTE',
            concluidaEm: concluida ? new Date().toISOString() : null
          }
        : prev
    );

    try {
      const atualizada = await TarefaService.alternarConclusao(id, concluida);
      setTarefasManuais((prev) => prev.map((t) => (t.id === atualizada.id ? atualizada : t)));
      setTarefaEditando((prev) => (prev && prev.id === id ? atualizada : prev));
      notificarMudanca();
    } catch (err) {
      setTarefasManuais((prev) => atualizarCampoConclusao(prev, !concluida));
      setTarefaEditando((prev) =>
        prev && prev.id === id
          ? {
              ...prev,
              concluida: !concluida,
              estado: !concluida ? 'CONCLUIDA' : 'PENDENTE',
              concluidaEm: !concluida ? new Date().toISOString() : null
            }
          : prev
      );
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar o status da tarefa.');
    }
  };

  const handleAlternarSubtarefa = async (tarefaId: string, subtarefaId: string, concluida: boolean) => {
    try {
      const atualizada = await TarefaService.alternarSubtarefa(tarefaId, subtarefaId, concluida);
      setTarefasManuais((prev) => prev.map((t) => (t.id === atualizada.id ? atualizada : t)));
      notificarMudanca();
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar a subtarefa.');
    }
  };

  const handleExcluirTarefa = async (tarefa: Tarefa) => {
    if (tarefa.serieId && !tarefa.desvinculadaDaSerie) {
      // Abre diálogo de seleção de escopo
      setTarefaParaExcluirRecorrente(tarefa);
      setEscopoExclusao('ESTA');
      return;
    }

    const confirmou = await confirm({
      title: 'Excluir tarefa?',
      description: 'A tarefa será movida para a lixeira e mantida por 30 dias antes de ser excluída definitivamente.',
      confirmText: 'Mover para Lixeira',
      variant: 'danger'
    });
    if (!confirmou) return;

    try {
      await TarefaService.excluirTarefa(tarefa.id);
      setTarefasManuais((prev) => prev.filter((t) => t.id !== tarefa.id));
      notificarMudanca();
      toast.success('Tarefa movida para a lixeira.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a tarefa.');
    }
  };

  const handleConfirmarExclusaoRecorrente = async () => {
    if (!tarefaParaExcluirRecorrente) return;
    const tarefa = tarefaParaExcluirRecorrente;
    setTarefaParaExcluirRecorrente(null);

    try {
      await TarefaService.excluirTarefa(tarefa.id, escopoExclusao);
      await carregarDados();
      notificarMudanca();
      toast.success('Exclusão de recorrência concluída com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a tarefa recorrente.');
    }
  };

  // ── MANIPULADORES DE LISTAS ──

  const handleSalvarLista = async (dados: { titulo: string; cor?: string }): Promise<boolean> => {
    try {
      if (listaEditando && listaEditando.id) {
        const atualizada = await TarefaService.atualizarLista(listaEditando.id, {
          nome: dados.titulo,
          cor: dados.cor
        });
        setListas((prev) => prev.map((l) => (l.id === atualizada.id ? atualizada : l)));
        toast.success('Lista atualizada com sucesso.');
      } else {
        const criada = await TarefaService.criarLista({
          nome: dados.titulo,
          cor: dados.cor
        });
        setListas((prev) => [...prev, criada]);
        setListaAtivaId(criada.id);
        toast.success('Lista criada com sucesso.');
      }
      setListaEditando(null);
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar a lista.');
      return false;
    }
  };

  const handleExcluirLista = async (id: string) => {
    const lista = listas.find((l) => l.id === id);
    const count = lista?.totalTarefas ?? tarefasManuais.filter((t) => t.listaId === id && !t.concluida).length;

    const confirmou = await confirm({
      title: `Excluir lista "${lista?.nome || lista?.titulo || 'esta lista'}"?`,
      description: `Esta lista contém ${count} tarefas ativas. A lista e suas tarefas serão movidas para a lixeira por 30 dias.`,
      confirmText: 'Mover Lista e Tarefas para Lixeira',
      variant: 'danger'
    });
    if (!confirmou) return;

    try {
      const res = await TarefaService.excluirLista(id);
      setListas((prev) => prev.filter((l) => l.id !== id));
      setTarefasManuais((prev) => prev.filter((t) => t.listaId !== id));
      if (listaAtivaId === id) setListaAtivaId(undefined);
      toast.success(`Lista e ${res.tarefasAfetadas} tarefas movidas para a lixeira.`);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a lista.');
    }
  };

  const handleRestaurarLista = async (id: string) => {
    try {
      const restaurada = await TarefaService.restaurarLista(id);
      setListas((prev) => [...prev, restaurada]);
      if (dadosLixeira) {
        setDadosLixeira((prev) =>
          prev ? { ...prev, listas: prev.listas.filter((l) => l.id !== id) } : null
        );
      }
      await carregarDados();
      notificarMudanca();
      toast.success(`Lista "${restaurada.nome}" restaurada com sucesso!`);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível restaurar a lista.');
    }
  };

  const handleRestaurarTarefa = async (id: string) => {
    try {
      const resultado = await TarefaService.restaurarTarefaLixeira(id);
      if (dadosLixeira) {
        setDadosLixeira((prev) =>
          prev ? { ...prev, tarefas: prev.tarefas.filter((t) => t.id !== id) } : null
        );
      }
      await carregarDados();
      notificarMudanca();
      if (resultado.realocadaParaListaGeral) {
        // a lista original foi purgada definitivamente.
        toast.info('Tarefa restaurada na Lista Geral (a lista original foi excluída definitivamente).');
      } else {
        toast.success(`Tarefa "${resultado.tarefa.titulo}" restaurada com sucesso!`);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível restaurar a tarefa.');
    }
  };

  const handleEsvaziarLixeira = async () => {
    const confirmou = await confirm({
      title: 'Esvaziar lixeira definitivamente?',
      description: 'Todos os itens na lixeira serão apagados de forma permanente. Esta ação não poderá ser revertida.',
      confirmText: 'Esvaziar Definitivamente',
      variant: 'danger'
    });
    if (!confirmou) return;

    try {
      await TarefaService.esvaziarLixeira();
      setDadosLixeira({ tarefas: [], listas: [], series: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 });
      toast.success('Lixeira esvaziada com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível esvaziar a lixeira.');
    }
  };

  const carregarMaisLixeira = async () => {
    if (!dadosLixeira || dadosLixeira.pagina >= dadosLixeira.totalPaginas || carregandoMaisLixeira) return;
    setCarregandoMaisLixeira(true);
    try {
      const prox = await TarefaService.listarLixeira({
        pagina: dadosLixeira.pagina + 1,
        tamanho: dadosLixeira.tamanho
      });
      setDadosLixeira((prev) =>
        prev
          ? {
              ...prox,
              listas: [...prev.listas, ...prox.listas],
              tarefas: [...prev.tarefas, ...prox.tarefas],
              series: [...prev.series, ...prox.series]
            }
          : prox
      );
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível carregar mais itens da lixeira.');
    } finally {
      setCarregandoMaisLixeira(false);
    }
  };

  // ── FILTRAGEM E ORDENAÇÃO DE TAREFAS ──

  const tarefasFiltradas = useMemo(() => {
    let base = tarefasManuais;

    if (listaAtivaId) {
      base = base.filter((t) => t.listaId === listaAtivaId);
    } else {
      switch (filtro) {
        case 'hoje':
          base = base.filter((t) => (t.dataVencimento || t.data) === hoje);
          break;
        case 'proximas':
          base = base.filter((t) => {
            const d = t.dataVencimento || t.data;
            return Boolean(d && d > hoje);
          });
          break;
        case 'concluidas':
          base = base.filter((t) => t.concluida);
          break;
        case 'todas':
        case 'calendario':
        default:
          break;
      }
    }

    return [...base].sort((a, b) => {
      if (ordenacao === 'prioridade') {
        const pesoA = PESO_PRIORIDADE[a.prioridade] ?? 0;
        const pesoB = PESO_PRIORIDADE[b.prioridade] ?? 0;
        if (pesoB !== pesoA) return pesoB - pesoA;
      }
      if (ordenacao === 'vencimento') {
        const dataA = a.dataVencimento || a.data || '9999-99-99';
        const dataB = b.dataVencimento || b.data || '9999-99-99';
        if (dataA !== dataB) return dataA.localeCompare(dataB);
      }
      return (a.ordem ?? 0) - (b.ordem ?? 0);
    });
  }, [tarefasManuais, listaAtivaId, filtro, hoje, ordenacao]);

  const tarefasPendentes = useMemo(
    () => tarefasFiltradas.filter((t) => !t.concluida),
    [tarefasFiltradas]
  );
  const tarefasConcluidas = useMemo(
    () => tarefasFiltradas.filter((t) => t.concluida),
    [tarefasFiltradas]
  );
  const tarefasPrincipais = filtro === 'concluidas' ? tarefasConcluidas : tarefasPendentes;

  // Contadores para as abas
  const countHoje = useMemo(
    () => tarefasManuais.filter((t) => (t.dataVencimento || t.data) === hoje && !t.concluida).length,
    [tarefasManuais, hoje]
  );
  const countProximas = useMemo(
    () => tarefasManuais.filter((t) => (t.dataVencimento || t.data || '') > hoje && !t.concluida).length,
    [tarefasManuais, hoje]
  );
  const countConcluidas = useMemo(
    () => tarefasManuais.filter((t) => t.concluida).length,
    [tarefasManuais]
  );
  const countTotal = useMemo(
    () => tarefasManuais.filter((t) => !t.concluida).length,
    [tarefasManuais]
  );

  const listaAtiva = useMemo(() => {
    if (!listaAtivaId) return null;
    return listas.find((l) => l.id === listaAtivaId) || null;
  }, [listas, listaAtivaId]);

  const contadoresPorLista = useMemo(() => {
    const map: Record<string, number> = {};
    for (const t of tarefasManuais) {
      if (!t.concluida && t.listaId) {
        map[t.listaId] = (map[t.listaId] || 0) + 1;
      }
    }
    return map;
  }, [tarefasManuais]);

  // ── AUTENTICAÇÃO E ROTEAMENTO ──

  const alternarTelaAuth = useCallback((tela: AuthScreen) => {
    setTelaAuth(tela);
    const url = new URL(window.location.href);
    if (tela === 'none') {
      url.searchParams.delete('pagina');
      url.searchParams.delete('auth');
    } else {
      url.searchParams.set('pagina', tela);
    }
    window.history.pushState(null, '', `${url.pathname}${url.search}${url.hash}`);
  }, []);

  useEffect(() => {
    function sincronizarUrl() {
      setTelaAuth(obterTelaAuthInicial());
    }
    window.addEventListener('popstate', sincronizarUrl);
    return () => window.removeEventListener('popstate', sincronizarUrl);
  }, []);

  useEffect(() => {
    if (isAuthenticated && telaAuth !== 'none') {
      alternarTelaAuth('none');
    }
  }, [isAuthenticated, telaAuth, alternarTelaAuth]);

  const tabs: TabItem[] = [
    { id: 'hoje', label: 'Hoje', count: countHoje, icon: <Clock size={15} /> },
    { id: 'proximas', label: 'Próximos Dias', count: countProximas, icon: <Calendar size={15} /> },
    { id: 'concluidas', label: 'Concluídas', count: countConcluidas, icon: <CheckCircle2 size={15} /> },
    { id: 'calendario', label: 'Calendário', icon: <CalendarDays size={15} /> },
    { id: 'todas', label: 'Todas as Tarefas', count: countTotal, icon: <ListTodo size={15} /> },
    { id: 'lixeira', label: 'Lixeira (30d)', icon: <Trash2 size={15} /> }
  ];

  if (carregando) {
    return (
      <div className={styles.loading}>
        <p>Verificando sessão...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    if (telaAuth === 'login' || telaAuth === 'cadastro' || telaAuth === 'recuperar') {
      return (
        <AuthView
          mode={telaAuth as AuthMode}
          onAlternarModo={(novoModo) => alternarTelaAuth(novoModo)}
          onAutenticado={() => {
            alternarTelaAuth('none');
            toast.success('Login realizado com sucesso!');
          }}
        />
      );
    }

    return (
      <>
        <AvisoContaExcluida />
        <LandingView
          onLogin={() => alternarTelaAuth('login')}
          onCriarConta={() => alternarTelaAuth('cadastro')}
        />
      </>
    );
  }

  return (
    <div className={styles.shell}>
      {/* ── BARRA LATERAL (SIDEBAR PADRONIZADA) ── */}
      <Navbar
        filtroAtivo={filtro}
        usuario={usuario}
        listas={listas}
        listaSelecionadaId={listaAtivaId}
        onSelecionarLista={(id) => {
          setTrilhaAtivaId(undefined);
          setListaAtivaId(id);
        }}
        onAbrirNovaLista={() => {
          setListaEditando(null);
          setModalListaAberto(true);
        }}
        onAbrirEditarLista={(lista) => {
          setListaEditando(lista);
          setModalListaAberto(true);
        }}
        onExcluirLista={handleExcluirLista}
        trilhasComCronograma={trilhasComCronograma}
        trilhaSelecionadaId={trilhaAtivaId}
        onSelecionarTrilha={(id) => {
          setListaAtivaId(undefined);
          setTrilhaAtivaId(id);
        }}
        contadores={{
          hoje: countHoje,
          concluidasHoje: countConcluidas,
          totalHoje: countHoje + countConcluidas,
          proximas: countProximas,
          concluidas: countConcluidas,
          total: countTotal
        }}
        contadoresPorLista={contadoresPorLista}
        onNavegar={(f) => {
          setListaAtivaId(undefined);
          setTrilhaAtivaId(undefined);
          setFiltro(f);
        }}
        onAbrirNovaTarefa={() => {
          setTarefaEditando(null);
          setModalAberto(true);
        }}
        onAbrirLogin={() => alternarTelaAuth('login')}
        onSettings={() => setModalPushAberto(true)}
      />

      {/* ── ÁREA PRINCIPAL DE CONTEÚDO ── */}
      <main className={styles.main}>
        <PageBanner
          title={
            trilhaAtiva ? (
              <DisciplineLabel
                name={trilhaAtiva.nome}
                color={trilhaAtiva.cor || 'var(--purple, #a855f7)'}
              />
            ) : listaAtiva ? (
              <DisciplineLabel
                name={listaAtiva.nome || listaAtiva.titulo || 'Lista'}
                color={listaAtiva.cor || 'var(--primary)'}
              />
            ) : filtro === 'hoje'
              ? 'Tarefas de Hoje'
              : filtro === 'proximas'
              ? 'Próximos Dias'
              : filtro === 'concluidas'
              ? 'Tarefas Concluídas'
              : filtro === 'calendario'
              ? 'Visão em Calendário'
              : filtro === 'lixeira'
              ? 'Lixeira (30 dias)'
              : 'Minhas Tarefas'
          }
          description={
            trilhaAtiva
              ? 'Lista Virtual da Trilha — blocos de estudo do cronograma ativo.'
              : listaAtiva
              ? 'Tarefas organizadas dentro desta lista personalizada.'
              : filtro === 'lixeira'
              ? 'Itens excluídos permanecem aqui por 30 dias antes de serem purgados definitivamente.'
              : 'Organize suas metas diárias, séries recorrentes e tarefas manuais.'
          }
          icon={
            listaAtiva ? (
              <Folder size={26} style={{ color: listaAtiva.cor || 'var(--primary)' }} />
            ) : filtro === 'lixeira' ? (
              <Trash2 size={26} />
            ) : filtro === 'concluidas' ? (
              <CheckCircle2 size={26} />
            ) : filtro === 'calendario' ? (
              <CalendarDays size={26} />
            ) : (
              <Clock size={26} />
            )
          }
          actions={
            filtro === 'lixeira' ? (
              <Button
                variant="danger"
                size="md"
                leftIcon={<Trash2 size={15} />}
                onClick={handleEsvaziarLixeira}
                disabled={!dadosLixeira?.tarefas.length && !dadosLixeira?.listas.length}
              >
                Esvaziar Lixeira
              </Button>
            ) : undefined
          }
          toolbar={
            !listaAtiva ? (
              <div className={styles.toolbar}>
                <Tabs
                  items={tabs}
                  activeId={filtro}
                  onChange={(id) => setFiltro(id as FiltroVisualizacao)}
                />
              </div>
            ) : undefined
          }
        />

        <div className={styles.content}>
          {/* ── VISÃO LIXEIRA (30 DIAS) ── */}
          {filtro === 'lixeira' ? (
            <div>
              <div className={styles.lixeiraSection}>
                <h3 className={styles.lixeiraTitle}>
                  Listas na Lixeira ({dadosLixeira?.listas.length || 0})
                </h3>
                {dadosLixeira?.listas && dadosLixeira.listas.length > 0 ? (
                  <div className={styles.taskList}>
                    {dadosLixeira.listas.map((l) => (
                      <div key={l.id} className={styles.lixeiraCard}>
                        <div className={styles.lixeiraCardInfo}>
                          <Folder size={18} style={{ color: l.cor || 'var(--primary)', flexShrink: 0 }} />
                          <span className={styles.lixeiraCardTitle}>{l.nome}</span>
                        </div>
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          leftIcon={<RotateCcw size={14} />}
                          onClick={() => handleRestaurarLista(l.id)}
                        >
                          Restaurar Lista e Tarefas
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className={styles.lixeiraEmpty}>
                    Nenhuma lista na lixeira.
                  </p>
                )}
              </div>

              <div>
                <h3 className={styles.lixeiraTitle}>
                  Tarefas na Lixeira ({dadosLixeira?.tarefas.length || 0})
                </h3>
                {dadosLixeira?.tarefas && dadosLixeira.tarefas.length > 0 ? (
                  <div className={styles.taskList}>
                    {dadosLixeira.tarefas.map((t) => (
                      <div key={t.id} className={styles.lixeiraCard}>
                        <div className={styles.lixeiraCardText}>
                          <div className={styles.lixeiraCardTitle}>{t.titulo}</div>
                          {t.descricao && (
                            <div className={styles.lixeiraCardDesc}>
                              {t.descricao}
                            </div>
                          )}
                        </div>
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          leftIcon={<RotateCcw size={14} />}
                          onClick={() => handleRestaurarTarefa(t.id)}
                        >
                          Restaurar Tarefa
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState
                    icone={<Trash2 size={24} />}
                    titulo="A lixeira está vazia"
                    descricao="Itens excluídos ficam aqui por 30 dias."
                  />
                )}
                {dadosLixeira && dadosLixeira.pagina < dadosLixeira.totalPaginas && (
                  <div className={styles.carregarMaisBtnWrapper}>
                    <Button
                      type="button"
                      variant="secondary"
                      size="md"
                      onClick={carregarMaisLixeira}
                      loading={carregandoMaisLixeira}
                    >
                      Carregar mais itens da lixeira
                    </Button>
                  </div>
                )}
              </div>
            </div>
          ) : filtro === 'calendario' ? (
            /* ── VISUALIZAÇÃO EM CALENDÁRIO ── */
            <CalendarView
              tarefas={tarefasManuais}
              onAlternarTarefa={handleAlternarTarefa}
              onAdicionarNoDia={(dataISO) => {
                setTarefaEditando({
                  id: '',
                  titulo: '',
                  dataVencimento: dataISO,
                  data: dataISO,
                  concluida: false,
                  prioridade: 'MEDIA'
                });
                setModalAberto(true);
              }}
              onEditarTarefa={(t) => {
                setTarefaEditando(t);
                setModalAberto(true);
              }}
            />
          ) : trilhaAtiva ? (
            /* ── LISTA VIRTUAL POR TRILHA ── */
            <div>
              <div className={styles.virtualListTopBar}>
                <div className={styles.virtualListTitleGroup}>
                  <h3 className={styles.virtualListTitle}>
                    Blocos do Cronograma Ativo ({blocosTrilha.length} nesta página)
                  </h3>
                  <p className={styles.virtualListSubtitle}>
                    Acompanhamento direto do cronograma de estudos. Concluir um bloco registra sua sessão de estudo.
                  </p>
                </div>

                {totalAtrasados > 0 && (
                  <Button
                    type="button"
                    variant="danger"
                    size="sm"
                    onClick={handleConcluirTodasAtrasadasSemSalvar}
                    loading={concluindoAtrasados}
                  >
                    Concluir todas as atrasadas sem salvar ({totalAtrasados})
                  </Button>
                )}
              </div>

              {erroBlocos ? (
                <SectionErrorCard
                  titulo="Erro ao carregar blocos da trilha"
                  descricao={erroBlocos.message}
                  onRetry={() => carregarBlocosTrilha(1)}
                  recarregando={carregandoBlocos}
                />
              ) : blocosTrilha.length > 0 ? (
                <>
                  <div className={styles.taskList}>
                    {blocosTrilha.map((bloco) => (
                      <BlocoAgendaItem
                        key={bloco.id}
                        disciplinaNome={bloco.disciplinaNome}
                        disciplinaCor={bloco.disciplinaCor}
                        duracaoMinutos={bloco.duracaoMinutos}
                        data={bloco.data}
                        statusData={bloco.statusData}
                        onConcluir={() => handleConcluirBloco(bloco)}
                      />
                    ))}
                  </div>

                  {paginaBlocos < totalPaginasBlocos && (
                    <div className={styles.carregarMaisBtnWrapper}>
                      <Button
                        type="button"
                        variant="secondary"
                        size="md"
                        onClick={() => carregarBlocosTrilha(paginaBlocos + 1)}
                        loading={carregandoBlocos}
                      >
                        Carregar mais blocos
                      </Button>
                    </div>
                  )}
                </>
              ) : !carregandoBlocos ? (
                <EmptyState
                  icone={<BookOpen size={24} />}
                  titulo="Nenhum bloco pendente"
                  descricao="Todos os blocos de estudo deste cronograma já foram concluídos!"
                />
              ) : null}
            </div>
          ) : filtro === 'hoje' && !listaAtiva ? (
            /* ── VISÃO HOJE DO REQUISITO 7.1 / 9 ── */
            <div>
              <QuickAddTask onAdicionar={handleCriarTarefaRapida} />

              <AgendaHoje
                modo="tarefas"
                dados={agendaHoje}
                onAlternarTarefa={handleAlternarTarefa}
                onConcluirBloco={handleConcluirBloco}
                onRetryTarefas={recarregarAgendaTarefas}
                onRetryBlocos={recarregarAgendaBlocos}
                onEditarTarefa={(t) => {
                  setTarefaEditando(t as Tarefa);
                  setModalAberto(true);
                }}
                onExcluirTarefa={(t) => handleExcluirTarefa(t as Tarefa)}
                onAlternarSubtarefa={handleAlternarSubtarefa}
              />

            </div>
          ) : (
            /* ── VISUALIZAÇÃO PADRÃO (LISTA / FILTRO) ── */
            <div>
              <QuickAddTask onAdicionar={handleCriarTarefaRapida} />

              {erroCarregamento && tarefasManuais.length === 0 ? (
                <SectionErrorCard
                  titulo="Não foi possível carregar as tarefas"
                  descricao={erroCarregamento}
                  onRetry={() => void carregarDados()}
                />
              ) : carregandoDados && tarefasManuais.length === 0 ? (
                <Skeleton variant="row" linhas={4} />
              ) : (
                <>
                  {tarefasFiltradas.length > 0 && (
                    <div className={styles.listHeader}>
                      <div className={styles.listHeaderTitle}>
                        <span>
                          {filtro === 'concluidas'
                            ? `Tarefas concluídas (${tarefasConcluidas.length})`
                            : `Tarefas (${tarefasPendentes.length} pendentes)`}
                        </span>
                      </div>
                      <div className={styles.listHeaderActions}>
                        <ArrowUpDown size={14} style={{ color: 'var(--text-soft)' }} />
                        <div className={styles.sortSelectWrapper}>
                          <Select
                            value={ordenacao}
                            onChange={(val) => setOrdenacao(val as Ordenacao)}
                            options={OPCOES_ORDENACAO}
                            aria-label="Ordenar tarefas"
                          />
                        </div>
                      </div>
                    </div>
                  )}

                  {tarefasPrincipais.length > 0 ? (
                    <div className={styles.taskList}>
                      {tarefasPrincipais.map((tarefa) => (
                        <TarefaAgendaItem
                          key={tarefa.id}
                          tarefa={tarefa}
                          onAlternar={handleAlternarTarefa}
                          onEditar={(t) => {
                            setTarefaEditando(t as Tarefa);
                            setModalAberto(true);
                          }}
                          onExcluir={(t) => handleExcluirTarefa(t as Tarefa)}
                          onAlternarSubtarefa={handleAlternarSubtarefa}
                        />
                      ))}
                    </div>
                  ) : tarefasFiltradas.length === 0 ? (
                    <EmptyState
                      icone={<Filter size={24} />}
                      titulo={
                        filtro === 'concluidas'
                          ? 'Nenhuma tarefa concluída ainda'
                          : filtro === 'hoje'
                            ? 'Tudo em dia por hoje'
                            : 'Nenhuma tarefa cadastrada'
                      }
                      descricao={
                        filtro === 'concluidas'
                          ? 'Quando você concluir uma tarefa, ela aparece aqui.'
                          : filtro === 'hoje'
                            ? 'Nenhuma tarefa pendente para hoje.'
                            : 'Adicione novas tarefas acima ou crie suas próprias listas personalizadas.'
                      }
                    />
                  ) : null}
                </>
              )}

              {/* "Carregar mais": progressão explícita na listagem paginada. */}
              {paginaTarefas < totalPaginasTarefas && (
                <div className={styles.carregarMaisBtnWrapper}>
                  <Button type="button" variant="secondary" size="md" onClick={carregarMaisTarefas}>
                    Carregar mais tarefas
                  </Button>
                </div>
              )}

              {/* Concluídas colapsáveis */}
              {tarefasConcluidas.length > 0 && filtro !== 'concluidas' && (
                <div className={styles.completedSection}>
                  <button
                    type="button"
                    className={styles.completedHeader}
                    onClick={() => setMostrarConcluidas((prev) => !prev)}
                  >
                    {mostrarConcluidas ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                    <span>Concluídas</span>
                    <span className={styles.completedBadge}>{tarefasConcluidas.length}</span>
                  </button>

                  {mostrarConcluidas && (
                    <div className={styles.taskList}>
                      {tarefasConcluidas.map((tarefa) => (
                        <TarefaAgendaItem
                          key={tarefa.id}
                          tarefa={tarefa}
                          onAlternar={handleAlternarTarefa}
                          onEditar={(t) => {
                            setTarefaEditando(t as Tarefa);
                            setModalAberto(true);
                          }}
                          onExcluir={(t) => handleExcluirTarefa(t as Tarefa)}
                          onAlternarSubtarefa={handleAlternarSubtarefa}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </main>

      {/* ── MODAIS DE TAREFA E LISTA ── */}
      <TaskModal
        isOpen={modalAberto}
        onClose={() => {
          setModalAberto(false);
          setTarefaEditando(null);
        }}
        onSalvar={handleSalvarTarefa}
        tarefaParaEditar={tarefaEditando}
        listas={listas}
        listaAtivaId={listaAtivaId}
        onExcluir={(tarefa) => void handleExcluirTarefa(tarefa)}
        onToggleConclusao={(id, concluida) => void handleAlternarTarefa(id, concluida)}
      />

      <ListaModal
        isOpen={modalListaAberto}
        onClose={() => {
          setModalListaAberto(false);
          setListaEditando(null);
        }}
        onSalvar={handleSalvarLista}
        listaParaEditar={listaEditando}
        listasExistentes={listas}
      />

      <ConfiguracaoLembreteModal
        isOpen={modalPushAberto}
        onClose={() => setModalPushAberto(false)}
        onSalvo={() => toast.success('Preferência global de lembretes atualizada com sucesso!')}
      />

      <ExcluirRecorrenteModal
        isOpen={Boolean(tarefaParaExcluirRecorrente)}
        escopo={escopoExclusao}
        onChangeEscopo={setEscopoExclusao}
        onCancel={() => setTarefaParaExcluirRecorrente(null)}
        onConfirm={handleConfirmarExclusaoRecorrente}
      />
    </div>
  );
}

export default TarefasApp;
