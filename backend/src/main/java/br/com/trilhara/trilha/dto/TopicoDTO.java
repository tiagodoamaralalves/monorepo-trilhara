package br.com.trilhara.trilha.dto;

/**
 * DTO para transporte de tópico de uma disciplina.
 */
public record TopicoDTO(
        String id,
        String nome,
        int ordem,
        boolean concluido) {
}
