package br.com.trilhara.shared.mongo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;

import org.bson.BsonValue;
import org.bson.Document;

/**
 * Utilitários BSON tipados e <strong>estritos</strong> (DC-17).
 *
 * <p>Regras normativas:</p>
 * <ol>
 *   <li>Instantes ({@code criadoEm}, {@code atualizadoEm}, {@code expiraEm}, …) são
 *       {@code BSON Date} no banco e {@link Instant} no Java;</li>
 *   <li>Datas de calendário ({@code dataVencimento}, {@code dataSessao}, …) são strings ISO
 *       estritas {@code yyyy-MM-dd}, sem conversão por fuso implícito;</li>
 *   <li>Leitura de tipo inesperado lança {@link IllegalStateException}: dados legados não são
 *       convertidos — devem ser recriados (o banco de desenvolvimento começa vazio).</li>
 * </ol>
 */
public final class BsonDates {

    private BsonDates() {
    }

    /** Converte {@link Instant} para {@code BSON Date} nativo; {@code null} propaga. */
    public static Date toBsonDate(Instant instant) {
        return instant != null ? Date.from(instant) : null;
    }

    /** Converte {@code BSON Date} para {@link Instant}; {@code null} propaga. */
    public static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    /** Converte {@link LocalDate} para string ISO {@code yyyy-MM-dd}; {@code null} propaga. */
    public static String toIso(LocalDate localDate) {
        return localDate != null ? localDate.toString() : null;
    }

    /**
     * Converte string ISO {@code yyyy-MM-dd} em {@link LocalDate}.
     *
     * @throws IllegalArgumentException formato inválido (o serviço de origem traduz em 400)
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
     * Lê um instante de um campo de documento, sem aceitar tipos legados.
     *
     * @throws IllegalStateException campo presente com tipo diferente de {@code BSON Date}
     */
    public static Instant lerInstant(Document doc, String campo) {
        if (doc == null || campo == null) {
            return null;
        }
        Object valor = doc.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Date data) {
            return data.toInstant();
        }
        if (valor instanceof Instant instante) {
            return instante;
        }
        throw new IllegalStateException("Campo '" + campo + "' deveria ser BSON Date, mas está como "
                + valor.getClass().getName());
    }

    /**
     * Lê uma data de calendário de um campo de documento, sem aceitar tipos legados.
     *
     * @throws IllegalStateException campo presente com tipo diferente de string
     */
    public static LocalDate lerLocalDate(Document doc, String campo) {
        if (doc == null || campo == null) {
            return null;
        }
        Object valor = doc.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof String texto) {
            return toLocalDate(texto);
        }
        throw new IllegalStateException("Campo '" + campo
                + "' deveria ser string ISO yyyy-MM-dd, mas está como " + valor.getClass().getName());
    }

    /**
     * Lê um instante de um valor BSON bruto.
     *
     * @throws IllegalStateException valor presente com tipo inesperado (não é BSON Date)
     */
    public static Instant lerInstant(BsonValue valor, String campo) {
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isDateTime()) {
            throw new IllegalStateException(
                    "Campo '" + campo + "' deveria ser BSON Date, mas está como " + valor.getBsonType());
        }
        return Instant.ofEpochMilli(valor.asDateTime().getValue());
    }

    /**
     * Lê uma data de calendário de um valor BSON bruto.
     *
     * @throws IllegalStateException valor presente com tipo inesperado (não é string)
     */
    public static LocalDate lerLocalDate(BsonValue valor, String campo) {
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isString()) {
            throw new IllegalStateException(
                    "Campo '" + campo + "' deveria ser string ISO yyyy-MM-dd, mas está como "
                            + valor.getBsonType());
        }
        return toLocalDate(valor.asString().getValue());
    }
}
