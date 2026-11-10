import React from 'react';
import styles from './DisciplineLabel.module.css';

export interface DisciplineLabelProps {
  name: string;
  color?: string;
  className?: string;
}

export const DisciplineLabel: React.FC<DisciplineLabelProps> = ({
  name,
  color,
  className = ''
}) => (
  <span className={`${styles.label} ${className}`}>
    <span
      className={styles.color}
      style={{ '--discipline-color': color || 'var(--primary)' } as React.CSSProperties}
      aria-hidden="true"
    />
    <span className={styles.name}>{name}</span>
  </span>
);
