package br.com.trilhara.tarefa.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarTarefaRequest(
        String listaId,
        @NotBlank @Size(min = 1, max = 200) String titulo,
        @JsonAlias("observacoes") String descricao,
        String prioridade,
        @JsonAlias("data") String dataVencimento,
        String horario,
        List<SubtarefaDTO> subtarefas,
        Integer lembreteMinutosAntes,
        Boolean usaPadraoGlobalLembrete,
        String fusoAgendamento,
        Integer ordem) {
}
