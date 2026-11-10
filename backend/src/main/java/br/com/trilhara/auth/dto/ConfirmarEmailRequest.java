package br.com.trilhara.auth.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ConfirmarEmailRequest", description = "Payload para confirmação de conta via token")
public record ConfirmarEmailRequest(
        @NotBlank(message = "Token de confirmação é obrigatório.")
        @Schema(description = "Token opaco de uso único enviado por e-mail")
        String token) {
}
