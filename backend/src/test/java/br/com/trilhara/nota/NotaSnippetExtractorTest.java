package br.com.trilhara.nota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.WebApplicationException;

class NotaSnippetExtractorTest {

    private final NotaSnippetExtractor extractor = new NotaSnippetExtractor();

    @Test
    void deveRejeitarQueryNulaSemLancarNullPointerException() {
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> extractor.validarQueryBusca(null));

        assertEquals(400, exception.getResponse().getStatus());
    }

    @Test
    void deveRejeitarExtracaoComQueryNulaSemLancarNullPointerException() {
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> extractor.extrairSnippet("texto", null, "Título"));

        assertEquals(400, exception.getResponse().getStatus());
    }
}