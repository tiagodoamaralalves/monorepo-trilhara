import React from 'react';
import styles from './ListDivider.module.css';

export interface ListDividerProps {
  label: string;
  className?: string;
}

/** Linha divisória horizontal discreta para separar grupos de uma lista (ex.: Concluídas). */
export const ListDivider: React.FC<ListDividerProps> = ({ label, className = '' }) => {
  return (
    <div className={`${styles.divider} ${className}`.trim()} role="separator" aria-label={label}>
      <span className={styles.label}>{label}</span>
    </div>
  );
};
