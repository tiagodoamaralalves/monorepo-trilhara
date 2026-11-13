import { TarefaDTO } from '../contracts/domainContracts';
import { compararAtrasadas } from './ordenarAgenda';

/** Grupo de ocorrências atrasadas de uma mesma série recorrente (D-23). */
export interface GrupoAtrasadas {
  /** Ocorrência mais recente: é ela quem representa o grupo na lista. */
  principal: TarefaDTO;
  /** Ocorrências anteriores, ordenadas da mais antiga para a mais recente. */
  anteriores: TarefaDTO[];
}

/**
 * Agrupa ocorrências atrasadas da mesma série recorrente (D-23).
 *
 * - a ocorrência **mais recente** é a principal (o expansor mostra as anteriores);
 * - tarefas sem série ou desvinculadas aparecem como grupos de um único item;
 * - os grupos seguem `dataVencimento ASC, prioridade DESC, ordem ASC, id ASC`,
 *   comparados pelo item principal.
 *
 * Implementação única no produto: Tarefas e Pomodoro consomem este módulo.
 */
export function agruparAtrasadasPorSerie(atrasadas: readonly TarefaDTO[]): GrupoAtrasadas[] {
  const porSerie = new Map<string, TarefaDTO[]>();
  const avulsas: TarefaDTO[] = [];

  for (const tarefa of atrasadas) {
    if (tarefa.serieId && !tarefa.desvinculadaDaSerie) {
      const ocorrencias = porSerie.get(tarefa.serieId);
      if (ocorrencias) {
        ocorrencias.push(tarefa);
      } else {
        porSerie.set(tarefa.serieId, [tarefa]);
      }
    } else {
      avulsas.push(tarefa);
    }
  }

  const grupos: GrupoAtrasadas[] = [];

  for (const ocorrencias of porSerie.values()) {
    const ordenadas = [...ocorrencias].sort(compararAtrasadas);
    grupos.push({
      principal: ordenadas[ordenadas.length - 1],
      anteriores: ordenadas.slice(0, ordenadas.length - 1)
    });
  }

  for (const tarefa of avulsas) {
    grupos.push({ principal: tarefa, anteriores: [] });
  }

  return grupos.sort((a, b) => compararAtrasadas(a.principal, b.principal));
}
