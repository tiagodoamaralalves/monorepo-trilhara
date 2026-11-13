package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R11 — payload e allowlist de URL (D-36d).
 */
class PushPayloadFactoryTest {

    private static final String ID = "507f1f77bcf86cd799439011";

    private static PushPayloadFactory factory(String url, String allowlist) {
        return new PushPayloadFactory(url, allowlist);
    }

    @Test
    @DisplayName("payload contém exatamente lembreteId, tarefaId, titulo e url absoluta")
    void payloadTemExatamenteOsQuatroCampos() {
        PushPayloadFactory f = factory("https://tarefas.trilhara.app/", "https://tarefas.trilhara.app");

        String json = f.payloadJson(ID, ID, "Estudar constitucional");

        assertTrue(json.startsWith("{\"lembreteId\":\"" + ID + "\""));
        assertTrue(json.contains("\"tarefaId\":\"" + ID + "\""));
        assertTrue(json.contains("\"titulo\":\"Estudar constitucional\""));
        assertTrue(json.contains("\"url\":\"https://tarefas.trilhara.app/?tarefaId=" + ID + "\""),
                "A URL do payload é absoluta (D-36d), nunca relativa.");
    }

    @Test
    @DisplayName("titulo é truncado em 80 code points sem quebrar par substituto")
    void tituloTruncadoEm80CodePoints() {
        String longo = "á".repeat(200);
        String truncado = PushPayloadFactory.truncar(longo);
        assertEquals(80, truncado.codePointCount(0, truncado.length()));

        // Emoji (par substituto) nunca é cortado ao meio.
        String comEmoji = "🎯".repeat(100);
        String truncadoEmoji = PushPayloadFactory.truncar(comEmoji);
        assertEquals(80, truncadoEmoji.codePointCount(0, truncadoEmoji.length()));
        assertEquals(truncadoEmoji, new String(truncadoEmoji.toCharArray()));
    }

    @Test
    @DisplayName("titulo vazio usa o texto padrão")
    void tituloVazioUsaPadrao() {
        assertEquals(PushPayloadFactory.TITULO_PADRAO, PushPayloadFactory.truncar("   "));
        assertEquals(PushPayloadFactory.TITULO_PADRAO, PushPayloadFactory.truncar(null));
    }

    @Test
    @DisplayName("aspas e barras do título são escapadas para JSON válido")
    void escapaCaracteresEspeciais() {
        String json = factory("https://tarefas.trilhara.app/", "https://tarefas.trilhara.app")
                .payloadJson(ID, ID, "Revisar \"Lei 8.112\" \\ art. 20\nlinha 2");

        assertTrue(json.contains("\\\"Lei 8.112\\\""), "Aspas duplas escapadas.");
        assertTrue(json.contains("\\\\ art. 20"), "Barra invertida escapada.");
        assertTrue(json.contains("\\nlinha 2"), "Quebra de linha escapada.");
    }

    @Test
    @DisplayName("URL fora da allowlist impede o boot: evita sequestro do clique")
    void urlForaDaAllowlistFalha() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> factory("https://malicioso.example/", "https://tarefas.trilhara.app"));
        assertTrue(erro.getMessage().contains("allowlist"));
    }

    @Test
    @DisplayName("esquema diferente de http/https é rejeitado")
    void esquemaInvalidoEhRejeitado() {
        assertThrows(IllegalStateException.class,
                () -> factory("javascript:alert(1)", "javascript:alert(1)"));
    }
}
