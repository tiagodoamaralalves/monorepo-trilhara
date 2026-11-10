package br.com.trilhara.sessao.dto;

public record RemocaoSessoesDisciplinaResponse(
        long sessoesRemovidas,
        long totalSegundosRemovidos
) {
}
