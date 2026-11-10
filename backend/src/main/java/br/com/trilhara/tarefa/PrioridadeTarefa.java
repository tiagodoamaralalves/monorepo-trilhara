package br.com.trilhara.tarefa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PrioridadeTarefa {
    BAIXA("BAIXA", 1),
    MEDIA("MEDIA", 2),
    ALTA("ALTA", 3);

    private final String valor;
    private final int peso;

    PrioridadeTarefa(String valor, int peso) {
        this.valor = valor;
        this.peso = peso;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    public int getPeso() {
        return peso;
    }

    @JsonCreator
    public static PrioridadeTarefa fromString(String texto) {
        if (texto == null || texto.isBlank()) {
            return MEDIA;
        }
        String limpo = texto.trim();
        for (PrioridadeTarefa p : values()) {
            if (p.name().equalsIgnoreCase(limpo) || p.valor.equalsIgnoreCase(limpo)) {
                return p;
            }
        }
        if ("urgente".equalsIgnoreCase(limpo)) {
            return ALTA;
        }
        if ("nenhuma".equalsIgnoreCase(limpo)) {
            return BAIXA;
        }
        return MEDIA;
    }
}
