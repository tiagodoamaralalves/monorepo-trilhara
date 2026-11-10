package br.com.trilhara.tarefa;

import java.time.LocalDate;
import java.util.List;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class RegraRecorrenciaValidator {

    public record ParametrosRegraRecorrencia(
            String frequencia,
            Integer intervalo,
            List<Integer> diasDaSemana,
            String modoMensal,
            Integer diaDoMes,
            Integer ordinalSemana,
            Integer diaSemanaOrdinal,
            String tipoTermino,
            String dataInicioStr,
            String dataFimStr,
            Integer maxOcorrencias) {
    }

    public void validar(ParametrosRegraRecorrencia p) {
        if (p == null) {
            throw new BadRequestException("Parâmetros de regra de recorrência são obrigatórios.");
        }

        validarFrequenciaEIntervalo(p.frequencia(), p.intervalo());

        String freq = p.frequencia().toUpperCase().trim();
        switch (freq) {
            case "SEMANAL" -> validarSemanal(p.diasDaSemana());
            case "MENSAL" -> validarMensal(p.modoMensal(), p.diaDoMes(), p.ordinalSemana(), p.diaSemanaOrdinal());
            case "DIARIO" -> { /* diário não exige parâmetros extras */ }
            default -> throw new BadRequestException("Frequência inválida. Valores aceitos: DIARIO, SEMANAL, MENSAL.");
        }

        validarTermino(p.tipoTermino(), p.dataInicioStr(), p.dataFimStr(), p.maxOcorrencias());
    }

    private void validarFrequenciaEIntervalo(String frequencia, Integer intervalo) {
        if (intervalo != null && intervalo < 1) {
            throw new BadRequestException("O intervalo da recorrência deve ser maior ou igual a 1.");
        }
        if (frequencia == null || frequencia.isBlank()) {
            throw new BadRequestException("A frequência da recorrência é obrigatória.");
        }
    }

    private void validarSemanal(List<Integer> diasDaSemana) {
        if (diasDaSemana == null || diasDaSemana.isEmpty()) {
            throw new BadRequestException("Recorrência semanal exige pelo menos um dia da semana (1..7).");
        }
        for (Integer d : diasDaSemana) {
            if (d == null || d < 1 || d > 7) {
                throw new BadRequestException("Dia da semana inválido: " + d + ". Deve ser entre 1 (Seg) e 7 (Dom).");
            }
        }
    }

    private void validarMensal(String modoMensal, Integer diaDoMes, Integer ordinalSemana, Integer diaSemanaOrdinal) {
        String modo = modoMensal != null ? modoMensal.toUpperCase().trim() : "DIA_DO_MES";
        if ("DIA_DO_MES".equals(modo)) {
            if (diaDoMes == null || diaDoMes < 1 || diaDoMes > 31) {
                throw new BadRequestException("Modo mensal por dia do mês exige diaDoMes entre 1 e 31.");
            }
        } else if ("ORDINAL_SEMANA".equals(modo)) {
            validarOrdinalSemana(ordinalSemana, diaSemanaOrdinal);
        } else {
            throw new BadRequestException("Modo mensal inválido: " + modoMensal);
        }
    }

    private void validarOrdinalSemana(Integer ordinalSemana, Integer diaSemanaOrdinal) {
        if (ordinalSemana == null || (ordinalSemana != -1 && (ordinalSemana < 1 || ordinalSemana > 4))) {
            throw new BadRequestException("Ordinal da semana inválido. Valores aceitos: 1..4 ou -1 (para última).");
        }
        if (diaSemanaOrdinal == null || diaSemanaOrdinal < 1 || diaSemanaOrdinal > 7) {
            throw new BadRequestException("Dia da semana ordinal inválido (deve ser 1..7).");
        }
    }

    private void validarTermino(String tipoTermino, String dataInicioStr, String dataFimStr, Integer maxOcorrencias) {
        if (tipoTermino == null || tipoTermino.isBlank()) {
            throw new BadRequestException("O tipo de término da série é obrigatório.");
        }

        LocalDate dataInicio = BsonDates.toLocalDate(dataInicioStr);
        if (dataInicio == null) {
            throw new BadRequestException("A data de início da série é obrigatória.");
        }

        LocalDate dataFim = BsonDates.toLocalDate(dataFimStr);
        String termino = tipoTermino.toUpperCase().trim();

        switch (termino) {
            case "NUNCA" -> {
                if (dataFim != null || maxOcorrencias != null) {
                    throw new BadRequestException("Tipo de término NUNCA não deve conter dataFim nem maxOcorrencias.");
                }
            }
            case "EM_DATA" -> validarTerminoEmData(dataInicio, dataFim, maxOcorrencias);
            case "APOS_OCORRENCIAS" -> validarTerminoAposOcorrencias(dataFim, maxOcorrencias);
            default -> throw new BadRequestException("Tipo de término inválido: " + tipoTermino);
        }
    }

    private void validarTerminoEmData(LocalDate dataInicio, LocalDate dataFim, Integer maxOcorrencias) {
        if (dataFim == null) {
            throw new BadRequestException("Tipo de término EM_DATA exige a dataFim.");
        }
        if (dataFim.isBefore(dataInicio)) {
            throw new BadRequestException("A dataFim não pode ser anterior à dataInicio.");
        }
        if (maxOcorrencias != null) {
            throw new BadRequestException("Tipo de término EM_DATA não aceita maxOcorrencias.");
        }
    }

    private void validarTerminoAposOcorrencias(LocalDate dataFim, Integer maxOcorrencias) {
        if (maxOcorrencias == null || maxOcorrencias < 1) {
            throw new BadRequestException("Tipo de término APOS_OCORRENCIAS exige maxOcorrencias >= 1.");
        }
        if (dataFim != null) {
            throw new BadRequestException("Tipo de término APOS_OCORRENCIAS não aceita dataFim.");
        }
    }
}
