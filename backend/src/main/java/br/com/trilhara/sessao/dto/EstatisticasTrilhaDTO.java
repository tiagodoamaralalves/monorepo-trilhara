package br.com.trilhara.sessao.dto;

import java.util.List;

import br.com.trilhara.sessao.Granularidade;

public record EstatisticasTrilhaDTO(
        long totalSegundos,
        long totalSessoes,
        int totalQuestoes,
        int totalAcertos,
        int totalErros,
        double percentualAcertoGlobal,
        List<EstatisticaDisciplinaDTO> porDisciplina,
        List<SerieTemporalPontoDTO> serieTemporal,
        int streakDias,
        Granularidade granularidade
) {
}

