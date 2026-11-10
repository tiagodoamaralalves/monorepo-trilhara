package br.com.trilhara.sessao.dto;

public record SerieTemporalDiaDTO(
        String data,
        long totalSegundos,
        int totalQuestoes,
        int totalAcertos
) {
}
