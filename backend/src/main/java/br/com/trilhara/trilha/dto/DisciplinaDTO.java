package br.com.trilhara.trilha.dto;

import java.util.List;

/**
 * DTO para transporte de disciplina detalhada com seus tópicos e contadores.
 */
public record DisciplinaDTO(
        String id,
        String trilhaId,
        String nome,
        String cor,
        int peso,
        int ordem,
        String cadernoLink,
        List<TopicoDTO> topicos,
        int totalTopicos,
        int topicosConcluidos,
        double percentualProgresso) {

    public DisciplinaDTO {
        topicos = topicos == null ? List.of() : List.copyOf(topicos);
    }
}
