package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "LogoutRequest", description = "Requisição de logout com invalidação do refresh token")
public record LogoutRequest(
        @Schema(description = "Refresh token a invalidar (opcional se enviado via cookie HttpOnly)")
        @Size(max = 256, message = "Token inválido.")
        String refreshToken) {

    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=[PROTECTED]]";
    }
}

