package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credenciais para autenticação")
public record LoginRequest(
        @Schema(description = "E-mail do usuário", examples = "ana.silva@exemplo.com.br", required = true)
        @NotBlank(message = "O e-mail é obrigatório.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @Schema(description = "Senha do usuário", examples = "Senh@Forte2026!", required = true)
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 1, max = 128, message = "A senha é obrigatória.")
        String senha) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", senha=[PROTECTED]]";
    }
}
