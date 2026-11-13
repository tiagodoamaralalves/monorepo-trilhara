package br.com.trilhara.tarefa.dto;

/**
 * Resposta da restauração de tarefa (D-25).
 *
 * @param tarefa                 a tarefa restaurada
 * @param realocadaParaListaGeral {@code true} quando a lista original já foi purgada e a
 *                                tarefa voltou para a Lista Geral ({@code listaId = null});
 *                                a UI usa esse flag para informar o usuário
 */
public record RestauracaoTarefaResponse(
        TarefaResponse tarefa,
        boolean realocadaParaListaGeral) {
}
