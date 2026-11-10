package br.com.trilhara.shared.mongo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;

import org.bson.Document;

/**
 * Utilitários padronizados para persistência e recuperação tipada de datas BSON.
 * 
 * Regras normativas:
 * 1. Instantes no tempo (criadoEm, atualizadoEm, expiraEm, etc.) são BSON Date nativos (java.util.Date / Instant).
 * 2. Datas de calendário (dataVencimento, dataSessao, dataProva, etc.) são Strings ISO "yyyy-MM-dd"
 *    (LocalDate), jamais convertidas para Date com fuso implícito.
 */
public final class BsonDates {

    private BsonDates() {
    }

    /**
     * Converte um Instant para java.util.Date (BSON Date nativo).
     */
    public static Date toBsonDate(Instant instant) {
        return instant != null ? Date.from(instant) : null;
    }

    /**
     * Converte um java.util.Date (BSON Date) para Instant.
     */
    public static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    /**
     * Extrai um Instant a partir de um campo de documento BSON (tipo BSON Date).
     */
    public static Instant toInstant(Document doc, String campo) {
        if (doc == null || campo == null) {
            return null;
        }
        Date date = doc.getDate(campo);
        return date != null ? date.toInstant() : null;
    }

    /**
     * Converte um LocalDate para string ISO "yyyy-MM-dd".
     */
    public static String toIsoString(LocalDate localDate) {
        return localDate != null ? localDate.toString() : null;
    }

    /**
     * Converte uma string ISO "yyyy-MM-dd" para LocalDate sem deslocamento de fuso.
     */
    public static LocalDate toLocalDate(String isoString) {
        if (isoString == null || isoString.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoString.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data ISO inválida: " + isoString, e);
        }
    }

    /**
     * Extrai um LocalDate a partir de um campo de documento BSON armazenado como string "yyyy-MM-dd".
     */
    public static LocalDate toLocalDate(Document doc, String campo) {
        if (doc == null || campo == null) {
            return null;
        }
        String valor = doc.getString(campo);
        return toLocalDate(valor);
    }
}
