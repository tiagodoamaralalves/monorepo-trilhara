import { useState, useEffect, useMemo, useCallback } from 'react';
import { Navbar } from './components/Navbar/Navbar';
import { HubView } from './views/HubView';
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
  RegistroPlanejamentoPendente,
  Trilha,
  PaginaAtiva,
  UsuarioAutenticado
} from './types/trilhara';
import { EstudosService } from './services/estudosService';
import {
  AuthStorage,
  gerarUrlComTokenHandoff,
  sanitizarReturnUrl,
  useAuth
} from '@shared/auth';
import { ApiError } from './services/api';
import { EstudosStorage } from './services/estudosStorage';
import {
  avancarSobreBlocosConcluidos,
  PlanejamentoStorage
} from './services/planejamentoStorage';
import {
  EDITAIS_VITRINE_FALLBACK,
  importarOuClonarEdital
} from './data/catalogoVitrine';
import { Button, PageBanner, useToast } from '@shared/design-system';
import { BarChart2, BookOpen, Calendar } from 'lucide-react';
import { StudyTimerProvider } from './context/StudyTimerContext';
import { EmptyJourneyState } from './components/EmptyJourneyState/EmptyJourneyState';
import styles from './App.module.css';

const STORAGE_PENDING_EDITAL_KEY = 'trilhara:pending_import_edital_id';

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

  const [trilhas, setTrilhas] = useState<Trilha[]>([]);
  const [trilhaAtivaId, setTrilhaAtivaId] = useState<string>('');
  const [trilhaAtivaDetalhe, setTrilhaAtivaDetalhe] = useState<Trilha | null>(null);
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false);
  const [paginaAtiva, setPaginaAtiva] = useState<PaginaAtiva>(paginaNaUrl);
  const [disciplinaInicialId, setDisciplinaInicialId] = useState('');
  const [registroPlanejamento, setRegistroPlanejamento] =
    useState<RegistroPlanejamentoPendente | null>(null);
  const [blocoPlanejamentoNoCronometro, setBlocoPlanejamentoNoCronometro] =
    useState<RegistroPlanejamentoPendente | null>(null);
  const [registroPlanejamentoRevision, setRegistroPlanejamentoRevision] = useState(0);
  const [carregando, setCarregando] = useState(false);
  const [erroCarregamento, setErroCarregamento] = useState<string | null>(null);

  // Modais globais
  const [modalNovaTrilha, setModalNovaTrilha] = useState(
    () => new URLSearchParams(window.location.search).get('modal') === 'novo'
  );
  const [trilhaEmEdicao, setTrilhaEmEdicao] = useState<Trilha | null>(null);

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
      setCarregando(false);
      return;
    }

    setCarregando(true);
    setErroCarregamento(null);
    try {
      const lista = await EstudosService.listarTrilhas();
      setTrilhas(lista);

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
      const mensagem = err instanceof Error ? err.message : 'Falha ao carregar trilhas.';
      setErroCarregamento(mensagem);
      toast.error(mensagem);
    } finally {
      setCarregando(false);
    }
  }, [toast]);

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
        const accessToken = AuthStorage.obterAccessToken();
        if (accessToken) {
          const urlComToken = gerarUrlComTokenHandoff(returnUrl, {
            accessToken,
            usuario
          });
          window.location.href = urlComToken;
          return;
        }
      }
      navegar('hub');
    }
  }, [isAuthenticated, paginaAtiva, verificandoSessao, navegar]);

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
        console.error('Falha ao carregar detalhe da trilha:', err);
      })
      .finally(() => {
        if (active) setCarregandoDetalhe(false);
      });

    return () => {
      active = false;
    };
  }, [trilhaAtivaId]);

  const trilhaResumoAtiva = useMemo(() => {
    return trilhas.find((t) => t.id === trilhaAtivaId) || trilhas[0] || null;
  }, [trilhas, trilhaAtivaId]);

  const trilhaAtiva: Trilha | null = useMemo(() => {
    if (trilhaAtivaDetalhe && trilhaAtivaDetalhe.id === trilhaResumoAtiva?.id) {
      return trilhaAtivaDetalhe;
    }
    return trilhaResumoAtiva;
  }, [trilhaAtivaDetalhe, trilhaResumoAtiva]);

  const handleTrilhaAtualizada = (atualizada: Trilha) => {
    setTrilhaAtivaDetalhe(atualizada);
    setTrilhas((prev) =>
      prev.map((t) =>
        t.id === atualizada.id
          ? {
              ...t,
              ...atualizada,
              totalDisciplinas: atualizada.disciplinas?.length ?? t.totalDisciplinas,
              totalTopicos:
                atualizada.disciplinas?.reduce((acc, d) => acc + (d.topicos?.length || 0), 0) ??
                t.totalTopicos,
              topicosConcluidos:
                atualizada.topicosConcluidosIds?.length ??
                Object.values(atualizada.progresso || {}).filter((p) => p.done).length
            }
          : t
      )
    );
    setTrilhaEmEdicao((atual) => (atual?.id === atualizada.id ? atualizada : atual));
  };

  const iniciarRegistroPlanejado = (registro: RegistroPlanejamentoPendente) => {
    setRegistroPlanejamento(registro);
  };

  const iniciarCronometroPlanejado = (
    disciplinaId: string,
    registro: RegistroPlanejamentoPendente
  ) => {
    setDisciplinaInicialId(disciplinaId);
    setBlocoPlanejamentoNoCronometro(registro);
    navegar('estudar');
  };

  const finalizarRegistroPlanejado = async (registro: RegistroPlanejamentoPendente) => {
    const salvarComRevisao = async (tentativa = 1): Promise<void> => {
      const estado = await PlanejamentoStorage.carregar(registro.trilhaId, registro.cicloId);
      const blocosConcluidos = registro.ocorrenciaId
        ? [...new Set([...estado.blocosConcluidos, registro.ocorrenciaId])]
        : estado.blocosConcluidos;
      const indiceAtual = avancarSobreBlocosConcluidos(
        estado,
        estado.indiceRastreador
      );
      const proximoIndiceRastreador = registro.proximoIndiceRastreador
        ?? (registro.indiceRotacao === indiceAtual
          ? indiceAtual + 1
          : indiceAtual);
      const estadoComConclusao = {
        ...estado,
        blocosConcluidos,
        indiceRastreador: avancarSobreBlocosConcluidos(
          { ...estado, blocosConcluidos },
          Math.max(indiceAtual, proximoIndiceRastreador)
        )
      };

      try {
        const salvo = await EstudosService.salvarPlanejamentoCiclo(
          registro.trilhaId,
          registro.cicloId,
          {
            revisaoEsperada: estado.revisao ?? 0,
            indiceRastreador: estadoComConclusao.indiceRastreador,
            blocosConcluidos: estadoComConclusao.blocosConcluidos,
            cronograma: estadoComConclusao.cronograma,
            cronogramaConfigurado: estadoComConclusao.cronogramaConfigurado ?? false
          }
        );
        try {
          PlanejamentoStorage.salvar(registro.trilhaId, registro.cicloId, {
            indiceRastreador: salvo.indiceRastreador,
            blocosConcluidos: salvo.blocosConcluidos,
            cronograma: salvo.cronograma,
            cronogramaConfigurado: salvo.cronogramaConfigurado,
            revisao: salvo.revisao,
            desatualizado: salvo.desatualizado
          });
        } catch (erroCache) {
          toast.error(
            erroCache instanceof Error
              ? `Bloco atualizado no servidor, mas o cache local falhou: ${erroCache.message}`
              : 'Bloco atualizado no servidor, mas o cache local falhou.'
          );
        }
        setRegistroPlanejamentoRevision((revision) => revision + 1);
      } catch (err) {
        if (err instanceof ApiError && err.status === 409 && tentativa < 3) {
          // Recarrega silenciosamente a revisão mais recente e reexecuta o patch do bloco concluído sem perder a sessão
          await salvarComRevisao(tentativa + 1);
          return;
        }
        throw err;
      }
    };

    try {
      await salvarComRevisao();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        try {
          await PlanejamentoStorage.carregar(registro.trilhaId, registro.cicloId);
          setRegistroPlanejamentoRevision((revision) => revision + 1);
          toast.error('A sessão foi salva, mas o planejamento mudou em outro dispositivo. Os dados mais recentes foram recarregados.');
        } catch (erroRemoto) {
          toast.error(
            erroRemoto instanceof Error
              ? `Sessão salva, mas o bloco não pôde ser atualizado: ${erroRemoto.message}`
              : 'Sessão salva, mas o bloco não pôde ser atualizado.'
          );
        }
      } else {
        toast.error(
          err instanceof Error
            ? `Sessão salva, mas o bloco não pôde ser atualizado: ${err.message}`
            : 'Sessão salva, mas o bloco não pôde ser atualizado.'
        );
      }
    } finally {
      setRegistroPlanejamento((atual) =>
        atual?.requestId === registro.requestId ? null : atual
      );
      setBlocoPlanejamentoNoCronometro((atual) =>
        atual?.requestId === registro.requestId ? null : atual
      );
    }
  };

  const cancelarRegistroPlanejado = (requestId: string) => {
    setRegistroPlanejamento((atual) => atual?.requestId === requestId ? null : atual);
    setBlocoPlanejamentoNoCronometro((atual) =>
      atual?.requestId === requestId ? null : atual
    );
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

  const handleTrilhaCriada = (nova: Trilha) => {
    fecharNovaTrilha();
    setTrilhas((prev) => [nova, ...prev]);
    handleSelecionarTrilha(nova.id);
    navegar('hub');
  };

  const handleTrilhaImportada = (importada: Trilha) => {
    setTrilhas((prev) => [importada, ...prev]);
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
      const accessToken = AuthStorage.obterAccessToken();
      if (accessToken) {
        const urlComToken = gerarUrlComTokenHandoff(returnUrl, {
          accessToken,
          usuario: usuarioLogado
        });
        window.location.href = urlComToken;
        return;
      }
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
          if (trilhaAtiva) setTrilhaEmEdicao(trilhaAtiva);
        }}
        onAbrirNovaTrilha={abrirNovaTrilha}
        onLogout={() => void handleLogout()}
        onAbrirConta={abrirContaModal}
      />

      {/* Conteúdo da Página */}
      <main className={styles.main}>
        {carregando || (carregandoDetalhe && paginaAtiva !== 'hub' && !trilhaAtivaDetalhe) ? (
          <div className={styles.loading}>
            Carregando trilha e assuntos...
          </div>
        ) : erroCarregamento ? (
          <div className={styles.loadError} role="alert">
            <p>Não foi possível carregar suas trilhas: {erroCarregamento}</p>
            <Button type="button" variant="secondary" onClick={() => void carregarTrilhas()}>
              Tentar novamente
            </Button>
          </div>
        ) : (
          <>
            {paginaAtiva === 'hub' && (
              <HubView
                trilhas={trilhas}
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
                onRegistrarBloco={iniciarRegistroPlanejado}
                registroPlanejamentoRevision={registroPlanejamentoRevision}
                onIniciarEstudo={iniciarCronometroPlanejado}
                onIrParaDisciplinas={() => navegar('disciplinas')}
              />
            )}

            {paginaAtiva === 'planejamento' && registroPlanejamento && (
              <EstudarView
                key={`registro-${registroPlanejamento.requestId}`}
                trilha={trilhaAtiva}
                disciplinaInicialId={registroPlanejamento.disciplinaId}
                registroPlanejamento={registroPlanejamento}
                onRegistroPlanejamentoConcluido={finalizarRegistroPlanejado}
                onCancelarRegistroPlanejamento={cancelarRegistroPlanejado}
                somenteModalRegistro
                onTrilhaCriada={(nova) => {
                  setTrilhas((prev) => [nova, ...prev]);
                  handleSelecionarTrilha(nova.id);
                }}
                onTrilhaAtualizada={handleTrilhaAtualizada}
                onSessaoRegistrada={() => {
                  // Pode recarregar dados se necessário
                }}
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
                registroPlanejamento={registroPlanejamento}
                contextoBlocoPlanejamento={blocoPlanejamentoNoCronometro}
                onRegistroPlanejamentoConcluido={finalizarRegistroPlanejado}
                onCancelarRegistroPlanejamento={cancelarRegistroPlanejado}
                onTrilhaCriada={(nova) => {
                  setTrilhas((prev) => [nova, ...prev]);
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
                    title="Planejamento & Ciclos de Estudo"
                    description="Configure seus ciclos, acompanhe a rotação e organize seus blocos no calendário."
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

