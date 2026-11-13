package br.com.trilhara.push.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/push/preferencias} (D-36c).
 *
 * <p>O conjunto aceito é fechado — {@code {0,5,15,30,60}} (D-26). A validação do conjunto é
 * feita no serviço para que o erro saia como {@code 400 VALIDATION_ERROR} com
 * {@code errors.padraoMinutosAntes}.</p>
 */
public record AtualizarPreferenciasPushRequest(
        @NotNull Integer padraoMinutosAntes) {
}

