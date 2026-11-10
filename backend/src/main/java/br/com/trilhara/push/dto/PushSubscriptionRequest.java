package br.com.trilhara.push.dto;

public record PushSubscriptionRequest(
        String endpoint,
        String p256dh,
        String auth,
        Keys keys,
        String userAgent) {

    public record Keys(String p256dh, String auth) {}

    public String resolvedP256dh() {
        if (p256dh != null && !p256dh.isBlank()) {
            return p256dh.trim();
        }
        return keys != null ? keys.p256dh() : null;
    }

    public String resolvedAuth() {
        if (auth != null && !auth.isBlank()) {
            return auth.trim();
        }
        return keys != null ? keys.auth() : null;
    }
}
