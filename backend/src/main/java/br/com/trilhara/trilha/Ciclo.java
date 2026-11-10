package br.com.trilhara.trilha;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record Ciclo(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Pattern(regexp = "ponderado|sequencial") String tipo,
        @NotNull @Min(15) @Max(180) Integer duracaoBlocoMin,
        @NotBlank @Size(max = 10) String dataInicio,
        @Size(max = 10) String dataFim,
        @NotNull @Size(min = 7, max = 7) List<@NotNull @PositiveOrZero @Max(48) Integer> blocosSemana,
        @NotNull @Size(max = 100) List<@Valid DisciplinaConfig> disciplinasConfig) {

    public Ciclo {
        blocosSemana = blocosSemana == null ? null : List.copyOf(blocosSemana);
        disciplinasConfig = disciplinasConfig == null ? List.of() : List.copyOf(disciplinasConfig);
    }

    public record DisciplinaConfig(
            @NotBlank @Size(max = 100) String disciplinaId,
            boolean participa) {
    }
}
