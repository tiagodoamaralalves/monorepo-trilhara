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
export function obterCorDisponivel<T extends ItemComCor>(
  itens: readonly T[],
  ignorarId?: string
): string {
  const coresEmUso = obterCoresEmUso(itens, ignorarId);
  const coresDisponiveis = DISCIPLINE_COLORS.filter(
    (cor) => !coresEmUso.has(cor.toLowerCase())
  );

  if (coresDisponiveis.length > 0) {
    const indiceAleatorio = Math.floor(Math.random() * coresDisponiveis.length);
    return coresDisponiveis[indiceAleatorio];
  }

  const indiceAleatorio = Math.floor(Math.random() * DISCIPLINE_COLORS.length);
  return DISCIPLINE_COLORS[indiceAleatorio];
}

