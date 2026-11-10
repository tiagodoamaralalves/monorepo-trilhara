package br.com.trilhara.nota.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO para atualização de notas.
 * Pelo menos um campo deve estar presente (validado no serviço).
 */
public record AtualizarNotaRequest(
    @Size(max = 200, message = "O título da nota não pode exceder 200 caracteres")
    String titulo,
    String conteudoMarkdown,
    Integer ordem
) {}
