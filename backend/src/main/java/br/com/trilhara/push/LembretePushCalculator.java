package br.com.trilhara.push;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

public final class LembretePushCalculator {

    private LembretePushCalculator() {
    }

    /**
     * Calcula o instante de entrega em UTC para um lembrete com base na data de vencimento,
     * horário local, fuso horário IANA e minutos de antecedência.
     * 
     * Trata estritamente as regras de DST:
     * - Gap DST (horário inexistente): lança InvalidLocalDatetimeException (HTTP 400 INVALID_LOCAL_DATETIME).
     * - Overlap DST (horário duplicado): escolhe deterministicamente a ocorrência anterior (offset de verão).
     */
    public static Instant calcularInstanteEntregaUtc(
            LocalDate dataVencimento,
            LocalTime horario,
            ZoneId zoneId,
            int minutosAntes) {

        if (dataVencimento == null || horario == null) {
            return null;
        }
        if (zoneId == null) {
            zoneId = ZoneId.of("America/Sao_Paulo");
        }

        LocalDateTime ldt = LocalDateTime.of(dataVencimento, horario);
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(ldt);

        if (validOffsets.isEmpty()) {
            throw new InvalidLocalDatetimeException(
                    "O horário local informado (" + ldt + ") não existe no fuso " + zoneId.getId() + " devido à transição de horário de verão (gap DST).");
        }

        ZoneOffset offsetEscolhido;
        if (validOffsets.size() > 1) {
            // Overlap DST: escolhe a ocorrência anterior (horário de verão / earlier instant)
            ZoneOffsetTransition trans = rules.getTransition(ldt);
            if (trans != null && validOffsets.contains(trans.getOffsetBefore())) {
                offsetEscolhido = trans.getOffsetBefore();
            } else {
                ZoneOffset o1 = validOffsets.get(0);
                ZoneOffset o2 = validOffsets.get(1);
                offsetEscolhido = ldt.toInstant(o1).isBefore(ldt.toInstant(o2)) ? o1 : o2;
            }
        } else {
            offsetEscolhido = validOffsets.get(0);
        }

        ZonedDateTime zdt = ZonedDateTime.ofStrict(ldt, offsetEscolhido, zoneId);
        return zdt.minusMinutes(minutosAntes).toInstant();
    }
}
