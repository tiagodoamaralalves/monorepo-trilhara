package br.com.trilhara.auth.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import br.com.trilhara.auth.RefreshToken;

@Schema(name = "SessaoAtivaResponse", description = "Sessão ativa de um dispositivo do usuário")
public record SessaoAtivaResponse(
        @Schema(description = "Identificador da família de sessão do dispositivo")
        String familiaId,

        @Schema(description = "User-Agent do dispositivo/navegador")
        String userAgent,

        @Schema(description = "Endereço IP de origem")
        String ip,

        @Schema(description = "Data de início da sessão")
        Instant criadoEm,

        @Schema(description = "Data da última renovação")
        Instant ultimoUsoEm,

        @Schema(description = "Data de expiração da sessão")
        Instant expiraEm) {

    public static SessaoAtivaResponse de(RefreshToken token) {
        return new SessaoAtivaResponse(
                token.familiaId(),
                token.userAgent(),
                token.ip(),
                token.criadoEm(),
                token.ultimoUsoEm(),
                token.expiraEm());
    }
}

