package br.com.trilhara.cronograma.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.trilhara.cronograma.ModalidadeCronograma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AtualizarCronogramaRequest(
        @NotNull(message = "Modalidade é obrigatória.") ModalidadeCronograma modalidade,
        @Min(value = 30, message = "Duração mínima do bloco é 30 minutos.")
        @Max(value = 240, message = "Duração máxima do bloco é 240 minutos.") int duracaoBlocoMinutos,
        String tipoPeriodo,
        @NotNull(message = "dataInicio é obrigatória.") LocalDate dataInicio,
        LocalDate dataFim,
        List<Integer> diasAtivosSemana,
        @Min(1) @Max(24) Integer blocosPorDia,
        @NotEmpty(message = "Ao menos uma disciplina participante é obrigatória.") List<String> disciplinasParticipantesIds,
        Map<Integer, List<String>> semanaModeloManual) {
}
