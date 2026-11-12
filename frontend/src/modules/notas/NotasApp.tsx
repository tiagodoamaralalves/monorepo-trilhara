import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { BookMarked, ChevronLeft, FileText, Folder, Plus } from 'lucide-react';
import {
  Button,
  EmptyState,
  useConfirm,
  useToast
} from '@shared/design-system';
import {
  useAuth,
  AuthView,
  request
} from '@shared/auth';

import { Caderno, Secao, Nota, NotaMetadata } from './types/nota';
import { NotaService } from './services/notaService';
import { LandingView } from './views/LandingView';
import { Navbar } from './components/Navbar/Navbar';
import { ListaPaginas } from './components/ListaPaginas/ListaPaginas';
import { TiptapEditor } from './components/Editor/TiptapEditor';
import { CadernoModal } from './components/CadernoModal/CadernoModal';
import { SecaoModal } from './components/SecaoModal/SecaoModal';
import { NotaModal } from './components/NotaModal/NotaModal';
import { ImportarTrilhaModal, TrilhaItem } from './components/ImportarTrilhaModal/ImportarTrilhaModal';
import { LixeiraModal } from './components/LixeiraModal/LixeiraModal';
import { BuscaModal } from './components/BuscaModal/BuscaModal';
import styles from './App.module.css';

type AuthScreen = 'none' | 'login' | 'cadastro' | 'recuperar';

function obterTelaAuthInicial(): AuthScreen {
  if (typeof window === 'undefined') return 'none';
  const p = new URLSearchParams(window.location.search).get('pagina');
  if (p === 'login' || p === 'cadastro' || p === 'recuperar') return p;
  const a = new URLSearchParams(window.location.search).get('auth');
  if (a === 'login' || a === 'cadastro' || a === 'recuperar') return a;
  return 'none';
}

