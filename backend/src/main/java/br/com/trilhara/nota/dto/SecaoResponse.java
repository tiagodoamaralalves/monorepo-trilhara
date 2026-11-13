package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Seção de um caderno (D-29).
 *
 * <p>{@code origemDisciplinaId} é a referência de origem da importação de trilha: permite
 * renomear a seção na sincronização explícita e desvinculá-la quando a disciplina deixa de
 * existir. Nenhuma seção guarda páginas embutidas.</p>
 *
 * @param totalPaginas páginas ativas da seção
 */
public record SecaoResponse(
        String id,
        String cadernoId,
        String titulo,
        int ordem,
        String origemDisciplinaId,
        long totalPaginas,
        Instant criadoEm,
        Instant atualizadoEm) {
}
