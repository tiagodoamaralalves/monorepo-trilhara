package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import com.mongodb.MongoException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Falha genérica do MongoDB → {@code 503 SERVICE_UNAVAILABLE}, sem expor detalhe interno (D-04).
 *
 * <p>Chave duplicada é tratada antes por {@link MongoWriteExceptionMapper}.</p>
 */
@Provider
public class MongoExceptionMapper implements ExceptionMapper<MongoException> {

    private static final Logger LOG = Logger.getLogger(MongoExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MongoException exception) {
        String correlationId = ProblemDetailsFactory.novoCorrelationId();
        LOG.errorf(exception, "Falha de operacao no MongoDB [correlationId=%s]", correlationId);

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

