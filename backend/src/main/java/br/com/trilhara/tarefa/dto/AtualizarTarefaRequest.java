package br.com.trilhara.tarefa.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import br.com.trilhara.tarefa.ModoLembrete;

import jakarta.validation.constraints.Size;

public record AtualizarTarefaRequest(
        String listaId,
        @Size(min = 1, max = 200) String titulo,
        @JsonAlias("observacoes") String descricao,
        String prioridade,
        @JsonAlias("data") String dataVencimento,
        String horario,
        List<SubtarefaDTO> subtarefas,
        ModoLembrete modoLembrete,
        Integer lembreteMinutosAntes,
        String fusoAgendamento,
        Integer ordem,
        String escopo,
        String origemOperacaoId) {
}
