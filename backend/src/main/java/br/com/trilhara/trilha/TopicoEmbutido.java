package br.com.trilhara.trilha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Representa um tópico embutido dentro de um documento de disciplina na coleção 'disciplinas'.
 */
public record TopicoEmbutido(
        @NotBlank @Size(max = 24, message = "O ID do tópico deve ter 24 caracteres hexadecimais.") String id,
        @NotBlank @Size(max = 300, message = "O nome do tópico deve ter no máximo 300 caracteres.") String nome,
        @NotNull Integer ordem) {

    public TopicoEmbutido {
        ordem = ordem == null ? 0 : ordem;
    }
}
