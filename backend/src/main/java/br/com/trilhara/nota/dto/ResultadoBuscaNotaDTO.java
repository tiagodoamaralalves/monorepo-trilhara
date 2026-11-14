package br.com.trilhara.nota.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de busca global de notas.
 *
 * <p>O backend devolve <strong>texto puro</strong>: {@code snippet} não contém marcação e os
 * realces vêm em {@code destaques}, com offsets em unidades UTF-16 do próprio snippet. O
 * frontend renderiza nós de texto React e {@code <mark>} — {@code dangerouslySetInnerHTML} é
 * proibido no repositório.</p>
 *
 * @param snippet texto puro, no máximo 180 caracteres
 * @param destaques intervalos {@code [inicio, fim)} do snippet que casaram com a consulta
 */
public record ResultadoBuscaNotaDTO(
        String notaId,
        String titulo,
        String cadernoId,
        String cadernoTitulo,
        String secaoId,
        String secaoTitulo,
        Instant atualizadoEm,
        String snippet,
        List<DestaqueDTO> destaques) {
}
