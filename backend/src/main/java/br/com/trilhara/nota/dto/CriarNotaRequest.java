package br.com.trilhara.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarNotaRequest(
    String cadernoId,
    String secaoId,
    String blocoId,
    @NotBlank(message = "O título da nota é obrigatório")
    @Size(max = 200, message = "O título da nota não pode exceder 200 caracteres")
    String titulo,
    String conteudoMarkdown,
    Integer ordem,
    String origemTopicoId
) {
    public String obterCadernoId() {
        return cadernoId != null && !cadernoId.isBlank() ? cadernoId : blocoId;
    }
}
