/**
 * Formatação numérica e textual (D-42).
 */

const formatadorPercentual = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0
});

/** Percentual com 0 casas: `72.5` → `"73%"`. */
export function formatarPercentual(valor: number | null | undefined): string {
  if (valor == null || !Number.isFinite(valor)) return '0%';
  return `${formatadorPercentual.format(valor)}%`;
}

