import React, { useMemo } from 'react';
import { DestaqueBusca } from '../../types/nota';

export interface SnippetDestacadoProps {
  /** Texto puro devolvido pelo backend — nunca HTML. */
  snippet: string;
  /** Offsets UTF-16 em `snippet` (D-30a). */
  destaques: DestaqueBusca[];
}

interface Trecho {
  texto: string;
  destacado: boolean;
}

/**
 * Normaliza os destaques antes de renderizar: descarta intervalos inválidos, ordena e funde
 * sobreposições. Um backend que devolva offsets fora do snippet não pode quebrar a renderização.
 */
export function calcularTrechos(snippet: string, destaques: DestaqueBusca[]): Trecho[] {
  if (!snippet) return [];

  const validos = (destaques ?? [])
    .filter(
      (d) =>
        Number.isInteger(d?.inicio) &&
        Number.isInteger(d?.fim) &&
        d.inicio >= 0 &&
        d.fim > d.inicio &&
        d.fim <= snippet.length
    )
    .map((d) => ({ inicio: d.inicio, fim: d.fim }))
    .sort((a, b) => a.inicio - b.inicio);

  const fundidos: Array<{ inicio: number; fim: number }> = [];
  for (const atual of validos) {
    const anterior = fundidos[fundidos.length - 1];
    if (anterior && atual.inicio <= anterior.fim) {
      anterior.fim = Math.max(anterior.fim, atual.fim);
    } else {
      fundidos.push({ ...atual });
    }
  }

  const trechos: Trecho[] = [];
  let cursor = 0;
  for (const { inicio, fim } of fundidos) {
    if (inicio > cursor) {
      trechos.push({ texto: snippet.slice(cursor, inicio), destacado: false });
    }
    trechos.push({ texto: snippet.slice(inicio, fim), destacado: true });
    cursor = fim;
  }
  if (cursor < snippet.length) {
    trechos.push({ texto: snippet.slice(cursor), destacado: false });
  }
  return trechos;
}

/**
 * Renderiza o snippet de busca como **nós de texto React** com `<mark>` (D-30b).
 *
 * A injeção de HTML bruto é proibida no repositório (regra ESLint + gate anti-legado). Um
 * snippet contendo `<img src=x onerror=alert(1)>` aparece literalmente como texto.
 */
export const SnippetDestacado: React.FC<SnippetDestacadoProps> = ({ snippet, destaques }) => {
  const trechos = useMemo(() => calcularTrechos(snippet ?? '', destaques ?? []), [snippet, destaques]);

  return (
    <span data-testid="snippet-destacado">
      {trechos.map((trecho, indice) =>
        trecho.destacado ? (
          <mark key={indice} data-testid="snippet-mark">
            {trecho.texto}
          </mark>
        ) : (
          <React.Fragment key={indice}>{trecho.texto}</React.Fragment>
        )
      )}
    </span>
  );
};
