package br.com.trilhara.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Alteração do tema de interface (D-10). */
public record AtualizarTemaRequest(
        @NotBlank(message = "tema é obrigatório.")
        @Pattern(regexp = "dark|light|system", message = "tema deve ser dark, light ou system.")
        String tema) {
}
