import { useEffect, useState } from 'react';

const CONSULTA = '(prefers-reduced-motion: reduce)';

function suportaMatchMedia(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function';
}

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
 * - lê o valor inicial de `matchMedia` e acompanha mudanças em tempo real;
 * - degrada para `false` sem `matchMedia` (jsdom antigo, SSR), mantendo o
 *   comportamento padrão em vez de quebrar o render.
 */
export function useReducedMotion(): boolean {
  const [reduzido, setReduzido] = useState<boolean>(() =>
    suportaMatchMedia() ? window.matchMedia(CONSULTA).matches : false
  );

  useEffect(() => {
    if (!suportaMatchMedia()) return;
    const media = window.matchMedia(CONSULTA);
    const aoMudar = (evento: MediaQueryListEvent) => setReduzido(evento.matches);
    setReduzido(media.matches);

    if (typeof media.addEventListener === 'function') {
      media.addEventListener('change', aoMudar);
      return () => media.removeEventListener('change', aoMudar);
    }
    // Safari < 14 e jsdom antigo: API legada de listener.
    media.addListener(aoMudar);
    return () => media.removeListener(aoMudar);
  }, []);

  return reduzido;
}
