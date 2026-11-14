package br.com.trilhara.cronograma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.erro.ResourceLimitExceededException;
import br.com.trilhara.trilha.Disciplina;
import jakarta.ws.rs.BadRequestException;

public final class GeradorBlocosCronograma {

    public static final int MAX_DIAS_HORIZONTE = 366;
    public static final int MAX_BLOCOS_POR_DIA = 24;
    public static final int MAX_BLOCOS_TOTAL = 8784;
    public static final int MIN_DURACAO_MINUTOS = 30;
    public static final int MAX_DURACAO_MINUTOS = 240;

    public record ParametrosGeracao(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId cronogramaId,
            long versaoGeracao,
            ModalidadeCronograma modalidade,
            int duracaoBlocoMinutos,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<Integer> diasAtivosSemana,
            Integer blocosPorDia,
            Map<Integer, Integer> blocosPorDiaSemana,
            List<Disciplina> disciplinasParticipantes,
            Map<Integer, List<String>> semanaModeloManual) {
    }

    private GeradorBlocosCronograma() {
    }

    public static List<BlocoCronograma> gerar(ParametrosGeracao params) {
        Objects.requireNonNull(params, "params é obrigatório");
        Objects.requireNonNull(params.usuarioId(), "usuarioId é obrigatório");
        Objects.requireNonNull(params.trilhaId(), "trilhaId é obrigatório");
        Objects.requireNonNull(params.cronogramaId(), "cronogramaId é obrigatório");
        Objects.requireNonNull(params.modalidade(), "modalidade é obrigatória");
        Objects.requireNonNull(params.dataInicio(), "dataInicio é obrigatória");
        Objects.requireNonNull(params.dataFim(), "dataFim é obrigatória");

        if (params.dataFim().isBefore(params.dataInicio())) {
            throw new BadRequestException("dataFim não pode ser anterior a dataInicio.");
        }

        if (params.duracaoBlocoMinutos() < MIN_DURACAO_MINUTOS || params.duracaoBlocoMinutos() > MAX_DURACAO_MINUTOS) {
            throw new BadRequestException("Duração do bloco deve estar entre 30 e 240 minutos.");
        }

        if (params.disciplinasParticipantes() == null || params.disciplinasParticipantes().isEmpty()) {
            throw new BadRequestException("Ao menos uma disciplina participante deve ser informada.");
        }

        // Horizonte máximo de 366 dias (dataFim <= dataInicio.plusDays(365))
        LocalDate dataFimEfetiva = params.dataFim().isAfter(params.dataInicio().plusDays(365))
                ? params.dataInicio().plusDays(365)
                : params.dataFim();

        ParametrosGeracao paramsEfetivos = new ParametrosGeracao(
                params.usuarioId(),
                params.trilhaId(),
                params.cronogramaId(),
                params.versaoGeracao(),
                params.modalidade(),
                params.duracaoBlocoMinutos(),
                params.dataInicio(),
                dataFimEfetiva,
                params.diasAtivosSemana(),
                params.blocosPorDia(),
                params.blocosPorDiaSemana(),
                params.disciplinasParticipantes(),
                params.semanaModeloManual()
        );

        Instant agora = Instant.now();
        List<BlocoCronograma> blocosGerados = switch (paramsEfetivos.modalidade()) {
            case SEQUENCIAL -> gerarSequencial(paramsEfetivos, agora);
            case PONDERADO -> gerarPonderado(paramsEfetivos, agora);
            case MANUAL -> gerarManual(paramsEfetivos, agora);
        };

        if (blocosGerados.size() > MAX_BLOCOS_TOTAL) {
            throw new ResourceLimitExceededException("Total de blocos gerados excede o limite máximo permitido (" + MAX_BLOCOS_TOTAL + ").");
        }

        return blocosGerados;
    }

    private static List<BlocoCronograma> gerarSequencial(ParametrosGeracao params, Instant agora) {
        validarDiasEBlocos(params.diasAtivosSemana(), params.blocosPorDia(), params.blocosPorDiaSemana());

        List<Disciplina> ordenadas = new ArrayList<>(params.disciplinasParticipantes());
        ordenadas.sort(Comparator.comparingInt(Disciplina::ordem));

        List<BlocoCronograma> resultado = new ArrayList<>();
        int indiceFila = 0;

        for (LocalDate data = params.dataInicio(); !data.isAfter(params.dataFim()); data = data.plusDays(1)) {
            int diaSemana = data.getDayOfWeek().getValue();
            int quantidadeBlocos = blocosNoDia(params, diaSemana);
            for (int ordem = 1; ordem <= quantidadeBlocos; ordem++) {
                Disciplina disc = ordenadas.get(indiceFila % ordenadas.size());
                indiceFila++;
                resultado.add(criarBloco(params, data, ordem, disc, agora));
            }
        }
        return resultado;
    }

