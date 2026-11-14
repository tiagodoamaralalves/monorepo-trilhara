package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * cifra RFC 8291 / {@code aes128gcm} (RFC 8188).
 *
 * <p>O teste é um <em>round-trip</em>: o corpo gerado é decifrado com as mesmas chaves do
 * "dispositivo" de teste, provando que cabeçalho, HKDF e delimitador de registro estão
 * corretos. Nenhum provedor externo é usado.</p>
 */
class WebPushCriptografiaTest {

    /** Par de chaves do "dispositivo": o que um navegador geraria para {@code p256dh}. */
    private record Dispositivo(byte[] publica, byte[] auth, PrivateKey privada) {
    }

    private static Dispositivo novoDispositivo() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = kpg.generateKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return new Dispositivo(
                WebPushCriptografia.exportarNaoComprimido((ECPublicKey) par.getPublic()),
                auth,
                par.getPrivate());
    }

    /** Decifra seguindo a RFC 8291 com as chaves do dispositivo (lado do navegador). */
    private static byte[] decifrar(byte[] corpo, Dispositivo dispositivo) throws Exception {
        byte[] salt = Arrays.copyOfRange(corpo, 0, 16);
        byte[] asPublic = Arrays.copyOfRange(corpo, 21, 86);
        byte[] cifrado = Arrays.copyOfRange(corpo, 86, corpo.length);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(dispositivo.privada());
        ka.doPhase(WebPushCriptografia.importarUaPublic(asPublic), true);
        byte[] ecdhSecret = ka.generateSecret();

        byte[] infoIkm = concatenar(WebPushCriptografia.INFO_CHAVE, new byte[] {0x00},
                dispositivo.publica(), asPublic);
        byte[] ikm = hkdf(dispositivo.auth(), infoIkm, ecdhSecret, 32);
        byte[] cek = hkdf(salt, concatenar(WebPushCriptografia.INFO_CEK, new byte[] {0x00}), ikm, 16);
        byte[] nonce = hkdf(salt, concatenar(WebPushCriptografia.INFO_NONCE, new byte[] {0x00}), ikm, 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(cifrado);
    }

    private static byte[] hkdf(byte[] salt, byte[] info, byte[] ikm, int tamanho) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), tamanho);
    }

    private static byte[] concatenar(byte[]... partes) {
        int total = Arrays.stream(partes).mapToInt(p -> p.length).sum();
        byte[] saida = new byte[total];
        int posicao = 0;
        for (byte[] parte : partes) {
            System.arraycopy(parte, 0, saida, posicao, parte.length);
            posicao += parte.length;
        }
        return saida;
    }

    @Test
    @DisplayName("round-trip: o dispositivo decifra o payload e recupera o JSON original")
    void roundTripRecuperaOPayload() throws Exception {
        Dispositivo dispositivo = novoDispositivo();
        byte[] payload = "{\"tarefaId\":\"abc\",\"titulo\":\"Estudar\"}".getBytes(StandardCharsets.UTF_8);

        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(dispositivo.publica());
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(dispositivo.auth());

        byte[] corpo = WebPushCriptografia.cifrar(p256dh, auth, payload);
        byte[] claroComDelimitador = decifrar(corpo, dispositivo);

        // RFC 8188: o último byte do registro é o delimitador 0x02.
        assertEquals(WebPushCriptografia.DELIMITADOR_REGISTRO,
                claroComDelimitador[claroComDelimitador.length - 1]);
        byte[] claro = Arrays.copyOf(claroComDelimitador, claroComDelimitador.length - 1);
        assertArrayEquals(payload, claro);
    }

    @Test
    @DisplayName("cabeçalho: salt de 16 bytes, rs=4096, idlen=65 e chave efêmera de 65 bytes")
    void cabecalhoSegueARfc8188() throws Exception {
        Dispositivo dispositivo = novoDispositivo();
        byte[] corpo = WebPushCriptografia.cifrar(dispositivo.publica(), dispositivo.auth(), "x".getBytes());

        long rs = ((long) (corpo[16] & 0xFF) << 24) | ((long) (corpo[17] & 0xFF) << 16)
                | ((long) (corpo[18] & 0xFF) << 8) | (corpo[19] & 0xFF);
        assertEquals(4096L, rs, "O tamanho de registro é 4096 (RFC 8188).");
        assertEquals(65, corpo[20], "idlen é o tamanho da chave efêmera: 65 bytes.");
        assertEquals(0x04, corpo[21], "A chave efêmera é um ponto EC não comprimido.");
        assertTrue(corpo.length > 86, "O corpo contém o ciphertext depois do cabeçalho de 86 bytes.");
    }

    @Test
    @DisplayName("cada envio usa salt e par efêmero novos: dois corpos nunca coincidem")
    void cadaEnvioEhUnico() throws Exception {
        Dispositivo dispositivo = novoDispositivo();
        byte[] payload = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        byte[] primeiro = WebPushCriptografia.cifrar(dispositivo.publica(), dispositivo.auth(), payload);
        byte[] segundo = WebPushCriptografia.cifrar(dispositivo.publica(), dispositivo.auth(), payload);

        assertTrue(!Arrays.equals(Arrays.copyOfRange(primeiro, 0, 16), Arrays.copyOfRange(segundo, 0, 16)),
                "O salt é aleatório por mensagem.");
        assertTrue(!Arrays.equals(Arrays.copyOfRange(primeiro, 21, 86), Arrays.copyOfRange(segundo, 21, 86)),
                "O par efêmero é novo por mensagem.");
    }

    @Test
    @DisplayName("chave p256dh malformada é rejeitada antes de qualquer envio")
    void chaveMalformadaEhRejeitada() {
        assertThrows(Exception.class, () -> WebPushCriptografia.importarUaPublic(new byte[10]));
    }
}
