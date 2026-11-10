package br.com.trilhara.cronograma.dto;

import java.util.List;
import java.util.Map;

public record CronogramaDTO(
        String id,
        String trilhaId,
        String modalidade,
        boolean ativo,
        long versaoAtiva,
        int duracaoBlocoMinutos,
        String tipoPeriodo,
        String dataInicio,
        String dataFim,
        List<Integer> diasAtivosSemana,
        Integer blocosPorDia,
        List<String> disciplinasParticipantesIds,
        Map<Integer, List<String>> semanaModeloManual,
        Map<String, Object> configuracao,
        int quantidadeBlocos,
        List<Integer> cargaMinutosPorDia,
        String estadoPublicacao,
        String criadoEm,
        String atualizadoEm) {
}
