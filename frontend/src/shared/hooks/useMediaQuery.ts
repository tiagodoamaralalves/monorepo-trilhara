import { useEffect, useState } from 'react';

function suportaMatchMedia(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function';
}

/**
 * Acompanha uma media query CSS via `window.matchMedia`, reagindo a mudanças de viewport
 * ou preferências em tempo real. Retorna `false` quando `matchMedia` não está disponível.
 */
export function useMediaQuery(consulta: string): boolean {
  const [corresponde, setCorresponde] = useState<boolean>(() =>
    suportaMatchMedia() ? window.matchMedia(consulta).matches : false
  );

  useEffect(() => {
    if (!suportaMatchMedia()) return;
    const media = window.matchMedia(consulta);
    const aoMudar = (evento: MediaQueryListEvent) => setCorresponde(evento.matches);
    setCorresponde(media.matches);

    if (typeof media.addEventListener === 'function') {
      media.addEventListener('change', aoMudar);
      return () => media.removeEventListener('change', aoMudar);
    }
    // Safari < 14 e jsdom antigo: API legada de listener.
    media.addListener(aoMudar);
    return () => media.removeListener(aoMudar);
  }, [consulta]);

  return corresponde;
}
