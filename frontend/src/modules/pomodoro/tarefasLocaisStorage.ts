import { TarefaDTO } from '@shared/contracts/domainContracts';
import { registrarFalha } from '@shared/diagnostico';

/** Chave de tarefas criadas sem login no Pomodoro (armazenamento local do navegador). */
export const POMODORO_LOCAL_TASKS_STORAGE_KEY = 'trilhara:anon:pomodoro_tarefas';

export interface TarefaLocalPomodoro {
  id: string;
  titulo: string;
  concluida: boolean;
  criadaEm: string;
}

/** Chave da preferência de expansão do card de tarefas no Pomodoro, escopada por conta. */
export function getPomodoroAgendaExpandidaStorageKey(usuarioId?: string | null): string {
  return usuarioId
    ? `trilhara:u:${usuarioId}:pomodoro_agenda_expandida`
    : 'trilhara:anon:pomodoro_agenda_expandida';
}

/** Lê se o card de tarefas deve iniciar expandido (padrão: `true`). */
export function lerAgendaExpandidaPomodoro(usuarioId?: string | null): boolean {
  if (typeof window === 'undefined') return true;
  try {
    const bruto = window.localStorage.getItem(getPomodoroAgendaExpandidaStorageKey(usuarioId));
    if (bruto === 'false') return false;
    return true;
  } catch {
    return true;
  }
}

/** Persiste a preferência de expansão/recolhimento do card de tarefas no navegador. */
export function salvarAgendaExpandidaPomodoro(
  usuarioId: string | null | undefined,
  expandida: boolean
): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(
      getPomodoroAgendaExpandidaStorageKey(usuarioId),
      expandida ? 'true' : 'false'
    );
  } catch {
    // Storage restrito: mantém apenas em memória.
  }
}

/** Lê a lista de tarefas criadas sem login no navegador. */
export function lerTarefasLocaisPomodoro(): TarefaLocalPomodoro[] {
  if (typeof window === 'undefined') return [];
  try {
    const bruto = window.localStorage.getItem(POMODORO_LOCAL_TASKS_STORAGE_KEY);
    if (!bruto) return [];
    const parsed: unknown = JSON.parse(bruto);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is TarefaLocalPomodoro =>
        Boolean(item) &&
        typeof item === 'object' &&
        typeof (item as TarefaLocalPomodoro).id === 'string' &&
        typeof (item as TarefaLocalPomodoro).titulo === 'string' &&
        typeof (item as TarefaLocalPomodoro).concluida === 'boolean' &&
        typeof (item as TarefaLocalPomodoro).criadaEm === 'string'
    );
  } catch (err) {
    registrarFalha('estado-local.pomodoro-tarefas', err);
    return [];
  }
}

/** Persiste a lista de tarefas locais do Pomodoro no navegador. */
export function salvarTarefasLocaisPomodoro(tarefas: TarefaLocalPomodoro[]): void {
  if (typeof window === 'undefined') return;
  try {
    if (tarefas.length === 0) {
      window.localStorage.removeItem(POMODORO_LOCAL_TASKS_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(POMODORO_LOCAL_TASKS_STORAGE_KEY, JSON.stringify(tarefas));
  } catch (err) {
    registrarFalha('estado-local.pomodoro-tarefas-gravar', err);
  }
}

/** Gera identificador único para uma tarefa local no navegador. */
export function gerarIdTarefaLocal(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `local-${crypto.randomUUID()}`;
  }
  return `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/** Adapta uma tarefa local para o formato esperado pelo cartão compartilhado `TarefaAgendaItem`. */
export function paraTarefaAgendaDTO(local: TarefaLocalPomodoro): TarefaDTO {
  return {
    id: local.id,
    titulo: local.titulo,
    prioridade: 'MEDIA',
    concluida: local.concluida,
    criadoEm: local.criadaEm,
    atualizadoEm: local.criadaEm
  };
}
