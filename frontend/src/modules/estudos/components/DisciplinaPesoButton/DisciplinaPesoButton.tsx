import React from 'react';
import { Scale } from 'lucide-react';
import styles from './DisciplinaPesoButton.module.css';

export interface DisciplinaPesoButtonProps {
  peso: number;
  onClick?: () => void;
  className?: string;
  title?: string;
}

export const DisciplinaPesoButton: React.FC<DisciplinaPesoButtonProps> = ({
  peso,
  onClick,
  className = '',
  title
}) => {
  return (
    <button
      type="button"
      className={`${styles.button} ${className}`}
      onClick={(e) => {
        e.stopPropagation();
        onClick?.();
      }}
      title={title || `Peso: ${peso}x. Clique para editar.`}
      aria-label={`Peso atual ${peso}x. Clique para editar.`}
    >
      <Scale size={14} aria-hidden="true" className={styles.scaleIcon} />
      <span className={styles.label}>Peso</span>
      <span className={styles.pill}>{peso}x</span>
    </button>
  );
};
