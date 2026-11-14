import React from 'react';
import styles from './EmptyState.module.css';

export interface EmptyStateProps {
  /** Ícone decorativo do contexto (ex.: `<BookMarked size={44} />`). */
  icone: React.ReactNode;
  titulo: string;
  descricao: string;
  /** Ação primária opcional (normalmente um `Button`). */
  acaoPrimaria?: React.ReactNode;
  className?: string;
}

/**
 * Lista vazia com sucesso.
 *
 * Só pode ser renderizado após resposta `2xx` com zero itens — nunca como estado inicial
 * de uma lista que ainda não carregou (use `Skeleton` e `useRecurso`).
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  icone,
  titulo,
  descricao,
  acaoPrimaria,
  className = ''
}) => (
  <div className={`${styles.container} ${className}`.trim()} data-testid="empty-state">
    <div className={styles.iconWrapper} aria-hidden="true">{icone}</div>
    <h3 className={styles.title}>{titulo}</h3>
    <p className={styles.description}>{descricao}</p>
    {acaoPrimaria && <div className={styles.actions}>{acaoPrimaria}</div>}
  </div>
);


