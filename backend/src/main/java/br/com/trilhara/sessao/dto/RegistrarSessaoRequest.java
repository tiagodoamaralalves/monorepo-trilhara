package br.com.trilhara.sessao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrarSessaoRequest(
        @NotBlank(message = "O identificador da disciplina é obrigatório.")
        String disciplinaId,

        @NotNull(message = "A data da sessão é obrigatória.")
        String dataSessao,

        @Min(value = 60, message = "A duração mínima da sessão é de 60 segundos.")
        @Max(value = 86400, message = "A duração máxima da sessão é de 86400 segundos (24 horas).")
        int duracaoSegundos,

        @Min(value = 0, message = "O total de questões deve ser maior ou igual a zero.")
        Integer questoes,

        @Min(value = 0, message = "O total de acertos deve ser maior ou igual a zero.")
        Integer acertos,

        String chaveIdempotencia
) {
    public int questoesEfetivas() {
        return questoes != null ? questoes : 0;
    }

    public int acertosEfetivos() {
        return acertos != null ? acertos : 0;
    }
}
