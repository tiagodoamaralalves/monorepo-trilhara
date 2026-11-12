package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;

/**
 * Catálogo fechado de erros (D-04).
 *
 * <p>Valida que cada {@link CodigoErro} produz um {@link ProblemDetails} coerente:
 * {@code status}, {@code type} no padrão {@code urn:trilhara:problem:<kebab>},
 * {@code code} igual ao nome do enum, {@code instance} normalizado e serialização sem
 * campo {@code errors} quando vazio.</p>
 */
@DisplayName("R1 - Catálogo fechado de códigos de erro (RFC 9457)")
class ProblemDetailsCatalogoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Todo código do catálogo produz problem+json com type, code e status coerentes")
    void todoCodigoProduzProblemDetailsCoerente() throws Exception {
        for (CodigoErro codigo : CodigoErro.values()) {
            ProblemDetails problem = ProblemDetailsFactory.criar(codigo, "Detalhe de teste.", "/api/trilhas/123");

            assertEquals(codigo.name(), problem.code(), "code deve ser o nome do enum");
            assertEquals(codigo.httpStatus(), problem.status(), "status deve vir do catálogo");
            assertEquals(codigo.title(), problem.title());
            assertEquals(codigo.typeUri(), problem.type());
            assertTrue(problem.type().startsWith("urn:trilhara:problem:"), "type deve usar o prefixo do produto");
            assertEquals("/api/trilhas/123", problem.instance());

            String esperadoKebab = codigo.name().toLowerCase(Locale.ROOT).replace('_', '-');
            assertEquals("urn:trilhara:problem:" + esperadoKebab, problem.type());

            String json = objectMapper.writeValueAsString(problem);
            JsonNode root = objectMapper.readTree(json);
            assertEquals(codigo.httpStatus(), root.get("status").asInt());
            assertEquals(codigo.name(), root.get("code").asText());
            assertEquals(codigo.typeUri(), root.get("type").asText());
            assertFalse(root.has("errors"), "errors nulo não deve ser serializado");
            assertFalse(json.contains("Exception"), "detail não deve conter stack trace");

            assertEquals(ProblemDetails.MEDIA_TYPE, "application/problem+json");
        }
    }

    @Test
    @DisplayName("Catálogo não contém códigos legados removidos por D-04")
    void catalogoNaoContemCodigosLegados() {
        for (CodigoErro codigo : CodigoErro.values()) {
            assertFalse(codigo.name().equals("PAYLOAD_TOO_LARGE"), "PAYLOAD_TOO_LARGE foi removido");
            assertFalse(codigo.name().equals("SCHEDULE_LIMIT_EXCEEDED"), "SCHEDULE_LIMIT_EXCEEDED foi removido");
            assertFalse(codigo.name().equals("BAD_REQUEST"), "BAD_REQUEST virou VALIDATION_ERROR");
            assertFalse(codigo.name().startsWith("HTTP_"), "HTTP_<status> foi removido");
            assertTrue(codigo.httpStatus() != 422, "422 não é usado pelo produto");
        }
    }

    @Test
    @DisplayName("instance normalizado: barra inicial e sem query string")
    void instanceNormalizado() {
        assertEquals("/api/notas?pagina=2", "/api/notas?pagina=2");
        assertEquals("/api/notas", ProblemDetailsFactory.normalizarInstance("api/notas?pagina=2"));
        assertEquals("/api/notas", ProblemDetailsFactory.normalizarInstance("/api/notas?pagina=2"));
        assertEquals("/api/notas", ProblemDetailsFactory.normalizarInstance("/api/notas#frag"));
        assertEquals("/", ProblemDetailsFactory.normalizarInstance(null));
        assertEquals("/", ProblemDetailsFactory.normalizarInstance("   "));
        assertEquals("/", ProblemDetailsFactory.normalizarInstance("?pagina=1"));
    }

    @Test
    @DisplayName("Falha interna usa detail neutro e nunca a causa original")
    void falhaInternaNaoExpoeCausa() {
        ProblemDetails problem = ProblemDetailsFactory.interno("/api/trilhas");
        assertEquals(CodigoErro.INTERNAL_ERROR.name(), problem.code());
        assertEquals(500, problem.status());
        assertFalse(problem.detail().contains("SQL"));
        assertFalse(problem.detail().contains("Exception"));
    }
}
