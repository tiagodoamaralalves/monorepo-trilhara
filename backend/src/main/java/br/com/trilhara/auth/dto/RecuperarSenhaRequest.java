package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RecuperarSenhaRequest", description = "Solicitação de recuperação de senha por e-mail")
public record RecuperarSenhaRequest(
        @Schema(description = "E-mail cadastrado", required = true)
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email) {

    public RecuperarSenhaRequest {
        email = email != null ? email.trim() : null;
    }
}

