import React from 'react';
import {
  ArrowRight,
  Calendar,
  Check,
  CheckCircle2,
  Clock,
  Sparkles,
  Zap
} from 'lucide-react';
import { Button, ThemeToggle } from '@shared/design-system';
import logoMark from '@shared/assets/logo-mark.svg';
import styles from './LandingView.module.css';

export interface LandingViewProps {
  onLogin: () => void;
  onCriarConta: () => void;
}

export const LandingView: React.FC<LandingViewProps> = ({ onLogin, onCriarConta }) => {
  return (
    <div className={styles.page}>
      {/* ── HEADER PÚBLICO ── */}
      <header className={styles.navbar}>
        <div className={styles.navbarInner}>
          <div className={styles.brandLink}>
            <div className={styles.brandMark}>
              <img src={logoMark} alt="Trilhara" />
            </div>
            <span className={styles.brandName}>Trilhara</span>
            <span className={styles.brandTag}>Tarefas</span>
          </div>

          <div className={styles.navActions}>
            <ThemeToggle size="sm" showLabel={false} />
            <Button variant="ghost" size="sm" onClick={onLogin}>
              Entrar
            </Button>
            <Button variant="primary" size="sm" onClick={onCriarConta}>
              Criar Conta
            </Button>
          </div>
        </div>
      </header>

      {/* ── 1. HERO SECTION ── */}
      <section className={styles.heroSection}>
        <div className={styles.container}>
          <div className={styles.heroGrid}>
            <div className={styles.heroCopy}>
              <div className={styles.heroEyebrow}>
                <span className={styles.pulseDot} aria-hidden="true" />
                <span>Produtividade &amp; Foco Diário</span>
              </div>

              <h1 className={styles.heroTitle}>
                Sua rotina em ordem.{' '}
                <span className={styles.heroGradient}>Seu foco no que importa.</span>
              </h1>

              <p className={styles.heroSubtitle}>
                Organize tarefas, compromissos pessoais e metas do dia a dia com agilidade.
                Sincronize com seus ciclos de estudo do concurso quando desejar ou use como seu gerenciador de produtividade autônomo.
              </p>

              <div className={styles.heroActions}>
                <Button
                  variant="primary"
                  size="lg"
                  rightIcon={<ArrowRight size={17} />}
                  onClick={onCriarConta}
                >
                  Começar Gratuitamente
                </Button>
                <Button variant="secondary" size="lg" onClick={onLogin}>
                  Acessar Minhas Tarefas
                </Button>
              </div>
            </div>

            {/* Preview Mock da Interface */}
            <div className={styles.previewWrapper}>
              <div className={styles.mockChrome}>
                <div className={styles.mockDots} aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </div>
                <span className={styles.mockTitle}>Tarefas de Hoje</span>
                <span className={styles.mockTime}>3 pendentes</span>
              </div>

              <div className={styles.mockBody}>
                <div className={`${styles.mockTaskItem} ${styles.mockTaskDone}`}>
                  <div className={styles.mockTaskLeft}>
                    <span className={`${styles.mockCheck} ${styles.mockCheckDone}`}>
                      <Check size={12} />
                    </span>
                    <span className={styles.mockTaskTitle}>Revisar flashcards de Direito Constitucional</span>
                  </div>
                  <div className={styles.mockTaskMeta}>
                    <span className={`${styles.mockBadge} ${styles.badgeCycle}`}>Ciclo</span>
                    <span className={styles.mockTime}>08:30</span>
                  </div>
                </div>

                <div className={styles.mockTaskItem}>
                  <div className={styles.mockTaskLeft}>
                    <span className={styles.mockCheck} />
                    <span className={styles.mockTaskTitle}>Resolver 30 questões de Português (FGV)</span>
                  </div>
                  <div className={styles.mockTaskMeta}>
                    <span className={`${styles.mockBadge} ${styles.badgeHigh}`}>Alta</span>
                    <span className={styles.mockTime}>10:00</span>
                  </div>
                </div>

                <div className={styles.mockTaskItem}>
                  <div className={styles.mockTaskLeft}>
                    <span className={styles.mockCheck} />
                    <span className={styles.mockTaskTitle}>Enviar relatório mensal de trabalho</span>
                  </div>
                  <div className={styles.mockTaskMeta}>
                    <span className={`${styles.mockBadge} ${styles.badgeMid}`}>Média</span>
                    <span className={styles.mockTime}>14:30</span>
                  </div>
                </div>

                <div className={styles.mockTaskItem}>
                  <div className={styles.mockTaskLeft}>
                    <span className={styles.mockCheck} />
                    <span className={styles.mockTaskTitle}>Treino na academia (45 min)</span>
                  </div>
                  <div className={styles.mockTaskMeta}>
                    <span className={styles.mockTime}>18:00</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 2. PILARES ── */}
      <section className={styles.pillarsSection}>
        <div className={styles.container}>
          <div className={styles.sectionHeading}>
            <span className={styles.sectionEyebrow}>Funcionalidades</span>
            <h2 className={styles.sectionTitle}>Simples, rápido e integrado</h2>
            <p className={styles.sectionSub}>
              Tudo o que você precisa para gerenciar seus afazeres com clareza mental e sem burocracia.
            </p>
          </div>

          <div className={styles.pillarsGrid}>
            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <CheckCircle2 size={22} />
              </div>
              <h3 className={styles.pillarTitle}>Checklist com Prioridades</h3>
              <p className={styles.pillarDesc}>
                Defina horários, prioridades e durações estimadas para manter a clareza sobre o próximo passo da sua rotina.
              </p>
            </article>

            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <Calendar size={22} />
              </div>
              <h3 className={styles.pillarTitle}>Visão Semanal &amp; Calendário</h3>
              <p className={styles.pillarDesc}>
                Visualize compromissos distribuídos pelos dias da semana para antecipar prazos e planejar com calma.
              </p>
            </article>

            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <Zap size={22} />
              </div>
              <h3 className={styles.pillarTitle}>Sincronização com Concursos</h3>
              <p className={styles.pillarDesc}>
                Se você estuda para concursos, importe os blocos do ciclo de estudos do Trilhara para sua lista do dia com um clique.
              </p>
            </article>
          </div>
        </div>
      </section>

      {/* ── 3. CTA FINAL ── */}
      <section className={styles.ctaSection}>
        <div className={styles.container}>
          <div className={styles.ctaCard}>
            <h2 className={styles.ctaTitle}>Pronto para organizar sua rotina?</h2>
            <p className={styles.ctaSub}>
              Crie sua conta gratuitamente e experimente um jeito ágil e sem distrações de cumprir suas metas.
            </p>
            <div className={styles.ctaActions}>
              <Button
                variant="primary"
                size="lg"
                rightIcon={<ArrowRight size={17} />}
                onClick={onCriarConta}
              >
                Criar Conta Gratuita
              </Button>
              <Button variant="secondary" size="lg" onClick={onLogin}>
                Já possuo uma conta
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* ── FOOTER ── */}
      <footer className={styles.footer}>
        <div className={styles.container}>
          <p>© {new Date().getFullYear()} Trilhara · Todos os direitos reservados.</p>
        </div>
      </footer>
    </div>
  );
};

