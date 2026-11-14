package br.com.trilhara.cronograma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

public record Cronograma(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        ModalidadeCronograma modalidade,
        boolean ativo,
        long versaoAtiva,
        Long versaoPreparada,
        String estadoPublicacao,
        int duracaoBlocoMinutos,
        String tipoPeriodo,
        LocalDate dataInicio,
        LocalDate dataFim,
        List<Integer> diasAtivosSemana,
        Integer blocosPorDia,
        Map<Integer, Integer> blocosPorDiaSemana,
        List<ObjectId> disciplinasParticipantesIds,
        Map<Integer, List<String>> semanaModeloManual,
        Document configuracaoAtiva,
        Document configuracaoPreparada,
        Instant criadoEm,
        Instant atualizadoEm) {

    public Cronograma {
        Objects.requireNonNull(modalidade, "Modalidade é obrigatória.");
        diasAtivosSemana = diasAtivosSemana == null ? List.of() : List.copyOf(diasAtivosSemana);
        blocosPorDiaSemana = blocosPorDiaSemana == null ? Map.of() : Collections.unmodifiableMap(blocosPorDiaSemana);
        disciplinasParticipantesIds = disciplinasParticipantesIds == null ? List.of() : List.copyOf(disciplinasParticipantesIds);
        semanaModeloManual = semanaModeloManual == null ? Map.of() : Collections.unmodifiableMap(semanaModeloManual);
        if (estadoPublicacao == null) {
            estadoPublicacao = ativo ? "ATIVO" : "PREPARANDO";
        }
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
}
