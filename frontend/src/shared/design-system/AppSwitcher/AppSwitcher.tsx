import React, { useState, useRef, useEffect } from 'react';
import {
  LayoutGrid,
  ChevronDown,
  GraduationCap,
  CheckSquare,
  FileText,
  Timer,
  type LucideIcon
} from 'lucide-react';
import {
  ModuloApp,
  obterModuloAtual,
  obterUrlAppEstudos,
  obterUrlAppTarefas,
  obterUrlAppPomodoro,
  obterUrlAppNotas,
  iniciarHandoffParaApp
} from '../../auth/sso';
import styles from './AppSwitcher.module.css';

export interface AppConfig {
  id: ModuloApp;
  nome: string;
  descricao: string;
  icone: LucideIcon;
  obterUrl: () => string;
}

export const APPS_TRILHARA: readonly AppConfig[] = [
  {
    id: 'estudos',
    nome: 'Estudos',
    descricao: 'Ciclos & Editais',
    icone: GraduationCap,
    obterUrl: obterUrlAppEstudos
  },
  {
    id: 'tarefas',
    nome: 'Tarefas',
    descricao: 'Listas & Prazos',
    icone: CheckSquare,
    obterUrl: obterUrlAppTarefas
  },
  {
    id: 'notas',
    nome: 'Notas',
    descricao: 'Cadernos & Resumos',
    icone: FileText,
    obterUrl: obterUrlAppNotas
  },
  {
    id: 'pomodoro',
    nome: 'Pomodoro',
    descricao: 'Foco & Sessões',
    icone: Timer,
    obterUrl: obterUrlAppPomodoro
  }
];

export interface AppSwitcherProps {
  moduloAtual?: ModuloApp;
  compacto?: boolean;
  className?: string;
  onTrocarApp?: (modulo: ModuloApp, urlDestino: string) => void;
}

export const AppSwitcher: React.FC<AppSwitcherProps> = ({
  moduloAtual,
  compacto = false,
  className = '',
  onTrocarApp
}) => {
  const [aberto, setAberto] = useState<boolean>(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const moduloAtivo = moduloAtual || obterModuloAtual();
  const appAtualConfig = APPS_TRILHARA.find((a) => a.id === moduloAtivo) || APPS_TRILHARA[0];
  const IconeAtual = appAtualConfig.icone;

  // Fecha o dropdown ao clicar fora
  useEffect(() => {
    if (!aberto) return;

    const handleClickFora = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setAberto(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setAberto(false);
      }
    };

    document.addEventListener('mousedown', handleClickFora);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleClickFora);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [aberto]);

  const handleSelecionarModulo = async (app: AppConfig, event: React.MouseEvent) => {
    event.preventDefault();
    setAberto(false);

    if (app.id === moduloAtivo) {
      return;
    }

    const urlBase = app.obterUrl();
    if (onTrocarApp) {
      onTrocarApp(app.id, urlBase);
      return;
    }

    // Executa SSO handoff quando necessário
    const urlFinal = await iniciarHandoffParaApp(urlBase);
    window.location.href = urlFinal;
  };

  return (
    <div
      ref={containerRef}
      className={`${styles.appSwitcherContainer} ${className}`.trim()}
    >
      <button
        type="button"
        className={`${styles.switcherTrigger} ${compacto ? styles.switcherTriggerCompact : ''}`}
        onClick={() => setAberto((prev) => !prev)}
        aria-haspopup="menu"
        aria-expanded={aberto}
        aria-label={`Alternar aplicativo Trilhara (ativo: ${appAtualConfig.nome})`}
        title={`Alternar aplicativo Trilhara (ativo: ${appAtualConfig.nome})`}
      >
        <div className={styles.triggerContent}>
          <div className={styles.triggerIcon}>
            {compacto ? <LayoutGrid size={18} /> : <IconeAtual size={18} />}
          </div>
          {!compacto && (
            <div className={styles.triggerText}>
              <span className={styles.currentAppName}>{appAtualConfig.nome}</span>
              <span className={styles.switcherHint}>Ecossistema</span>
            </div>
          )}
        </div>
        {!compacto && (
          <ChevronDown
            size={14}
            className={`${styles.chevronIcon} ${aberto ? styles.chevronOpen : ''}`}
          />
        )}
      </button>

      {aberto && (
        <div
          className={styles.switcherDropdown}
          role="menu"
          aria-label="Aplicativos do Ecossistema Trilhara"
        >
          <div className={styles.switcherHeader}>Módulos Trilhara</div>
          {APPS_TRILHARA.map((app) => {
            const AppIcon = app.icone;
            const ativo = app.id === moduloAtivo;

            return (
              <a
                key={app.id}
                href={app.obterUrl()}
                className={`${styles.appItem} ${ativo ? styles.appItemActive : ''}`}
                role="menuitem"
                aria-current={ativo ? 'page' : undefined}
                onClick={(e) => handleSelecionarModulo(app, e)}
              >
                <div className={styles.appItemContent}>
                  <div className={styles.appIconWrap}>
                    <AppIcon size={16} />
                  </div>
                  <div className={styles.appMeta}>
                    <span className={styles.appName}>{app.nome}</span>
                    <span className={styles.appDesc}>{app.descricao}</span>
                  </div>
                </div>
                {ativo && <span className={styles.activeBadge}>Atual</span>}
              </a>
            );
          })}
        </div>
      )}
    </div>
  );
};
