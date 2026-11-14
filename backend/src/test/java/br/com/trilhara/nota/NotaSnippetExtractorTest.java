package br.com.trilhara.nota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.nota.dto.DestaqueDTO;
import jakarta.ws.rs.WebApplicationException;

/**
 * Snippet de busca: texto puro, ≤ 180 caracteres e offsets corretos mesmo com acento.
 */
class NotaSnippetExtractorTest {

    private final NotaSnippetExtractor extractor = new NotaSnippetExtractor();

    @Test
    @DisplayName("rejeita query nula ou vazia sem lançar NullPointerException")
    void deveRejeitarQueryNulaSemLancarNullPointerException() {
        WebApplicationException nula = assertThrows(
                WebApplicationException.class, () -> extractor.validarQueryBusca(null));
        assertEquals(400, nula.getResponse().getStatus());

        WebApplicationException vazia = assertThrows(
                WebApplicationException.class, () -> extractor.validarQueryBusca("   "));
        assertEquals(400, vazia.getResponse().getStatus());
    }

    @Test
    @DisplayName("rejeita consulta só com stop words e aceita termo significativo")
    void deveRejeitarApenasStopWords() {
        assertThrows(WebApplicationException.class, () -> extractor.validarQueryBusca("de que o a"));
        assertThrows(WebApplicationException.class, () -> extractor.validarQueryBusca("a"));
        extractor.validarQueryBusca("constituicao");
        extractor.validarQueryBusca("Constituição");
    }

    @Test
    @DisplayName("extrai snippet texto puro com destaque do termo acentuado (offsets no original)")
    void deveExtrairSnippetComDestaqueDeTermoAcentuado() {
        String texto = "Introdução detalhada sobre os princípios fundamentais e a Constituição da "
                + "República Federativa do Brasil promulgada em 1988.";

        NotaSnippetExtractor.SnippetBusca resultado = extractor.extrairSnippet(texto, "constituicao", "Título");

        assertTrue(resultado.texto().length() <= NotaSnippetExtractor.LIMITE_SNIPPET,
                "Snippet deve respeitar o limite de 180 caracteres: " + resultado.texto().length());
        assertFalse(resultado.texto().contains("<mark>"), "Backend nunca gera marcação");
        assertFalse(resultado.destaques().isEmpty(), "Deve destacar o termo encontrado");

        DestaqueDTO destaque = resultado.destaques().get(0);
        String recortado = resultado.texto().substring(destaque.inicio(), destaque.fim());
        assertEquals("Constituição", recortado,
                "Os offsets devem apontar exatamente para o termo no texto original");
    }

    @Test
    @DisplayName("snippet termina com reticências mas nunca excede 180 caracteres")
    void snippetNuncaExcedeOLimite() {
        String longo = "palavra ".repeat(200) + "constituicao " + "fim ".repeat(200);
        NotaSnippetExtractor.SnippetBusca resultado = extractor.extrairSnippet(longo, "constituicao", "T");

        assertTrue(resultado.texto().length() <= NotaSnippetExtractor.LIMITE_SNIPPET);
        assertTrue(resultado.texto().endsWith("...") || resultado.texto().length() < 180);
        assertFalse(resultado.destaques().isEmpty());
    }

    @Test
    @DisplayName("sem ocorrência devolve o começo do texto truncado; conteúdo vazio cai no título")
    void deveCairNoTituloQuandoNaoHaConteudo() {
        NotaSnippetExtractor.SnippetBusca semMatch =
                extractor.extrairSnippet("Texto sem o termo procurado.", "constituicao", "Título");
        assertEquals(List.of(), semMatch.destaques());
        assertTrue(semMatch.texto().startsWith("Texto sem o termo"));

        NotaSnippetExtractor.SnippetBusca vazio =
                extractor.extrairSnippet("", "constituicao", "Meu Título");
        assertEquals("Meu Título", vazio.texto());
        assertEquals(List.of(), vazio.destaques());
    }

    @Test
    @DisplayName("extrai snippet com query nula lança 400 em vez de NPE")
    void deveRejeitarExtracaoComQueryNula() {
        WebApplicationException exception = assertThrows(
                WebApplicationException.class, () -> extractor.extrairSnippet("texto", null, "Título"));
        assertEquals(400, exception.getResponse().getStatus());
    }
}
