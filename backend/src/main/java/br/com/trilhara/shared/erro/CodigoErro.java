package br.com.trilhara.shared.erro;

import java.util.Locale;

import jakarta.ws.rs.core.Response;

/**
 * Catálogo fechado de códigos de erro de máquina do Trilhara.
 *
 * <p>Cada entrada fixa o status HTTP, o {@code title} em pt-BR e o {@code type} RFC 9457
 * derivado do próprio nome: {@code urn:trilhara:problem:<code-em-kebab-case>}.</p>
 *
 * <p>O cliente decide pelo {@code status} e pelo {@code code}; {@code detail} é texto
 * localizável e nunca contrato de máquina.</p>
 */
public enum CodigoErro {

    VALIDATION_ERROR(Response.Status.BAD_REQUEST, "Erro de validação"),

    INVALID_IDENTIFIER(Response.Status.BAD_REQUEST, "Identificador inválido"),

    INVALID_PAGINATION(Response.Status.BAD_REQUEST, "Paginação inválida"),

    INVALID_SEARCH_QUERY(Response.Status.BAD_REQUEST, "Consulta de busca inválida"),

    INVALID_LOCAL_DATETIME(Response.Status.BAD_REQUEST, "Data e hora locais inválidas"),

    IDEMPOTENCY_KEY_REQUIRED(Response.Status.BAD_REQUEST, "Chave de idempotência obrigatória"),

    IDEMPOTENCY_KEY_MISMATCH(Response.Status.BAD_REQUEST, "Chave de idempotência divergente"),

    UNAUTHORIZED(Response.Status.UNAUTHORIZED, "Não autorizado"),

    FORBIDDEN(Response.Status.FORBIDDEN, "Acesso negado"),

    NOT_FOUND(Response.Status.NOT_FOUND, "Recurso não encontrado"),

    TRASH_ITEM_EXPIRED(Response.Status.NOT_FOUND, "Item da lixeira expirado"),

    RESOURCE_LIMIT_EXCEEDED(Response.Status.CONFLICT, "Limite de recursos excedido"),

    DUPLICATE_MODALITY(Response.Status.CONFLICT, "Modalidade de cronograma duplicada"),

    SCHEDULE_GENERATION_IN_PROGRESS(Response.Status.CONFLICT, "Operação concorrente em andamento"),

    IDEMPOTENCY_KEY_CONFLICT(Response.Status.CONFLICT, "Conflito de chave de idempotência"),

    IDEMPOTENCY_RESULT_REMOVED(Response.Status.CONFLICT, "Resultado da idempotência removido"),

    TRAIL_ALREADY_IMPORTED(Response.Status.CONFLICT, "Trilha já importada"),

    DUPLICATE_RESOURCE(Response.Status.CONFLICT, "Recurso duplicado"),

    RATE_LIMITED(Response.Status.TOO_MANY_REQUESTS, "Limite de requisições excedido"),

    INTERNAL_ERROR(Response.Status.INTERNAL_SERVER_ERROR, "Erro interno do servidor"),

    SERVICE_UNAVAILABLE(Response.Status.SERVICE_UNAVAILABLE, "Serviço indisponível");

    private static final String PREFIXO_TYPE = "urn:trilhara:problem:";

    private final int httpStatus;
    private final String title;

    CodigoErro(Response.Status status, String title) {
        this.httpStatus = status.getStatusCode();
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }

    /** {@code urn:trilhara:problem:<code-em-kebab-case>} — URI estável por código. */
    public String typeUri() {
        return PREFIXO_TYPE + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
