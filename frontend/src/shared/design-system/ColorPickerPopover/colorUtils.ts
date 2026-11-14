import { DISCIPLINE_COLORS } from '../Controls/Controls';

export interface ItemComCor {
  id: string;
  cor?: string | null;
  titulo?: string;
  nome?: string;
}

/**
 * Retorna um mapa de cor (em minúsculas) para o nome/título do item que a utiliza.
 */
export function obterCoresEmUso<T extends ItemComCor>(
  itens: readonly T[],
  ignorarId?: string
): Map<string, string> {
  const coresEmUso = new Map<string, string>();
  itens.forEach((item) => {
    if (item.id === ignorarId || !item.cor) return;
    const nomeOuTitulo = item.titulo?.trim() || item.nome?.trim() || 'Item';
    coresEmUso.set(item.cor.toLowerCase(), nomeOuTitulo);
  });
  return coresEmUso;
}

/**
 * Seleciona inteligentemente uma cor não utilizada dentre as 27 cores da paleta.
 * Caso todas já estejam em uso, faz fallback sorteando qualquer uma das 27 cores.
 */
function sortearIndice(tamanho: number): number {
  if (tamanho <= 1) return 0;
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    const buffer = new Uint32Array(1);
    globalThis.crypto.getRandomValues(buffer);
    return buffer[0] % tamanho;
  }
  return Date.now() % tamanho;
}

export function obterCorDisponivel<T extends ItemComCor>(
  itens: readonly T[],
  ignorarId?: string
): string {
  const coresEmUso = obterCoresEmUso(itens, ignorarId);
  const coresDisponiveis = DISCIPLINE_COLORS.filter(
    (cor) => !coresEmUso.has(cor.toLowerCase())
  );

  if (coresDisponiveis.length > 0) {
    return coresDisponiveis[sortearIndice(coresDisponiveis.length)];
  }

  return DISCIPLINE_COLORS[sortearIndice(DISCIPLINE_COLORS.length)];
}

