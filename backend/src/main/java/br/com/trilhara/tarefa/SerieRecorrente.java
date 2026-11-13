package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SerieRecorrente(
        ObjectId id,
        @NotNull ObjectId usuarioId,
        ObjectId listaId,
        @NotBlank @Size(min = 1, max = 200) String titulo,
        String descricao,
        @NotNull PrioridadeTarefa prioridade,
        String horario,
        List<Subtarefa> subtarefasModelo,
        @NotBlank String frequencia,
        int intervalo,
        List<Integer> diasDaSemana,
        String modoMensal,
        Integer diaDoMes,
        Integer ordinalSemana,
        Integer diaSemanaOrdinal,
        @NotBlank String tipoTermino,
        @NotNull LocalDate dataInicio,
        LocalDate dataFim,
        Integer maxOcorrencias,
        LocalDate geradoAte,
        int totalGeradas,
        boolean ativa,
        ModoLembrete modoLembrete,
        Integer lembreteMinutosAntes,
        String fusoAgendamento,
        String origemOperacaoId,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        @NotNull Instant criadoEm,
        @NotNull Instant atualizadoEm) {

    public SerieRecorrente {
        subtarefasModelo = subtarefasModelo == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(subtarefasModelo));
        diasDaSemana = diasDaSemana == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(diasDaSemana));
        // D-26: ausência nunca significa "herdar"; o default do modelo é HERDAR explícito.
        modoLembrete = modoLembrete == null ? ModoLembrete.HERDAR : modoLembrete;
    }
}
