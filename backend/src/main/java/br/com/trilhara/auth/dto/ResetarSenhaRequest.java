package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetarSenhaRequest", description = "Redefinição de senha via token único")
public record ResetarSenhaRequest(
        @Schema(description = "Token único de redefinição de senha", required = true)
        @NotBlank(message = "O token de redefinição é obrigatório.")
        @Size(max = 256, message = "Token inválido.")
        String token,

        @Schema(description = "Nova senha forte", required = true)
        @NotBlank(message = "A nova senha é obrigatória.")
        @Size(min = 8, max = 128, message = "A nova senha deve ter entre 8 e 128 caracteres.")
        String novaSenha) {

    @Override
    public String toString() {
        return "ResetarSenhaRequest[token=[PROTECTED], novaSenha=[PROTECTED]]";
    }
}

