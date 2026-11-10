package br.com.trilhara.tarefa;

import org.bson.types.ObjectId;

import jakarta.validation.constraints.NotBlank;

public record Subtarefa(
        String id,
        @NotBlank String titulo,
        boolean concluida,
        int ordem) {

    public Subtarefa {
        if (id == null || id.isBlank()) {
            id = new ObjectId().toHexString();
        }
    }

    public Subtarefa(String titulo, boolean concluida, int ordem) {
        this(new ObjectId().toHexString(), titulo, concluida, ordem);
    }
}
