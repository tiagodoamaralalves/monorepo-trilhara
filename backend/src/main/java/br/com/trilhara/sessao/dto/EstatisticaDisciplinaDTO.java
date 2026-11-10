package br.com.trilhara.sessao.dto;

public record EstatisticaDisciplinaDTO(
        String disciplinaId,
        String nome,
        String cor,
        boolean disciplinaRemovida,
        long totalSegundos,
        long totalSessoes,
        int totalQuestoes,
        int totalAcertos,
        int totalErros,
        double percentualAcerto
) {
}
