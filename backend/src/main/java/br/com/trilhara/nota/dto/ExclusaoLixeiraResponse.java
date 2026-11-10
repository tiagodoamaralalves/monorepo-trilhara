package br.com.trilhara.nota.dto;

public record ExclusaoLixeiraResponse(
    String id,
    String tipo,
    long secoesAfetadas,
    long notasAfetadas,
    String loteExclusaoId
) {}
