package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Resposta de autenticação contendo tokens e dados do usuário")
public record AuthResponse(
        @Schema(description = "Access token JWT de curta duração")
        String accessToken,

        @Schema(description = "Refresh token opaco com suporte a rotação")
        String refreshToken,

        @Schema(description = "Tipo do token de autorização", examples = "Bearer")
        String tokenType,

        @Schema(description = "Tempo de expiração do access token em segundos", examples = "900")
        long expiresIn,

        @Schema(description = "Dados públicos do usuário autenticado")
        UsuarioResponse usuario) {

    @Override
    public String toString() {
        return "AuthResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", usuario=" + usuario + "]";
    }
}

