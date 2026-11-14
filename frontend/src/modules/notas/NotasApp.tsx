import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { BookMarked, ChevronLeft, FileText, Folder, Plus, Trash2 } from 'lucide-react';
import { AvisoContaExcluida, Button, EmptyState, useConfirm, useToast } from '@shared/design-system';
import { useAuth, AuthView, request } from '@shared/auth';

import { Caderno, Nota, NotaMetadata, Secao } from './types/nota';
import { NotaService } from './services/notaService';
import { LandingView } from './views/LandingView';
import { Navbar } from './components/Navbar/Navbar';
import { ListaPaginas } from './components/ListaPaginas/ListaPaginas';
import { TiptapEditor } from './components/Editor/TiptapEditor';
import { CadernoModal } from './components/CadernoModal/CadernoModal';
import { SecaoModal } from './components/SecaoModal/SecaoModal';
import { NotaModal } from './components/NotaModal/NotaModal';
import {
  CHAVE_OCULTAR_MODAL_IMPORTACAO,
  ImportarTrilhaModal,
  TrilhaItem
} from './components/ImportarTrilhaModal/ImportarTrilhaModal';
import { LixeiraModal } from './components/LixeiraModal/LixeiraModal';
import { BuscaModal } from './components/BuscaModal/BuscaModal';
import styles from './App.module.css';

type AuthScreen = 'none' | 'login' | 'cadastro' | 'recuperar';

const TAMANHO_PAGINA_NOTAS = 50;

function obterTelaAuthInicial(): AuthScreen {
  if (typeof window === 'undefined') return 'none';
  const params = new URLSearchParams(window.location.search);
  const direto = params.get('pagina') ?? params.get('auth');
  if (direto === 'login' || direto === 'cadastro' || direto === 'recuperar') return direto;
  return 'none';
}

/** Parâmetro do link "Abrir no Notas" de Estudos. */
function obterOrigemTrilhaInicial(): string | null {
  if (typeof window === 'undefined') return null;
  return new URLSearchParams(window.location.search).get('origemTrilhaId');
}

