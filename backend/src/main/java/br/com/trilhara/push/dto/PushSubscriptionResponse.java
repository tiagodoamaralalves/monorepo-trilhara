package br.com.trilhara.push.dto;

import java.time.Instant;

public record PushSubscriptionResponse(
        String id,
        String endpoint,
        Instant criadoEm) {
}
