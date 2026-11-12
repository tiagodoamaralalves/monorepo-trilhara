import { useCallback, useEffect, useRef, useState } from 'react';

type StatusRecurso = 'carregando' | 'sucesso' | 'erro';

export interface UseRecursoResultado<T> {
  /** `carregando` apenas na primeira carga; refetch usa `recarregando`. */
  status: StatusRecurso;
  dados: T | undefined;
  erro: unknown;
  /** `true` durante um refetch com dados já exibidos (mostra `Spinner`, nunca `Skeleton`). */
  recarregando: boolean;
  recarregar: () => void;
}

/**
 * Ciclo de vida único de carregamento de recurso de seção/lista (D-37).
 *
 * Regras aplicadas:
 * - a lista **nunca** começa como `[]`: `dados` é `undefined` até a primeira resposta, o
 *   que impede renderizar `EmptyState` antes de saber se há erro;
 * - refetch mantém `status: 'sucesso'` e apenas liga `recarregando` (dados atuais + spinner);
 * - erros são propagados em `erro` para `classificarErro` decidir o estado visual.
 */
export function useRecurso<T>(
  carregar: (signal: AbortSignal) => Promise<T>,
  deps: ReadonlyArray<unknown> = []
): UseRecursoResultado<T> {
  const [status, setStatus] = useState<StatusRecurso>('carregando');
  const [dados, setDados] = useState<T | undefined>(undefined);
  const [erro, setErro] = useState<unknown>(undefined);
  const [recarregando, setRecarregando] = useState(false);
  const [gatilho, setGatilho] = useState(0);
  const jaCarregouRef = useRef(false);
  const carregarRef = useRef(carregar);

  useEffect(() => {
    carregarRef.current = carregar;
  }, [carregar]);

  useEffect(() => {
    const controller = new AbortController();
    let ativo = true;

    if (jaCarregouRef.current) {
      setRecarregando(true);
    } else {
      setStatus('carregando');
    }

    carregarRef.current(controller.signal)
      .then((resultado) => {
        if (!ativo) return;
        setDados(resultado);
        setErro(undefined);
        setStatus('sucesso');
      })
      .catch((falha) => {
        if (!ativo || controller.signal.aborted) return;
        setErro(falha);
        setStatus('erro');
      })
      .finally(() => {
        if (!ativo) return;
        jaCarregouRef.current = true;
        setRecarregando(false);
      });

    return () => {
      ativo = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gatilho, ...deps]);

  const recarregar = useCallback(() => setGatilho((atual) => atual + 1), []);

  return { status, dados, erro, recarregando, recarregar };
}
