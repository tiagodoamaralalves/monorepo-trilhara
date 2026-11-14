package br.com.trilhara.push;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra de payload Web Push conforme RFC 8291 sobre {@code aes128gcm} (RFC 8188), usando apenas
 * JCA do JDK.
 *
 * <p>Fluxo normativo:</p>
 * <ol>
 * <li>par efêmero ECDH P-256 ({@code as_public}/{@code as_private});</li>
 * <li>{@code ecdh_secret = ECDH(as_private, ua_public)};</li>
 * <li>{@code ikm = HKDF(salt=auth_secret, info="WebPush: info\0" || ua_public || as_public,
 * L=32, ecdh_secret)};</li>
 * <li>{@code cek = HKDF(salt=salt, info="Content-Encoding: aes128gcm\0", L=16, ikm)} e
 * {@code nonce = HKDF(salt=salt, info="Content-Encoding: nonce\0", L=12, ikm)};</li>
 * <li>corpo = {@code salt(16) || rs=4096 (uint32 BE) || idlen=65 || as_public(65) ||
 * AES-128-GCM(cek, nonce, payload || 0x02)}.</li>
 * </ol>
 *
 * <p>O byte {@code 0x02} ao final é o delimitador de registro único exigido pela RFC 8188.</p>
 */
public final class WebPushCriptografia {

    static final int TAMANHO_SALT = 16;
    static final int TAMANHO_NONCE = 12;
    static final long TAMANHO_REGISTRO = 4096L;
    static final byte DELIMITADOR_REGISTRO = 0x02;
    static final byte[] INFO_CHAVE = "WebPush: info".getBytes(StandardCharsets.UTF_8);
    static final byte[] INFO_CEK = "Content-Encoding: aes128gcm".getBytes(StandardCharsets.UTF_8);
    static final byte[] INFO_NONCE = "Content-Encoding: nonce".getBytes(StandardCharsets.UTF_8);

    private static final String ALGORITMO_HMAC_SHA256 = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCriptografia() {
    }

    /**
 * Cifra {@code payload} para o dispositivo descrito por {@code p256dh}/{@code auth}
 * (base64url).
 *
 * @return corpo pronto para {@code POST}, com {@code Content-Encoding: aes128gcm}
 */
    public static byte[] cifrar(String p256dhBase64Url, String authBase64Url, byte[] payload)
            throws GeneralSecurityException {
        return cifrar(decodificarBase64Url(p256dhBase64Url), decodificarBase64Url(authBase64Url), payload);
    }

    static byte[] cifrar(byte[] uaPublicBytes, byte[] authSecret, byte[] payload) throws GeneralSecurityException {
        byte[] salt = new byte[TAMANHO_SALT];
        RANDOM.nextBytes(salt);

        KeyPair parServidor = gerarParEfemero();
        byte[] asPublic = exportarNaoComprimido((ECPublicKey) parServidor.getPublic());
        byte[] ecdhSecret = calcularSegredo(parServidor.getPrivate(), importarUaPublic(uaPublicBytes));

        byte[] infoIkm = concatenar(INFO_CHAVE, new byte[] {0x00},
                Arrays.copyOf(uaPublicBytes, 65), Arrays.copyOf(asPublic, 65));
        byte[] ikm = hkdf(authSecret, infoIkm, ecdhSecret, 32);

        byte[] cek = hkdf(salt, concatenar(INFO_CEK, new byte[] {0x00}), ikm, 16);
        byte[] nonce = hkdf(salt, concatenar(INFO_NONCE, new byte[] {0x00}), ikm, 12);

        byte[] textoClaro = concatenar(payload, new byte[] {DELIMITADOR_REGISTRO});
        byte[] cifrado = aesGcm(cek, nonce, textoClaro);

        return concatenar(salt, cabecalho(asPublic), cifrado);
    }

