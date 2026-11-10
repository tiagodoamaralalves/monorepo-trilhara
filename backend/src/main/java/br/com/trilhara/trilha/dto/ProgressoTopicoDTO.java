package br.com.trilhara.trilha.dto;

import java.time.Instant;

/**
 * Representação do progresso de um tópico para retrocompatibilidade.
 */
public record ProgressoTopicoDTO(
        boolean done,
        Instant doneAt) {
}
