package br.com.trilhara.auth.dto;

import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.Usuario;

@Schema(name = "UsuarioResponse", description = "Representação pública e segura de um usuário")
public record UsuarioResponse(
        @Schema(description = "ID único do usuário")
        String id,

        @Schema(description = "Nome do usuário")
        String nome,

        @Schema(description = "E-mail normalizado do usuário")
        String email,

        @Schema(description = "Roles de autorização do usuário")
        List<Role> roles,

        @Schema(description = "Indica se a conta está ativa")
        boolean ativo,

        @Schema(description = "Indica se o e-mail foi verificado")
        boolean emailVerificado,

        @Schema(description = "Data de criação da conta")
        Instant criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.roles(),
                usuario.ativo(),
                usuario.emailVerificado(),
                usuario.criadoEm());
    }
}

