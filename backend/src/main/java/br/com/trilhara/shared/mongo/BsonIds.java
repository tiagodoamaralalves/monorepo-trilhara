package br.com.trilhara.shared.mongo;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.erro.InvalidIdentifierException;

/**
 * Utilitários BSON para conversão, validação e manipulação padronizada de ObjectId nativo.
 */
public final class BsonIds {

    private BsonIds() {
    }

    /**
 * Converte uma string hexadecimal de 24 caracteres em {@link ObjectId}.
 *
 * <p>ID malformado lança {@link InvalidIdentifierException}, mapeada para
 * {@code 400 INVALID_IDENTIFIER} com {@code errors[campo]}. Recurso inexistente ou de
 * outro tenant continua sendo {@code 404 NOT_FOUND}, decidido pela consulta escopada
 * ao {@code usuarioId}.</p>
 */
    public static ObjectId parse(String valor, String campo) {
        String normalizado = valor != null ? valor.trim() : null;
        if (normalizado == null || !ObjectId.isValid(normalizado)) {
            throw new InvalidIdentifierException(campo);
        }
        return new ObjectId(normalizado);
    }

    /**
 * Gera um novo identificador ObjectId e retorna sua representação hexadecimal de 24 caracteres.
 */
    public static String novoIdHex() {
        return new ObjectId().toHexString();
    }

    /**
 * Retorna a string hexadecimal de um ObjectId ou nulo se for nulo.
 */
    public static String toHex(ObjectId id) {
        return id != null ? id.toHexString() : null;
    }

    /**
 * Verifica se a string hexadecimal informada é um ObjectId válido.
 */
    public static boolean isValid(String hexId) {
        return hexId != null && ObjectId.isValid(hexId);
    }
}

