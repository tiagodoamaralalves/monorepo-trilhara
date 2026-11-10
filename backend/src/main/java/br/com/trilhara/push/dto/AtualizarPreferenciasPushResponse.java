package br.com.trilhara.push.dto;

public record AtualizarPreferenciasPushResponse(
        int padraoMinutosAntes,
        long lembretesRecalculados) {
}
