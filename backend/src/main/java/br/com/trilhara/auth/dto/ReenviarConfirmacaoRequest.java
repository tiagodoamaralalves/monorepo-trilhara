package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ReenviarConfirmacaoRequest", description = "Solicitação de reenvio de e-mail de ativação")
public record ReenviarConfirmacaoRequest(
        @Schema(description = "E-mail do usuário (opcional se a requisição estiver autenticada)")
        String email) {
}
