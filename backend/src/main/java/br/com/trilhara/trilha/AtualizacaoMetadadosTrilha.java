package br.com.trilhara.trilha;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizacaoMetadadosTrilha(
        @NotBlank(message = "Informe o nome da trilha.")
        @Size(max = Trilha.MAX_NOME, message = "O nome da trilha deve ter no máximo 120 caracteres.") String nome,
        @Size(max = Trilha.MAX_AREA_CARGO, message = "Área/Cargo deve ter no máximo 120 caracteres.") String areaCargo,
        @Size(max = Trilha.MAX_BANCA, message = "A banca deve ter no máximo 80 caracteres.") String banca,
        LocalDate dataProva,
        @Size(max = Trilha.MAX_INFORMACOES, message = "As informações complementares devem ter no máximo 2000 caracteres.") String informacoesComplementares) {

    public AtualizacaoMetadadosTrilha {
        nome = nome == null ? null : nome.strip();
        areaCargo = vazioParaNulo(areaCargo);
        banca = vazioParaNulo(banca);
        informacoesComplementares = vazioParaNulo(informacoesComplementares);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
