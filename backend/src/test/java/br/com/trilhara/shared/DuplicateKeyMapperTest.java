package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.MongoWriteExceptionMapper;
import br.com.trilhara.shared.erro.ProblemDetails;
import jakarta.ws.rs.core.Response;

/**
 * {@code E11000} ({@link ErrorCategory#DUPLICATE_KEY}) vira
 * {@code 409 DUPLICATE_RESOURCE}; qualquer outra falha de escrita vira 503.
 */
@DisplayName("Mapeamento seguro de MongoWriteException (E11000)")
class DuplicateKeyMapperTest {

    private static MongoWriteException erroDuplicado() {
        WriteError erro = new WriteError(
                11000,
                "E11000 duplicate key error collection: trilhara.usuarios index: uk_usuarios_email",
                new BsonDocument());
        return new MongoWriteException(erro, new ServerAddress("localhost", 27017), java.util.List.of());
    }

    private static MongoWriteException erroGenerico() {
        WriteError erro = new WriteError(13, "not authorized on trilhara", new BsonDocument());
        return new MongoWriteException(erro, new ServerAddress("localhost", 27017), java.util.List.of());
    }

    @Test
    @DisplayName("Chave duplicada responde 409 DUPLICATE_RESOURCE sem expor detalhe do servidor")
    void chaveDuplicadaResponde409() {
        MongoWriteExceptionMapper mapper = new MongoWriteExceptionMapper();

        Response response = mapper.toResponse(erroDuplicado());

        assertEquals(409, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        ProblemDetails problem = (ProblemDetails) response.getEntity();
        assertEquals(CodigoErro.DUPLICATE_RESOURCE.name(), problem.code());
        assertEquals("urn:trilhara:problem:duplicate-resource", problem.type());
        assertTrue(!problem.detail().contains("uk_usuarios_email"), "detail não pode revelar índice/coleção");
        assertTrue(!problem.detail().contains("E11000"), "detail não pode revelar o erro do banco");
    }

    @Test
    @DisplayName("Erro de escrita não duplicado responde 503 SERVICE_UNAVAILABLE com correlation ID")
    void erroDeEscritaNaoDuplicadoResponde503() {
        MongoWriteExceptionMapper mapper = new MongoWriteExceptionMapper();

        Response response = mapper.toResponse(erroGenerico());

        assertEquals(503, response.getStatus());
        assertEquals(ProblemDetails.MEDIA_TYPE, response.getMediaType().toString());
        assertNotNull(response.getHeaderString("X-Correlation-ID"));
        ProblemDetails problem = (ProblemDetails) response.getEntity();
        assertEquals(CodigoErro.SERVICE_UNAVAILABLE.name(), problem.code());
    }

    @Test
    @DisplayName("ErrorCategory do erro 11000 é DUPLICATE_KEY")
    void categoriaDoErro11000EhDuplicateKey() {
        assertEquals(ErrorCategory.DUPLICATE_KEY, erroDuplicado().getError().getCategory());
    }
}
