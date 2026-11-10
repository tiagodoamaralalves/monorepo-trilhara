package br.com.trilhara.push.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushConfigResponse(
        boolean habilitado,
        String publicKey,
        int padraoMinutosAntes) {
}
