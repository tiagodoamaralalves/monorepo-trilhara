package br.com.trilhara.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Criação de página dentro de uma seção (D-29).
 *
 * <p>A rota canônica é {@code POST /api/cadernos/{cadernoId}/secoes/{secaoId}/notas}: o
 * caderno e a seção vêm do path e ambos são validados contra o usuário autenticado. Não há
 * mais campo {@code blocoId} nem {@code cadernoId} no corpo — o modelo embutido que os
 * aceitava foi removido.</p>
 *
 * @param topicoOrigemId referência opcional ao tópico de origem da importação de trilha;
 *                       não sincroniza tópicos no MVP
 */
public record CriarNotaRequest(
        @NotBlank(message = "O título da nota é obrigatório")
        @Size(max = 200, message = "O título da nota não pode exceder 200 caracteres")
        String titulo,
        String conteudoMarkdown,
        Integer ordem,
        String topicoOrigemId) {
}
