import React from 'react';
import styles from './Skeleton.module.css';
import { useDelayedFlag } from '../../hooks';

type SkeletonVariant = 'line' | 'row' | 'card' | 'chart';

export interface SkeletonProps {
  variant?: SkeletonVariant;
  /** Quantidade de linhas/linhas de tabela; ignorado em `card`/`chart`. */
  linhas?: number;
  className?: string;
  /** Força a exibição imediata (usado em testes e em carregamentos já conhecidos como longos). */
  carregando?: boolean;
}

/**
 * Placeholder de carregamento inicial com o formato do conteúdo.
 *
 * aparece somente após 200 ms (`useDelayedFlag`) e fica no mínimo 300 ms visíveis;
 * o container recebe `aria-busy="true"` e um texto oculto "Carregando…";
 * nunca substitui conteúdo em refetch: use `Spinner` no cabeçalho da seção.
 */
export const Skeleton: React.FC<SkeletonProps> = ({
  variant = 'line',
  linhas = 3,
  className = '',
  carregando = true
}) => {
  const visivel = useDelayedFlag(carregando);
  if (!visivel) return null;

  const quantidade = variant === 'row' || variant === 'line' ? Math.max(1, linhas) : 1;

  return (
    <div
      className={`${styles.container} ${className}`.trim()}
      aria-busy="true"
      data-testid="skeleton"
      data-variant={variant}
    >
      <span className={styles.srOnly}>Carregando…</span>
      {Array.from({ length: quantidade }).map((_, indice) => (
        <span
          key={indice}
          className={`${styles[variant]} ${styles.animado}`.trim()}
          aria-hidden="true"
        />
      ))}
    </div>
  );
};
