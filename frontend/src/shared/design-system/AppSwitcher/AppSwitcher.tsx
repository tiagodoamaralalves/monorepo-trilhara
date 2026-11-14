import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
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
import { unsavedChangesRegistry } from '../../state/unsavedChangesRegistry';
import { ConfirmContext } from '../ConfirmDialog/ConfirmDialog';
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
    descricao: 'Cronogramas & Editais',
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

/**
 * Alternador de módulos no padrão WAI-ARIA APG *Menu Button*.
 *
 * Teclado: Enter/Espaço/↓ abre e foca o primeiro item; ↑/↓ circulam; Home/End vão aos
 * extremos; Escape fecha e devolve o foco ao botão; Tab fecha. Antes de navegar consulta o
 * `unsavedChangesRegistry` e pede confirmação quando há alterações não salvas.
 */
export const AppSwitcher: React.FC<AppSwitcherProps> = ({
  moduloAtual,
  compacto = false,
  className = '',
  onTrocarApp
}) => {
  const [aberto, setAberto] = useState<boolean>(false);
  const [indiceAtivo, setIndiceAtivo] = useState<number>(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const itensRef = useRef<Array<HTMLAnchorElement | null>>([]);

  const confirmContext = React.useContext(ConfirmContext);

  const moduloAtivo = moduloAtual || obterModuloAtual();
  const appAtualConfig = APPS_TRILHARA.find((a) => a.id === moduloAtivo) || APPS_TRILHARA[0];
  const IconeAtual = appAtualConfig.icone;

  const fecharComFoco = useCallback(() => {
    setAberto(false);
    triggerRef.current?.focus();
  }, []);

  const abrir = useCallback(() => {
    const indiceAtual = APPS_TRILHARA.findIndex((a) => a.id === moduloAtivo);
    setIndiceAtivo(indiceAtual >= 0 ? indiceAtual : 0);
    setAberto(true);
  }, [moduloAtivo]);

  // Foco programático no item ativo após abrir (roving tabindex).
  useLayoutEffect(() => {
    if (!aberto) return;
    itensRef.current[indiceAtivo]?.focus();
  }, [aberto, indiceAtivo]);

  // Clique fora fecha sem mover o foco.
  useEffect(() => {
    if (!aberto) return;

    const handleClickFora = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setAberto(false);
      }
    };

    document.addEventListener('mousedown', handleClickFora);
    return () => document.removeEventListener('mousedown', handleClickFora);
  }, [aberto]);

  const solicitarTroca = useCallback(
    async (app: AppConfig, urlBase: string) => {
      const mesmoModulo = app.id === moduloAtivo;

      if (mesmoModulo && !onTrocarApp) {
        fecharComFoco();
        return;
      }

      // grava rascunhos e confirma antes de destruir o estado em memória.
      unsavedChangesRegistry.flushTodos();

      if (unsavedChangesRegistry.temAlteracoes() && confirmContext) {
        const sair = await confirmContext.confirm({
          title: 'Você tem alterações não salvas',
          description:
            'Ao trocar de módulo o estado não salvo deste formulário será perdido. Deseja sair mesmo assim?',
          confirmText: 'Sair mesmo assim',
          cancelText: 'Ficar',
          variant: 'warning'
        });
        if (!sair) {
          triggerRef.current?.focus();
          return;
        }
        unsavedChangesRegistry.limpar();
      }

      setAberto(false);

      if (onTrocarApp) {
        onTrocarApp(app.id, urlBase);
        return;
      }

      const urlFinal = await iniciarHandoffParaApp(urlBase);
      window.location.href = urlFinal;
    },
    [moduloAtivo, onTrocarApp, confirmContext, fecharComFoco]
  );

  const handleKeyDownTrigger = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    switch (event.key) {
      case 'Enter':
      case ' ':
      case 'Spacebar':
      case 'ArrowDown':
      case 'Down':
        event.preventDefault();
        abrir();
        break;
      case 'ArrowUp':
      case 'Up':
        event.preventDefault();
        setIndiceAtivo(APPS_TRILHARA.length - 1);
        setAberto(true);
        break;
      default:
        break;
    }
  };

  const handleKeyDownItem = (event: React.KeyboardEvent<HTMLAnchorElement>, indice: number) => {
    const ultimo = APPS_TRILHARA.length - 1;
    switch (event.key) {
      case 'ArrowDown':
      case 'Down':
        event.preventDefault();
        setIndiceAtivo(indice >= ultimo ? 0 : indice + 1);
        break;
      case 'ArrowUp':
      case 'Up':
        event.preventDefault();
        setIndiceAtivo(indice <= 0 ? ultimo : indice - 1);
        break;
      case 'Home':
        event.preventDefault();
        setIndiceAtivo(0);
        break;
      case 'End':
        event.preventDefault();
        setIndiceAtivo(ultimo);
        break;
      case 'Escape':
      case 'Esc':
        event.preventDefault();
        fecharComFoco();
        break;
      case 'Tab':
        setAberto(false);
        break;
      case 'Enter':
      case ' ':
      case 'Spacebar': {
        event.preventDefault();
        const app = APPS_TRILHARA[indice];
        void solicitarTroca(app, app.obterUrl());
        break;
      }
      default:
        break;
    }
  };

  return (
    <div ref={containerRef} className={`${styles.appSwitcherContainer} ${className}`.trim()}>
      <button
        ref={triggerRef}
        type="button"
        className={`${styles.switcherTrigger} ${compacto ? styles.switcherTriggerCompact : ''}`}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        onKeyDown={handleKeyDownTrigger}
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
            aria-hidden="true"
            className={`${styles.chevronIcon} ${aberto ? styles.chevronOpen : ''}`}
          />
        )}
      </button>

      {aberto && (
        <div className={styles.switcherDropdown} role="menu" aria-label="Aplicativos do Ecossistema Trilhara">
          <div className={styles.switcherHeader}>Módulos Trilhara</div>
          {APPS_TRILHARA.map((app, indice) => {
            const AppIcon = app.icone;
            const ativo = app.id === moduloAtivo;

            return (
              <a
                key={app.id}
                ref={(elemento) => {
                  itensRef.current[indice] = elemento;
                }}
                href={app.obterUrl()}
                className={`${styles.appItem} ${ativo ? styles.appItemActive : ''}`}
                role="menuitem"
                tabIndex={indice === indiceAtivo ? 0 : -1}
                aria-current={ativo ? 'page' : undefined}
                onKeyDown={(event) => handleKeyDownItem(event, indice)}
                onClick={(event) => {
                  event.preventDefault();
                  void solicitarTroca(app, app.obterUrl());
                }}
              >
                <div className={styles.appItemContent}>
                  <div className={styles.appIconWrap}>
                    <AppIcon size={16} aria-hidden="true" />
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

