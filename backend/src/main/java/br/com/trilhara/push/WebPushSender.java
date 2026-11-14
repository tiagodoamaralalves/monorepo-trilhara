package br.com.trilhara.push;

public interface WebPushSender {

    record PushSendResult(int statusCode, boolean sucesso, boolean endpointInvalido, String erro) {
        public static PushSendResult sucesso(int code) {
            return new PushSendResult(code, true, false, null);
        }

        public static PushSendResult endpointInvalido(int code, String erro) {
            return new PushSendResult(code, false, true, erro);
        }

        public static PushSendResult falha(int code, String erro) {
            return new PushSendResult(code, false, false, erro);
        }
    }

    /**
 * Entrega o payload <strong>cifrado</strong> (RFC 8291 / {@code aes128gcm}).
 *
 * @param subscription inscrição do dispositivo, com {@code p256dh}/{@code auth}
 * @param payload corpo JSON em claro que será cifrado para o dispositivo
 * @param ttlSeconds valor do cabeçalho {@code TTL} (RFC 8030); a janela útil é 900 s
 */
    PushSendResult send(PushSubscription subscription, byte[] payload, int ttlSeconds);
}

