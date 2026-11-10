package br.com.trilhara.sessao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AtualizarSessaoRequest(
        @Min(value = 60, message = "A duração mínima da sessão é de 60 segundos.")
        @Max(value = 86400, message = "A duração máxima da sessão é de 86400 segundos (24 horas).")
        int duracaoSegundos,

        @Min(value = 0, message = "O total de questões deve ser maior ou igual a zero.")
        Integer questoes,

        @Min(value = 0, message = "O total de acertos deve ser maior ou igual a zero.")
        Integer acertos
) {
    public int questoesEfetivas() {
        return questoes != null ? questoes : 0;
    }

    public int acertosEfetivos() {
        return acertos != null ? acertos : 0;
    }
}
