import { useEffect, useRef, useState } from 'react';

export interface UseDelayedFlagOptions {
  /** Tempo em ms antes de a flag virar `true`. Padrão: 200 ms. */
  atrasoMs?: number;
  /** Tempo mínimo em ms que a flag permanece `true` depois de ativada. Padrão: 300 ms. */
  minimoVisivelMs?: number;
}

/**
 * Evita *flicker* de estados de carregamento.
 *
 * a flag só liga depois de `atrasoMs` (padrão 200 ms);
 * depois de ligada, permanece no mínimo `minimoVisivelMs` (padrão 300 ms).
 *
 * Use em `Skeleton`/`Spinner` para que carregamentos rápidos não pisquem na tela.
 */
export function useDelayedFlag(ativo: boolean, options: UseDelayedFlagOptions = {}): boolean {
  const { atrasoMs = 200, minimoVisivelMs = 300 } = options;
  const [visivel, setVisivel] = useState(false);
  const ligouEmRef = useRef<number | null>(null);

  useEffect(() => {
    if (!ativo) return;

    const timer = window.setTimeout(() => {
      ligouEmRef.current = Date.now();
      setVisivel(true);
    }, atrasoMs);

    return () => window.clearTimeout(timer);
  }, [ativo, atrasoMs]);

  useEffect(() => {
    if (ativo || !visivel) return;
    const ligouEm = ligouEmRef.current;
    const restante = ligouEm == null ? 0 : Math.max(0, minimoVisivelMs - (Date.now() - ligouEm));
    const timer = window.setTimeout(() => {
      ligouEmRef.current = null;
      setVisivel(false);
    }, restante);
    return () => window.clearTimeout(timer);
  }, [ativo, visivel, minimoVisivelMs]);

  return visivel;
}
