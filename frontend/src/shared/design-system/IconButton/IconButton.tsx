import React from 'react';
import styles from './IconButton.module.css';

export interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Ícone obrigatório: é o conteúdo acessível visível (acompanha `aria-label`). */
  icon: React.ReactNode;
  /** Rótulo acessível obrigatório — o ícone é decorativo (D-40). */
  'aria-label': string;
  variant?: 'ghost' | 'surface' | 'danger';
  size?: 'sm' | 'md';
  /** Estado de seleção (ex.: item de barra inferior ativo). */
  selected?: boolean;
  /** Ação em andamento: desabilita e expõe `aria-busy` (D-37). */
  loading?: boolean;
}

/**
 * Botão somente-ícone com alvo mínimo garantido (WCAG 2.2 SC 2.5.8).
 *
 * Existe para substituir os botões de ícone ad hoc (34 `className={styles.iconBtn}`,
 * `logoutBtn` de 28×28 na `AppSidebar`, ações de item de agenda): o alvo mínimo vem
 * de `--target-min` (24px, 40px em `pointer: coarse`) em vez de uma regra global
 * sobre `button`, que distorcia controles pequenos.
 *
 * O `aria-label` é obrigatório por tipo: um botão só com ícone não tem nome
 * acessível e viola WCAG 4.1.2.
 */
export const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      icon,
      variant = 'ghost',
      size = 'md',
      selected,
      loading = false,
      className = '',
      disabled,
      type = 'button',
      ...props
    },
    ref
  ) => {
    const classNames = [
      styles.iconButton,
      styles[variant],
      styles[size],
      selected ? styles.selected : '',
      className
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <button
        ref={ref}
        type={type}
        className={classNames}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        aria-pressed={selected === undefined ? undefined : selected}
        {...props}
      >
        <span className={styles.icon} aria-hidden="true">
          {icon}
        </span>
      </button>
    );
  }
);

IconButton.displayName = 'IconButton';
