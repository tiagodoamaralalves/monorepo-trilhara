package br.com.trilhara.cronograma.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarDisponibilidadeSemanalRequest(
        @NotNull(message = "disponibilidadeSemanalMinutos é obrigatória.")
        @Size(min = 7, max = 7, message = "disponibilidadeSemanalMinutos deve conter exatamente 7 valores.")
        List<Integer> disponibilidadeSemanalMinutos) {
}
