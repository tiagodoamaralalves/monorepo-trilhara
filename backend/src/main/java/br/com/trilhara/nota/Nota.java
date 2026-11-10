package br.com.trilhara.nota;

import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Entidade normalizada da coleção 'notas'.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §10 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §8.2.
 */
public record Nota(
    ObjectId id,
    ObjectId usuarioId,
    ObjectId cadernoId,
    ObjectId secaoId,
    String titulo,
    String conteudoMarkdown,
    int ordem,
    String origemTopicoId,
    Instant deletedAt,
    Instant expiresAt,
    String loteExclusaoId,
    Instant criadoEm,
    Instant atualizadoEm
) {
    public static final int MAX_MARKDOWN_BYTES = 524288; // 512 KB

    public Nota {
        conteudoMarkdown = conteudoMarkdown == null ? "" : conteudoMarkdown;
    }

    public String idHex() {
        return id != null ? id.toHexString() : null;
    }

    public String usuarioIdHex() {
        return usuarioId != null ? usuarioId.toHexString() : null;
    }

    public String cadernoIdHex() {
        return cadernoId != null ? cadernoId.toHexString() : null;
    }

    public String secaoIdHex() {
        return secaoId != null ? secaoId.toHexString() : null;
    }
}
