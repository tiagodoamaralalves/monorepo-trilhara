package br.com.trilhara.edital;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record Edital(
        String id,
        @NotBlank String nome,
        String area,
        @NotBlank String cargo,
        @NotBlank String orgao,
        @NotBlank String banca,
        @NotNull Esfera esfera,
        String uf,
        LocalDate inicioInscricao,
        LocalDate fimInscricao,
        LocalDate dataProva,
        StatusEdital status,
        long versao,
        @NotNull List<@Valid Disciplina> disciplinas,
        Instant criadoEm,
        Instant atualizadoEm) {

    public Edital {
        disciplinas = disciplinas == null ? null : List.copyOf(disciplinas);
    }

    public record Disciplina(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 100) String nome,
            @Size(max = 32) String cor,
            @Positive @Max(5) Integer peso,
            @Size(max = 500) List<@Valid Topico> topicos) {

        public Disciplina {
            peso = peso == null ? 1 : peso;
            topicos = topicos == null ? List.of() : List.copyOf(topicos);
        }
    }

    public record Topico(
            @NotBlank @Size(max = 100, message = "O ID do tópico deve ter no máximo 100 caracteres.") String id,
            @NotBlank @Size(max = 500, message = "O nome do tópico deve ter no máximo 500 caracteres.") String nome) {
    }
}
