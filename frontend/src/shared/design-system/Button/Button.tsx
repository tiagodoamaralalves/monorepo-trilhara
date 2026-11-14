import React from 'react';
import styles from './Button.module.css';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'success' | 'warning';
  size?: 'sm' | 'md' | 'lg';
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  /** Ação em andamento: spinner interno, largura preservada, `aria-busy` e desabilitado. */
  loading?: boolean;
  fullWidth?: boolean;
  iconOnly?: boolean;
  selected?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({
  children,
  variant = 'primary',
  size = 'md',
  leftIcon,
  rightIcon,
  loading = false,
  fullWidth = false,
  iconOnly = false,
  selected,
  className = '',
  disabled,
  ...props
}, ref) => {
  const classNames = [
    styles.btn,
    styles[variant],
    styles[size],
    fullWidth ? styles.fullWidth : '',
    iconOnly ? styles.iconOnly : '',
    selected ? styles.selected : '',
    className
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      ref={ref}
      className={classNames}
      disabled={disabled || loading}
      aria-disabled={disabled || loading}
      aria-pressed={selected}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <span className={styles.spinner} aria-hidden="true" />}
      <span className={`${styles.content} ${loading ? styles.loadingContent : ''}`}>
        {leftIcon && <span className={styles.icon}>{leftIcon}</span>}
        {children && <span className={styles.label}>{children}</span>}
        {rightIcon && <span className={styles.icon}>{rightIcon}</span>}
      </span>
    </button>
  );
});

Button.displayName = 'Button';
