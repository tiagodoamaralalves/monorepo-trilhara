package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RecuperacaoSenhaResponse", description = "Resposta genérica da solicitação de recuperação de senha")
public record RecuperacaoSenhaResponse(
        @Schema(description = "Mensagem neutra que não revela se o e-mail existe")
        String detail) {
}

