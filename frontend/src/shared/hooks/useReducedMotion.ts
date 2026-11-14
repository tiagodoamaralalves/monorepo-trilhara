import { useMediaQuery } from './useMediaQuery';

const CONSULTA = '(prefers-reduced-motion: reduce)';

/**
 * Preferência de movimento reduzido do usuário (`prefers-reduced-motion: reduce`).
 *
 * Requisito WCAG 2.2 §12.1.5: animações contínuas e alternâncias rápidas de título
 * devem ser desligadas para quem registra a preferência. Uso típico:
 *
 * ```tsx
 * const reduzirMovimento = useReducedMotion();
 * alertarTitulo('Pausa finalizada', { reduzirMovimento });
 * ```
 *
 * lê o valor inicial de `matchMedia` e acompanha mudanças em tempo real;
 * degrada para `false` sem `matchMedia` (jsdom antigo, SSR), mantendo o
 * comportamento padrão em vez de quebrar o render.
 */
export function useReducedMotion(): boolean {
  return useMediaQuery(CONSULTA);
}