    private static List<BlocoCronograma> gerarPonderado(ParametrosGeracao params, Instant agora) {
        validarDiasEBlocos(params.diasAtivosSemana(), params.blocosPorDia(), params.blocosPorDiaSemana());

        List<Disciplina> ordenadas = new ArrayList<>(params.disciplinasParticipantes());
        ordenadas.sort(Comparator.comparingInt(Disciplina::ordem));

        int totalBlocos = contarTotalBlocosPonderado(params);
        if (totalBlocos == 0) {
            return List.of();
        }

        List<Disciplina> escalaGlobal = construirEscalaGlobalPonderada(ordenadas, totalBlocos);

        List<BlocoCronograma> resultado = new ArrayList<>(totalBlocos);
        int indiceEscala = 0;

        for (LocalDate data = params.dataInicio(); !data.isAfter(params.dataFim()); data = data.plusDays(1)) {
            int quantidadeBlocos = blocosNoDia(params, data.getDayOfWeek().getValue());
            for (int ordem = 1; ordem <= quantidadeBlocos; ordem++) {
                Disciplina disc = escalaGlobal.get(indiceEscala++);
                resultado.add(criarBloco(params, data, ordem, disc, agora));
            }
        }
        return resultado;
    }

    private static int contarTotalBlocosPonderado(ParametrosGeracao params) {
        int totalBlocos = 0;
        for (LocalDate data = params.dataInicio(); !data.isAfter(params.dataFim()); data = data.plusDays(1)) {
            totalBlocos += blocosNoDia(params, data.getDayOfWeek().getValue());
        }
        return totalBlocos;
    }

    private static int blocosNoDia(ParametrosGeracao params, int diaSemana) {
        if (params.diasAtivosSemana() == null || !params.diasAtivosSemana().contains(diaSemana)) {
            return 0;
        }
        if (params.blocosPorDiaSemana() != null && params.blocosPorDiaSemana().containsKey(diaSemana)) {
            Integer qtd = params.blocosPorDiaSemana().get(diaSemana);
            return qtd != null ? qtd : 0;
        }
        return params.blocosPorDia() != null ? params.blocosPorDia() : 0;
    }

    private static List<Disciplina> construirEscalaGlobalPonderada(List<Disciplina> ordenadas, int totalBlocos) {
        int somaPesos = ordenadas.stream().mapToInt(d -> Math.clamp(d.peso(), 1, 5)).sum();
        if (somaPesos <= 0) {
            somaPesos = ordenadas.size();
        }

        Map<ObjectId, Integer> alocados = new HashMap<>();
        for (Disciplina d : ordenadas) {
            alocados.put(d.id(), 0);
        }

        List<Disciplina> escalaGlobal = new ArrayList<>(totalBlocos);
        for (int t = 1; t <= totalBlocos; t++) {
            Disciplina escolhida = selecionarDisciplinaMaiorDeficit(ordenadas, alocados, t, somaPesos);
            escalaGlobal.add(escolhida);
            alocados.put(escolhida.id(), alocados.get(escolhida.id()) + 1);
        }
        return escalaGlobal;
    }

    private static Disciplina selecionarDisciplinaMaiorDeficit(
            List<Disciplina> ordenadas,
            Map<ObjectId, Integer> alocados,
            int posicaoGlobal,
            int somaPesos) {
        Disciplina melhor = ordenadas.get(0);
        double melhorDelta = calcularDeficit(melhor, alocados.get(melhor.id()), posicaoGlobal, somaPesos);

        for (int i = 1; i < ordenadas.size(); i++) {
            Disciplina candidata = ordenadas.get(i);
            double delta = calcularDeficit(candidata, alocados.get(candidata.id()), posicaoGlobal, somaPesos);
            if (deveSubstituirMelhor(candidata, delta, melhor, melhorDelta)) {
                melhor = candidata;
                melhorDelta = delta;
            }
        }
        return melhor;
    }

