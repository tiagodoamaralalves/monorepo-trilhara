package br.com.trilhara.trilha;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.trilhara.edital.Edital;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record Trilha(
        String id,
        String usuarioId,
        String origemEditalId,
        long origemVersao,
        @NotBlank @Size(max = Trilha.MAX_NOME, message = "O nome da trilha deve ter no máximo 120 caracteres.") String nome,
        @Size(max = Trilha.MAX_CARGO, message = "Cargo deve ter no máximo 120 caracteres.") String cargo,
        @Size(max = Trilha.MAX_BANCA, message = "A banca deve ter no máximo 80 caracteres.") String banca,
        LocalDate dataProva,
        @Size(max = 32) String cor,
        @Size(max = Trilha.MAX_INFORMACOES, message = "As informações complementares devem ter no máximo 2000 caracteres.") String informacoesComplementares,
        String cronogramaAtivoId,
        Long versaoAtivaCronograma,
        String geracaoCronogramaLeaseToken,
        Instant geracaoCronogramaEmAndamentoAte,
        List<@Valid Disciplina> disciplinas,
        Map<String, @Valid Progresso> progresso,
        List<@Valid Ciclo> ciclos,
        String cicloAtivoId,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static final int MAX_NOME = 120;
    public static final int MAX_CARGO = 120;
    public static final int MAX_AREA_CARGO = 120;
    public static final int MAX_BANCA = 80;
    public static final int MAX_INFORMACOES = 2000;

    public Trilha {
        disciplinas = disciplinas == null ? List.of() : List.copyOf(disciplinas);
        progresso = progresso == null ? Map.of() : Map.copyOf(progresso);
        ciclos = ciclos == null ? List.of() : List.copyOf(ciclos);
    }

    public Trilha(
            String id,
            String donoId,
            String origemEditalId,
            long origemVersao,
            String nome,
            String areaCargo,
            String banca,
            LocalDate dataProva,
            String informacoesComplementares,
            List<Disciplina> disciplinas,
            Map<String, Progresso> progresso,
            List<Ciclo> ciclos,
            String cicloAtivoId,
            Instant criadoEm,
            Instant atualizadoEm) {
        this(id, donoId, origemEditalId, origemVersao, nome, areaCargo, banca, dataProva, null, informacoesComplementares, null, null, null, null, disciplinas, progresso, ciclos, cicloAtivoId, criadoEm, atualizadoEm);
    }

    public String donoId() {
        return usuarioId;
    }

    public String areaCargo() {
        return cargo;
    }

    public record Disciplina(
            @NotBlank @Size(max = 100, message = "O ID da disciplina deve ter no máximo 100 caracteres.") String id,
            @NotBlank @Size(max = 100, message = "O nome da disciplina deve ter no máximo 100 caracteres.") String nome,
            @Size(max = 32, message = "A cor da disciplina deve ter no máximo 32 caracteres.") String cor,
            @Positive @Min(value = 1, message = "O peso mínimo da disciplina é 1.") @Max(value = 5, message = "O peso máximo da disciplina é 5.") Integer peso,
            @Size(max = 2048, message = "O link do caderno deve ter no máximo 2048 caracteres.") @Pattern(regexp = "https?://.+", message = "O caderno deve usar uma URL HTTP ou HTTPS.") String cadernoLink,
            @Size(max = 500, message = "Cada disciplina pode ter no máximo 500 tópicos.") List<Edital.@Valid Topico> topicos) {

        public Disciplina {
            peso = peso == null ? 1 : peso;
            topicos = topicos == null ? List.of() : List.copyOf(topicos);
        }
    }
}
