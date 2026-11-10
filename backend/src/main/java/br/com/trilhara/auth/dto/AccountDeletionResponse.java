package br.com.trilhara.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AccountDeletionResponse", description = "Resposta da solicitação assíncrona de exclusão de conta")
public record AccountDeletionResponse(
        @Schema(description = "Identificador opaco da operação de exclusão", required = true)
        String operationId,

        @Schema(description = "Estado da operação de exclusão (ex: PENDENTE, PROCESSANDO, CONCLUIDO, FALHA)", required = true)
        String estado) {
}
