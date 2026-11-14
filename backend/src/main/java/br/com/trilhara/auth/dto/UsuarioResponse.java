package br.com.trilhara.auth.dto;

import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.Usuario;

/**
 * Representação pública e segura de um usuário.
 *
 * <p>Contrato único para login, cadastro, refresh, handoff, {@code GET /auth/me} e admin:
 * {@code { id, nome, email, emailVerificado, papeis, preferencias }} acrescido de
 * {@code ativo} e {@code criadoEm}.</p>
 */
@Schema(name = "UsuarioResponse", description = "Representação pública e segura de um usuário")
public record UsuarioResponse(
        @Schema(description = "ID único do usuário")
        String id,

        @Schema(description = "Nome do usuário")
        String nome,

        @Schema(description = "E-mail normalizado do usuário")
        String email,

        @Schema(description = "Papéis de autorização do usuário")
        List<Role> papeis,

        @Schema(description = "Indica se a conta está ativa")
        boolean ativo,

        @Schema(description = "Indica se o e-mail foi verificado")
        boolean emailVerificado,

        @Schema(description = "Data de criação da conta")
        Instant criadoEm,

        @Schema(description = "Preferências persistidas (tema, disponibilidade, lembrete)")
        PreferenciasDTO preferencias) {

    /** Sem preferências carregadas: usado onde o contexto não consulta o documento. */
    public static UsuarioResponse de(Usuario usuario) {
        return de(usuario, new PreferenciasDTO(PreferenciasDTO.TEMA_PADRAO,
                PreferenciasDTO.DISPONIBILIDADE_PADRAO, PreferenciasDTO.PADRAO_LEMBRETE_PADRAO));
    }

    public static UsuarioResponse de(Usuario usuario, PreferenciasDTO preferencias) {
        return new UsuarioResponse(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.roles(),
                usuario.ativo(),
                usuario.emailVerificado(),
                usuario.criadoEm(),
                preferencias);
    }
}


