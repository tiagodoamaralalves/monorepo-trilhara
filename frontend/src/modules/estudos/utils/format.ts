/**
 * Compatibilidade de import para os módulos de Estudos.
 *
 * A implementação canônica vive em `@shared/format`. Este arquivo apenas reexporta,
 * para que os call sites antigos não dupliquem regra de formatação.
 */
export {
  formatarDataCurta as formatarDataBR,
  formatarDuracao,
  formatarRelogio as formatarSegundos,
  hojeISO
} from '@shared/format';

import { diasAte as diasAteBase, hojeISO as hojeISOBase, parseDataCalendario } from '@shared/format';

/** Diferença em dias com o rótulo de urgência usado nas views de Estudos. */
export function diasAte(dataISO?: string | null): { label: string; isUrgente: boolean } {
  const dias = diasAteBase(dataISO);
  if (dias == null) return { label: 'Ritmo contínuo', isUrgente: false };
  if (dias < 0) return { label: 'Realizada', isUrgente: false };
  if (dias === 0) return { label: 'Hoje!', isUrgente: true };
  if (dias === 1) return { label: '1 dia', isUrgente: true };
  return { label: `${dias} dias`, isUrgente: dias <= 30 };
}

/** A prova ainda está por vir? */
export function provaEmAndamento(dataISO?: string | null): boolean {
  const data = parseDataCalendario(dataISO);
  if (!data) return false;
  return Boolean(dataISO) && (dataISO as string) > hojeISOBase();
}

