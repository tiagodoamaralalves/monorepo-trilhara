package br.com.trilhara.cronograma.dto;

import java.util.List;
import java.util.UUID;

public record ConcluirAtrasadosResponse(
        UUID operationId,
        int totalProcessados,
        int totalConcluidos,
        List<FalhaBlocoDTO> falhas) {
}
