package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AccountDeletionResponse", description = "Resposta da solicitação assíncrona de exclusão de conta")
public record AccountDeletionResponse(
        @Schema(description = "Identificador opaco da operação de exclusão", required = true)
        String operationId,

        @Schema(description = "Estado da operação (PENDENTE, EM_ANDAMENTO ou RETENTANDO_LENTO)", required = true)
        String estado) {
}
