package br.com.trilhara.push;

import java.time.Instant;
import org.bson.types.ObjectId;

public record PushSubscription(
        ObjectId id,
        ObjectId usuarioId,
        String endpoint,
        String p256dh,
        String auth,
        String userAgent,
        Instant criadoEm,
        Instant atualizadoEm) {
}
