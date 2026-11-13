package br.com.trilhara.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pseudonimização de identificadores em trilhas operacionais (D-04/RI-34).
 *
 * <p>Eventos operacionais precisam correlacionar ocorrências da mesma conta sem registrar
 * {@code usuarioId}, e-mail, nome ou qualquer outro identificador pessoal. Esta classe devolve
 * um <strong>pseudônimo estável</strong> (primeiros 8 hex de SHA-256) derivado do identificador:
 * a mesma conta produz sempre o mesmo sufixo, mas o valor não permite recuperar o original e
 * nunca aparece em claro em nenhuma linha.</p>
 */
public final class Pseudonimo {

    private static final int TAMANHO_PSEUDONIMO = 8;

    private Pseudonimo() {
    }

    /** Pseudônimo estável de 8 caracteres hexadecimais; {@code "-"} para entrada vazia. */
    public static String de(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return "-";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of()
                    .formatHex(digest.digest(identificador.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, TAMANHO_PSEUDONIMO);
        } catch (NoSuchAlgorithmException e) {
            return "-";
        }
    }
}
