import React from 'react';
import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../Theme/useTheme';
import styles from './ThemeToggle.module.css';

export interface ThemeToggleProps {
  className?: string;
  size?: 'sm' | 'md';
  label?: string;
  showLabel?: boolean;
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({
  className = '',
  size = 'sm',
  label,
  showLabel = true
}) => {
  const { isLight, toggleTheme } = useTheme();
  const labelText = label ?? (isLight ? 'Claro' : 'Escuro');

  return (
    <button
      type="button"
      className={`${styles.themeToggle} ${size === 'sm' ? styles.sizeSm : styles.sizeMd} ${className}`.trim()}
      onClick={toggleTheme}
      aria-label={isLight ? 'Alternar para o modo escuro' : 'Alternar para o modo claro'}
      aria-pressed={isLight}
      title={isLight ? 'Alternar para o modo escuro' : 'Alternar para o modo claro'}
    >
      <span className={styles.themeToggleIcon} aria-hidden="true">
        {isLight ? <Moon size={size === 'sm' ? 14 : 16} /> : <Sun size={size === 'sm' ? 14 : 16} />}
      </span>
      {showLabel && <span className={styles.themeToggleLabel}>{labelText}</span>}
    </button>
  );
};
