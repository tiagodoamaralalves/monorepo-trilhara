package br.com.trilhara.auth.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import br.com.trilhara.auth.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AlterarRolesRequest", description = "Atualização das roles de um usuário (apenas ADMIN)")
public record AlterarRolesRequest(
        @Schema(description = "Lista de roles atribuídas ao usuário", required = true)
        @NotEmpty(message = "Pelo menos uma role deve ser informada.")
        List<@NotNull Role> roles) {
}

