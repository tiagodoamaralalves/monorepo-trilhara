package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CadastroRequest", description = "Dados para cadastro de novo usuário")
public record CadastroRequest(
        @Schema(description = "Nome completo do usuário", examples = "Ana Beatriz Silva", required = true)
        @NotBlank(message = "O nome é obrigatório.")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.")
        String nome,

        @Schema(description = "Endereço de e-mail único do usuário", examples = "ana.silva@exemplo.com.br", required = true)
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @Schema(description = "Senha forte (mínimo 8 caracteres, maiúscula, minúscula, número e símbolo)", examples = "Senh@Forte2026!", required = true)
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres.")
        String senha) {

    @Override
    public String toString() {
        return "CadastroRequest[nome=" + nome + ", email=" + email + ", senha=[PROTECTED]]";
    }
}
