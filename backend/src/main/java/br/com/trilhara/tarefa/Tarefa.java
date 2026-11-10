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

public record Tarefa(
        ObjectId id,
        @NotNull ObjectId usuarioId,
        ObjectId listaId,
        ObjectId serieId,
        LocalDate dataOriginalOcorrencia,
        boolean desvinculadaDaSerie,
        @NotBlank @Size(min = 1, max = 200) String titulo,
        String descricao,
        @NotNull PrioridadeTarefa prioridade,
        LocalDate dataVencimento,
        String horario,
        @NotBlank String estado,
        boolean concluida,
        Instant concluidaEm,
        int ordem,
        List<Subtarefa> subtarefas,
        Integer lembreteMinutosAntes,
        boolean usaPadraoGlobalLembrete,
        String fusoAgendamento,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        @NotNull Instant criadoEm,
        @NotNull Instant atualizadoEm) {

    public Tarefa {
        subtarefas = subtarefas == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(subtarefas));
    }
}
