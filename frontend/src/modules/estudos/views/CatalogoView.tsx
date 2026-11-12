import React, { useState, useEffect, useMemo } from 'react';
import {
  Search,
  LayoutGrid,
  List,
  RotateCcw,
  ChevronRight
} from 'lucide-react';
import {
  Button,
  Input,
  Select,
  Pagination,
  PublicHeader,
  useToast
} from '@shared/design-system';
import { EditalResumo, Trilha } from '../types/trilhara';
import { ApiError } from '../services/api';
import { EstudosService } from '../services/estudosService';
import { EstudosStorage } from '../services/estudosStorage';
import { diasAte, formatarDataBR, provaEmAndamento } from '../utils/format';
import { VerTrilhaModal } from '../components/CatalogoTrilhas/VerTrilhaModal';
import {
  ESFERAS_DISPONIVEIS,
  ORDENACAO_OPCOES,
  importarOuClonarEdital
} from '../data/catalogoVitrine';
import styles from './CatalogoView.module.css';

export interface CatalogoViewProps {
  isAuthenticated?: boolean;
  totalMinhasTrilhas?: number;
  onVoltarLanding: () => void;
  onIrParaPainel: () => void;
  onAbrirNovaTrilha: () => void;
  onLogin?: () => void;
  onCriarConta?: () => void;
  onRichAuth?: (edital: EditalResumo) => void;
  onTrilhaImportada: (trilha: Trilha) => void;
}

const STORAGE_PENDING_EDITAL_KEY = 'trilhara:pending_import_edital_id';
const TAMANHO_PAGINA = 6;

export interface CatalogCardProps {
  edital: EditalResumo;
  importando: boolean;
  onInspecionar: (edital: EditalResumo) => void;
  onUsarTrilha: (edital: EditalResumo) => void;
}

export const CatalogCard: React.FC<CatalogCardProps> = ({
  edital,
  importando,
  onInspecionar,
  onUsarTrilha
}) => {
  const prazo = diasAte(edital.dataProva);
  const totalTopicos = edital.disciplinas.reduce((acc, d) => acc + d.topicos.length, 0);
  const emAndamento = provaEmAndamento(edital.dataProva);

  const contextoMeta = [
    edital.banca,
    edital.area,
    edital.uf || edital.esfera
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <article className={styles.catalogCard}>
      <div className={styles.catalogCardTop}>
        <span className={styles.contextLabel}>{contextoMeta || 'Concurso Público'}</span>
        {emAndamento && (
          <span className={`${styles.statusBadge} ${styles.statusBadgeActive}`}>
            Edital aberto
          </span>
        )}
      </div>

      <div className={styles.catalogTitleGroup}>
        <h3 className={styles.catalogCardTitle}>{edital.nome}</h3>
        <p className={styles.catalogCardCargo}>
          {edital.cargo}
          {edital.orgao ? ` · ${edital.orgao}` : ''}
        </p>
      </div>

      <div className={styles.disciplineListPreview} aria-label="Principais disciplinas">
        {edital.disciplinas.slice(0, 3).map((disc) => (
          <span key={disc.id} className={styles.disciplineItem}>
            <span
              className={styles.disciplineDot}
              style={{ backgroundColor: disc.cor || '#0ea5e9' }}
              aria-hidden="true"
            />
            <span className={styles.disciplineName}>{disc.nome}</span>
          </span>
        ))}
        {edital.disciplinas.length > 3 && (
          <span className={styles.disciplineMore}>
            +{edital.disciplinas.length - 3}
          </span>
        )}
      </div>

      <div className={styles.metricsStrip}>
        <div className={styles.metricCell}>
          <span className={styles.metricValue}>
            {edital.disciplinas.length} disc · {totalTopicos} tópicos
          </span>
          <span className={styles.metricSub}>Edital verticalizado</span>
        </div>

        <div className={styles.metricCellRight}>
          <span
            className={`${styles.metricValue} ${
              edital.dataProva && prazo.isUrgente ? styles.valUrgente : ''
            }`}
          >
            {edital.dataProva ? formatarDataBR(edital.dataProva) : 'Contínuo'}
          </span>
          <span className={styles.metricSub}>
            {edital.dataProva ? prazo.label : 'Planejamento contínuo'}
          </span>
        </div>
      </div>

      <div className={styles.catalogCardFooter}>
        <button
          type="button"
          className={styles.inspectHint}
          onClick={() => onInspecionar(edital)}
          aria-label={`Ver grade de ${edital.nome}`}
        >
          <span>Ver grade</span>
          <ChevronRight size={14} className={styles.inspectArrow} aria-hidden="true" />
        </button>
        <Button
          type="button"
          variant="primary"
          size="sm"
          isLoading={importando}
          onClick={() => onUsarTrilha(edital)}
        >
          Usar trilha
        </Button>
      </div>
    </article>
  );
};

