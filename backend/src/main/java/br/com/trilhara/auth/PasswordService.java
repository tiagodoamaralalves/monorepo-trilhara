package br.com.trilhara.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class PasswordService {

    private static final String ALGORITMO_PBKDF2 = "PBKDF2WithHmacSHA512";
    private static final String ALGORITMO_HMAC = "HmacSHA256";
    private static final String PREFIXO_FORMATO = "$pbkdf2-sha512$";
    private static final int TAMANHO_SALT_BYTES = 16;
    private static final int TAMANHO_HASH_BITS = 512;

    private final SecureRandom secureRandom = new SecureRandom();
    private final int iteracoesPadrao;
    private final byte[] pepperBytes;
    private final String hashDummyParaTimingConstant;

    public PasswordService(
            @ConfigProperty(name = "trilhara.auth.password.iterations", defaultValue = "310000") int iteracoesPadrao,
            @ConfigProperty(name = "trilhara.auth.password.pepper") String pepper) {
        this.iteracoesPadrao = Math.max(10_000, iteracoesPadrao);
        this.pepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
        this.hashDummyParaTimingConstant = gerarHash("DummyTimingProtection@2026!");
    }

    public void validarPoliticaSenhaForte(String senha, String emailOpcional) {
        if (senha == null || senha.isBlank()) {
            throw new BadRequestException("A senha é obrigatória.");
        }
        if (senha.length() < 8 || senha.length() > 128) {
            throw new BadRequestException("A senha deve ter entre 8 e 128 caracteres.");
        }

        boolean temMaiuscula = false;
        boolean temMinuscula = false;
        boolean temDigito = false;
        boolean temEspecial = false;

        for (int i = 0; i < senha.length(); i++) {
            char c = senha.charAt(i);
            if (Character.isUpperCase(c)) {
                temMaiuscula = true;
            } else if (Character.isLowerCase(c)) {
                temMinuscula = true;
            } else if (Character.isDigit(c)) {
                temDigito = true;
            } else if (!Character.isWhitespace(c)) {
                temEspecial = true;
            }
        }

        if (!temMaiuscula || !temMinuscula || !temDigito || !temEspecial) {
            throw new BadRequestException(
                    "A senha deve conter pelo menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial.");
        }

        if (emailOpcional != null && !emailOpcional.isBlank()) {
            int arroba = emailOpcional.indexOf('@');
            String parteLocal = (arroba > 0 ? emailOpcional.substring(0, arroba) : emailOpcional)
                    .trim()
                    .toLowerCase();
            String senhaLower = senha.toLowerCase();
            if (parteLocal.length() >= 4 && senhaLower.contains(parteLocal)) {
                throw new BadRequestException("A senha não pode conter partes do seu endereço de e-mail.");
            }
            for (String segmento : parteLocal.split("[^a-z0-9]+")) {
                if (segmento.length() >= 4 && senhaLower.contains(segmento)) {
                    throw new BadRequestException("A senha não pode conter partes do seu endereço de e-mail.");
                }
            }
        }
    }

    public String gerarHash(String senhaPura) {
        if (senhaPura == null || senhaPura.isEmpty()) {
            throw new IllegalArgumentException("Senha não pode ser vazia para geração de hash.");
        }
        byte[] salt = new byte[TAMANHO_SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivado = derivarChave(senhaPura, salt, iteracoesPadrao, TAMANHO_HASH_BITS);
        String saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
        String hashB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(derivado);
        return PREFIXO_FORMATO + "i=" + iteracoesPadrao + "$" + saltB64 + "$" + hashB64;
    }

    public boolean verificarSenha(String senhaPura, String hashArmazenado) {
        if (senhaPura == null || senhaPura.isEmpty() || hashArmazenado == null || !hashArmazenado.startsWith(PREFIXO_FORMATO)) {
            return false;
        }
        try {
            String semPrefixo = hashArmazenado.substring(PREFIXO_FORMATO.length());
            String[] partes = semPrefixo.split("\\$");
            if (partes.length != 3 || !partes[0].startsWith("i=")) {
                return false;
            }
            int iteracoes = Integer.parseInt(partes[0].substring(2));
            if (iteracoes < 1_000 || iteracoes > 5_000_000) {
                return false;
            }
            byte[] salt = Base64.getUrlDecoder().decode(partes[1]);
            byte[] esperado = Base64.getUrlDecoder().decode(partes[2]);
            byte[] calculado = derivarChave(senhaPura, salt, iteracoes, esperado.length * 8);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Executa uma verificação real contra um hash dummy quando o usuário não existe,
     * mitigando enumeração de contas por análise de tempo de resposta (timing attack).
     */
    public void simularVerificacaoConstante(String senhaInformada) {
        verificarSenha(senhaInformada == null || senhaInformada.isEmpty() ? "dummy" : senhaInformada, hashDummyParaTimingConstant);
    }

    private byte[] derivarChave(String senhaPura, byte[] salt, int iteracoes, int tamanhoBits) {
        try {
            char[] senhaComPepper = aplicarPepper(senhaPura);
            KeySpec spec = new PBEKeySpec(senhaComPepper, salt, iteracoes, tamanhoBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITMO_PBKDF2);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha criptográfica ao processar hash de senha.", exception);
        }
    }

    private char[] aplicarPepper(String senhaPura) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITMO_HMAC);
        mac.init(new SecretKeySpec(pepperBytes, ALGORITMO_HMAC));
        byte[] preHash = mac.doFinal(senhaPura.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(preHash).toCharArray();
    }
}
