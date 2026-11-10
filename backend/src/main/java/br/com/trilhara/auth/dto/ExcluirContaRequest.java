package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ExcluirContaRequest", description = "Dados para solicitação de exclusão definitiva da conta")
public record ExcluirContaRequest(
        @Schema(description = "Senha atual do usuário para reautenticação server-side", required = true)
        @NotBlank(message = "A senha atual é obrigatória para excluir a conta.")
        @Size(max = 128, message = "A senha deve ter no máximo 128 caracteres.")
        String senha,

        @Schema(description = "Confirmação textual explícita de intenção ('EXCLUIR MINHA CONTA')", required = true)
        @NotBlank(message = "A confirmação textual de intenção é obrigatória.")
        String confirmacao,

        @Schema(description = "Chave de idempotência da solicitação", required = false)
        String idempotencyKey) {

    @Override
    public String toString() {
        return "ExcluirContaRequest[senha=[PROTECTED], confirmacao=" + confirmacao + ", idempotencyKey=" + idempotencyKey + "]";
    }
}
