package br.com.trilhara.trilha.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarTrilhaRequest(
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
        String cor,
        String editalId,
        List<@Valid CriarDisciplinaRequest> disciplinasIniciais,
        List<@Valid CriarDisciplinaRequest> disciplinas) {

    public String obterCargoEfetivo() {
        if (cargo != null && !cargo.isBlank()) {
            return cargo.trim();
        }
        if (areaCargo != null && !areaCargo.isBlank()) {
            return areaCargo.trim();
        }
        return null;
    }

    public List<CriarDisciplinaRequest> obterDisciplinasEfetivas() {
        if (disciplinasIniciais != null && !disciplinasIniciais.isEmpty()) {
            return disciplinasIniciais;
        }
        if (disciplinas != null && !disciplinas.isEmpty()) {
            return disciplinas;
        }
        return List.of();
    }
}
