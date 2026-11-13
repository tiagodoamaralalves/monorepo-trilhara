package br.com.trilhara.tarefa.dto;

/**
 * Impacto da exclusão de uma lista de tarefas (D-33), consumido pelo {@code ConfirmDialog}
 * antes de mover a lista e o conteúdo para a lixeira por 30 dias.
 *
 * @param id                identificador da lista
 * @param nome              nome da lista
 * @param tarefasAtivas     tarefas não concluídas que irão para a lixeira
 * @param tarefasConcluidas tarefas concluídas que irão para a lixeira
 * @param seriesVinculadas  séries recorrentes que ficarão suspensas enquanto a lista estiver
 *                          na lixeira (D-27)
 */
public record ListaImpactoExclusaoResponse(
        String id,
        String nome,
        long tarefasAtivas,
        long tarefasConcluidas,
        long seriesVinculadas) {
}