export const CatalogoView: React.FC<CatalogoViewProps> = ({
  isAuthenticated = false,
  totalMinhasTrilhas = 0,
  onVoltarLanding,
  onIrParaPainel,
  onAbrirNovaTrilha,
  onLogin,
  onCriarConta,
  onRichAuth,
  onTrilhaImportada
}) => {
  const toast = useToast();

  const [itensApi, setItensApi] = useState<EditalResumo[]>([]);
  const [totalApi, setTotalApi] = useState<number>(0);
  const [carregandoCatalogo, setCarregandoCatalogo] = useState<boolean>(true);
  const [erroCatalogo, setErroCatalogo] = useState(false);
  const [tentativaCatalogo, setTentativaCatalogo] = useState(0);

  const [busca, setBusca] = useState('');
  const [banca, setBanca] = useState('');
  const [bancasDisponiveis, setBancasDisponiveis] = useState<string[]>([]);
  const [esfera, setEsfera] = useState('');
  const [apenasEmAndamento, setApenasEmAndamento] = useState(false);
  const [ordenacao, setOrdenacao] = useState('prova_proxima');
  const [modoVisualizacao, setModoVisualizacao] = useState<'cards' | 'lista'>('cards');
  const [paginaAtual, setPaginaAtual] = useState(0);

  const [editalVisualizando, setEditalVisualizando] = useState<EditalResumo | null>(null);
  const [importandoId, setImportandoId] = useState<string | null>(null);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    EstudosService.listarEditais({ size: 100 })
      .then((resposta) => {
        const bancas = new Set<string>();
        (resposta?.itens ?? []).forEach((item) => {
          if (item.banca?.trim()) bancas.add(item.banca.trim());
        });
        setBancasDisponiveis(Array.from(bancas).sort((a, b) => a.localeCompare(b, 'pt-BR')));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    let ativo = true;
    setErroCatalogo(false);
    setCarregandoCatalogo(true);

    const timer = window.setTimeout(() => {
      EstudosService.listarEditais({
        q: busca.trim() || undefined,
        banca: banca || undefined,
        esfera: esfera || undefined,
        emAndamento: apenasEmAndamento || undefined,
        page: paginaAtual,
        size: TAMANHO_PAGINA
      })
        .then((resposta) => {
          if (!ativo) return;
          const itens = resposta?.itens ?? [];
          setItensApi(itens);
          setTotalApi(resposta?.total ?? 0);
          setBancasDisponiveis((atuais) => {
            const conjunto = new Set(atuais);
            itens.forEach((e) => {
              if (e.banca?.trim()) conjunto.add(e.banca.trim());
            });
            return Array.from(conjunto).sort((a, b) => a.localeCompare(b, 'pt-BR'));
          });
        })
        .catch(() => {
          if (!ativo) return;
          setErroCatalogo(true);
          setItensApi([]);
          setTotalApi(0);
        })
        .finally(() => {
          if (ativo) setCarregandoCatalogo(false);
        });
    }, 180);

    return () => {
      ativo = false;
      window.clearTimeout(timer);
    };
  }, [busca, banca, esfera, apenasEmAndamento, paginaAtual, tentativaCatalogo]);

  const opcoesBancas = useMemo(() => {
    const lista = new Set(bancasDisponiveis);
    if (banca && !lista.has(banca)) lista.add(banca);
    return [
      { value: '', label: 'Todas as bancas' },
      ...Array.from(lista).sort((a, b) => a.localeCompare(b, 'pt-BR')).map((b) => ({ value: b, label: b }))
    ];
  }, [bancasDisponiveis, banca]);

  const editaisPagina = useMemo(() => {
    const lista = [...itensApi];
    lista.sort((a, b) => {
      if (ordenacao === 'alfabetica') {
        return a.nome.localeCompare(b.nome, 'pt-BR');
      }
      if (ordenacao === 'prova_proxima') {
        const dataA = a.dataProva ? new Date(a.dataProva).getTime() : Number.MAX_SAFE_INTEGER;
        const dataB = b.dataProva ? new Date(b.dataProva).getTime() : Number.MAX_SAFE_INTEGER;
        return dataA - dataB;
      }
      return 0;
    });
    return lista;
  }, [itensApi, ordenacao]);

  const limparFiltros = () => {
    setBusca('');
    setBanca('');
    setEsfera('');
    setApenasEmAndamento(false);
    setOrdenacao('prova_proxima');
    setPaginaAtual(0);
  };

  const handleUsarTrilha = async (edital: EditalResumo) => {
    if (importandoId) return;

    if (!isAuthenticated) {
      try {
        window.sessionStorage.setItem(STORAGE_PENDING_EDITAL_KEY, edital.id);
      } catch {
        // Ignora bloqueio de storage
      }
      toast.info(
        `Trilha "${edital.nome}" selecionada. Entre ou crie sua conta para importar.`
      );
      setEditalVisualizando(null);
      if (onRichAuth) {
        onRichAuth(edital);
      } else {
        onIrParaPainel();
      }
      return;
    }

    setImportandoId(edital.id);
    try {
      const novaTrilha = await importarOuClonarEdital(edital);
      EstudosStorage.salvarTrilhaAtiva(novaTrilha.id);
      toast.success(`Trilha "${novaTrilha.nome}" adicionada ao seu painel.`);
      setEditalVisualizando(null);
      onTrilhaImportada(novaTrilha);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        try {
          window.sessionStorage.setItem(STORAGE_PENDING_EDITAL_KEY, edital.id);
        } catch {
          // Storage indisponível: o usuário poderá escolher a trilha novamente após o login
        }
        toast.info(`Sua sessão expirou. Entre novamente para importar "${edital.nome}".`);
        setEditalVisualizando(null);
        if (onRichAuth) {
          onRichAuth(edital);
        } else {
          onIrParaPainel();
        }
        return;
      }
      toast.error(
        err instanceof Error ? err : 'Não foi possível importar esta trilha. Tente novamente.'
      );
    } finally {
      setImportandoId(null);
    }
  };

  const totalResultados = totalApi;
  const totalPaginas = Math.max(1, Math.ceil(totalResultados / TAMANHO_PAGINA));
  const temFiltrosAtivos = Boolean(
    busca.trim() ||
      banca ||
      esfera ||
      apenasEmAndamento
  );

  return (
    <div className={styles.page}>
      <PublicHeader
        contextLabel="Catálogo"
        onHomeClick={onVoltarLanding}
        isAuthenticated={isAuthenticated}
        totalMinhasTrilhas={totalMinhasTrilhas}
        onLogin={onLogin}
        onEntrarApp={onIrParaPainel}
        onCriarConta={onCriarConta || onIrParaPainel}
        onIrParaPainel={onIrParaPainel}
      />

      <main className={styles.mainContent}>
        <div className={styles.container}>
          {/* ── CABEÇALHO DIRETO E SILENCIOSO ── */}
          <div className={styles.pageHeader}>
            <div className={styles.pageHeaderCopy}>
              <h1 id="catalogo-page-title" className={styles.pageTitle}>
                Catálogo de Trilhas
              </h1>
              <p className={styles.pageSubtitle}>
                Inspecione a grade verticalizada de cada concurso e importe para o seu ciclo em 1 clique.
              </p>
            </div>

            <div className={styles.headerMetaRight}>
              <span className={styles.resultsCounter} aria-live="polite">
                <strong>{totalResultados}</strong> trilhas disponíveis
              </span>
              <div
                className={styles.viewToggleGroup}
                role="group"
                aria-label="Modo de exibição"
              >
                <button
                  type="button"
                  className={`${styles.viewToggleBtn} ${
                    modoVisualizacao === 'cards' ? styles.viewToggleBtnActive : ''
                  }`}
                  onClick={() => setModoVisualizacao('cards')}
                  aria-pressed={modoVisualizacao === 'cards'}
                  title="Visualização em grade"
                >
                  <LayoutGrid size={15} />
                  <span>Grade</span>
                </button>
                <button
                  type="button"
                  className={`${styles.viewToggleBtn} ${
                    modoVisualizacao === 'lista' ? styles.viewToggleBtnActive : ''
                  }`}
                  onClick={() => setModoVisualizacao('lista')}
                  aria-pressed={modoVisualizacao === 'lista'}
                  title="Visualização em lista"
                >
                  <List size={15} />
                  <span>Lista</span>
                </button>
              </div>
            </div>
          </div>

          {/* ── BARRA DE COMANDO UNIFICADA (BUSCA + FILTROS + ÁREAS) ── */}
          <section className={styles.commandBar} role="search" aria-label="Filtrar trilhas">
            <div className={styles.searchAndFiltersRow}>
              <div className={styles.searchFieldWrap}>
                <Input
                  aria-label="Buscar por concurso, cargo, órgão ou banca"
                  placeholder="Buscar por concurso, cargo, órgão ou banca..."
                  leftIcon={<Search size={16} />}
                  value={busca}
                  onChange={(e) => {
                    setBusca(e.target.value);
                    setPaginaAtual(0);
                  }}
                />
              </div>

              <Select
                aria-label="Filtrar por banca examinadora"
                options={opcoesBancas}
                value={banca}
                onChange={(valor) => {
                  setBanca(valor);
                  setPaginaAtual(0);
                }}
              />

              <Select
                aria-label="Filtrar por esfera"
                options={ESFERAS_DISPONIVEIS}
                value={esfera}
                onChange={(valor) => {
                  setEsfera(valor);
                  setPaginaAtual(0);
                }}
              />

              <Select
                aria-label="Ordenar resultados"
                options={ORDENACAO_OPCOES}
                value={ordenacao}
                onChange={(valor) => setOrdenacao(valor)}
              />

              <button
                type="button"
                aria-pressed={apenasEmAndamento}
                className={`${styles.quickFilterToggle} ${
                  apenasEmAndamento ? styles.quickFilterToggleActive : ''
                }`}
                onClick={() => {
                  setApenasEmAndamento((prev) => !prev);
                  setPaginaAtual(0);
                }}
              >
                <span className={styles.toggleDot} aria-hidden="true" />
                <span>Edital aberto</span>
              </button>
            </div>

            {temFiltrosAtivos && (
              <button
                type="button"
                className={styles.clearFiltersLink}
                onClick={limparFiltros}
              >
                <RotateCcw size={13} aria-hidden="true" />
                <span>Limpar filtros</span>
              </button>
            )}
          </section>

          {/* ── RESULTADOS DO CATÁLOGO ── */}
          {carregandoCatalogo && editaisPagina.length === 0 ? (
            <div className={styles.catalogStateBox} role="status">
              Carregando trilhas...
            </div>
          ) : erroCatalogo ? (
            <div className={styles.catalogStateBox} role="alert">
              <p>Não foi possível carregar o catálogo agora.</p>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setTentativaCatalogo((n) => n + 1)}
              >
                Tentar novamente
              </Button>
            </div>
          ) : editaisPagina.length === 0 ? (
            <div className={styles.catalogEmptyBox}>
              <h3>Nenhuma trilha encontrada</h3>
              <p>
                Tente buscar por outro termo ou limpar os filtros ativos. Você também pode criar uma trilha personalizada do zero.
              </p>
              <div className={styles.emptyActions}>
                <Button type="button" variant="secondary" size="sm" onClick={limparFiltros}>
                  Limpar filtros
                </Button>
                <Button type="button" variant="primary" size="sm" onClick={onAbrirNovaTrilha}>
                  Criar trilha do zero
                </Button>
              </div>
            </div>
          ) : modoVisualizacao === 'lista' ? (
            <div className={styles.catalogList} role="list">
              {editaisPagina.map((edital) => {
                const totalTopicos = edital.disciplinas.reduce(
                  (acc, d) => acc + d.topicos.length,
                  0
                );
                const emAndamento = provaEmAndamento(edital.dataProva);
                const contexto = [edital.banca, edital.area, edital.uf || edital.esfera]
                  .filter(Boolean)
                  .join(' · ');

                return (
                  <div
                    key={edital.id}
                    className={styles.catalogListRow}
                    role="listitem"
                  >
                    <div className={styles.catalogListPrimary}>
                      <div className={styles.catalogListMeta}>
                        <span>{contexto}</span>
                        {emAndamento && (
                          <span className={`${styles.statusBadge} ${styles.statusBadgeActive}`}>
                            Edital aberto
                          </span>
                        )}
                      </div>
                      <h3 className={styles.catalogListTitle}>{edital.nome}</h3>
                      <p className={styles.catalogListSub}>
                        {edital.cargo} · {edital.disciplinas.length} disciplinas · {totalTopicos} tópicos
                      </p>
                    </div>

                    <div className={styles.catalogListActions}>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setEditalVisualizando(edital)}
                      >
                        Ver grade
                      </Button>
                      <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        isLoading={importandoId === edital.id}
                        onClick={() => void handleUsarTrilha(edital)}
                      >
                        Usar trilha
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className={styles.catalogGrid} role="list">
              {editaisPagina.map((edital) => (
                <CatalogCard
                  key={edital.id}
                  edital={edital}
                  importando={importandoId === edital.id}
                  onInspecionar={setEditalVisualizando}
                  onUsarTrilha={(item) => void handleUsarTrilha(item)}
                />
              ))}
            </div>
          )}

          <Pagination
            ariaLabel="Paginação do catálogo de trilhas"
            currentPage={paginaAtual + 1}
            totalPages={totalPaginas}
            disabled={carregandoCatalogo}
            summary={`Página ${paginaAtual + 1} de ${totalPaginas}`}
            onPageChange={(page) => setPaginaAtual(page - 1)}
          />
        </div>
      </main>

      <footer className={styles.footer}>
        <div className={styles.container}>
          <div className={styles.footerInner}>
            <span>Trilhara · Catálogo de Editais Verticalizados</span>
          </div>
        </div>
      </footer>

      <VerTrilhaModal
        edital={editalVisualizando}
        importando={Boolean(editalVisualizando && importandoId === editalVisualizando.id)}
        onClose={() => !importandoId && setEditalVisualizando(null)}
        onUsar={(edital) => void handleUsarTrilha(edital)}
      />
    </div>
  );
};
