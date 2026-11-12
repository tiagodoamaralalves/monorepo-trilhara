/**
 * Formatação de durações (D-42).
 *
 * `formatarDuracao(segundos)` → "45 s" (< 60), "12 min", "1 h 05 min".
 */

const doisDigitos = (valor: number): string => String(valor).padStart(2, '0');

/** Duração legível em pt-BR: "45 s", "12 min", "1 h 05 min". */
export function formatarDuracao(segundosTotais: number | null | undefined): string {
  if (segundosTotais == null || !Number.isFinite(segundosTotais) || segundosTotais <= 0) {
    return '0 min';
  }
  const total = Math.floor(segundosTotais);
  if (total < 60) return `${total} s`;

  const horas = Math.floor(total / 3600);
  const minutos = Math.floor((total % 3600) / 60);

  if (horas === 0) return `${minutos} min`;
  if (minutos === 0) return `${horas} h`;
  return `${horas} h ${doisDigitos(minutos)} min`;
}

/** Duração a partir de minutos: "12 min", "1 h 05 min". */
export function formatarMinutos(minutosTotais: number | null | undefined): string {
  if (minutosTotais == null || !Number.isFinite(minutosTotais) || minutosTotais <= 0) {
    return '0 min';
  }
  return formatarDuracao(Math.floor(minutosTotais) * 60);
}

/** Cronômetro `mm:ss` (ou `hh:mm:ss` a partir de 1 hora). */
export function formatarRelogio(segundosTotais: number | null | undefined): string {
  const total = Math.max(0, Math.floor(segundosTotais ?? 0));
  const horas = Math.floor(total / 3600);
  const minutos = Math.floor((total % 3600) / 60);
  const segundos = total % 60;
  if (horas > 0) {
    return `${doisDigitos(horas)}:${doisDigitos(minutos)}:${doisDigitos(segundos)}`;
  }
  return `${doisDigitos(minutos)}:${doisDigitos(segundos)}`;
}
