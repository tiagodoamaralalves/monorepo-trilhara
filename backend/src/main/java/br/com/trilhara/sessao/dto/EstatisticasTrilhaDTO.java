package br.com.trilhara.sessao.dto;

import java.util.List;

public record EstatisticasTrilhaDTO(
        long totalSegundos,
        long totalSessoes,
        int totalQuestoes,
        int totalAcertos,
        int totalErros,
        double percentualAcertoGlobal,
        List<EstatisticaDisciplinaDTO> porDisciplina,
        List<SerieTemporalDiaDTO> serieTemporal,
        int streakDias
) {
}
