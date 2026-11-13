import { BlocoHojeDTO, TarefaDTO } from '../contracts/domainContracts';

/**
 * Ordens normativas da Visão Hoje (§9) usadas por Tarefas e Pomodoro.
 *
 * Toda ordenação inclui o identificador como desempate final para ser estável e
 * determinística, conforme o contrato comum de paginação/ordenação.
 */

const PESO_PRIORIDADE: Record<string, number> = {
  ALTA: 3,
  URGENTE: 3,
  MEDIA: 2,
  BAIXA: 1,
  NENHUMA: 0
};

/** Peso de `prioridade` para ordenação DESC; desconhecida cai em `MEDIA`. */
function obterPesoPrioridade(prioridade?: string | null): number {
  if (!prioridade) return PESO_PRIORIDADE.MEDIA;
  const normalizada = String(prioridade).toUpperCase();
  return PESO_PRIORIDADE[normalizada] ?? PESO_PRIORIDADE.MEDIA;
}

/** Comparação textual tolerante a `undefined`/`null` (vazio ordena antes). */
function compararTexto(a?: string | null, b?: string | null): number {
  return (a ?? '').localeCompare(b ?? '');
}

/** `dataVencimento ASC, prioridade DESC, ordem ASC, id ASC` (Atrasadas). */
export function compararAtrasadas(a: TarefaDTO, b: TarefaDTO): number {
  const porData = compararTexto(a.dataVencimento, b.dataVencimento);
  if (porData !== 0) return porData;

  const porPrioridade = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
  if (porPrioridade !== 0) return porPrioridade;

  const porOrdem = (a.ordem ?? 0) - (b.ordem ?? 0);
  if (porOrdem !== 0) return porOrdem;

  return compararTexto(a.id, b.id);
}

/** `horario ASC NULLS LAST, prioridade DESC, ordem ASC, id ASC` (Tarefas de Hoje). */
export function compararTarefasHoje(a: TarefaDTO, b: TarefaDTO): number {
  const temHorarioA = Boolean(a.horario);
  const temHorarioB = Boolean(b.horario);

  // NULLS LAST: item sem horário sempre depois de quem tem horário.
  if (temHorarioA && !temHorarioB) return -1;
  if (!temHorarioA && temHorarioB) return 1;
  if (temHorarioA && temHorarioB) {
    const porHorario = compararTexto(a.horario, b.horario);
    if (porHorario !== 0) return porHorario;
  }

  const porPrioridade = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
  if (porPrioridade !== 0) return porPrioridade;

  const porOrdem = (a.ordem ?? 0) - (b.ordem ?? 0);
  if (porOrdem !== 0) return porOrdem;

  return compararTexto(a.id, b.id);
}

/** `prioridade DESC, ordem ASC, id ASC` (Sem data). */
export function compararSemData(a: TarefaDTO, b: TarefaDTO): number {
  const porPrioridade = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
  if (porPrioridade !== 0) return porPrioridade;

  const porOrdem = (a.ordem ?? 0) - (b.ordem ?? 0);
  if (porOrdem !== 0) return porOrdem;

  return compararTexto(a.id, b.id);
}

/** `trilhaNome ASC, ordem ASC, blocoId ASC` (Blocos de estudo de Hoje). */
export function compararBlocosHoje(a: BlocoHojeDTO, b: BlocoHojeDTO): number {
  const porTrilha = compararTexto(a.trilhaNome, b.trilhaNome);
  if (porTrilha !== 0) return porTrilha;

  const porOrdem = (a.ordem ?? 0) - (b.ordem ?? 0);
  if (porOrdem !== 0) return porOrdem;

  return compararTexto(a.blocoId || a.id, b.blocoId || b.id);
}

/** Cópia ordenada — nunca muta o array recebido. */
export function ordenar<T>(itens: readonly T[], comparador: (a: T, b: T) => number): T[] {
  return [...itens].sort(comparador);
}