function NotasApp() {
  const toast = useToast();
  const { confirm } = useConfirm();
  const { usuario, isAuthenticated, carregando } = useAuth();
  const [telaAuth, setTelaAuth] = useState<AuthScreen>(obterTelaAuthInicial);

  const [cadernos, setCadernos] = useState<Caderno[]>([]);
  const [cadernoAtivoId, setCadernoAtivoId] = useState('');
  const [secoes, setSecoes] = useState<Secao[]>([]);
  const [secaoAtivaId, setSecaoAtivaId] = useState('');
  const [notasMetadados, setNotasMetadados] = useState<NotaMetadata[]>([]);
  const [notaAtivaId, setNotaAtivaId] = useState('');
  const [notaAtivaDetalhe, setNotaAtivaDetalhe] = useState<Nota | null>(null);

  const [modalCadernoAberto, setModalCadernoAberto] = useState(false);
  const [cadernoParaEditar, setCadernoParaEditar] = useState<Caderno | null>(null);
  const [modalSecaoAberto, setModalSecaoAberto] = useState(false);
  const [secaoParaEditar, setSecaoParaEditar] = useState<Secao | null>(null);
  const [modalNotaAberto, setModalNotaAberto] = useState(false);
  const [notaParaEditar, setNotaParaEditar] = useState<NotaMetadata | null>(null);

  const [modalImportarTrilhaAberto, setModalImportarTrilhaAberto] = useState(false);
  const [trilhasDisponiveis, setTrilhasDisponiveis] = useState<TrilhaItem[]>([]);
  const [modalLixeiraAberto, setModalLixeiraAberto] = useState(false);
  const [modalBuscaAberto, setModalBuscaAberto] = useState(false);

  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem('trilhara_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  const [mobileVisao, setMobileVisao] = useState<'lista' | 'editor'>('lista');

  const reqNotaIdRef = useRef<string | null>(null);
  const sugestaoImportacaoAvaliadaRef = useRef(false);
  const origemTrilhaInicialRef = useRef<string | null>(obterOrigemTrilhaInicial());

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

  const carregarCadernos = useCallback(
    async (origemTrilhaId?: string) => {
      try {
        const lista = await NotaService.listarCadernos(origemTrilhaId);
        setCadernos(lista);
        if (lista.length > 0) {
          setCadernoAtivoId((prev) => (lista.some((c) => c.id === prev) ? prev : lista[0].id));
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
    },
    [toast]
  );

  const carregarSecoes = useCallback(
    async (cadernoId: string) => {
      if (!cadernoId) {
        setSecoes([]);
        setSecaoAtivaId('');
        setNotasMetadados([]);
        setNotaAtivaId('');
        setNotaAtivaDetalhe(null);
        return;
      }
      try {
        const lista = await NotaService.listarSecoes(cadernoId);
        setSecoes(lista);
        if (lista.length > 0) {
          setSecaoAtivaId((prev) => (lista.some((s) => s.id === prev) ? prev : lista[0].id));
        } else {
          setSecaoAtivaId('');
          setNotasMetadados([]);
          setNotaAtivaId('');
          setNotaAtivaDetalhe(null);
        }
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Falha ao buscar seções do caderno');
      }
    },
    [toast]
  );

  const carregarNotasMetadados = useCallback(
    async (cadernoId: string, secaoId: string) => {
      if (!cadernoId || !secaoId) {
        setNotasMetadados([]);
        setNotaAtivaId('');
        setNotaAtivaDetalhe(null);
        return;
      }
      try {
        const pagina = await NotaService.listarNotasPorSecao(
          cadernoId,
          secaoId,
          1,
          TAMANHO_PAGINA_NOTAS
        );
        setNotasMetadados(pagina.itens);
        if (pagina.itens.length > 0) {
          setNotaAtivaId((prev) =>
            pagina.itens.some((n) => n.id === prev) ? prev : pagina.itens[0].id
          );
        } else {
          setNotaAtivaId('');
          setNotaAtivaDetalhe(null);
        }
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Falha ao buscar páginas da seção');
      }
    },
    [toast]
  );

  const carregarNotaDetalhe = useCallback(
    async (idNota: string) => {
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
    },
    [toast]
  );

  // 1. Carga inicial: respeita o filtro `origemTrilhaId` do link "Abrir no Notas".
  useEffect(() => {
    if (!isAuthenticated) return;
    void carregarCadernos(origemTrilhaInicialRef.current ?? undefined);
  }, [isAuthenticated, carregarCadernos]);

  useEffect(() => {
    if (isAuthenticated && cadernoAtivoId) {
      void carregarSecoes(cadernoAtivoId);
    }
  }, [isAuthenticated, cadernoAtivoId, carregarSecoes]);

  useEffect(() => {
    if (isAuthenticated && cadernoAtivoId && secaoAtivaId) {
      void carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
    }
  }, [isAuthenticated, cadernoAtivoId, secaoAtivaId, carregarNotasMetadados]);

  useEffect(() => {
    if (isAuthenticated && notaAtivaId) {
      void carregarNotaDetalhe(notaAtivaId);
    } else {
      setNotaAtivaDetalhe(null);
    }
  }, [isAuthenticated, notaAtivaId, carregarNotaDetalhe]);

  // 2. Consulta em segundo plano de /api/trilhas: a sugestão automática é apenas um convite.
  // É avaliada UMA vez por sessão de tela (nunca reabre a cada recarga de cadernos) e a falha
  // da consulta não abre o modal nem exibe erro intrusivo.
  useEffect(() => {
    if (!isAuthenticated || !usuario?.id) return;
    if (sugestaoImportacaoAvaliadaRef.current) return;

    const ocultarModal =
      window.localStorage.getItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(usuario.id)) === 'true';

    request<TrilhaItem[]>('/trilhas')
      .then((trilhas) => {
        sugestaoImportacaoAvaliadaRef.current = true;
        if (!trilhas) return;
        setTrilhasDisponiveis(trilhas);
        if (ocultarModal || trilhas.length === 0) return;

        const existeNaoImportada = trilhas.some(
          (trilha) => !cadernos.some((c) => c.origemTrilhaId === trilha.id)
        );
        if (existeNaoImportada) {
          setModalImportarTrilhaAberto(true);
        }
      })
      .catch(() => {
        // Sugestão best-effort: a ação "Importar trilha" da sidebar continua disponível.
        sugestaoImportacaoAvaliadaRef.current = true;
      });
    // Avaliada apenas quando a identidade do usuário muda: deps intencionais.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, usuario?.id]);

  const cadernoAtivo = useMemo(
    () => cadernos.find((c) => c.id === cadernoAtivoId) ?? null,
    [cadernos, cadernoAtivoId]
  );

  const secaoAtiva = useMemo(
    () => secoes.find((s) => s.id === secaoAtivaId) ?? null,
    [secoes, secaoAtivaId]
  );

  // ── AÇÕES DE CADERNO ──

  const handleSalvarCaderno = async (dados: { titulo: string; cor?: string; icone?: string }) => {
    try {
      if (cadernoParaEditar) {
        const atualizado = await NotaService.atualizarCaderno(cadernoParaEditar.id, dados);
        setCadernos((prev) => prev.map((c) => (c.id === atualizado.id ? atualizado : c)));
        toast.success(`O caderno "${atualizado.titulo}" foi atualizado.`);
      } else {
        const novo = await NotaService.criarCaderno(dados);
        await carregarCadernos();
        setCadernoAtivoId(novo.id);
        toast.success(`O caderno "${novo.titulo}" foi criado com sucesso.`);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível salvar o caderno.');
      throw err;
    }
  };

  const restaurarCaderno = useCallback(
    async (cadernoId: string, titulo: string) => {
      try {
        await NotaService.restaurarItemLixeira('CADERNO', cadernoId);
        toast.success(`O caderno "${titulo}" foi restaurado.`);
        await carregarCadernos();
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Não foi possível desfazer a exclusão.');
      }
    },
    [carregarCadernos, toast]
  );

  /**
 * Exclusão de caderno com contagem real de impacto.
 *
 * O número vem de `GET /api/cadernos/{id}/impacto-exclusao` e conta só itens ativos. Sem essa
 * resposta não há confirmação destrutiva: o erro aparece no toast.
 */
  const handleExcluirCaderno = async () => {
    if (!cadernoAtivo) return;

    try {
      const impacto = await NotaService.obterImpactoExclusaoCaderno(cadernoAtivo.id);
      const confirmado = await confirm({
        title: 'Mover caderno para a lixeira',
        description:
          `O caderno "${impacto.titulo}" será movido para a lixeira com ` +
          `${impacto.secoes} seção(ões) e ${impacto.paginas} página(s). ` +
          'Os itens permanecem recuperáveis por 30 dias.',
        confirmText: 'Mover para a lixeira',
        variant: 'danger'
      });
      if (!confirmado) return;

      await NotaService.excluirCaderno(cadernoAtivo.id);
      toast.success(`O caderno "${cadernoAtivo.titulo}" foi movido para a lixeira.`, {
        acao: {
          rotulo: 'Desfazer',
          aoAcionar: () => void restaurarCaderno(cadernoAtivo.id, cadernoAtivo.titulo)
        }
      });
      await carregarCadernos();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir o caderno.');
    }
  };

  const handleSalvarSecao = async (dados: { titulo: string }) => {
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

  const restaurarSecao = useCallback(
    async (cadernoId: string, secaoId: string, titulo: string) => {
      try {
        await NotaService.restaurarItemLixeira('SECAO', secaoId);
        toast.success(`A seção "${titulo}" foi restaurada.`);
        await carregarSecoes(cadernoId);
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Não foi possível desfazer a exclusão.');
      }
    },
    [carregarSecoes, toast]
  );

  const handleExcluirSecao = async (secao: Secao) => {
    if (!cadernoAtivoId) return;

    try {
      const impacto = await NotaService.obterImpactoExclusaoSecao(secao.id);
      const confirmado = await confirm({
        title: 'Mover seção para a lixeira',
        description:
          `A seção "${impacto.titulo}" será movida para a lixeira com ` +
          `${impacto.paginas} página(s). Os itens permanecem recuperáveis por 30 dias.`,
        confirmText: 'Mover para a lixeira',
        variant: 'danger'
      });
      if (!confirmado) return;

      await NotaService.excluirSecao(cadernoAtivoId, secao.id);
      toast.success(`A seção "${secao.titulo}" foi movida para a lixeira.`, {
        acao: {
          rotulo: 'Desfazer',
          aoAcionar: () => void restaurarSecao(cadernoAtivoId, secao.id, secao.titulo)
        }
      });
      await carregarSecoes(cadernoAtivoId);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir a seção.');
    }
  };

  // ── AÇÕES DE PÁGINA ──

  const handleSalvarNota = async (titulo: string) => {
    if (!cadernoAtivoId || !secaoAtivaId) return;

    try {
      if (notaParaEditar) {
        const atualizada = await NotaService.atualizarNota(notaParaEditar.id, { titulo });
        setNotasMetadados((prev) =>
          prev.map((n) =>
            n.id === atualizada.id
              ? { ...n, titulo: atualizada.titulo, atualizadoEm: atualizada.atualizadoEm }
              : n
          )
        );
        if (notaAtivaDetalhe?.id === atualizada.id) {
          setNotaAtivaDetalhe((prev) =>
            prev
              ? { ...prev, titulo: atualizada.titulo, atualizadoEm: atualizada.atualizadoEm }
              : prev
          );
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

  /**
 * Persistência chamada pelo autosave do editor.
 *
 * O `AbortSignal` do hook é repassado ao `PUT`; o retorno carrega o `atualizadoEm` novo, que
 * vira a nova base do rascunho.
 */
  const handleSalvarConteudoNota = async (
    idNota: string,
    novoTitulo: string,
    novoMarkdown: string,
    signal?: AbortSignal
  ): Promise<{ atualizadoEm: string }> => {
    const atualizada = await NotaService.atualizarNota(
      idNota,
      { titulo: novoTitulo, conteudoMarkdown: novoMarkdown },
      { signal }
    );

    setNotasMetadados((prev) =>
      prev.map((n) =>
        n.id === idNota
          ? { ...n, titulo: atualizada.titulo, atualizadoEm: atualizada.atualizadoEm }
          : n
      )
    );

    if (reqNotaIdRef.current === idNota) {
      setNotaAtivaDetalhe((prev) =>
        prev && prev.id === idNota
          ? { ...prev, titulo: atualizada.titulo, atualizadoEm: atualizada.atualizadoEm }
          : prev
      );
    }

    return { atualizadoEm: atualizada.atualizadoEm };
  };

  const handleUpdateTituloNota = (novoTitulo: string) => {
    if (!notaAtivaDetalhe) return;
    setNotasMetadados((prev) =>
      prev.map((n) => (n.id === notaAtivaDetalhe.id ? { ...n, titulo: novoTitulo } : n))
    );
    setNotaAtivaDetalhe((prev) => (prev ? { ...prev, titulo: novoTitulo } : null));
  };

  const restaurarNota = useCallback(
    async (notaId: string, titulo: string) => {
      try {
        await NotaService.restaurarItemLixeira('NOTA', notaId);
        toast.success(`A página "${titulo}" foi restaurada.`);
        if (cadernoAtivoId && secaoAtivaId) {
          await carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
        }
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Não foi possível desfazer a exclusão.');
      }
    },
    [cadernoAtivoId, carregarNotasMetadados, secaoAtivaId, toast]
  );

  /** Exclusão de página com toast "Desfazer". */
  const handleExcluirNota = async (notaAlvo?: NotaMetadata) => {
    const alvo = notaAlvo ?? notaAtivaDetalhe;
    if (!alvo) return;

    try {
      await NotaService.excluirNota(alvo.id);
      toast.success('Página movida para a lixeira', {
        acao: { rotulo: 'Desfazer', aoAcionar: () => void restaurarNota(alvo.id, alvo.titulo) }
      });
      if (cadernoAtivoId && secaoAtivaId) {
        await carregarNotasMetadados(cadernoAtivoId, secaoAtivaId);
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível excluir a página.');
    }
  };

  // ── IMPORTAÇÃO E SINCRONIZAÇÃO DE TRILHA ──

  const handleImportarTrilha = async (trilhaId: string, reimportar: boolean) => {
    try {
      const caderno = await NotaService.importarTrilha(trilhaId, reimportar);
      toast.success(`Caderno "${caderno.titulo}" importado a partir da trilha.`);
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
      const resultado = await NotaService.sincronizarTrilha(cadernoAtivo.id);
      toast.success(
        `Trilha sincronizada. Seções criadas: ${resultado.secoesCriadas}, ` +
          `renomeadas: ${resultado.secoesRenomeadas}, desvinculadas: ${resultado.secoesDesvinculadas}, ` +
          `páginas criadas: ${resultado.paginasCriadas}.`
      );
      await carregarCadernos();
      await carregarSecoes(cadernoAtivo.id);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao sincronizar trilha.');
    }
  };

  // ── NAVEGAÇÃO A PARTIR DA BUSCA ──

  const handleSelecionarNotaDaBusca = (cadernoId: string, secaoId: string, notaId: string) => {
    setCadernoAtivoId(cadernoId);
    setSecaoAtivaId(secaoId);
    setNotaAtivaId(notaId);
    setMobileVisao('editor');
  };

  // ── RENDERIZAÇÃO ──

  if (carregando) {
    return (
      <div className={styles.loading}>
        <span>Carregando…</span>
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
        onSettings={() => toast.info('Configurações gerais em breve.')}
        onCollapsedChange={setSidebarCollapsed}
      />

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
                  Criar primeiro caderno
                </Button>
              }
            />
          </div>
        ) : secoes.length === 0 ? (
          <div className={styles.emptyStateWrapper}>
            <EmptyState
              icone={<Folder size={44} />}
              titulo="Este caderno não tem seções"
              descricao="Crie uma seção para organizar as páginas ou restaure uma seção que esteja na lixeira."
              acaoPrimaria={
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <Button
                    variant="primary"
                    leftIcon={<Plus size={16} />}
                    onClick={() => {
                      setSecaoParaEditar(null);
                      setModalSecaoAberto(true);
                    }}
                  >
                    Nova seção
                  </Button>
                  <Button
                    variant="secondary"
                    leftIcon={<Trash2 size={16} />}
                    onClick={() => setModalLixeiraAberto(true)}
                  >
                    Abrir lixeira
                  </Button>
                </div>
              }
            />
          </div>
        ) : (
          <>
            <div
              className={`${styles.pagesColumn} ${mobileVisao === 'editor' ? styles.hideOnMobile : ''}`}
            >
              <ListaPaginas
                secaoNome={secaoAtiva?.titulo ?? 'Seção'}
                secaoCor={cadernoAtivo?.cor}
                notas={notasMetadados}
                notaAtivaId={notaAtivaId}
                onSelecionarNota={(id) => {
                  setNotaAtivaId(id);
                  setMobileVisao('editor');
                }}
                onNovaPagina={() => {
                  setNotaParaEditar(null);
                  setModalNotaAberto(true);
                }}
                onExcluirNota={(nota) => void handleExcluirNota(nota)}
              />
            </div>

            <div
              className={`${styles.editorContainer} ${mobileVisao === 'lista' ? styles.hideOnMobile : ''}`}
            >
              <div className={styles.mobileNavHeader}>
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<ChevronLeft size={16} />}
                  onClick={() => setMobileVisao('lista')}
                >
                  Voltar às páginas ({secaoAtiva?.titulo ?? 'Seção'})
                </Button>
              </div>

              {notaAtivaDetalhe ? (
                <TiptapEditor
                  key={notaAtivaDetalhe.id}
                  usuarioId={usuario?.id ?? ''}
                  notaId={notaAtivaDetalhe.id}
                  titulo={notaAtivaDetalhe.titulo}
                  conteudoMarkdown={notaAtivaDetalhe.conteudoMarkdown}
                  atualizadoEmServidor={notaAtivaDetalhe.atualizadoEm}
                  onSalvar={handleSalvarConteudoNota}
                  onUpdateTitulo={handleUpdateTituloNota}
                  onExcluirNota={() => void handleExcluirNota(notaAtivaDetalhe)}
                />
              ) : (
                <div className={styles.emptyStateWrapper}>
                  <EmptyState
                    icone={<FileText size={38} />}
                    titulo={
                      notasMetadados.length === 0
                        ? 'Nenhuma página na seção'
                        : 'Nenhuma página selecionada'
                    }
                    descricao={
                      notasMetadados.length === 0
                        ? 'Crie uma nova página para iniciar o conteúdo desta seção.'
                        : 'Selecione uma página na coluna ao lado para começar a redigir.'
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
                        Nova página
                      </Button>
                    }
                  />
                </div>
              )}
            </div>
          </>
        )}
      </main>

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
      />

      <NotaModal
        isOpen={modalNotaAberto}
        onClose={() => setModalNotaAberto(false)}
        onSalvar={handleSalvarNota}
        notaParaEditar={notaParaEditar}
      />

      <ImportarTrilhaModal
        isOpen={modalImportarTrilhaAberto}
        onClose={() => setModalImportarTrilhaAberto(false)}
        trilhas={trilhasDisponiveis}
        cadernos={cadernos}
        usuarioId={usuario?.id ?? ''}
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

