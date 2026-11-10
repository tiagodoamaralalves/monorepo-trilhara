package br.com.trilhara.shared.erro;

import java.util.UUID;

import org.jboss.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalThrowableMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalThrowableMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webAppEx && webAppEx.getResponse() != null) {
            Response existing = webAppEx.getResponse();
            if (existing.getEntity() instanceof ProblemDetails) {
                return existing;
            }
            int status = existing.getStatus();
            String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
            String title = existing.getStatusInfo() != null ? existing.getStatusInfo().getReasonPhrase() : "Erro HTTP";
            String detail = status >= 500
                    ? "Ocorreu um erro interno inesperado. Tente novamente mais tarde."
                    : "Não foi possível concluir a requisição.";
            String correlationId = status >= 500 ? UUID.randomUUID().toString() : null;
            ProblemDetails problem = new ProblemDetails(
                    "urn:trilhara:error:http-" + status,
                    title,
                    status,
                    detail,
                    instance,
                    "HTTP_" + status,
                    null
            );
            Response.ResponseBuilder response = Response.fromResponse(existing)
                    .type(ProblemDetails.MEDIA_TYPE)
                    .entity(problem);
            if (correlationId != null) {
                LOG.errorf(webAppEx, "Erro HTTP interno não tratado [correlationId=%s]", correlationId);
                response.header("X-Correlation-ID", correlationId);
            }
            return response.build();
        }

        String correlationId = UUID.randomUUID().toString();
        LOG.errorf(exception, "Erro interno não tratado [correlationId=%s]: %s", correlationId, exception.getMessage());

        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:internal",
                "Erro interno do servidor",
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Ocorreu um erro interno inesperado. Tente novamente mais tarde.",
                instance,
                "INTERNAL_ERROR",
                null
        );

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .header("X-Correlation-ID", correlationId)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
