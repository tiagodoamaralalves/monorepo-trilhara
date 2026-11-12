import React, { useState, useCallback, useEffect } from 'react';
import {
  Play,
  CheckCircle2,
  Clock,
  BarChart2,
  BookOpen,
  Calendar,
  ArrowRight,
  Route
} from 'lucide-react';
import {
  Button,
  PublicHeader,
  useToast
} from '@shared/design-system';
import logoMark from '@shared/assets/logo-mark.svg';
import { EditalResumo, TrilhaDetalheDTO } from '../types/trilhara';
import { ApiError } from '../services/api';
import { EstudosStorage } from '../services/estudosStorage';
import { EstudosService } from '../services/estudosService';
import { VerTrilhaModal } from '../components/CatalogoTrilhas/VerTrilhaModal';
import {
  EDITAIS_VITRINE_FALLBACK,
  importarOuClonarEdital
} from '../data/catalogoVitrine';
import { CatalogCard } from './CatalogoView';
import styles from './LandingView.module.css';

export interface LandingViewProps {
  isAuthenticated?: boolean;
  usuarioNome?: string;
  onEntrarApp?: () => void;
  onLogin?: () => void;
  onAbrirCatalogo?: () => void;
  onCriarConta?: (pendingEdital?: EditalResumo) => void;
  onTrilhaImportada?: (trilha: TrilhaDetalheDTO) => void;
}

const STORAGE_PENDING_EDITAL_KEY = 'trilhara:pending_import_edital_id';

interface SectionHeadingProps {
  id?: string;
  eyebrow: string;
  title: string;
  subtitle?: string;
  align?: 'left' | 'center';
  rightSlot?: React.ReactNode;
}

const SectionHeading: React.FC<SectionHeadingProps> = ({
  id,
  eyebrow,
  title,
  subtitle,
  align = 'left',
  rightSlot
}) => (
  <div className={`${styles.sectionHeading} ${align === 'center' ? styles.sectionHeadingCenter : ''}`}>
    <div className={styles.sectionHeadingText}>
      <span className={styles.eyebrow}>{eyebrow}</span>
      <h2 id={id} className={styles.sectionTitle}>
        {title}
      </h2>
      {subtitle && <p className={styles.sectionSubtitle}>{subtitle}</p>}
    </div>
    {rightSlot && <div className={styles.sectionHeadingAction}>{rightSlot}</div>}
  </div>
);

interface PillarCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

const PillarCard: React.FC<PillarCardProps> = ({ icon, title, description }) => (
  <article className={styles.pillarCard}>
    <div className={styles.pillarIcon} aria-hidden="true">
      {icon}
    </div>
    <h3 className={styles.pillarTitle}>{title}</h3>
    <p className={styles.pillarDescription}>{description}</p>
  </article>
);

type DemoTabId = 'trilha' | 'disciplinas' | 'planejamento' | 'dashboard';

