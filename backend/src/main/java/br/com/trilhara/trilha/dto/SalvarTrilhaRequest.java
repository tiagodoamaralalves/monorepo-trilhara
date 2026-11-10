package br.com.trilhara.trilha.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.trilhara.edital.Edital;
import br.com.trilhara.trilha.Ciclo;
import br.com.trilhara.trilha.Progresso;
import br.com.trilhara.trilha.Trilha;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SalvarTrilhaRequest(
        @NotBlank(message = "O nome da trilha deve ter no máximo 120 caracteres.")
        @Size(max = 120, message = "O nome da trilha deve ter no máximo 120 caracteres.")
        String nome,

        @Size(max = 120, message = "Área/Cargo deve ter no máximo 120 caracteres.")
        String areaCargo,

        @Size(max = 80, message = "A banca deve ter no máximo 80 caracteres.")
        String banca,

        LocalDate dataProva,

        @Size(max = 2000, message = "As informações complementares devem ter no máximo 2000 caracteres.")
        String informacoesComplementares,

        @NotNull(message = "A trilha pode ter no máximo 100 disciplinas.")
        @Size(max = 100, message = "A trilha pode ter no máximo 100 disciplinas.")
        List<@Valid DisciplinaRequest> disciplinas,

        Map<String, @Valid Progresso> progresso,

        List<@Valid Ciclo> ciclos,

        String cicloAtivoId
) {
    public SalvarTrilhaRequest {
        nome = nome != null ? nome.trim() : null;
        areaCargo = areaCargo != null ? areaCargo.trim() : null;
        banca = banca != null ? banca.trim() : null;
        informacoesComplementares = informacoesComplementares != null ? informacoesComplementares.trim() : null;
        disciplinas = disciplinas == null ? List.of() : List.copyOf(disciplinas);
        progresso = progresso == null ? Map.of() : Map.copyOf(progresso);
        ciclos = ciclos == null ? List.of() : List.copyOf(ciclos);
    }

    public record DisciplinaRequest(
            @NotBlank(message = "O ID da disciplina deve ter no máximo 100 caracteres.")
            @Size(max = 100, message = "O ID da disciplina deve ter no máximo 100 caracteres.")
            String id,

            @NotBlank(message = "O nome da disciplina deve ter no máximo 100 caracteres.")
            @Size(max = 100, message = "O nome da disciplina deve ter no máximo 100 caracteres.")
            String nome,

            @Size(max = 32, message = "A cor da disciplina deve ter no máximo 32 caracteres.")
            String cor,

            @Positive
            @Min(value = 1, message = "O peso mínimo da disciplina é 1.")
            @Max(value = 5, message = "O peso máximo da disciplina é 5.")
            Integer peso,

            @Size(max = 2048, message = "O link do caderno deve ter no máximo 2048 caracteres.")
            @Pattern(regexp = "https?://.+", message = "O caderno deve usar uma URL HTTP ou HTTPS.")
            String cadernoLink,

            @Size(max = 500, message = "Cada disciplina pode ter no máximo 500 tópicos.")
            List<Edital.@Valid Topico> topicos
    ) {
        public DisciplinaRequest {
            peso = peso == null ? 1 : peso;
            topicos = topicos == null ? List.of() : List.copyOf(topicos);
        }

        public Trilha.Disciplina paraEntidade() {
            return new Trilha.Disciplina(id, nome, cor, peso, cadernoLink, topicos);
        }
    }
}