    /** Cabeçalho RFC 8188: {@code rs (uint32 BE) || idlen || keyid}. */
    private static byte[] cabecalho(byte[] asPublic) {
        byte[] cabecalho = new byte[4 + 1 + asPublic.length];
        long rs = TAMANHO_REGISTRO;
        cabecalho[0] = (byte) (rs >>> 24);
        cabecalho[1] = (byte) (rs >>> 16);
        cabecalho[2] = (byte) (rs >>> 8);
        cabecalho[3] = (byte) rs;
        cabecalho[4] = (byte) asPublic.length;
        System.arraycopy(asPublic, 0, cabecalho, 5, asPublic.length);
        return cabecalho;
    }

    static KeyPair gerarParEfemero() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return kpg.generateKeyPair();
    }

    static byte[] calcularSegredo(PrivateKey privada, PublicKey publica) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privada);
        ka.doPhase(publica, true);
        return ka.generateSecret();
    }

    /** Reconstrói a chave pública do dispositivo a partir do ponto EC não comprimido (65 bytes). */
    static PublicKey importarUaPublic(byte[] uaPublic) throws GeneralSecurityException {
        if (uaPublic.length != 65 || uaPublic[0] != 0x04) {
            throw new GeneralSecurityException("Chave p256dh deve ser ponto EC não comprimido de 65 bytes.");
        }
        AlgorithmParameters parametros = AlgorithmParameters.getInstance("EC");
        parametros.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parametros.getParameterSpec(ECParameterSpec.class);

        ECPoint ponto = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uaPublic, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uaPublic, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(ponto, spec));
    }

    static byte[] exportarNaoComprimido(ECPublicKey chave) {
        byte[] saida = new byte[65];
        saida[0] = 0x04;
        System.arraycopy(para32Bytes(chave.getW().getAffineX()), 0, saida, 1, 32);
        System.arraycopy(para32Bytes(chave.getW().getAffineY()), 0, saida, 33, 32);
        return saida;
    }

    private static byte[] para32Bytes(BigInteger valor) {
        byte[] bruto = valor.toByteArray();
        if (bruto.length == 32) {
            return bruto;
        }
        byte[] saida = new byte[32];
        int copiar = Math.min(bruto.length, 32);
        System.arraycopy(bruto, bruto.length - copiar, saida, 32 - copiar, copiar);
        return saida;
    }

    /** HKDF-SHA256 (RFC 5869) com {@code salt} obrigatório. */
    static byte[] hkdf(byte[] salt, byte[] info, byte[] ikm, int tamanho) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITMO_HMAC_SHA256);
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, ALGORITMO_HMAC_SHA256));
        byte[] prk = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(prk, ALGORITMO_HMAC_SHA256));
        byte[] saida = new byte[tamanho];
        byte[] bloco = new byte[0];
        int preenchido = 0;
        int contador = 1;
        while (preenchido < tamanho) {
            mac.update(bloco);
            mac.update(info);
            mac.update((byte) contador);
            bloco = mac.doFinal();
            int copiar = Math.min(bloco.length, tamanho - preenchido);
            System.arraycopy(bloco, 0, saida, preenchido, copiar);
            preenchido += copiar;
            contador++;
        }
        return saida;
    }

    static byte[] aesGcm(byte[] cek, byte[] nonce, byte[] textoClaro) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        return cipher.doFinal(textoClaro);
    }

    static byte[] decodificarBase64Url(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Chave Web Push ausente.");
        }
        String normalizado = valor.trim().replace('-', '+').replace('_', '/');
        return Base64.getDecoder().decode(normalizado);
    }

    private static byte[] concatenar(byte[]... partes) {
        int total = 0;
        for (byte[] parte : partes) {
            total += parte.length;
        }
        byte[] saida = new byte[total];
        int posicao = 0;
        for (byte[] parte : partes) {
            System.arraycopy(parte, 0, saida, posicao, parte.length);
            posicao += parte.length;
        }
        return saida;
    }
}
