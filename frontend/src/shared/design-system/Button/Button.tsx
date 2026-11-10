import React from 'react';
import styles from './Button.module.css';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'success' | 'warning';
  size?: 'sm' | 'md' | 'lg';
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  isLoading?: boolean;
  fullWidth?: boolean;
  iconOnly?: boolean;
  selected?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  leftIcon,
  rightIcon,
  isLoading = false,
  fullWidth = false,
  iconOnly = false,
  selected,
  className = '',
  disabled,
  ...props
}) => {
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
      className={classNames}
      disabled={disabled || isLoading}
      aria-disabled={disabled || isLoading}
      aria-pressed={selected}
      aria-busy={isLoading || undefined}
      {...props}
    >
      {isLoading && <span className={styles.spinner} aria-hidden="true" />}
      <span className={`${styles.content} ${isLoading ? styles.loadingContent : ''}`}>
        {leftIcon && <span className={styles.icon}>{leftIcon}</span>}
        {children && <span className={styles.label}>{children}</span>}
        {rightIcon && <span className={styles.icon}>{rightIcon}</span>}
      </span>
    </button>
  );
};