const DemoAppMock: React.FC<{ onExplorarCatalogo?: () => void }> = ({ onExplorarCatalogo }) => {
  const [abaAtiva, setAbaAtiva] = useState<DemoTabId>('trilha');

  const abas: { id: DemoTabId; label: string; icon: React.ReactNode }[] = [
    { id: 'trilha', label: 'Visão da Trilha', icon: <Route size={14} /> },
    { id: 'disciplinas', label: 'Disciplinas & Pesos', icon: <BookOpen size={14} /> },
    { id: 'planejamento', label: 'Cronograma do Dia', icon: <Calendar size={14} /> },
    { id: 'dashboard', label: 'Desempenho', icon: <BarChart2 size={14} /> }
  ];

  return (
    <div className={styles.showcaseWrapper}>
      <div
        className={styles.segmentedTabs}
        role="tablist"
        aria-label="Módulos do Trilhara"
      >
        {abas.map((aba) => {
          const ativa = aba.id === abaAtiva;
          return (
            <button
              key={aba.id}
              type="button"
              role="tab"
              id={`tab-${aba.id}`}
              aria-selected={ativa}
              aria-controls={`panel-${aba.id}`}
              className={`${styles.segmentedTabBtn} ${ativa ? styles.segmentedTabBtnActive : ''}`}
              onClick={() => setAbaAtiva(aba.id)}
            >
              {aba.icon}
              <span>{aba.label}</span>
            </button>
          );
        })}
      </div>

      <div
        id={`panel-${abaAtiva}`}
        role="tabpanel"
        aria-labelledby={`tab-${abaAtiva}`}
        className={styles.showcaseWindow}
      >
        <div className={styles.mockWindowChrome}>
          <div className={styles.mockDots} aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <span className={styles.mockActiveTrackLabel}>
            TCU — Auditor Federal de Controle Externo · FGV
          </span>
          <span className={styles.mockWindowStatus}>Cronograma ativo</span>
        </div>

        <div className={styles.mockContentArea}>
          {abaAtiva === 'trilha' && (
            <div className={styles.mockPaneGrid}>
              <div className={styles.mockTrilhaSpotlight}>
                <div className={styles.mockSpotlightHeader}>
                  <div>
                    <span className={styles.mockKicker}>TRILHA ATIVA</span>
                    <h4 className={styles.mockCardHeading}>
                      TCU — Auditor Federal de Controle Externo
                    </h4>
                    <p className={styles.mockCardSub}>
                      Controle &amp; Gestão · FGV · Federal (DF)
                    </p>
                  </div>
                  <span className={styles.mockPercentBadge}>68% concluído</span>
                </div>

                <div className={styles.mockProgressBar}>
                  <div className={styles.mockProgressFill} style={{ width: '68%' }} />
                </div>

                <div className={styles.mockInlineStats}>
                  <div>
                    <span className={styles.mockStatLabel}>Data da Prova</span>
                    <strong className={styles.mockStatVal}>14/02/2027 (102d)</strong>
                  </div>
                  <div>
                    <span className={styles.mockStatLabel}>Estrutura</span>
                    <strong className={styles.mockStatVal}>12 disciplinas · 184 tópicos</strong>
                  </div>
                  <div>
                    <span className={styles.mockStatLabel}>Tópicos vencidos</span>
                    <strong className={styles.mockStatValSuccess}>125 de 184</strong>
                  </div>
                </div>
              </div>

              <div className={styles.mockSideCard}>
                <div>
                  <h4 className={styles.mockSideTitle}>Importação em 1 clique</h4>
                  <p className={styles.mockSideDesc}>
                    Escolha qualquer edital no catálogo público para gerar sua trilha completa com pesos e tópicos personalizáveis.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  rightIcon={<ArrowRight size={14} />}
                  onClick={onExplorarCatalogo}
                >
                  Explorar catálogo
                </Button>
              </div>
            </div>
          )}

          {abaAtiva === 'disciplinas' && (
            <div className={styles.mockDisciplinasList}>
              {[
                { nome: 'Controle Externo', cor: '#0ea5e9', peso: 5, concluidos: 24, total: 28, pct: 86 },
                { nome: 'Direito Administrativo (Lei 14.133/21)', cor: '#10b981', peso: 5, concluidos: 19, total: 24, pct: 79 },
                { nome: 'Administração Financeira e Orçamentária', cor: '#a855f7', peso: 4, concluidos: 15, total: 22, pct: 68 },
                { nome: 'Ciência de Dados e SQL', cor: '#f59e0b', peso: 4, concluidos: 11, total: 20, pct: 55 }
              ].map((d) => (
                <div key={d.nome} className={styles.mockDiscRow}>
                  <div className={styles.mockDiscMain}>
                    <span
                      className={styles.disciplineColorDot}
                      style={{ backgroundColor: d.cor }}
                    />
                    <span className={styles.mockDiscName}>{d.nome}</span>
                    <span className={styles.mockWeightPill}>Peso {d.peso}</span>
                  </div>
                  <div className={styles.mockDiscRight}>
                    <span className={styles.mockDiscRatio}>
                      {d.concluidos}/{d.total} ({d.pct}%)
                    </span>
                    <div className={styles.mockMiniTrack}>
                      <div
                        className={styles.mockMiniFill}
                        style={{ width: `${d.pct}%`, backgroundColor: d.cor }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {abaAtiva === 'planejamento' && (
            <div className={styles.mockScheduleGrid}>
              {[
                { ordem: 'Bloco 01', disc: 'Controle Externo', cor: '#0ea5e9', duracao: '90 min', status: 'Concluído', done: true },
                { ordem: 'Bloco 02', disc: 'Direito Administrativo', cor: '#10b981', duracao: '90 min', status: 'Em foco · 00:42:19', active: true },
                { ordem: 'Bloco 03', disc: 'AFO & LRF', cor: '#a855f7', duracao: '60 min', status: 'Próximo na fila', done: false },
                { ordem: 'Bloco 04', disc: 'Análise de Dados (FGV)', cor: '#f59e0b', duracao: '60 min', status: 'Programado para hoje', done: false }
              ].map((bloco) => (
                <div
                  key={bloco.ordem}
                  className={`${styles.mockScheduleCard} ${
                    bloco.active ? styles.mockScheduleCardActive : ''
                  }`}
                >
                  <div className={styles.mockScheduleTop}>
                    <span>{bloco.ordem}</span>
                    <span>{bloco.duracao}</span>
                  </div>
                  <div className={styles.mockScheduleTitle}>
                    <span
                      className={styles.disciplineColorDot}
                      style={{ backgroundColor: bloco.cor }}
                    />
                    <span>{bloco.disc}</span>
                  </div>
                  <div
                    className={`${styles.mockScheduleStatus} ${
                      bloco.done
                        ? styles.statusDone
                        : bloco.active
                        ? styles.statusActive
                        : ''
                    }`}
                  >
                    {bloco.status}
                  </div>
                </div>
              ))}
            </div>
          )}

          {abaAtiva === 'dashboard' && (
            <div className={styles.mockKpiGrid}>
              <div className={styles.mockKpiBox}>
                <span className={styles.mockKpiLabel}>HORAS LÍQUIDAS</span>
                <span className={styles.mockKpiValue}>184h 20m</span>
                <span className={styles.mockKpiTrend}>+14% vs. semana anterior</span>
              </div>
              <div className={styles.mockKpiBox}>
                <span className={styles.mockKpiLabel}>QUESTÕES RESOLVIDAS</span>
                <span className={styles.mockKpiValue}>3.420</span>
                <span className={styles.mockKpiTrend}>84,6% de acerto médio</span>
              </div>
              <div className={styles.mockKpiBox}>
                <span className={styles.mockKpiLabel}>ADERÊNCIA AO CRONOGRAMA</span>
                <span className={styles.mockKpiValue}>94%</span>
                <span className={styles.mockKpiTrend}>22 de 23 blocos cumpridos</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export const LandingView: React.FC<LandingViewProps> = ({
  isAuthenticated = false,
  usuarioNome,
  onEntrarApp,
  onLogin,
  onAbrirCatalogo,
  onCriarConta,
  onTrilhaImportada
}) => {
  const toast = useToast();
  const [editalVisualizando, setEditalVisualizando] = useState<EditalResumo | null>(null);
  const [importandoId, setImportandoId] = useState<string | null>(null);
  const [totalCatalogoApi, setTotalCatalogoApi] = useState<number>(0);

  const trilhasDestaque = EDITAIS_VITRINE_FALLBACK.slice(0, 3);

  const formatarTotalCatalogo = useCallback((total: number) => {
    if (!Number.isFinite(total) || total <= 0) return 0;
    if (total < 50) return 0;
    return Math.floor(total / 50) * 50;
  }, []);

  const totalCatalogoExibicao = formatarTotalCatalogo(totalCatalogoApi);

  useEffect(() => {
    let ativo = true;

    EstudosService.listarEditais({ page: 0, size: 1 })
      .then((resposta) => {
        if (!ativo) return;
        setTotalCatalogoApi(resposta?.total ?? 0);
      })
      .catch(() => {
        if (!ativo) return;
        setTotalCatalogoApi(0);
      });

    return () => {
      ativo = false;
    };
  }, []);

  const handleUsarTrilha = async (edital: EditalResumo) => {
    if (importandoId) return;

    if (!isAuthenticated) {
      try {
        window.sessionStorage.setItem(STORAGE_PENDING_EDITAL_KEY, edital.id);
      } catch {
        // Ignora bloqueio de storage
      }
      toast.info(
        `Trilha "${edital.nome}" selecionada. Crie sua conta ou entre para adicioná-la ao painel.`
      );
      setEditalVisualizando(null);
      if (onCriarConta) {
        onCriarConta(edital);
      } else {
        onLogin?.();
      }
      return;
    }

    setImportandoId(edital.id);
    try {
      const novaTrilha = await importarOuClonarEdital(edital);
      EstudosStorage.salvarTrilhaAtiva(novaTrilha.id);
      toast.success(`Trilha "${novaTrilha.nome}" adicionada ao seu painel.`);
      setEditalVisualizando(null);
      onTrilhaImportada?.(novaTrilha);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        try {
          window.sessionStorage.setItem(STORAGE_PENDING_EDITAL_KEY, edital.id);
        } catch {
          // Ignora bloqueio de storage
        }

        if (onCriarConta) {
          toast.info(
            `Trilha "${edital.nome}" selecionada. Entre na sua conta para iniciar.`
          );
          setEditalVisualizando(null);
          onCriarConta(edital);
        } else {
          setEditalVisualizando(null);
          onEntrarApp?.();
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

  return (
    <div className={styles.page}>
      <PublicHeader
        onHomeClick={() => {
          const url = new URL(window.location.href);
          url.hash = '';
          window.history.replaceState(null, '', `${url.pathname}${url.search}`);
        }}
        isAuthenticated={isAuthenticated}
        usuarioNome={usuarioNome}
        onEntrarApp={onEntrarApp}
        onLogin={onLogin}
        onCriarConta={onCriarConta}
      />

      <main id="topo">
        {/* ── 1. HERO SECTION (FOCO IMEDIATO E ZERO RUÍDO) ── */}
        <section className={styles.heroSection} aria-labelledby="hero-headline">
          <div className={styles.container}>
            <div className={styles.heroGrid}>
              <div className={styles.heroCopy}>
                <span className={styles.heroEyebrow}>
                  <span className={styles.pulseDot} aria-hidden="true" />
                  <span>Cronogramas inteligentes · Editais verticalizados</span>
                </span>

                <h1 id="hero-headline" className={styles.heroTitle}>
                  Pare de estudar no escuro.{' '}
                  <span className={styles.heroTitleAccent}>
                    Domine cada tópico do edital.
                  </span>
                </h1>

                <p className={styles.heroSubtitle}>
                  Transforme editais densos em cronogramas diários ponderados por peso, com cronômetro integrado e progresso real assunto por assunto.
                </p>

                <div className={styles.heroCtaRow}>
                  <Button
                    type="button"
                    variant="primary"
                    size="lg"
                    rightIcon={<ArrowRight size={17} />}
                    onClick={isAuthenticated ? onEntrarApp : () => onCriarConta?.()}
                  >
                    {isAuthenticated ? 'Abrir Meu Painel' : 'Começar agora'}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    size="lg"
                    leftIcon={<Route size={17} />}
                    onClick={onAbrirCatalogo}
                  >
                    Explorar catálogo (+{totalCatalogoExibicao})
                  </Button>
                </div>

              </div>

              {/* Artefato Único de Produto (Sem Widgets Pendurados) */}
              <div className={styles.heroVisualStage} aria-label="Prévia da rotina diária no Trilhara">
                <div className={styles.heroMockCard}>
                  <div className={styles.heroMockHeader}>
                    <div>
                      <span className={styles.mockKicker}>CRONOGRAMA DE HOJE · FGV</span>
                      <h2 className={styles.heroMockTitle}>
                        TCU — Auditor Federal de Controle Externo
                      </h2>
                    </div>
                    <span className={styles.heroCountdownChip}>102 dias até a prova</span>
                  </div>

                  <div className={styles.heroProgressBlock}>
                    <div className={styles.heroProgressLabels}>
                      <span>Cobertura do edital verticalizado</span>
                      <strong className={styles.heroProgressPct}>68% · 125/184 tópicos</strong>
                    </div>
                    <div className={styles.mockProgressBar}>
                      <div className={styles.mockProgressFill} style={{ width: '68%' }} />
                    </div>
                  </div>

                  <div className={styles.heroBlocksStack}>
                    <div className={`${styles.heroBlockRow} ${styles.heroBlockDone}`}>
                      <CheckCircle2 size={15} className={styles.iconGreen} />
                      <div className={styles.heroBlockInfo}>
                        <span className={styles.heroBlockName}>Controle Externo</span>
                        <span className={styles.heroBlockTopic}>
                          Lei Orgânica do TCU (Lei 8.443/92)
                        </span>
                      </div>
                      <span className={styles.heroBlockDoneLabel}>90m concluídos</span>
                    </div>

                    <div className={`${styles.heroBlockRow} ${styles.heroBlockCurrent}`}>
                      <Play size={15} className={styles.iconCyan} />
                      <div className={styles.heroBlockInfo}>
                        <span className={styles.heroBlockName}>
                          Direito Administrativo · Peso 5
                        </span>
                        <span className={styles.heroBlockTopic}>
                          Nova Lei de Licitações (Lei 14.133/2021)
                        </span>
                      </div>
                      <span className={styles.heroLiveTimerBadge}>00:42:19</span>
                    </div>

                    <div className={styles.heroBlockRow}>
                      <Clock size={15} className={styles.iconMuted} />
                      <div className={styles.heroBlockInfo}>
                        <span className={styles.heroBlockName}>AFO &amp; LRF · Peso 4</span>
                        <span className={styles.heroBlockTopic}>
                          Créditos Adicionais e Restos a Pagar
                        </span>
                      </div>
                      <span className={styles.heroBlockNextLabel}>60m na fila</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── 2. OS 4 PILARES DO MÉTODO (ESSENCIALISMO) ── */}
        <section
          id="diferenciais"
          className={styles.section}
          aria-labelledby="diferenciais-heading"
        >
          <div className={styles.container}>
            <SectionHeading
              id="diferenciais-heading"
              eyebrow="Arquitetura de Estudos"
              title="Quatro pilares para substituir planilhas quebradas por execução diária"
            />

            <div className={styles.pillarsGrid}>
              <PillarCard
                icon={<Route size={18} />}
                title="Edital verticalizado"
                description="Acompanhe cada assunto do edital com progresso percentual exato por matéria e visão isolada por concurso."
              />
              <PillarCard
                icon={<Calendar size={18} />}
                title="Cronograma ponderado"
                description="Distribua seus blocos de estudo automaticamente conforme o peso e a relevância de cada disciplina na prova."
              />
              <PillarCard
                icon={<Clock size={18} />}
                title="Cronômetro integrado"
                description="Inicie o temporizador diretamente do bloco planejado do dia, com persistência de sessão e registro imediato."
              />
              <PillarCard
                icon={<BarChart2 size={18} />}
                title="Métricas de precisão"
                description="Monitore horas líquidas, volume de questões e evolução da sua taxa de acertos sem sair do fluxo de estudo."
              />
            </div>
          </div>
        </section>

        {/* ── 3. VITRINE DO CATÁLOGO OFICIAL ── */}
        <section
          id="catalogo-preview"
          className={styles.sectionBordered}
          aria-labelledby="catalogo-preview-heading"
        >
          <div className={styles.container}>
            <SectionHeading
              id="catalogo-preview-heading"
              eyebrow="Catálogo Público"
              title="Comece com um edital já estruturado"
              subtitle={
                totalCatalogoApi > 0
                  ? `Inspecione gratuitamente a grade de disciplinas ou explore mais de ${totalCatalogoExibicao} trilhas prontas.`
                  : 'Inspecione gratuitamente a grade de disciplinas ou explore trilhas prontas.'
              }
              rightSlot={
                <Button
                  type="button"
                  variant="secondary"
                  size="md"
                  rightIcon={<ArrowRight size={15} />}
                  onClick={onAbrirCatalogo}
                >
                  Ver catálogo completo (+{totalCatalogoExibicao})
                </Button>
              }
            />

            <div className={styles.catalogGrid} role="list">
              {trilhasDestaque.map((edital) => (
                <CatalogCard
                  key={edital.id}
                  edital={edital}
                  importando={importandoId === edital.id}
                  onInspecionar={setEditalVisualizando}
                  onUsarTrilha={(item) => void handleUsarTrilha(item)}
                />
              ))}
            </div>
          </div>
        </section>

        {/* ── 4. DEMONSTRAÇÃO DE INTERFACE ── */}
        <section
          id="demonstracao-interface"
          className={styles.section}
          aria-labelledby="demo-heading"
        >
          <div className={styles.container}>
            <SectionHeading
              id="demo-heading"
              eyebrow="Ambiente de Execução"
              title="Silencioso, rápido e desenhado para foco profundo"
              subtitle="Alterne entre os módulos reais da plataforma para ver como trilha, cronograma e métricas operam em conjunto."
            />

            <DemoAppMock onExplorarCatalogo={onAbrirCatalogo} />
          </div>
        </section>

        {/* ── 5. FLUXO EM 3 PASSOS & CONVERSÃO FINAL ── */}
        <section
          id="como-funciona"
          className={styles.sectionBordered}
          aria-labelledby="como-funciona-heading"
        >
          <div className={styles.container}>
            <SectionHeading
              id="como-funciona-heading"
              eyebrow="Fluxo Direto"
              title="Da escolha do edital ao primeiro bloco em 30 segundos"
            />

            <ol className={styles.stepsGrid}>
              <li className={styles.stepItem}>
                <span className={styles.stepNumber}>01</span>
                <h3 className={styles.stepTitle}>Escolha ou crie sua trilha</h3>
                <p className={styles.stepDesc}>
                  Importe um edital verticalizado do catálogo público em 1 clique ou monte sua estrutura do zero.
                </p>
              </li>

              <li className={styles.stepItem}>
                <span className={styles.stepNumber}>02</span>
                <h3 className={styles.stepTitle}>Calibre pesos e metas</h3>
                <p className={styles.stepDesc}>
                  Ajuste o peso de cada disciplina de 1 a 5 para que o cronograma priorize o que mais pontua na sua prova.
                </p>
              </li>

              <li className={styles.stepItem}>
                <span className={styles.stepNumber}>03</span>
                <h3 className={styles.stepTitle}>Execute o cronograma do dia</h3>
                <p className={styles.stepDesc}>
                  Acione o cronômetro no bloco do dia, marque tópicos vencidos e acompanhe sua evolução líquida.
                </p>
              </li>
            </ol>

            <div className={styles.ctaBanner}>
              <div className={styles.ctaBannerCopy}>
                <h2 className={styles.ctaBannerTitle}>
                  Pronto para colocar seu estudo em ritmo de aprovação?
                </h2>
                <p className={styles.ctaBannerSub}>
                  Comece gratuitamente importando uma trilha oficial ou configurando seu próprio cronograma.
                </p>
              </div>
              <div className={styles.ctaBannerActions}>
                <Button
                  type="button"
                  variant="primary"
                  size="lg"
                  rightIcon={<ArrowRight size={17} />}
                  onClick={isAuthenticated ? onEntrarApp : () => onCriarConta?.()}
                >
                  {isAuthenticated ? 'Abrir Meu Painel' : 'Começar agora'}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  size="lg"
                  onClick={onAbrirCatalogo}
                >
                  Explorar catálogo
                </Button>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* ── FOOTER MINIMALISTA ── */}
      <footer className={styles.footer}>
        <div className={styles.container}>
          <div className={styles.footerInner}>
            <div className={styles.footerBrand}>
              <span className={styles.brandMark} aria-hidden="true">
                <img src={logoMark} alt="Trilhara" width={15} height={15} />
              </span>
              <strong>Trilhara</strong>
              <span className={styles.footerCopy}>
                Gestão de estudos, cronogramas e editais verticalizados.
              </span>
            </div>
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
