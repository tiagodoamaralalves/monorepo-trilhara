package br.com.trilhara.trilha.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarMetadadosTrilhaRequest(
        @NotBlank(message = "O nome da trilha é obrigatório.")
        @Size(max = 120, message = "O nome da trilha deve ter no máximo 120 caracteres.")
        String nome,
        @Size(max = 120, message = "Cargo deve ter no máximo 120 caracteres.")
        String cargo,
        @Size(max = 120, message = "Área/Cargo deve ter no máximo 120 caracteres.")
        String areaCargo,
        @Size(max = 80, message = "A banca deve ter no máximo 80 caracteres.")
        String banca,
        LocalDate dataProva,
        @Size(max = 32)
        String cor) {

    public String obterCargoEfetivo() {
        if (cargo != null && !cargo.isBlank()) {
            return cargo.trim();
        }
        if (areaCargo != null && !areaCargo.isBlank()) {
            return areaCargo.trim();
        }
        return null;
    }
}
