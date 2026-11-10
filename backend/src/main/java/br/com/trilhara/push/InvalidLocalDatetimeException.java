package br.com.trilhara.push;

import java.util.Map;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class InvalidLocalDatetimeException extends WebApplicationException {

    public InvalidLocalDatetimeException(String message) {
        super(message, Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("codigo", "INVALID_LOCAL_DATETIME", "mensagem", message))
                .build());
    }
}
