import React from 'react';
import {
  ArrowRight,
  BookMarked,
  Code2,
  FileText,
  FolderTree,
  Sparkles,
  Table,
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
            <span className={styles.brandTag}>Notas</span>
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
                <span>Editor Minimalista &amp; Markdown</span>
              </div>

              <h1 className={styles.heroTitle}>
                Sua folha em branco.{' '}
                <span className={styles.heroGradient}>Suas ideias estruturadas.</span>
              </h1>

              <p className={styles.heroSubtitle}>
                Cadernos, páginas e notas com formatação Markdown em tempo real. Sem barras de ferramentas
                poluídas: apenas você e o seu raciocínio, sincronizado em nuvem com a sua conta Trilhara.
              </p>

              <div className={styles.heroActions}>
                <Button
                  variant="primary"
                  size="lg"
                  rightIcon={<ArrowRight size={17} />}
                  onClick={onCriarConta}
                >
                  Começar a Escrever
                </Button>
                <Button variant="secondary" size="lg" onClick={onLogin}>
                  Acessar Meus Cadernos
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
                <span className={styles.mockTitle}>Direito Constitucional &gt; Direitos Fundamentais</span>
                <span className={styles.mockStatus}>● Salvo</span>
              </div>

              <div className={styles.mockBody}>
                <div className={styles.mockSidebar}>
                  <div className={`${styles.mockItem} ${styles.mockItemActive}`}>
                    <FileText size={13} />
                    <span>Art. 5º CF/88</span>
                  </div>
                  <div className={styles.mockItem}>
                    <FileText size={13} />
                    <span>Remédios Const.</span>
                  </div>
                  <div className={styles.mockItem}>
                    <FileText size={13} />
                    <span>Nacionalidade</span>
                  </div>
                </div>

                <div className={styles.mockEditorArea}>
                  <div className={styles.mockEditorTitle}>Garantias Fundamentais</div>
                  <p className={styles.mockEditorParagraph}>
                    Todos são iguais perante a lei, sem distinção de qualquer natureza, garantindo-se a inviolabilidade do direito à vida, à liberdade e à igualdade.
                  </p>
                  <div className={styles.mockEditorList}>
                    <div>• <strong>Habeas Corpus:</strong> liberdade de locomoção.</div>
                    <div>• <strong>Mandado de Segurança:</strong> direito líquido e certo.</div>
                    <div>• <strong>Habeas Data:</strong> acesso e retificação de informações.</div>
                  </div>
                  <table className={styles.mockEditorTable}>
                    <thead>
                      <tr>
                        <th>Remédio</th>
                        <th>Gratuidade</th>
                        <th>Finalidade</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Habeas Corpus</td>
                        <td>Gratuito</td>
                        <td>Locomoção</td>
                      </tr>
                      <tr>
                        <td>Habeas Data</td>
                        <td>Gratuito</td>
                        <td>Informação Pessoal</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 2. RECURSOS PRINCIPAIS ── */}
      <section className={styles.featuresSection}>
        <div className={styles.container}>
          <div className={styles.sectionHeading}>
            <span className={styles.sectionEyebrow}>Produtividade sem ruído</span>
            <h2 className={styles.sectionTitle}>Feito para leitura fluida e escrita sem distrações</h2>
            <p className={styles.sectionSubtitle}>
              Reunimos a simplicidade do Markdown com a elegância de um editor WYSIWYG em tempo real.
            </p>
          </div>

          <div className={styles.pillarsGrid}>
            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <FolderTree size={20} />
              </div>
              <h3 className={styles.pillarTitle}>Hierarquia Organizada</h3>
              <p className={styles.pillarDescription}>
                Estruture seu conhecimento em <strong>Caderno &gt; Página &gt; Nota</strong>. Separe por disciplinas, matérias ou projetos e navegue com agilidade.
              </p>
            </article>

            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <Code2 size={20} />
              </div>
              <h3 className={styles.pillarTitle}>Markdown Puro &amp; Tabelas</h3>
              <p className={styles.pillarDescription}>
                Digite sintaxe nativa como cabeçalhos, listas, citações e tabelas. O editor converte tudo visualmente e armazena os dados em Markdown puro.
              </p>
            </article>

            <article className={styles.pillarCard}>
              <div className={styles.pillarIcon}>
                <Zap size={20} />
              </div>
              <h3 className={styles.pillarTitle}>Salvamento Automático</h3>
              <p className={styles.pillarDescription}>
                Seus textos são salvos automaticamente com debounce inteligente, mantendo sua sessão segura e sincronizada com o backend Trilhara.
              </p>
            </article>
          </div>
        </div>
      </section>

      {/* ── FOOTER ── */}
      <footer className={styles.footer}>
        <div className={styles.container}>
          <div className={styles.footerInner}>
            <span>Trilhara Notas &copy; {new Date().getFullYear()}</span>
            <span>Estudos · Tarefas · Pomodoro · Notas</span>
          </div>
        </div>
      </footer>
    </div>
  );
};
