package br.com.trilhara.trilha.dto;

import java.time.Instant;

/**
 * Estado de conclusão de um tópico, retornado por
 * {@code PUT /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso}.
 *
 * <p>O progresso é persistido apenas quando o tópico está concluído (requisitos §4.2);
 * por isso a resposta é sempre {@code concluido = true}. O mapa legado
 * {@code progresso: { topicoId: { done, doneAt } }} foi removido (D-11): o cliente deriva
 * o progresso do conjunto {@code topicosConcluidosIds} do detalhe da trilha.</p>
 */
public record ProgressoTopicoDTO(
        String topicoId,
        boolean concluido,
        Instant concluidoEm) {

    public static ProgressoTopicoDTO concluido(String topicoId, Instant concluidoEm) {
        return new ProgressoTopicoDTO(topicoId, true, concluidoEm);
    }
}
