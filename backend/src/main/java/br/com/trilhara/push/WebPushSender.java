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

    PushSendResult send(PushSubscription subscription, String payloadJson, int ttlSeconds);
}
