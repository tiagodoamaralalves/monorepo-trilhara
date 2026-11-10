package br.com.trilhara.shared.mongo;

import org.bson.types.ObjectId;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Utilitários BSON para conversão, validação e manipulação padronizada de ObjectId nativo.
 */
public final class BsonIds {

    private BsonIds() {
    }

    /**
     * Converte uma string hexadecimal para ObjectId validando seu formato.
     * Se inválido ou nulo, lança BadRequestException (400).
     */
    public static ObjectId toObjectId(String hexId, String nomeCampo) {
        if (hexId == null || !ObjectId.isValid(hexId)) {
            String campo = (nomeCampo != null && !nomeCampo.isBlank()) ? nomeCampo : "id";
            throw new BadRequestException("Identificador inválido para " + campo);
        }
        return new ObjectId(hexId);
    }

    /**
     * Converte uma string hexadecimal para ObjectId.
     * Se inválido ou nulo, lança NotFoundException (404).
     */
    public static ObjectId toObjectIdOrNotFound(String hexId) {
        if (hexId == null || !ObjectId.isValid(hexId)) {
            throw new NotFoundException("Recurso não encontrado.");
        }
        return new ObjectId(hexId);
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
