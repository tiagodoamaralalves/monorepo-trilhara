package br.com.trilhara.cronograma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import org.bson.types.ObjectId;

public record BlocoCronograma(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        ObjectId cronogramaId,
        long versaoGeracao,
        LocalDate data,
        int ordem,
        ObjectId disciplinaId,
        String disciplinaNomeSnapshot,
        String disciplinaCorSnapshot,
        int duracaoMinutos,
        boolean concluido,
        Instant concluidoEm,
        Instant criadoEm,
        Instant atualizadoEm) {

    public BlocoCronograma {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório.");
        Objects.requireNonNull(trilhaId, "trilhaId é obrigatório.");
        Objects.requireNonNull(cronogramaId, "cronogramaId é obrigatório.");
        Objects.requireNonNull(data, "data é obrigatória.");
        Objects.requireNonNull(disciplinaId, "disciplinaId é obrigatória.");
        disciplinaNomeSnapshot = disciplinaNomeSnapshot == null ? "" : disciplinaNomeSnapshot;
        disciplinaCorSnapshot = disciplinaCorSnapshot == null ? "#6366F1" : disciplinaCorSnapshot;
    }

    public String idHex() {
        return id != null ? id.toHexString() : null;
    }

    public String usuarioIdHex() {
        return usuarioId != null ? usuarioId.toHexString() : null;
    }

    public String trilhaIdHex() {
        return trilhaId != null ? trilhaId.toHexString() : null;
    }

    public String cronogramaIdHex() {
        return cronogramaId != null ? cronogramaId.toHexString() : null;
    }

    public String disciplinaIdHex() {
        return disciplinaId != null ? disciplinaId.toHexString() : null;
    }

    public String dataIso() {
        return data != null ? data.toString() : null;
    }
}
