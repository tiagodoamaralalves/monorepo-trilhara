package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Escrita recusada pelo MongoDB.
 *
 * <p>Chave duplicada ({@code E11000}) que não foi capturada como idempotência por um
 * repositório vira {@code 409 DUPLICATE_RESOURCE}; qualquer outra falha de escrita vira
 * {@code 503 SERVICE_UNAVAILABLE}.</p>
 */
@Provider
public class MongoWriteExceptionMapper implements ExceptionMapper<MongoWriteException> {

    private static final Logger LOG = Logger.getLogger(MongoWriteExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MongoWriteException exception) {
        boolean duplicada = exception.getError() != null
                && exception.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;

        if (duplicada) {
            ProblemDetails problem = ProblemDetailsFactory.criar(
                    CodigoErro.DUPLICATE_RESOURCE,
                    "Já existe um recurso com os mesmos dados únicos.",
                    DomainConflictExceptionMapper.resolvePath(uriInfo));
            return Response.status(problem.status())
                    .type(ProblemDetails.MEDIA_TYPE)
                    .entity(problem)
                    .build();
        }

        String correlationId = ProblemDetailsFactory.novoCorrelationId();
        LOG.errorf(exception, "Falha de escrita no MongoDB [correlationId=%s]", correlationId);

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.SERVICE_UNAVAILABLE,
                "Banco de dados indisponível. Tente novamente em instantes.",
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(ProblemDetailsFactory.HEADER_CORRELATION_ID, correlationId)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