    private static double calcularDeficit(Disciplina disciplina, int alocadosAteAgora, int posicaoGlobal, int somaPesos) {
        int peso = Math.clamp(disciplina.peso(), 1, 5);
        return ((double) posicaoGlobal * peso / somaPesos) - alocadosAteAgora;
    }

    private static boolean deveSubstituirMelhor(
            Disciplina candidata,
            double deltaCandidata,
            Disciplina melhorAtual,
            double melhorDelta) {
        if (deltaCandidata > melhorDelta + 1e-9) {
            return true;
        }
        return Math.abs(deltaCandidata - melhorDelta) <= 1e-9 && candidata.ordem() < melhorAtual.ordem();
    }

    private static List<BlocoCronograma> gerarManual(ParametrosGeracao params, Instant agora) {
        Map<Integer, List<String>> semanaModeloManual = params.semanaModeloManual();
        if (semanaModeloManual == null || semanaModeloManual.isEmpty()) {
            throw new BadRequestException("semanaModeloManual é obrigatória na modalidade MANUAL.");
        }

        Map<String, Disciplina> discPorId = new HashMap<>();
        for (Disciplina d : params.disciplinasParticipantes()) {
            discPorId.put(d.id().toHexString(), d);
        }

        List<BlocoCronograma> resultado = new ArrayList<>();
        for (LocalDate data = params.dataInicio(); !data.isAfter(params.dataFim()); data = data.plusDays(1)) {
            int diaSemana = data.getDayOfWeek().getValue();
            List<String> idsDia = semanaModeloManual.get(diaSemana);
            if (idsDia != null && !idsDia.isEmpty()) {
                adicionarBlocosDiaManual(resultado, params, data, diaSemana, idsDia, discPorId, agora);
            }
        }
        return resultado;
    }

    private static void adicionarBlocosDiaManual(
            List<BlocoCronograma> resultado,
            ParametrosGeracao params,
            LocalDate data,
            int diaSemana,
            List<String> idsDia,
            Map<String, Disciplina> discPorId,
            Instant agora) {
        if (idsDia.size() > MAX_BLOCOS_POR_DIA) {
            throw new ResourceLimitExceededException("O dia " + diaSemana + " na semana modelo excede o limite de " + MAX_BLOCOS_POR_DIA + " blocos.");
        }
        for (int i = 0; i < idsDia.size(); i++) {
            String dIdStr = idsDia.get(i);
            Disciplina disc = discPorId.get(dIdStr);
            if (disc == null) {
                throw new BadRequestException("Disciplina " + dIdStr + " na semana modelo não pertence às disciplinas participantes da trilha.");
            }
            resultado.add(criarBloco(params, data, i + 1, disc, agora));
        }
    }

    private static BlocoCronograma criarBloco(
            ParametrosGeracao params,
            LocalDate data,
            int ordem,
            Disciplina disc,
            Instant agora) {
        return new BlocoCronograma(
                new ObjectId(),
                params.usuarioId(),
                params.trilhaId(),
                params.cronogramaId(),
                params.versaoGeracao(),
                data,
                ordem,
                disc.id(),
                disc.nome(),
                disc.cor(),
                params.duracaoBlocoMinutos(),
                false,
                null,
                agora,
                agora);
    }

    private static void validarDiasEBlocos(
            List<Integer> diasAtivosSemana, Integer blocosPorDia, Map<Integer, Integer> blocosPorDiaSemana) {
        if (diasAtivosSemana == null || diasAtivosSemana.isEmpty()) {
            throw new BadRequestException("diasAtivosSemana é obrigatório para modalidade sequencial/ponderada.");
        }
        for (Integer dia : diasAtivosSemana) {
            if (dia == null || dia < 1 || dia > 7) {
                throw new BadRequestException("Dia da semana inválido: " + dia + ". Deve ser entre 1 (Seg) e 7 (Dom).");
            }
            Integer blocosDia = (blocosPorDiaSemana != null && blocosPorDiaSemana.containsKey(dia))
                    ? blocosPorDiaSemana.get(dia)
                    : blocosPorDia;
            if (blocosDia == null || blocosDia < 1 || blocosDia > MAX_BLOCOS_POR_DIA) {
                throw new BadRequestException("blocosPorDia deve estar entre 1 e " + MAX_BLOCOS_POR_DIA + ".");
            }
        }
    }
}
