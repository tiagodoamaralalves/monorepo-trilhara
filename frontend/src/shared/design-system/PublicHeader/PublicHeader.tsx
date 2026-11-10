import React from 'react';
import { ArrowRight } from 'lucide-react';
import logoMark from '@shared/assets/logo-mark.svg';
import { Button } from '../Button/Button';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import styles from './PublicHeader.module.css';

export interface PublicHeaderProps {
  contextLabel?: string;
  onHomeClick?: () => void;
  onLogin?: () => void;
  onEntrarApp?: () => void;
  onCriarConta?: () => void;
  onIrParaPainel?: () => void;
  isAuthenticated?: boolean;
  usuarioNome?: string;
  totalMinhasTrilhas?: number;
}

export const PublicHeader: React.FC<PublicHeaderProps> = ({
  contextLabel,
  onHomeClick,
  onLogin,
  onEntrarApp,
  onCriarConta,
  onIrParaPainel,
  isAuthenticated = false,
  usuarioNome,
  totalMinhasTrilhas = 0
}) => {
  const handleHomeClick = onHomeClick ?? (() => {
    if (typeof window === 'undefined') return;
    const url = new URL(window.location.href);
    url.hash = '';
    window.history.replaceState(null, '', `${url.pathname}${url.search}`);
  });

  const handlePrimaryAction = () => {
    if (isAuthenticated) {
      onIrParaPainel?.();
      return;
    }

    onEntrarApp?.();
  };

  const primaryLabel = isAuthenticated
    ? totalMinhasTrilhas > 0
      ? `Meu Painel (${totalMinhasTrilhas})`
      : usuarioNome
        ? `Painel de ${usuarioNome.split(' ')[0]}`
        : 'Abrir Painel'
    : 'Abrir Painel';

  return (
    <header className={styles.navbar}>
      <div className={styles.navbarInner}>
        {contextLabel ? (
          <div className={styles.brandGroup}>
            <button
              type="button"
              className={styles.brandButton}
              onClick={handleHomeClick}
              title="Voltar para a página inicial"
            >
              <img className={styles.brandLogo} src={logoMark} alt="Trilhara" />
              <span className={styles.brandName}>Trilhara</span>
            </button>
            <span className={styles.brandDivider} aria-hidden="true" />
            <span className={styles.pageContextLabel}>{contextLabel}</span>
          </div>
        ) : (
          <button
            type="button"
            className={styles.brandLink}
            aria-label="Trilhara — Página inicial"
            onClick={handleHomeClick}
          >
            <span className={styles.brandMark} aria-hidden="true">
              <img src={logoMark} alt="Trilhara" width={18} height={18} />
            </span>
            <span className={styles.brandName}>Trilhara</span>
          </button>
        )}

        <div className={styles.navActions}>
          <ThemeToggle size="sm" />

          {isAuthenticated ? (
            <Button
              type="button"
              variant="primary"
              size="sm"
              rightIcon={<ArrowRight size={14} />}
              onClick={handlePrimaryAction}
            >
              {primaryLabel}
            </Button>
          ) : (
            <>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={onLogin || onEntrarApp}
              >
                Acessar
              </Button>
              <Button
                type="button"
                variant="primary"
                size="sm"
                rightIcon={<ArrowRight size={14} />}
                onClick={onCriarConta || onEntrarApp}
              >
                Criar conta
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
};
