import React from 'react';
import { Scale } from 'lucide-react';
import styles from './DisciplinaPesoSelector.module.css';

export interface DisciplinaPesoSelectorProps {
  peso: number;
  onChange: (peso: number) => void;
  className?: string;
  compact?: boolean;
}

export const DisciplinaPesoSelector: React.FC<DisciplinaPesoSelectorProps> = ({
  peso,
  onChange,
  className = '',
  compact = false
}) => {
  const options = [1, 2, 3, 4, 5];

  return (
    <div
      className={`${styles.selectorWrapper} ${compact ? styles.compact : ''} ${className}`}
      role="group"
      aria-label="Peso da disciplina"
      title="O novo peso será aplicado na geração dos próximos ciclos de estudo"
    >
      <div
        className={styles.labelGroup}
        title="O novo peso será aplicado na geração dos próximos ciclos de estudo"
      >
        <Scale size={15} aria-hidden="true" className={styles.scaleIcon} />
        <span className={styles.label}>Peso</span>
      </div>

      <div className={`${styles.periodPills} ${compact ? styles.periodPillsCompact : ''}`}>
        {options.map((option) => {
          const isSelected = option === peso;
          return (
            <button
              key={option}
              type="button"
              className={`${styles.periodPill} ${compact ? styles.periodPillCompact : ''} ${isSelected ? styles.periodPillActive : ''}`}
              aria-pressed={isSelected}
              aria-label={`Peso ${option}x`}
              onClick={() => onChange(option)}
            >
              {option}x
            </button>
          );
        })}
      </div>
    </div>
  );
};
