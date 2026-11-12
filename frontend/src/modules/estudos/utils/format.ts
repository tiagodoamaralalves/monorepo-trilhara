/**
 * Compatibilidade de import para os módulos de Estudos.
 *
 * A implementação canônica vive em `@shared/format` (D-42). Este arquivo apenas reexporta,
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

/** Data local (yyyy-MM-dd) a partir de um timestamp ISO completo. */
export function paraDataLocalISO(isoTimestamp: string): string {
  if (!isoTimestamp) return '';
  const d = new Date(isoTimestamp);
  if (Number.isNaN(d.getTime())) return isoTimestamp.slice(0, 10);
  const ano = d.getFullYear();
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

