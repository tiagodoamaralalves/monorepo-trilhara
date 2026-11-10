package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AlterarSenhaRequest", description = "Dados para alteração de senha do usuário autenticado")
public record AlterarSenhaRequest(
        @Schema(description = "Senha atual do usuário", required = true)
        @NotBlank(message = "A senha atual é obrigatória.")
        @Size(max = 128, message = "A senha atual deve ter no máximo 128 caracteres.")
        String senhaAtual,

        @Schema(description = "Nova senha forte", required = true)
        @NotBlank(message = "A nova senha é obrigatória.")
        @Size(min = 8, max = 128, message = "A nova senha deve ter entre 8 e 128 caracteres.")
        String novaSenha) {

    @Override
    public String toString() {
        return "AlterarSenhaRequest[senhaAtual=[PROTECTED], novaSenha=[PROTECTED]]";
    }
}

