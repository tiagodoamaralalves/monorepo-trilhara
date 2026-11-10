import React from 'react';
import { Check, Loader2, CloudUpload, AlertCircle, RotateCcw } from 'lucide-react';
import styles from './SyncStatusBadge.module.css';

export type SyncStatus = 'salvo' | 'salvando' | 'alteracoes-locais' | 'erro';

export interface SyncStatusBadgeProps {
  status: SyncStatus;
  onRetry?: () => void;
  className?: string;
}

/**
 * Indicador visual compacto com role="status" e aria-live="polite"
 * Atende aos requisitos da FASE 8 do Design System Trilhara.
 */
export const SyncStatusBadge: React.FC<SyncStatusBadgeProps> = ({
  status,
  onRetry,
  className = ''
}) => {
  const renderContent = () => {
    switch (status) {
      case 'salvo':
        return (
          <>
            <span className={styles.icon} aria-hidden="true">
              <Check size={13} />
            </span>
            <span className={styles.text}>Salvo</span>
          </>
        );

      case 'salvando':
        return (
          <>
            <span className={`${styles.icon} ${styles.spinner}`} aria-hidden="true">
              <Loader2 size={13} />
            </span>
            <span className={styles.text}>Salvando...</span>
          </>
        );

      case 'alteracoes-locais':
        return (
          <>
            <span className={styles.icon} aria-hidden="true">
              <CloudUpload size={13} />
            </span>
            <span className={styles.text}>Alterações locais pendentes</span>
          </>
        );

      case 'erro':
        return (
          <>
            <span className={styles.icon} aria-hidden="true">
              <AlertCircle size={13} />
            </span>
            <span className={styles.text}>Erro ao salvar</span>
            {onRetry && (
              <button
                type="button"
                className={styles.retryBtn}
                onClick={onRetry}
                aria-label="Tentar novamente"
              >
                <RotateCcw size={11} aria-hidden="true" />
                <span>Tentar novamente</span>
              </button>
            )}
          </>
        );

      default:
        return null;
    }
  };

  const getStatusClass = () => {
    switch (status) {
      case 'salvo':
        return styles.salvo;
      case 'salvando':
        return styles.salvando;
      case 'alteracoes-locais':
        return styles.alteracoesLocais;
      case 'erro':
        return styles.erro;
    }
  };

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="sync-status-badge"
      data-status={status}
      className={`${styles.badge} ${getStatusClass()} ${className}`.trim()}
    >
      {renderContent()}
    </div>
  );
};
