package br.com.trilhara.trilha;

import java.time.Instant;

import org.bson.types.ObjectId;

/**
 * Representa um registro individual de tópico concluído na coleção normalizada 'progresso_topicos'.
 */
public record ProgressoTopico(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        ObjectId disciplinaId,
        String topicoId,
        Instant concluidoEm) {
}
