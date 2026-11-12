package br.com.trilhara.trilha.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * DTO completo com metadados, disciplinas ordenadas, tópicos e progresso sob demanda.
 * Retornado por GET /api/trilhas/{trilhaId} e POST /api/trilhas.
 * Conforme PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.3.
 *
 * <p>O progresso é expresso apenas por {@code topicosConcluidosIds}: o mapa legado
 * {@code progresso: { topicoId: { done, doneAt } }} foi removido (D-11). Identificadores de
 * usuário são omitidos do contrato público (D-06).</p>
 *
 * <p>{@code ciclos} permanece apenas como passagem da estrutura herdada de planejamento,
 * cuja remoção pertence às revisões de cronograma/planejamento; nenhuma regra desta revisão
 * depende dele.</p>
 */
public record TrilhaDetalheDTO(
        String id,
        String nome,
        String cargo,
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
        List<Object> ciclos,
        Instant criadoEm,
        Instant atualizadoEm) {

    public TrilhaDetalheDTO {
        disciplinas = disciplinas == null ? List.of() : List.copyOf(disciplinas);
        topicosConcluidosIds = topicosConcluidosIds == null ? Set.of() : Set.copyOf(topicosConcluidosIds);
        ciclos = ciclos == null ? List.of() : List.copyOf(ciclos);
    }
}
