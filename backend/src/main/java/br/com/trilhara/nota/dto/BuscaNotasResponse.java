package br.com.trilhara.nota.dto;

import java.util.List;

public record BuscaNotasResponse(
    List<ResultadoBuscaNotaDTO> itens,
    long total,
    int pagina,
    int tamanho,
    int totalPaginas
) {}
