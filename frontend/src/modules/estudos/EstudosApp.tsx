import { useState, useEffect, useMemo, useCallback } from 'react';
import { registrarFalha } from '@shared/diagnostico';
import { Navbar } from './components/Navbar/Navbar';
import { HubView, StatusCarregamento } from './views/HubView';
import { PlanejamentoView } from './views/PlanejamentoView';
import { DisciplinasView } from './views/DisciplinasView';
import { EstudarView } from './views/EstudarView';
import { DashboardView } from './views/DashboardView';
import { LandingView } from './views/LandingView';
import { CatalogoView } from './views/CatalogoView';
import { AuthView, AuthMode } from './views/AuthView';
import { TrilhaModal } from './components/TrilhaModal/TrilhaModal';
import {
  EditalResumo,
  RegistroBlocoPendente,
  TrilhaDetalheDTO,
  TrilhaResumoDTO,
  PaginaAtiva,
  UsuarioAutenticado
} from './types/trilhara';
import { EstudosService } from './services/estudosService';
import {
  AuthStorage,
  iniciarHandoffParaApp,
  sanitizarReturnUrl,
  useAuth
} from '@shared/auth';
import { ApiError } from './services/api';
import { EstudosStorage } from './services/estudosStorage';
import {
  EDITAIS_VITRINE_FALLBACK,
  importarOuClonarEdital
} from './data/catalogoVitrine';
import { PageBanner, useToast } from '@shared/design-system';
import { BarChart2, BookOpen, Calendar } from 'lucide-react';
import { StudyTimerProvider } from './context/StudyTimerContext';
import { EmptyJourneyState } from './components/EmptyJourneyState/EmptyJourneyState';
import styles from './App.module.css';

const STORAGE_PENDING_EDITAL_KEY = 'trilhara:pending_import_edital_id';

/**
 * Projeta o resumo leve a partir do detalhe devolvido por `POST /api/trilhas`, evitando um
 * refetch imediato da listagem. Não é um adaptador de contrato: os dois lados são DTOs do backend.
 */
function resumoDe(detalhe: TrilhaDetalheDTO): TrilhaResumoDTO {
  return {
    id: detalhe.id,
    nome: detalhe.nome,
    cargo: detalhe.cargo,
    banca: detalhe.banca,
    dataProva: detalhe.dataProva,
    cor: detalhe.cor,
    origemEditalId: detalhe.origemEditalId,
    totalDisciplinas: detalhe.totalDisciplinas ?? detalhe.disciplinas.length,
    totalTopicos: detalhe.totalTopicos ?? 0,
    topicosConcluidos: detalhe.topicosConcluidos ?? 0,
    percentualProgresso: detalhe.percentualProgresso ?? 0,
    cronogramaAtivoResumo: detalhe.cronogramaAtivoResumo ?? null,
    criadoEm: detalhe.criadoEm,
    atualizadoEm: detalhe.atualizadoEm
  };
}

const PAGINAS: PaginaAtiva[] = [
  'landing',
  'catalogo',
  'login',
  'cadastro',
  'hub',
  'planejamento',
  'disciplinas',
  'estudar',
  'dashboard'
];

const PAGINAS_PROTEGIDAS = new Set<PaginaAtiva>([
  'hub',
  'planejamento',
  'disciplinas',
  'estudar',
  'dashboard'
]);

function paginaNaUrl(): PaginaAtiva {
  const pagina = new URLSearchParams(window.location.search).get('pagina');
  if (pagina && PAGINAS.includes(pagina as PaginaAtiva)) {
    return pagina as PaginaAtiva;
  }

  const temSessaoPersistida = Boolean(
    AuthStorage.obterAccessToken() || AuthStorage.obterUsuario()
  );

  return temSessaoPersistida ? 'hub' : 'landing';
}

function atualizarUrlPagina(pagina: PaginaAtiva): void {
  const url = new URL(window.location.href);
  if (pagina === 'landing') {
    url.searchParams.delete('pagina');
  } else {
    url.searchParams.set('pagina', pagina);
  }
  window.history.pushState(null, '', `${url.pathname}${url.search}${url.hash}`);
}

