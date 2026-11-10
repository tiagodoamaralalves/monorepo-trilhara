package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import com.mongodb.MongoException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MongoExceptionMapper implements ExceptionMapper<MongoException> {

    private static final Logger LOG = Logger.getLogger(MongoExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MongoException exception) {
        LOG.errorf(exception, "Falha de operacao no MongoDB: %s", exception.getMessage());
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:service-unavailable",
                "Serviço indisponível",
                Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                "Banco de dados MongoDB indisponível.",
                instance,
                "SERVICE_UNAVAILABLE",
                null
        );

        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
