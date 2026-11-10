package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.trilhara.shared.erro.BadRequestExceptionMapper;
import br.com.trilhara.shared.erro.ConstraintViolationExceptionMapper;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.DomainConflictExceptionMapper;
import br.com.trilhara.shared.erro.GlobalThrowableMapper;
import br.com.trilhara.shared.erro.NotFoundExceptionMapper;
import br.com.trilhara.shared.erro.ProblemDetails;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

class ProblemDetailsMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    record ExemploValidacao(@NotBlank(message = "não pode ser vazio") String nome) {}

    @Test
    @DisplayName("Deve mapear 400 Bad Request no formato RFC 9457")
    void deveMapearBadRequest400() throws Exception {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();

        Response response = mapper.toResponse(new BadRequestException("Parâmetro 'tamanho' inválido"));

        assertEquals(400, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof ProblemDetails);

        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals(400, details.status());
        assertEquals("urn:trilhara:error:bad-request", details.type());
        assertEquals("Requisição inválida", details.title());
        assertEquals("Parâmetro 'tamanho' inválido", details.detail());
        assertEquals("BAD_REQUEST", details.code());
        assertNull(details.errors());

        // Validação da serialização JSON
        String json = objectMapper.writeValueAsString(details);
        JsonNode root = objectMapper.readTree(json);
        assertEquals(400, root.get("status").asInt());
        assertEquals("BAD_REQUEST", root.get("code").asText());
        assertEquals("Parâmetro 'tamanho' inválido", root.get("detail").asText());
        assertFalse(root.has("errors"), "Campo 'errors' nulo não deve ser serializado");
    }

    @Test
    @DisplayName("Deve mapear 400 de validação com mapa de erros por campo")
    void deveMapearConstraintViolation400() throws Exception {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ExemploValidacao>> violations = validator.validate(new ExemploValidacao(""));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals(400, details.status());
        assertEquals("VALIDATION_ERROR", details.code());
        assertNotNull(details.errors());
        assertTrue(details.errors().containsKey("nome"));
        assertEquals(List.of("não pode ser vazio"), details.errors().get("nome"));

        String json = objectMapper.writeValueAsString(details);
        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("errors"));
        assertEquals("não pode ser vazio", root.get("errors").get("nome").get(0).asText());
    }

    @Test
    @DisplayName("Deve mapear 404 Not Found no formato RFC 9457 com instance")
    void deveMapearNotFound404() throws Exception {
        NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();
        setField(mapper, "uriInfo", new StubUriInfo("/api/trilhas/123"));

        Response response = mapper.toResponse(new NotFoundException("Trilha não encontrada"));

        assertEquals(404, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals(404, details.status());
        assertEquals("urn:trilhara:error:not-found", details.type());
        assertEquals("Recurso não encontrado", details.title());
        assertEquals("Trilha não encontrada", details.detail());
        assertEquals("NOT_FOUND", details.code());
        assertEquals("/api/trilhas/123", details.instance());

        String json = objectMapper.writeValueAsString(details);
        JsonNode root = objectMapper.readTree(json);
        assertEquals(404, root.get("status").asInt());
        assertEquals("/api/trilhas/123", root.get("instance").asText());
    }

    @Test
    @DisplayName("Deve mapear 409 Conflict via DomainConflictException e DomainConflictExceptionMapper")
    void deveMapearConflict409() throws Exception {
        DomainConflictExceptionMapper mapper = new DomainConflictExceptionMapper();
        setField(mapper, "uriInfo", new StubUriInfo("/api/trilhas/123/sessoes"));

        DomainConflictException ex = new DomainConflictException(
                "IDEMPOTENCY_KEY_CONFLICT",
                "Chave de idempotência já utilizada com parâmetros funcionais distintos."
        );

        Response response = mapper.toResponse(ex);

        assertEquals(409, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals(409, details.status());
        assertEquals("urn:trilhara:error:conflict", details.type());
        assertEquals("Conflito de negócio", details.title());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", details.code());
        assertEquals("Chave de idempotência já utilizada com parâmetros funcionais distintos.", details.detail());
        assertEquals("/api/trilhas/123/sessoes", details.instance());

        String json = objectMapper.writeValueAsString(details);
        JsonNode root = objectMapper.readTree(json);
        assertEquals(409, root.get("status").asInt());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", root.get("code").asText());
    }

    @Test
    @DisplayName("Deve normalizar erros HTTP com entidade para RFC 9457 sem expor a mensagem original")
    void deveNormalizarWebApplicationExceptionComEntidade() {
        GlobalThrowableMapper mapper = new GlobalThrowableMapper();
        setField(mapper, "uriInfo", new StubUriInfo("/api/recurso"));
        WebApplicationException exception = new WebApplicationException(
                "detalhe interno sensível",
                Response.status(Response.Status.BAD_REQUEST).entity("resposta legada").build());

        Response response = mapper.toResponse(exception);

        assertEquals(400, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals(400, details.status());
        assertEquals("/api/recurso", details.instance());
        assertEquals("Não foi possível concluir a requisição.", details.detail());
    }

    @Test
    @DisplayName("Deve registrar correlation ID para WebApplicationException 5xx")
    void deveAdicionarCorrelationIdParaWebApplicationExceptionInterna() {
        GlobalThrowableMapper mapper = new GlobalThrowableMapper();
        WebApplicationException exception = new WebApplicationException(
                "segredo interno",
                Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());

        Response response = mapper.toResponse(exception);

        assertEquals(500, response.getStatus());
        assertNotNull(response.getHeaderString("X-Correlation-ID"));
        ProblemDetails details = (ProblemDetails) response.getEntity();
        assertEquals("Ocorreu um erro interno inesperado. Tente novamente mais tarde.", details.detail());
        assertFalse(details.detail().contains("segredo interno"));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StubUriInfo implements UriInfo {
        private final String path;

        StubUriInfo(String path) {
            this.path = path;
        }

        @Override public String getPath() { return path; }
        @Override public String getPath(boolean decode) { return path; }
        @Override public List<PathSegment> getPathSegments() { return List.of(); }
        @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
        @Override public URI getRequestUri() { return URI.create("http://localhost" + path); }
        @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromPath(path); }
        @Override public URI getAbsolutePath() { return URI.create("http://localhost" + path); }
        @Override public UriBuilder getAbsolutePathBuilder() { return UriBuilder.fromPath(path); }
        @Override public URI getBaseUri() { return URI.create("http://localhost/"); }
        @Override public UriBuilder getBaseUriBuilder() { return UriBuilder.fromPath("/"); }
        @Override public MultivaluedMap<String, String> getPathParameters() { return null; }
        @Override public MultivaluedMap<String, String> getPathParameters(boolean decode) { return null; }
        @Override public MultivaluedMap<String, String> getQueryParameters() { return null; }
        @Override public MultivaluedMap<String, String> getQueryParameters(boolean decode) { return null; }
        @Override public List<String> getMatchedURIs() { return List.of(); }
        @Override public List<String> getMatchedURIs(boolean decode) { return List.of(); }
        @Override public List<Object> getMatchedResources() { return List.of(); }
        @Override public URI resolve(URI uri) { return uri; }
        @Override public URI relativize(URI uri) { return uri; }
    }
}
