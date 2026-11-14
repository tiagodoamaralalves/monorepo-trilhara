import React from 'react';
import styles from './Spinner.module.css';

export interface SpinnerProps {
  /** Rótulo acessível; padrão "Carregando…". */
  rotulo?: string;
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * Indicador de atividade para refetch e ações pontuais.
 *
 * Para carregamento inicial de seção use `Skeleton`; para ação de botão use `Button loading`.
 */
export const Spinner: React.FC<SpinnerProps> = ({ rotulo = 'Carregando…', size = 'sm', className = '' }) => (
  <span
    className={`${styles.spinner} ${styles[size]} ${className}`.trim()}
    role="status"
    aria-live="polite"
  >
    <span className={styles.circulo} aria-hidden="true" />
    <span className={styles.srOnly}>{rotulo}</span>
  </span>
);
