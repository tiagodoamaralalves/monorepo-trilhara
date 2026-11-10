package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Resultado de busca global de notas com metadados e snippet (sem Markdown completo).
 */
public record ResultadoBuscaNotaDTO(
    String notaId,
    String titulo,
    String cadernoId,
    String cadernoTitulo,
    String secaoId,
    String secaoTitulo,
    Instant atualizadoEm,
    String snippet
) {}
