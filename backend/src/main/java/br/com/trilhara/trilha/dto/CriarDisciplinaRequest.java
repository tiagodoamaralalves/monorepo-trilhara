package br.com.trilhara.trilha.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação de disciplina.
 *
 * <p>O limite de 500 tópicos não é declarado com {@code @Size}: limite de acervo é conflito
 * de estado com os dados atuais do usuário e responde {@code 409 RESOURCE_LIMIT_EXCEEDED} no
 * serviço, nunca {@code 400} de sintaxe do payload.</p>
 */
public record CriarDisciplinaRequest(
        String id,
        @NotBlank(message = "O nome da disciplina é obrigatório.")
        @Size(max = 120, message = "O nome da disciplina deve ter no máximo 120 caracteres.")
        String nome,
        @Size(max = 32, message = "A cor da disciplina deve ter no máximo 32 caracteres.")
        String cor,
        @Min(value = 1, message = "O peso mínimo é 1.")
        @Max(value = 5, message = "O peso máximo é 5.")
        Integer peso,
        List<@Valid CriarTopicoRequest> topicos) {

    public CriarDisciplinaRequest {
        peso = peso == null ? 1 : peso;
        topicos = topicos == null ? List.of() : List.copyOf(topicos);
    }
}
