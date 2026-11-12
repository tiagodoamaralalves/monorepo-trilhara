package br.com.trilhara.trilha.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação de trilha.
 *
 * <p>É <b>exatamente um</b> entre {@code editalId} e {@code disciplinasIniciais}: enviar os dois
 * responde {@code 400 VALIDATION_ERROR} com {@code errors.editalId}. A variante {@code disciplinas}
 * mantida na F2 por compatibilidade foi removida (D-11); o mesmo vale para o endpoint dedicado
 * {@code POST /api/trilhas/importar/{editalId}}.</p>
 */
public record CriarTrilhaRequest(
        @NotBlank(message = "O nome da trilha é obrigatório.")
        @Size(max = 120, message = "O nome da trilha deve ter no máximo 120 caracteres.")
        String nome,
        @Size(max = 120, message = "Cargo deve ter no máximo 120 caracteres.")
        String cargo,
        @Size(max = 80, message = "A banca deve ter no máximo 80 caracteres.")
        String banca,
        LocalDate dataProva,
        @Size(max = 32)
        String cor,
        String editalId,
        List<@Valid CriarDisciplinaRequest> disciplinasIniciais) {

    public CriarTrilhaRequest {
        disciplinasIniciais = disciplinasIniciais == null ? List.of() : List.copyOf(disciplinasIniciais);
    }
}