function NotasApp() {
  const toast = useToast();
  const { confirm } = useConfirm();
  const { usuario, isAuthenticated, carregando, logout, abrirContaModal } = useAuth();
  const [telaAuth, setTelaAuth] = useState<AuthScreen>(obterTelaAuthInicial);

  // Estados de dados da arquitetura de 3 níveis: Caderno -> Seção -> Nota/Página
  const [cadernos, setCadernos] = useState<Caderno[]>([]);
  const [cadernoAtivoId, setCadernoAtivoId] = useState<string>('');
  const [secoes, setSecoes] = useState<Secao[]>([]);
  const [secaoAtivaId, setSecaoAtivaId] = useState<string>('');
  const [notasMetadados, setNotasMetadados] = useState<NotaMetadata[]>([]);
  const [notaAtivaId, setNotaAtivaId] = useState<string>('');
  const [notaAtivaDetalhe, setNotaAtivaDetalhe] = useState<Nota | null>(null);

  // Modais de Criação / Edição
  const [modalCadernoAberto, setModalCadernoAberto] = useState(false);
  const [cadernoParaEditar, setCadernoParaEditar] = useState<Caderno | null>(null);

  const [modalSecaoAberto, setModalSecaoAberto] = useState(false);
  const [secaoParaEditar, setSecaoParaEditar] = useState<Secao | null>(null);

  const [modalNotaAberto, setModalNotaAberto] = useState(false);
  const [notaParaEditar, setNotaParaEditar] = useState<NotaMetadata | null>(null);

  // Modais Fase 8: Importar Trilha, Lixeira, Busca Global
  const [modalImportarTrilhaAberto, setModalImportarTrilhaAberto] = useState(false);
  const [trilhasDisponiveis, setTrilhasDisponiveis] = useState<TrilhaItem[]>([]);
  const [modalLixeiraAberto, setModalLixeiraAberto] = useState(false);
  const [modalBuscaAberto, setModalBuscaAberto] = useState(false);

  // Estado de recolhimento da sidebar
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem('trilhara_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  // Controle de alternância em telas móveis (< 960px): Lista de Páginas vs Editor Full Canvas
  const [mobileVisao, setMobileVisao] = useState<'lista' | 'editor'>('lista');

  const reqNotaIdRef = useRef<string | null>(null);

  const alternarTelaAuth = (modo: AuthScreen) => {
    setTelaAuth(modo);
    const url = new URL(window.location.href);
    if (modo === 'none') {
      url.searchParams.delete('pagina');
      url.searchParams.delete('auth');
    } else {
      url.searchParams.set('pagina', modo);
    }
    window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
  };

  // 1. Carrega lista de cadernos ao autenticar
  const carregarCadernos = useCallback(async () => {
    try {
      const lista = await NotaService.listarCadernos();
      setCadernos(lista);
      if (lista.length > 0) {
        setCadernoAtivoId((prev) => {
          const existe = lista.some((c) => c.id === prev);
          return existe ? prev : lista[0].id;
        });
      } else {
        setCadernoAtivoId('');
        setSecoes([]);
        setSecaoAtivaId('');
        setNotasMetadados([]);
        setNotaAtivaId('');
        setNotaAtivaDetalhe(null);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao buscar cadernos');
    }
  }, [toast]);

  // 2. Carrega seções quando o caderno ativo muda
  const carregarSecoes = useCallback(async (cadId: string) => {
    if (!cadId) {
      setSecoes([]);
      setSecaoAtivaId('');
      setNotasMetadados([]);
      setNotaAtivaId('');
      setNotaAtivaDetalhe(null);
      return;
    }
    try {
      const listaSecoes = await NotaService.listarSecoes(cadId);
      setSecoes(listaSecoes);
      if (listaSecoes.length > 0) {
        setSecaoAtivaId((prev) => {
          const existe = listaSecoes.some((s) => s.id === prev);
          return existe ? prev : listaSecoes[0].id;
        });
      } else {
        setSecaoAtivaId('');
        setNotasMetadados([]);
        setNotaAtivaId('');
        setNotaAtivaDetalhe(null);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao buscar seções do caderno');
    }
  }, [toast]);

  // 3. Carrega lista de metadados das notas quando a seção ativa muda (projeção leve sem markdown)
  const carregarNotasMetadados = useCallback(async (cadId: string, secId: string) => {
    if (!cadId || !secId) {
      setNotasMetadados([]);
      setNotaAtivaId('');
      setNotaAtivaDetalhe(null);
      return;
    }
    try {
      const lista = await NotaService.listarNotasPorSecao(cadId, secId);
      setNotasMetadados(lista);
      if (lista.length > 0) {
        setNotaAtivaId((prev) => {
          const existe = lista.some((n) => n.id === prev);
          return existe ? prev : lista[0].id;
        });
      } else {
        setNotaAtivaId('');
        setNotaAtivaDetalhe(null);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao buscar páginas da seção');
    }
  }, [toast]);

  // 4. Carrega o markdown completo sob demanda apenas para a nota ativa
  const carregarNotaDetalhe = useCallback(async (idNota: string) => {
    if (!idNota) {
      setNotaAtivaDetalhe(null);
      return;
    }
    reqNotaIdRef.current = idNota;
    try {
      const detalhe = await NotaService.buscarNota(idNota);
      if (reqNotaIdRef.current === idNota) {
        setNotaAtivaDetalhe(detalhe);
      }
    } catch (err: unknown) {
      if (reqNotaIdRef.current === idNota) {
        toast.error(err instanceof Error ? err.message : 'Falha ao carregar conteúdo da página');
      }
    }
  }, [toast]);

  useEffect(() => {
    if (isAuthenticated) {
      carregarCadernos();
    }
  }, [isAuthenticated, carregarCadernos]);

  useEffect(() => {
    if (isAuthenticated && cadernoAtivoId) {
      carregarSecoes(cadernoAtivoId);
    }
  }, [isAuthenticated, cadernoAtivoId, carregarSecoes]);

  useEffect(() => {
    if (isAuthenticated && cadernoAtivoId && secaoAtivaId) {
      carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
    }
  }, [isAuthenticated, cadernoAtivoId, secaoAtivaId, carregarNotasMetadados]);

  useEffect(() => {
    if (isAuthenticated && notaAtivaId) {
      carregarNotaDetalhe(notaAtivaId);
    } else {
      setNotaAtivaDetalhe(null);
    }
  }, [isAuthenticated, notaAtivaId, carregarNotaDetalhe]);

  // 5. Consulta em segundo plano /api/trilhas para sugestão automática de importação
  useEffect(() => {
    if (!isAuthenticated || !usuario?.id) return;

    const ocultarModal = window.localStorage.getItem(`trilhara:u:${usuario.id}:notas_hide_auto_import_modal`) === 'true';

    request<TrilhaItem[]>('/trilhas')
      .then((trilhas) => {
        if (!trilhas) return;
        setTrilhasDisponiveis(trilhas);

        if (!ocultarModal && trilhas.length > 0) {
          const existeTrilhaNaoImportada = trilhas.some(
            (t) => !cadernos.some((c) => c.origemTrilhaId === t.id && !c.deletedAt)
          );
          if (existeTrilhaNaoImportada) {
            setModalImportarTrilhaAberto(true);
          }
        }
      })
      .catch(() => {
        // Ignora silenciosamente se o endpoint de trilhas falhar
      });
  }, [isAuthenticated, usuario?.id, cadernos]);

  // Referências do Caderno, Seção e Nota ativas
  const cadernoAtivo = useMemo(
    () => cadernos.find((c) => c.id === cadernoAtivoId) || null,
    [cadernos, cadernoAtivoId]
  );

  const secaoAtiva = useMemo(
    () => secoes.find((s) => s.id === secaoAtivaId) || null,
    [secoes, secaoAtivaId]
  );

  // ── AÇÕES DE CADERNO (Nível 1) ──

  const handleSalvarCaderno = async (dados: { titulo: string; cor?: string; icone?: string }) => {
    try {
      if (cadernoParaEditar) {
        const atualizado = await NotaService.atualizarCaderno(cadernoParaEditar.id, dados);
        setCadernos((prev) => prev.map((c) => (c.id === atualizado.id ? atualizado : c)));
        toast.success(`O caderno "${atualizado.titulo}" foi atualizado.`);
      } else {
        const novo = await NotaService.criarCaderno(dados);
        setCadernos((prev) => [novo, ...prev]);
        setCadernoAtivoId(novo.id);
        toast.success(`O caderno "${novo.titulo}" foi criado com sucesso.`);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível salvar o caderno.');
      throw err;
    }
  };

  const handleExcluirCaderno = async () => {
    if (!cadernoAtivo) return;

    const totalSec = cadernoAtivo.totalSecoes ?? secoes.length;
    const totalNot = cadernoAtivo.totalNotas ?? 0;

    const confirmado = await confirm({
      title: 'Mover Caderno para Lixeira',
      description: `Tem certeza de que deseja mover o caderno "${cadernoAtivo.titulo}" para a lixeira? Serão afetadas ${totalSec} seção(ões) e ${totalNot} página(s). Os itens permanecerão na lixeira por até 30 dias.`,
      confirmText: 'Mover para Lixeira',
      variant: 'danger'
    });

    if (!confirmado) return;

    try {
      await NotaService.excluirCaderno(cadernoAtivo.id);
      toast.success(`O caderno "${cadernoAtivo.titulo}" foi movido para a lixeira.`);
      await carregarCadernos();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir o caderno.');
    }
  };

  // ── AÇÕES DE SEÇÃO (Nível 2) ──

  const handleSalvarSecao = async (dados: { titulo: string; cor?: string }) => {
    if (!cadernoAtivoId) return;

    try {
      if (secaoParaEditar) {
        const atualizada = await NotaService.atualizarSecao(cadernoAtivoId, secaoParaEditar.id, dados);
        setSecoes((prev) => prev.map((s) => (s.id === atualizada.id ? atualizada : s)));
        toast.success(`A seção "${atualizada.titulo}" foi atualizada.`);
      } else {
        const nova = await NotaService.criarSecao(cadernoAtivoId, dados);
        setSecoes((prev) => [...prev, nova]);
        setSecaoAtivaId(nova.id);
        toast.success(`A seção "${nova.titulo}" foi criada.`);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível salvar a seção.');
      throw err;
    }
  };

  const handleExcluirSecao = async (secao: Secao) => {
    if (!cadernoAtivoId) return;

    const totalNot = secao.totalNotas ?? 0;

    const confirmado = await confirm({
      title: 'Mover Seção para Lixeira',
      description: `Deseja realmente mover a seção "${secao.titulo}" para a lixeira? ${totalNot > 0 ? `Todas as ${totalNot} páginas desta seção também serão movidas.` : ''} Os itens permanecerão na lixeira por até 30 dias.`,
      confirmText: 'Mover para Lixeira',
      variant: 'danger'
    });

    if (!confirmado) return;

    try {
      await NotaService.excluirSecao(cadernoAtivoId, secao.id);
      toast.success(`A seção "${secao.titulo}" foi movida para a lixeira.`);
      await carregarSecoes(cadernoAtivoId);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir a seção.');
    }
  };

  // ── AÇÕES DE NOTA / PÁGINA (Nível 3) ──

  const handleSalvarNota = async (titulo: string) => {
    if (!cadernoAtivoId || !secaoAtivaId) return;

    try {
      if (notaParaEditar) {
        const atualizada = await NotaService.atualizarNota(notaParaEditar.id, { titulo });
        setNotasMetadados((prev) => prev.map((n) => (n.id === atualizada.id ? atualizada : n)));
        if (notaAtivaDetalhe && notaAtivaDetalhe.id === atualizada.id) {
          setNotaAtivaDetalhe(atualizada);
        }
        toast.success(`A página foi renomeada para "${atualizada.titulo}".`);
      } else {
        const nova = await NotaService.criarNota({
          cadernoId: cadernoAtivoId,
          secaoId: secaoAtivaId,
          titulo,
          conteudoMarkdown: ''
        });
        setNotasMetadados((prev) => [...prev, nova]);
        setNotaAtivaId(nova.id);
        setNotaAtivaDetalhe(nova);
        toast.success(`Página "${nova.titulo}" criada com sucesso.`);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível salvar a página.');
      throw err;
    }
  };

  const handleSalvarConteudoNota = async (idNota: string, novoTitulo: string, novoMarkdown: string) => {
    if (!idNota) return;

    const atualizada = await NotaService.atualizarNota(idNota, {
      titulo: novoTitulo,
      conteudoMarkdown: novoMarkdown
    });

    setNotasMetadados((prev) =>
      prev.map((n) => (n.id === idNota ? { ...n, titulo: atualizada.titulo, atualizadoEm: atualizada.atualizadoEm } : n))
    );

    if (reqNotaIdRef.current === idNota) {
      setNotaAtivaDetalhe(atualizada);
    }
  };

  const handleUpdateTituloNota = (novoTitulo: string) => {
    if (!notaAtivaDetalhe) return;
    setNotasMetadados((prev) =>
      prev.map((n) => (n.id === notaAtivaDetalhe.id ? { ...n, titulo: novoTitulo } : n))
    );
    setNotaAtivaDetalhe((prev) => (prev ? { ...prev, titulo: novoTitulo } : null));
  };

  const handleExcluirNota = async (notaAlvo?: NotaMetadata) => {
    const alvo = notaAlvo || notaAtivaDetalhe;
    if (!alvo) return;

    const confirmado = await confirm({
      title: 'Mover Página para Lixeira',
      description: `Deseja realmente mover a página "${alvo.titulo}" para a lixeira? Ela permanecerá disponível por até 30 dias.`,
      confirmText: 'Mover para Lixeira',
      variant: 'danger'
    });

    if (!confirmado) return;

    try {
      await NotaService.excluirNota(alvo.id);
      toast.success('A página foi movida para a lixeira.');
      if (cadernoAtivoId && secaoAtivaId) {
        await carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir a página.');
    }
  };

  // ── IMPORTAÇÃO E SINCRONIZAÇÃO DE TRILHA ──

  const handleImportarTrilha = async (trilhaId: string) => {
    try {
      const caderno = await NotaService.importarTrilha(trilhaId);
      toast.success(`Caderno "${caderno.titulo}" importado com sucesso a partir da trilha.`);
      await carregarCadernos();
      setCadernoAtivoId(caderno.id);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao importar trilha.');
      throw err;
    }
  };

  const handleSincronizarTrilha = async () => {
    if (!cadernoAtivo) return;

    try {
      const res = await NotaService.sincronizarTrilha(cadernoAtivo.id);
      toast.success(
        `Trilha sincronizada com sucesso. Criadas: ${res.secoesCriadas} seção(ões), ${res.paginasCriadas} página(s).`
      );
      await carregarCadernos();
      await carregarSecoes(cadernoAtivo.id);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao sincronizar trilha.');
    }
  };

  // ── NAVEGAÇÃO DE RESULTADO DE BUSCA ──

  const handleSelecionarNotaDaBusca = (cadId: string, secId: string, notId: string) => {
    setCadernoAtivoId(cadId);
    setSecaoAtivaId(secId);
    setNotaAtivaId(notId);
    setMobileVisao('editor');
  };

  // ── RENDERIZAÇÃO DA INTERFACE ──

  if (carregando) {
    return (
      <div className={styles.loading}>
        <span>Carregando seus cadernos...</span>
      </div>
    );
  }

  if (!isAuthenticated) {
    if (telaAuth === 'login' || telaAuth === 'cadastro' || telaAuth === 'recuperar') {
      return (
        <AuthView
          mode={telaAuth}
          onAlternarModo={(novoModo) => alternarTelaAuth(novoModo)}
          onAutenticado={() => alternarTelaAuth('none')}
        />
      );
    }
    return (
      <LandingView
        onLogin={() => alternarTelaAuth('login')}
        onCriarConta={() => alternarTelaAuth('cadastro')}
      />
    );
  }

  return (
    <div className={styles.shell}>
      {/* ── 1. SIDEBAR DE NAVEGAÇÃO COMPARTILHADA COM 3 NÍVEIS ── */}
      <Navbar
        cadernos={cadernos}
        cadernoAtivoId={cadernoAtivoId}
        secoes={secoes}
        secaoAtivaId={secaoAtivaId}
        usuario={usuario}
        onSelecionarCaderno={(id) => {
          setCadernoAtivoId(id);
          setMobileVisao('lista');
        }}
        onSelecionarSecao={(id) => {
          setSecaoAtivaId(id);
          setMobileVisao('lista');
        }}
        onAbrirNovoCaderno={() => {
          setCadernoParaEditar(null);
          setModalCadernoAberto(true);
        }}
        onAbrirEditarCaderno={() => {
          if (cadernoAtivo) {
            setCadernoParaEditar(cadernoAtivo);
            setModalCadernoAberto(true);
          }
        }}
        onExcluirCaderno={handleExcluirCaderno}
        onAbrirNovaSecao={() => {
          setSecaoParaEditar(null);
          setModalSecaoAberto(true);
        }}
        onAbrirEditarSecao={(secao) => {
          setSecaoParaEditar(secao);
          setModalSecaoAberto(true);
        }}
        onExcluirSecao={handleExcluirSecao}
        onAbrirNovaNota={() => {
          setNotaParaEditar(null);
          setModalNotaAberto(true);
        }}
        onAbrirImportarTrilha={() => setModalImportarTrilhaAberto(true)}
        onSincronizarTrilha={handleSincronizarTrilha}
        onAbrirBusca={() => setModalBuscaAberto(true)}
        onAbrirLixeira={() => setModalLixeiraAberto(true)}
        onAbrirLogin={() => alternarTelaAuth('login')}
        onLogout={logout}
        onAbrirConta={abrirContaModal}
        onCollapsedChange={setSidebarCollapsed}
      />

      {/* ── 2. ÁREA DE TRABALHO: LAYOUT ONENOTE (COLUNA 2: PÁGINAS + COLUNA 3: EDITOR) ── */}
      <main className={`${styles.workspace} ${sidebarCollapsed ? styles.workspaceCollapsed : ''}`}>
        {cadernos.length === 0 ? (
          <div className={styles.emptyStateWrapper}>
            <EmptyState
              icone={<BookMarked size={44} />}
              titulo="Nenhum caderno criado"
              descricao="Crie seu primeiro caderno para começar a organizar seus resumos e anotações."
              acaoPrimaria={
                <Button
                  variant="primary"
                  leftIcon={<Plus size={16} />}
                  onClick={() => {
                    setCadernoParaEditar(null);
                    setModalCadernoAberto(true);
                  }}
                >
                  Criar Primeiro Caderno
                </Button>
              }
            />
          </div>
        ) : secoes.length === 0 ? (
          <div className={styles.emptyStateWrapper}>
            <EmptyState
              icone={<Folder size={44} />}
              titulo="Nenhuma seção neste caderno"
              descricao="Adicione uma seção divisória para começar a organizar as páginas deste caderno."
              acaoPrimaria={
                <Button
                  variant="primary"
                  leftIcon={<Plus size={16} />}
                  onClick={() => {
                    setSecaoParaEditar(null);
                    setModalSecaoAberto(true);
                  }}
                >
                  Criar Primeira Seção
                </Button>
              }
            />
          </div>
        ) : (
          <>
            {/* ── COLUNA 2: PAINEL DE PÁGINAS ONENOTE ── */}
            <div className={`${styles.pagesColumn} ${mobileVisao === 'editor' ? styles.hideOnMobile : ''}`}>
              <ListaPaginas
                secaoNome={secaoAtiva?.titulo || 'Seção'}
                secaoCor={secaoAtiva?.cor || cadernoAtivo?.cor}
                notas={notasMetadados as Nota[]}
                notaAtivaId={notaAtivaId}
                onSelecionarNota={(id) => {
                  setNotaAtivaId(id);
                  setMobileVisao('editor');
                }}
                onNovaPagina={() => {
                  setNotaParaEditar(null);
                  setModalNotaAberto(true);
                }}
                onExcluirNota={(nota) => handleExcluirNota(nota)}
              />
            </div>

            {/* ── COLUNA 3: EDITOR TIPTAP FULL CANVAS COM CARREGAMENTO SOB DEMANDA ── */}
            <div className={`${styles.editorContainer} ${mobileVisao === 'lista' ? styles.hideOnMobile : ''}`}>
              {/* Botão de retorno visível apenas em telas menores / mobile */}
              <div className={styles.mobileNavHeader}>
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<ChevronLeft size={16} />}
                  onClick={() => setMobileVisao('lista')}
                >
                  Voltar às Páginas ({secaoAtiva?.titulo || 'Seção'})
                </Button>
              </div>

              {notaAtivaDetalhe ? (
                <TiptapEditor
                  key={notaAtivaDetalhe.id}
                  usuarioId={usuario?.id || ''}
                  notaId={notaAtivaDetalhe.id}
                  titulo={notaAtivaDetalhe.titulo}
                  conteudoMarkdown={notaAtivaDetalhe.conteudoMarkdown}
                  atualizadoEmServidor={notaAtivaDetalhe.atualizadoEm}
                  onSalvar={handleSalvarConteudoNota}
                  onUpdateTitulo={handleUpdateTituloNota}
                  onExcluirNota={() => handleExcluirNota(notaAtivaDetalhe)}
                />
              ) : (
                <div className={styles.emptyStateWrapper}>
                  <EmptyState
                    icone={<FileText size={38} />}
                    titulo={notasMetadados.length === 0 ? "Nenhuma página na seção" : "Nenhuma página selecionada"}
                    descricao={
                      notasMetadados.length === 0
                        ? "Crie uma nova página para iniciar o conteúdo desta seção."
                        : "Selecione uma página na coluna ao lado para começar a redigir."
                    }
                    acaoPrimaria={
                      <Button
                        variant="primary"
                        leftIcon={<Plus size={16} />}
                        onClick={() => {
                          setNotaParaEditar(null);
                          setModalNotaAberto(true);
                        }}
                      >
                        Nova Página
                      </Button>
                    }
                  />
                </div>
              )}
            </div>
          </>
        )}
      </main>

      {/* ── 3. MODAIS DE CADERNO, SEÇÃO E NOTA ── */}
      <CadernoModal
        isOpen={modalCadernoAberto}
        onClose={() => setModalCadernoAberto(false)}
        onSalvar={handleSalvarCaderno}
        cadernoParaEditar={cadernoParaEditar}
        cadernos={cadernos}
      />

      <SecaoModal
        isOpen={modalSecaoAberto}
        onClose={() => setModalSecaoAberto(false)}
        onSalvar={handleSalvarSecao}
        secaoParaEditar={secaoParaEditar}
        corSugerida={cadernoAtivo?.cor}
      />

      <NotaModal
        isOpen={modalNotaAberto}
        onClose={() => setModalNotaAberto(false)}
        onSalvar={handleSalvarNota}
        notaParaEditar={notaParaEditar as Nota | null}
      />

      {/* ── 4. MODAIS FASE 8: IMPORTAR TRILHA, BUSCA GLOBAL, LIXEIRA ── */}
      <ImportarTrilhaModal
        isOpen={modalImportarTrilhaAberto}
        onClose={() => setModalImportarTrilhaAberto(false)}
        trilhas={trilhasDisponiveis}
        cadernos={cadernos}
        usuarioId={usuario?.id || ''}
        onImportar={handleImportarTrilha}
      />

      <BuscaModal
        isOpen={modalBuscaAberto}
        onClose={() => setModalBuscaAberto(false)}
        onSelecionarNota={handleSelecionarNotaDaBusca}
      />

      <LixeiraModal
        isOpen={modalLixeiraAberto}
        onClose={() => setModalLixeiraAberto(false)}
        onItemRestaurado={async () => {
          await carregarCadernos();
          if (cadernoAtivoId) {
            await carregarSecoes(cadernoAtivoId);
            if (secaoAtivaId) {
              await carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
            }
          }
        }}
      />
    </div>
  );
}

export default NotasApp;
