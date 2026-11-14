package br.com.trilhara.trilha.dto;

import java.util.List;

/**
 * DTO para transporte de disciplina detalhada com seus tópicos e contadores.
 *
 * <p>O vínculo de caderno ({@code cadernoLink}) foi removido de {@code Disciplina}:
 * a relação entre estudos e notas passa a ser feita por {@code GET /api/cadernos?origemTrilhaId=},
 * nunca por uma URL arbitrária guardada na disciplina.</p>
 */
public record DisciplinaDTO(
        String id,
        String trilhaId,
        String nome,
        String cor,
        int peso,
        int ordem,
        List<TopicoDTO> topicos,
        int totalTopicos,
        int topicosConcluidos,
        double percentualProgresso) {

    public DisciplinaDTO {
        topicos = topicos == null ? List.of() : List.copyOf(topicos);
    }
}
