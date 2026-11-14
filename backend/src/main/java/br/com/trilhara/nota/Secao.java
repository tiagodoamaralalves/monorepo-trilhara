package br.com.trilhara.nota;

import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Entidade normalizada da coleção 'secoes'.
 */
public record Secao(
    ObjectId id,
    ObjectId usuarioId,
    ObjectId cadernoId,
    String titulo,
    int ordem,
    ObjectId origemDisciplinaId,
    Instant deletedAt,
    Instant expiresAt,
    String loteExclusaoId,
    Instant criadoEm,
    Instant atualizadoEm
) {
    public String idHex() {
        return id != null ? id.toHexString() : null;
    }

    public String usuarioIdHex() {
        return usuarioId != null ? usuarioId.toHexString() : null;
    }

    public String cadernoIdHex() {
        return cadernoId != null ? cadernoId.toHexString() : null;
    }

    public String origemDisciplinaIdHex() {
        return origemDisciplinaId != null ? origemDisciplinaId.toHexString() : null;
    }
}
