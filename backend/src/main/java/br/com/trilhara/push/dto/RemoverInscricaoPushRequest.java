package br.com.trilhara.push.dto;

/**
 * Corpo de {@code DELETE /api/push/subscriptions} (D-36e).
 *
 * <p>Somente o corpo é canal válido: a query string foi removida para não existirem duas
 * grafias do mesmo contrato.</p>
 */
public record RemoverInscricaoPushRequest(String endpoint) {
}
