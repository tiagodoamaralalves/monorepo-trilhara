package br.com.trilhara.sessao.dto;

/**
 * Ponto da série temporal de estatísticas.
 *
 * <p>A chave ({@code data}) é formatada conforme a {@code granularidade} da resposta:</p>
 * <ul>
 * <li>{@code DIA} → {@code yyyy-MM-dd};</li>
 * <li>{@code SEMANA} → {@code yyyy-MM-dd} da segunda-feira da semana ISO;</li>
 * <li>{@code MES} → {@code yyyy-MM-01}.</li>
 * </ul>
 *
 * <p>Os totais da soma dos pontos são idênticos em qualquer granularidade.</p>
 */
public record SerieTemporalPontoDTO(
        String data,
        long totalSegundos,
        int totalQuestoes,
        int totalAcertos
) {
}
