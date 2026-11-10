package br.com.trilhara.cronograma.dto;

import java.util.List;

public record DisponibilidadeSemanalDTO(
        List<Integer> disponibilidadeSemanalMinutos,
        List<Integer> cargaAtivaMinutosPorDia,
        List<Boolean> sobrecargaPorDia) {
}
