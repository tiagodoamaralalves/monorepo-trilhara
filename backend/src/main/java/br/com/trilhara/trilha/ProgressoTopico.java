package br.com.trilhara.trilha;

import java.time.Instant;

import org.bson.types.ObjectId;

/**
 * Representa um registro individual de tópico concluído na coleção normalizada 'progresso_topicos'.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §4.2 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.2.
 */
public record ProgressoTopico(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        ObjectId disciplinaId,
        String topicoId,
        Instant concluidoEm) {
}
