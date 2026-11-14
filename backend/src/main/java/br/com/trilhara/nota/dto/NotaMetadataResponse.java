package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Projeção leve de metadados de página.
 *
 * <p>É o único formato aceito em listagens: {@code conteudoMarkdown} nunca é projetado
 * (`Projections.exclude("conteudoMarkdown")`). O Markdown completo só é carregado ao abrir a
 * página, por {@code GET /api/notas/{notaId}}.</p>
 *
 * @param topicoOrigemId referência de origem da importação de trilha (sem sincronização de
 * tópicos no MVP)
 */
public record NotaMetadataResponse(
        String id,
        String cadernoId,
        String secaoId,
        String titulo,
        int ordem,
        String topicoOrigemId,
        Instant criadoEm,
        Instant atualizadoEm) {
}
