package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DomainConflictExceptionMapper implements ExceptionMapper<DomainConflictException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(DomainConflictException exception) {
        String instance = resolvePath(uriInfo);
        String code = exception.getCode() != null ? exception.getCode() : DomainConflictException.DEFAULT_CODE;
        String detail = exception.getMessage() != null ? exception.getMessage() : "Conflito na operação.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:conflict",
                "Conflito de negócio",
                Response.Status.CONFLICT.getStatusCode(),
                detail,
                instance,
                code,
                null
        );

        return Response.status(Response.Status.CONFLICT)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }

    static String resolvePath(UriInfo uriInfo) {
        if (uriInfo == null) {
            return null;
        }
        try {
            return uriInfo.getPath();
        } catch (Exception e) {
            return null;
        }
    }
}
