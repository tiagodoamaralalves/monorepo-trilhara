package br.com.trilhara.trilha.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DTO completo com metadados, disciplinas ordenadas, tópicos e progresso sob demanda.
 * Retornado por GET /api/trilhas/{trilhaId} e POST /api/trilhas.
 * Conforme PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.3.
 */
public record TrilhaDetalheDTO(
        String id,
        String usuarioId,
        String donoId,
        String nome,
        String cargo,
        String areaCargo,
        String banca,
        String dataProva,
        String cor,
        String origemEditalId,
        int totalDisciplinas,
        int totalTopicos,
        int topicosConcluidos,
        double percentualProgresso,
        CronogramaAtivoResumoDTO cronogramaAtivoResumo,
        List<DisciplinaDTO> disciplinas,
        Set<String> topicosConcluidosIds,
        Map<String, ProgressoTopicoDTO> progresso,
        Instant criadoEm,
        Instant atualizadoEm) {

    public TrilhaDetalheDTO {
        disciplinas = disciplinas == null ? List.of() : List.copyOf(disciplinas);
        topicosConcluidosIds = topicosConcluidosIds == null ? Set.of() : Set.copyOf(topicosConcluidosIds);
        progresso = progresso == null ? Map.of() : Map.copyOf(progresso);
    }
}
