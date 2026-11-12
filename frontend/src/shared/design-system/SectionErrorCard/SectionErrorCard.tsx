import React from 'react';
import { AlertCircle, RotateCcw } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './SectionErrorCard.module.css';

type SectionErrorVariant = 'section' | 'inline';

export interface SectionErrorCardProps {
  titulo: string;
  descricao: string;
  onRetry?: () => void;
  /** `section` (padrão) ocupa a área da seção; `inline` fica dentro de um subgrupo. */
  variant?: SectionErrorVariant;
  /** Bloqueia o botão enquanto o retry está em andamento. */
  recarregando?: boolean;
  className?: string;
}

/**
 * Falha recuperável de seção/lista (D-37).
 *
 * Use para falha de rede, 5xx, 503 e timeout — **nunca** para 401 (nesse caso abra o
 * `AuthModal`). O botão "Tentar novamente" é ligado à descrição por `aria-describedby`.
 */
export const SectionErrorCard: React.FC<SectionErrorCardProps> = ({
  titulo,
  descricao,
  onRetry,
  variant = 'section',
  recarregando = false,
  className = ''
}) => {
  const descricaoId = React.useId();

  return (
    <div
      role="alert"
      aria-live="polite"
      data-testid="section-error-card"
      data-variant={variant}
      className={`${styles.card} ${styles[variant]} ${className}`.trim()}
    >
      <div className={styles.content}>
        <AlertCircle size={20} className={styles.icon} aria-hidden="true" />
        <div className={styles.textGroup}>
          <h4 className={styles.title}>{titulo}</h4>
          <p id={descricaoId} className={styles.message}>{descricao}</p>
        </div>
      </div>
      {onRetry && (
        <div className={styles.actions}>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={onRetry}
            loading={recarregando}
            leftIcon={<RotateCcw size={14} />}
            aria-describedby={descricaoId}
          >
            Tentar novamente
          </Button>
        </div>
      )}
    </div>
  );
};

