/**
 * Formatação de datas de calendário (D-42).
 *
 * <p>Datas de calendário chegam como `yyyy-MM-dd` e <strong>não</strong> são instantes:
 * construir `new Date('2026-03-01')` interpreta o valor como UTC e desloca o dia em fusos
 * negativos (vira 28/02 em `America/Sao_Paulo`). Por isso o parse é manual.</p>
 */

const MESES_CURTOS = [
  'jan', 'fev', 'mar', 'abr', 'mai', 'jun',
  'jul', 'ago', 'set', 'out', 'nov', 'dez'
];

interface DataCalendario {
  ano: number;
  mes: number; // 1-based
  dia: number;
}

/** Interpreta `yyyy-MM-dd` sem passar por `Date`/UTC. Retorna `null` se inválido. */
export function parseDataCalendario(dataISO?: string | null): DataCalendario | null {
  if (!dataISO) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(dataISO.trim());
  if (!match) return null;
  const ano = Number(match[1]);
  const mes = Number(match[2]);
  const dia = Number(match[3]);
  if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
  return { ano, mes, dia };
}

const doisDigitos = (valor: number): string => String(valor).padStart(2, '0');

/** `2026-03-01` → `01/03/2026`. Texto alternativo quando ausente. */
export function formatarDataCurta(dataISO?: string | null, vazio = 'Não definida'): string {
  const data = parseDataCalendario(dataISO);
  if (!data) return vazio;
  return `${doisDigitos(data.dia)}/${doisDigitos(data.mes)}/${data.ano}`;
}

/** `2026-03-01` → `1 de março de 2026`. */
export function formatarDataLonga(dataISO?: string | null, vazio = 'Não definida'): string {
  const data = parseDataCalendario(dataISO);
  if (!data) return vazio;
  const nomeMes = [
    'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
    'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'
  ][data.mes - 1];
  return `${data.dia} de ${nomeMes} de ${data.ano}`;
}

/** `2026-03-01` → `01 mar`. */
export function formatarDataCompacta(dataISO?: string | null, vazio = '—'): string {
  const data = parseDataCalendario(dataISO);
  if (!data) return vazio;
  return `${doisDigitos(data.dia)} ${MESES_CURTOS[data.mes - 1]}`;
}

/** Data de hoje no fuso local como `yyyy-MM-dd` (nunca UTC do servidor). */
export function hojeISO(agora: Date = new Date()): string {
  return `${agora.getFullYear()}-${doisDigitos(agora.getMonth() + 1)}-${doisDigitos(agora.getDate())}`;
}

/** Diferença em dias entre a data informada e hoje (positivo = futuro). */
export function diasAte(dataISO?: string | null, hoje: string = hojeISO()): number | null {
  const alvo = parseDataCalendario(dataISO);
  const base = parseDataCalendario(hoje);
  if (!alvo || !base) return null;
  const msAlvo = Date.UTC(alvo.ano, alvo.mes - 1, alvo.dia);
  const msBase = Date.UTC(base.ano, base.mes - 1, base.dia);
  return Math.round((msAlvo - msBase) / 86_400_000);
}
