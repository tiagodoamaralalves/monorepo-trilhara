package br.com.trilhara.nota;

import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Entidade normalizada da coleção 'cadernos'.
 */
public record Caderno(
    ObjectId id,
    ObjectId usuarioId,
    String titulo,
    String cor,
    String icone,
    ObjectId origemTrilhaId,
    Instant deletedAt,
    Instant expiresAt,
    String loteExclusaoId,
    Instant criadoEm,
    Instant atualizadoEm
) {
    public Caderno {
        cor = (cor == null || cor.isBlank()) ? "#0284c7" : cor;
    }

    public String idHex() {
        return id != null ? id.toHexString() : null;
    }

    public String usuarioIdHex() {
        return usuarioId != null ? usuarioId.toHexString() : null;
    }

    public String origemTrilhaIdHex() {
        return origemTrilhaId != null ? origemTrilhaId.toHexString() : null;
    }
}
