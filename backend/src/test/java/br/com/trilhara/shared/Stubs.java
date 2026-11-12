package br.com.trilhara.shared;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * Stubs mínimos (via {@link Proxy}) dos contextos JAX-RS usados pelos testes unitários
 * de mapeadores e filtros. Evita dependência de runtime do Quarkus em testes de unidade.
 */
final class Stubs {

    private Stubs() {
    }

    static ContainerRequestContext requestSemCorrelation() {
        return request(null);
    }

    static ContainerRequestContext requestComCorrelation(String correlationId) {
        return request(correlationId);
    }

    private static ContainerRequestContext request(String correlationId) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        if (correlationId != null) {
            headers.putSingle("X-Correlation-ID", correlationId);
        }
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[] { ContainerRequestContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeaderString" -> headers.getFirst((String) args[0]);
                    case "getHeaders" -> headers;
                    case "getMethod" -> "GET";
                    case "hasEntity" -> false;
                    case "getPropertyNames" -> List.of();
                    case "getAcceptableMediaTypes" -> List.of();
                    case "getAcceptableLanguages" -> List.of();
                    case "getCookies" -> Map.of();
                    default -> null;
                });
    }

    static ContainerResponseContext response() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        return (ContainerResponseContext) Proxy.newProxyInstance(
                ContainerResponseContext.class.getClassLoader(),
                new Class<?>[] { ContainerResponseContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeaders", "getStringHeaders" -> headers;
                    case "getHeaderString" -> {
                        Object valor = headers.getFirst((String) args[0]);
                        yield valor != null ? valor.toString() : null;
                    }
                    case "getStatus" -> 200;
                    case "hasEntity" -> false;
                    case "getAllowedMethods", "getLinks", "getPropertyNames" -> Set.of();
                    case "getEntityClass", "getEntityType" -> Object.class;
                    default -> null;
                });
    }
}
