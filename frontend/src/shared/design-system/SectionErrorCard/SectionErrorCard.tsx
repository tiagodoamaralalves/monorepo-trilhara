import React from 'react';
import { AlertCircle, RotateCcw } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './SectionErrorCard.module.css';

export interface SectionErrorCardProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  isRetrying?: boolean;
  className?: string;
}

/**
 * Card compacto de erro recuperável do Design System Trilhara.
 * Utilizado para falhas parciais em seções da Visão Hoje e listagens paginadas.
 */
export const SectionErrorCard: React.FC<SectionErrorCardProps> = ({
  title = 'Erro ao carregar seção',
  message = 'Não foi possível carregar os dados desta seção.',
  onRetry,
  isRetrying = false,
  className = ''
}) => {
  return (
    <div
      role="alert"
      className={`${styles.card} ${className}`.trim()}
      aria-live="polite"
      data-testid="section-error-card"
    >
      <div className={styles.content}>
        <AlertCircle size={20} className={styles.icon} aria-hidden="true" />
        <div className={styles.textGroup}>
          <h4 className={styles.title}>{title}</h4>
          {message && <p className={styles.message}>{message}</p>}
        </div>
      </div>
      {onRetry && (
        <div className={styles.actions}>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={onRetry}
            isLoading={isRetrying}
            leftIcon={<RotateCcw size={14} />}
            aria-label="Tentar novamente"
          >
            Tentar novamente
          </Button>
        </div>
      )}
    </div>
  );
};
