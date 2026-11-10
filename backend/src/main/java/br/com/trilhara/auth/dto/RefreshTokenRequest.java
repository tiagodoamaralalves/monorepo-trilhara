package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "RefreshTokenRequest", description = "Requisição de renovação de token (opcional se enviado via cookie HttpOnly)")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token opaco emitido no login ou na última rotação")
        @Size(max = 256, message = "Token inválido.")
        String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=[PROTECTED]]";
    }
}