export default function EstudosApp() {
  const toast = useToast();
  const { usuario, isAuthenticated, carregando: verificandoSessao, logout, setUsuario, abrirContaModal } = useAuth();
  const [pendingEdital, setPendingEdital] = useState<EditalResumo | null>(null);
  const [abrirModalNovoAposAuth, setAbrirModalNovoAposAuth] = useState(false);

  const [trilhas, setTrilhas] = useState<TrilhaResumoDTO[]>([]);
  const [trilhaAtivaId, setTrilhaAtivaId] = useState<string>('');
  const [trilhaAtivaDetalhe, setTrilhaAtivaDetalhe] = useState<TrilhaDetalheDTO | null>(null);
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false);
  const [paginaAtiva, setPaginaAtiva] = useState<PaginaAtiva>(paginaNaUrl);
  const [disciplinaInicialId, setDisciplinaInicialId] = useState('');
  const [blocoPlanejamentoNoCronometro, setBlocoPlanejamentoNoCronometro] =
    useState<RegistroBlocoPendente | null>(null);
  const [statusLista, setStatusLista] = useState<StatusCarregamento>('carregando');

  // Modais globais
  const [modalNovaTrilha, setModalNovaTrilha] = useState(
    () => new URLSearchParams(window.location.search).get('modal') === 'novo'
  );
  const [trilhaEmEdicao, setTrilhaEmEdicao] = useState<TrilhaResumoDTO | null>(null);

  const navegar = useCallback((pagina: PaginaAtiva) => {
    setPaginaAtiva(pagina);
    atualizarUrlPagina(pagina);
  }, []);

  const navegarParaCatalogo = useCallback(() => {
    navegar('catalogo');
  }, [navegar]);

  const abrirNovaTrilha = useCallback(() => {
    const url = new URL(window.location.href);
    url.searchParams.set('modal', 'novo');
    window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
    setModalNovaTrilha(true);
  }, []);

  const fecharNovaTrilha = useCallback(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get('modal') === 'novo') {
      url.searchParams.delete('modal');
      window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
    }
    setModalNovaTrilha(false);
  }, []);

  const carregarTrilhas = useCallback(async () => {
    if (!AuthStorage.obterAccessToken()) {
      setTrilhas([]);
      setTrilhaAtivaId('');
      setStatusLista('sucesso');
      return;
    }

    setStatusLista('carregando');
    try {
      const lista = await EstudosService.listarTrilhas();
      setTrilhas(lista);
      setStatusLista('sucesso');

      let idSalvo: string | null = null;
      try {
        idSalvo = EstudosStorage.obterTrilhaAtiva();
      } catch (err) {
        toast.error(err instanceof Error ? err : 'Não foi possível ler a trilha ativa.');
      }
      const candidata = lista.find((t) => t.id === idSalvo);
      if (candidata) {
        setTrilhaAtivaId(candidata.id);
      } else if (lista.length > 0) {
        setTrilhaAtivaId(lista[0].id);
      } else {
        setTrilhaAtivaId('');
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        void logout();
        setTrilhas([]);
        setTrilhaAtivaId('');
        return;
      }
      // Falha recuperável: a seção mostra `SectionErrorCard` com retry, nunca "sem trilhas".
      setStatusLista('erro');
      registrarFalha('estudos.listarTrilhas', err);
    }
  }, [toast, logout]);

  // Carrega trilhas sempre que o usuário estiver autenticado
  useEffect(() => {
    if (verificandoSessao) return;
    if (isAuthenticated) {
      void carregarTrilhas();
    } else {
      setTrilhas([]);
      setTrilhaAtivaId('');
    }
  }, [isAuthenticated, verificandoSessao, carregarTrilhas]);

  // Proteção de rotas: redireciona para login caso tente acessar rota interna sem autenticação
  // e envia usuários autenticados diretamente para o painel ao acessar a raiz/landing.
  useEffect(() => {
    if (verificandoSessao) return;
    if (!isAuthenticated && PAGINAS_PROTEGIDAS.has(paginaAtiva)) {
      navegar('login');
      return;
    }
    if (isAuthenticated && (paginaAtiva === 'landing' || paginaAtiva === 'login' || paginaAtiva === 'cadastro')) {
      const returnUrl = sanitizarReturnUrl(new URLSearchParams(window.location.search).get('returnUrl'));
      if (returnUrl && usuario) {
        // D-09/D-12: navegação por handoff opaco; token nunca vai na URL.
        void iniciarHandoffParaApp(returnUrl).then((destino) => {
          window.location.href = destino;
        });
        return;
      }
      navegar('hub');
    }
  }, [isAuthenticated, paginaAtiva, verificandoSessao, navegar, usuario]);

  useEffect(() => {
    function sincronizarComUrl() {
      const parametros = new URLSearchParams(window.location.search);
      setPaginaAtiva(paginaNaUrl());
      setModalNovaTrilha(parametros.get('modal') === 'novo');
    }

    window.addEventListener('popstate', sincronizarComUrl);
    return () => window.removeEventListener('popstate', sincronizarComUrl);
  }, []);

  const handleSelecionarTrilha = (id: string) => {
    setTrilhaAtivaId(id);
    try {
      EstudosStorage.salvarTrilhaAtiva(id);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar a trilha ativa.');
    }
  };

  useEffect(() => {
    if (!trilhaAtivaId) {
      setTrilhaAtivaDetalhe(null);
      return;
    }
    let active = true;
    setCarregandoDetalhe(true);
    EstudosService.buscarTrilha(trilhaAtivaId)
      .then((detalhe) => {
        if (!active) return;
        setTrilhaAtivaDetalhe(detalhe);
      })
      .catch((err) => {
        if (!active) return;
        registrarFalha('estudos.detalheTrilha', err);
      })
      .finally(() => {
        if (active) setCarregandoDetalhe(false);
      });

    return () => {
      active = false;
    };
  }, [trilhaAtivaId]);

  const trilhaResumoAtiva = useMemo<TrilhaResumoDTO | null>(() => {
    return trilhas.find((t) => t.id === trilhaAtivaId) || trilhas[0] || null;
  }, [trilhas, trilhaAtivaId]);

  /** Detalhe sob demanda: a trilha ativa é sempre o DTO completo de `GET /api/trilhas/{id}`. */
  const trilhaAtiva: TrilhaDetalheDTO | null = useMemo(() => {
    if (trilhaAtivaDetalhe && trilhaAtivaDetalhe.id === trilhaResumoAtiva?.id) {
      return trilhaAtivaDetalhe;
    }
    return null;
  }, [trilhaAtivaDetalhe, trilhaResumoAtiva]);

  const handleTrilhaAtualizada = (atualizada: TrilhaDetalheDTO) => {
    setTrilhaAtivaDetalhe(atualizada);
    setTrilhas((prev) =>
      prev.map((t) =>
        t.id === atualizada.id
          ? {
              ...t,
              nome: atualizada.nome,
              cargo: atualizada.cargo,
              banca: atualizada.banca,
              dataProva: atualizada.dataProva,
              cor: atualizada.cor,
              totalDisciplinas:
                atualizada.totalDisciplinas ?? atualizada.disciplinas.length,
              totalTopicos: atualizada.totalTopicos ?? t.totalTopicos,
              topicosConcluidos:
                atualizada.topicosConcluidos ?? t.topicosConcluidos,
              percentualProgresso:
                atualizada.percentualProgresso ?? t.percentualProgresso,
              cronogramaAtivoResumo:
                atualizada.cronogramaAtivoResumo ?? t.cronogramaAtivoResumo
            }
          : t
      )
    );
    setTrilhaEmEdicao((atual) =>
      atual?.id === atualizada.id
        ? {
            ...atual,
            nome: atualizada.nome,
            cargo: atualizada.cargo,
            banca: atualizada.banca,
            dataProva: atualizada.dataProva
          }
        : atual
    );
  };

  /**
   * R5/D-19: "Estudar este bloco" inicia o timer global com o `blocoId` do cronograma.
   * A conclusão do bloco deixa de passar por estado local de planejamento — quem conclui é o
   * `GlobalSessionModal` (PATCH idempotente), então não há mais revisão/rollback local.
   */
  const iniciarCronometroPlanejado = (
    disciplinaId: string,
    registro: RegistroBlocoPendente
  ) => {
    setDisciplinaInicialId(disciplinaId);
    setBlocoPlanejamentoNoCronometro(registro);
    navegar('estudar');
  };


  const handleExcluirTrilha = async (id: string): Promise<boolean> => {
    try {
      await EstudosService.excluirTrilha(id);
      setTrilhas((prev) => prev.filter((t) => t.id !== id));
      if (trilhaAtivaId === id) {
        const restantes = trilhas.filter((t) => t.id !== id);
        if (restantes.length > 0) {
          handleSelecionarTrilha(restantes[0].id);
        } else {
          setTrilhaAtivaId('');
          navegar('hub');
        }
      }
      toast.success('Trilha excluída com sucesso.');
      return true;
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Erro ao excluir trilha.');
      return false;
    }
  };

  const handleTrilhaCriada = (nova: TrilhaDetalheDTO) => {
    fecharNovaTrilha();
    setTrilhaAtivaDetalhe(nova);
    setTrilhas((prev) => [resumoDe(nova), ...prev]);
    handleSelecionarTrilha(nova.id);
    navegar('hub');
  };

  const handleTrilhaImportada = (importada: TrilhaDetalheDTO) => {
    setTrilhaAtivaDetalhe(importada);
    setTrilhas((prev) => [resumoDe(importada), ...prev]);
    handleSelecionarTrilha(importada.id);
    navegar('hub');
  };

  const handleEntrarOuLogin = useCallback(() => {
    if (isAuthenticated) {
      navegar('hub');
    } else {
      navegar('login');
    }
  }, [isAuthenticated, navegar]);

  const handleIniciarCadastroOuNovaTrilha = useCallback(
    (editalSelecionado?: EditalResumo) => {
      if (editalSelecionado) {
        setPendingEdital(editalSelecionado);
      }
      if (isAuthenticated) {
        navegar('hub');
        abrirNovaTrilha();
      } else {
        navegar('cadastro');
      }
    },
    [isAuthenticated, navegar, abrirNovaTrilha]
  );

  const handleAutenticadoComSucesso = async (
    usuarioLogado: UsuarioAutenticado,
    _modo: AuthMode
  ) => {
    setUsuario(usuarioLogado);

    const returnUrl = sanitizarReturnUrl(new URLSearchParams(window.location.search).get('returnUrl'));
    if (returnUrl) {
      const destino = await iniciarHandoffParaApp(returnUrl);
      window.location.href = destino;
      return;
    }

    let editalParaImportar: EditalResumo | null = pendingEdital;
    let idPendente: string | null = null;
    if (!editalParaImportar) {
      try {
        idPendente = window.sessionStorage.getItem(STORAGE_PENDING_EDITAL_KEY);
      } catch {
        // Storage indisponível
      }
      if (idPendente) {
        editalParaImportar = EDITAIS_VITRINE_FALLBACK.find((e) => e.id === idPendente) ?? null;
      }
    }

    try {
      window.sessionStorage.removeItem(STORAGE_PENDING_EDITAL_KEY);
    } catch {
      // Storage indisponível
    }
    setPendingEdital(null);

    if (editalParaImportar || idPendente) {
      try {
        const importada = editalParaImportar
          ? await importarOuClonarEdital(editalParaImportar)
          : await EstudosService.importarEdital(idPendente as string);
        EstudosStorage.salvarTrilhaAtiva(importada.id);
        toast.success(`Trilha "${importada.nome}" importada para sua conta!`);
        await carregarTrilhas();
        setTrilhaAtivaId(importada.id);
        navegar('hub');
        return;
      } catch (err) {
        toast.error(err instanceof Error ? err : 'Não foi possível importar a trilha selecionada.');
      }
    }

    await carregarTrilhas();
    navegar('hub');

    if (abrirModalNovoAposAuth) {
      setAbrirModalNovoAposAuth(false);
      abrirNovaTrilha();
    }
  };

  const handleLogout = async () => {
    await logout();
    setTrilhas([]);
    setTrilhaAtivaId('');
    toast.info('Você saiu da sua conta.');
    navegar('landing');
  };

  if (paginaAtiva === 'landing') {
    return (
      <LandingView
        isAuthenticated={isAuthenticated}
        usuarioNome={usuario?.nome}
        onEntrarApp={handleEntrarOuLogin}
        onLogin={() => navegar('login')}
        onAbrirCatalogo={() => navegar('catalogo')}
        onCriarConta={handleIniciarCadastroOuNovaTrilha}
        onTrilhaImportada={handleTrilhaImportada}
      />
    );
  }

  if (paginaAtiva === 'catalogo') {
    return (
      <CatalogoView
        isAuthenticated={isAuthenticated}
        totalMinhasTrilhas={trilhas.length}
        onVoltarLanding={() => navegar('landing')}
        onIrParaPainel={handleEntrarOuLogin}
        onAbrirNovaTrilha={() => {
          if (isAuthenticated) {
            navegar('hub');
            abrirNovaTrilha();
          } else {
            setAbrirModalNovoAposAuth(true);
            navegar('cadastro');
          }
        }}
        onLogin={() => navegar('login')}
        onCriarConta={() => navegar('cadastro')}
        onRichAuth={(edital) => {
          setPendingEdital(edital);
          navegar('cadastro');
        }}
        onTrilhaImportada={handleTrilhaImportada}
      />
    );
  }

  if (paginaAtiva === 'login' || paginaAtiva === 'cadastro' || !isAuthenticated) {
    if (verificandoSessao) {
      return (
        <div className={styles.loading}>
          Verificando sua sessão...
        </div>
      );
    }

    const modoAuth: AuthMode = paginaAtiva === 'cadastro' ? 'cadastro' : 'login';
    return (
      <AuthView
        mode={modoAuth}
        onAlternarModo={(novoModo) => {
          if (novoModo === 'login' || novoModo === 'cadastro') {
            navegar(novoModo);
          }
        }}
        onAutenticado={(u, m) => void handleAutenticadoComSucesso(u, m)}
      />
    );
  }

  return (
    <StudyTimerProvider>
    <div className={styles.shell}>
      {/* Painel Lateral (Desktop) & Navegação Embutida (Mobile) */}
      <Navbar
        trilhas={trilhas}
        trilhaAtiva={trilhaAtiva}
        paginaAtiva={paginaAtiva}
        usuario={usuario}
        onNavegar={navegar}
        onSelecionarTrilha={handleSelecionarTrilha}
        onAbrirEditarTrilha={() => {
          if (trilhaAtiva) setTrilhaEmEdicao(resumoDe(trilhaAtiva));
        }}
        onAbrirNovaTrilha={abrirNovaTrilha}
        onLogout={() => void handleLogout()}
        onAbrirConta={abrirContaModal}
      />

      {/* Conteúdo da Página */}
      <main className={styles.main}>
        {statusLista === 'carregando' || (carregandoDetalhe && paginaAtiva !== 'hub' && !trilhaAtivaDetalhe) ? (
          <div className={styles.loading}>
            Carregando trilha e assuntos...
          </div>
        ) : (
          <>
            {paginaAtiva === 'hub' && (
              <HubView
                trilhas={trilhas}
                status={statusLista}
                onRecarregar={() => void carregarTrilhas()}
                trilhaAtivaId={trilhaAtivaId}
                onSelecionarTrilha={handleSelecionarTrilha}
                onNavegar={navegar}
                onAbrirNovaTrilha={abrirNovaTrilha}
                onEditarTrilha={(trilha) => {
                  handleSelecionarTrilha(trilha.id);
                  setTrilhaEmEdicao(trilha);
                }}
              />
            )}

            {paginaAtiva === 'planejamento' && trilhaAtiva && (
              <PlanejamentoView
                key={trilhaAtiva.id}
                trilha={trilhaAtiva}
                onTrilhaAtualizada={handleTrilhaAtualizada}
                onIniciarEstudo={iniciarCronometroPlanejado}
                onIrParaDisciplinas={() => navegar('disciplinas')}
              />
            )}


            {paginaAtiva === 'disciplinas' && trilhaAtiva && (
              <DisciplinasView
                key={trilhaAtiva.id}
                trilha={trilhaAtiva}
                onTrilhaAtualizada={handleTrilhaAtualizada}
              />
            )}

            {paginaAtiva === 'estudar' && (
              <EstudarView
                key="estudar-view"
                trilha={trilhaAtiva}
                disciplinaInicialId={disciplinaInicialId}
                contextoBlocoPlanejamento={blocoPlanejamentoNoCronometro}
                onTrilhaCriada={(nova) => {
                  setTrilhaAtivaDetalhe(nova);
                  setTrilhas((prev) => [resumoDe(nova), ...prev]);
                  handleSelecionarTrilha(nova.id);
                }}
                onTrilhaAtualizada={handleTrilhaAtualizada}
                onSessaoRegistrada={() => {
                  // Pode recarregar dados se necessário
                }}
              />
            )}

            {paginaAtiva === 'dashboard' && trilhaAtiva && (
              <DashboardView
                key={trilhaAtiva.id}
                trilha={trilhaAtiva}
                onIrParaDisciplinas={() => navegar('disciplinas')}
              />
            )}

            {/* Se estiver em uma tela interna mas não houver trilhas criadas, o banner da página nunca deve ser ocultado */}
            {paginaAtiva !== 'hub' && paginaAtiva !== 'estudar' && !trilhaAtiva && (
              <div className={styles.emptyPageContainer}>
                {paginaAtiva === 'planejamento' && (
                  <PageBanner
                    title="Planejamento de Cronogramas"
                    description="Gere cronogramas por modalidade e acompanhe os blocos gerados."
                    icon={<Calendar size={26} />}
                  />
                )}
                {paginaAtiva === 'disciplinas' && (
                  <PageBanner
                    title="Disciplinas & Assuntos do Edital"
                    description="Acompanhe a cobertura do edital e organize as matérias da sua trilha."
                    icon={<BookOpen size={26} />}
                  />
                )}
                {paginaAtiva === 'dashboard' && (
                  <PageBanner
                    title="Dashboard & Desempenho"
                    description="Acompanhe resultados, carga de estudo e evolução por disciplina."
                    icon={<BarChart2 size={26} />}
                  />
                )}
                <EmptyJourneyState
                  hasTrilha={false}
                  onPrimaryAction={abrirNovaTrilha}
                  onViewCatalog={navegarParaCatalogo}
                />
              </div>
            )}
          </>
        )}
      </main>

      <TrilhaModal
        isOpen={modalNovaTrilha || Boolean(trilhaEmEdicao)}
        trilha={trilhaEmEdicao}
        onClose={() => {
          if (modalNovaTrilha) fecharNovaTrilha();
          if (trilhaEmEdicao) setTrilhaEmEdicao(null);
        }}
        onTrilhaSalva={(trilhaSalva, modo) => {
          if (modo === 'criada') {
            handleTrilhaCriada(trilhaSalva);
          } else {
            handleTrilhaAtualizada(trilhaSalva);
            setTrilhaEmEdicao(null);
          }
        }}
        onExcluirTrilha={handleExcluirTrilha}
      />

    </div>
    </StudyTimerProvider>
  );
}

