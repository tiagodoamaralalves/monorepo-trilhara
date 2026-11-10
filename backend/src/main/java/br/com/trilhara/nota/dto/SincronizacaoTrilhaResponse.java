package br.com.trilhara.nota.dto;

public record SincronizacaoTrilhaResponse(
    String cadernoId,
    int secoesCriadas,
    int paginasCriadas,
    int secoesDesvinculadas
) {}
