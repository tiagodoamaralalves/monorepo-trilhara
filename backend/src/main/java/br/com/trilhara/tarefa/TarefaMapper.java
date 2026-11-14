package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.tarefa.dto.AtualizarTarefaRequest;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.ListaTarefasResponse;
import br.com.trilhara.tarefa.dto.PosicaoFluxoDTO;
import br.com.trilhara.tarefa.dto.SerieRecorrenteResponse;
import br.com.trilhara.tarefa.dto.SubtarefaDTO;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import br.com.trilhara.tarefa.dto.TarefasHojeResponse;
import jakarta.ws.rs.NotFoundException;

final class TarefaMapper {

    record DadosEdicaoTarefa(
            ObjectId listaId,
            String titulo,
            String descricao,
            PrioridadeTarefa prioridade,
            LocalDate dataVencimento,
            String horario,
            int ordem,
            List<Subtarefa> subtarefas,
            ModoLembrete modoLembrete,
            Integer lembreteMinutosAntes,
            String fusoAgendamento) {
    }

    private TarefaMapper() {
    }

    static Tarefa construirNovaTarefa(
            ObjectId usuarioId,
            ObjectId listaId,
            CriarTarefaRequest request,
            LocalDate dataVencimento,
            int ordem,
            String fusoAgendamento,
            Instant agora) {
        return new Tarefa(
                new ObjectId(),
                usuarioId,
                listaId,
                null,
                null,
                false,
                request.titulo().trim(),
                request.descricao() != null ? request.descricao().trim() : null,
                PrioridadeTarefa.fromString(request.prioridade()),
                dataVencimento,
                request.horario(),
                "PENDENTE",
                false,
                null,
                ordem,
                converterSubtarefas(request.subtarefas()),
                request.modoLembrete(),
                request.lembreteMinutosAntes(),
                fusoAgendamento,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                null,
                agora,
                agora
        );
    }

    static DadosEdicaoTarefa extrairDadosEdicao(
            ListaTarefasRepository listaRepository,
            TimezoneContext timezoneContext,
            ObjectId usuarioId,
            Tarefa existente,
            AtualizarTarefaRequest request) {
        ObjectId listaId = request.listaId() != null
                ? TarefaSupport.validarEResolverListaId(listaRepository, usuarioId, request.listaId())
                : existente.listaId();
        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? request.titulo().trim()
                : existente.titulo();
        String descricao = request.descricao() != null ? request.descricao().trim() : existente.descricao();
        PrioridadeTarefa prioridade = request.prioridade() != null
                ? PrioridadeTarefa.fromString(request.prioridade())
                : existente.prioridade();
        LocalDate dataVencimento = request.dataVencimento() != null
                ? BsonDates.toLocalDate(request.dataVencimento())
                : existente.dataVencimento();
        String horario = request.horario() != null ? request.horario() : existente.horario();
        int ordem = request.ordem() != null ? request.ordem() : existente.ordem();
        ModoLembrete modoLembrete = request.modoLembrete() != null
                ? request.modoLembrete()
                : existente.modoLembrete();
        Integer lembrete = request.lembreteMinutosAntes() != null
                ? request.lembreteMinutosAntes()
                : existente.lembreteMinutosAntes();
        String fuso = TarefaSupport.resolverFusoEdicao(
                timezoneContext, request.fusoAgendamento(), existente.fusoAgendamento());
        List<Subtarefa> subtarefas = request.subtarefas() != null
                ? converterSubtarefas(request.subtarefas())
                : existente.subtarefas();

        TarefaSupport.validarPreferenciaLembrete(modoLembrete, lembrete);

        return new DadosEdicaoTarefa(
                listaId, titulo, descricao, prioridade, dataVencimento, horario, ordem, subtarefas,
                modoLembrete, lembrete, fuso);
    }

    static SerieRecorrente cortarSerieEmData(SerieRecorrente serieOriginal, LocalDate corte) {
        boolean serieAtiva = !corte.isBefore(serieOriginal.dataInicio());
        return new SerieRecorrente(
                serieOriginal.id(), serieOriginal.usuarioId(), serieOriginal.listaId(), serieOriginal.titulo(),
                serieOriginal.descricao(), serieOriginal.prioridade(), serieOriginal.horario(),
                serieOriginal.subtarefasModelo(), serieOriginal.frequencia(), serieOriginal.intervalo(),
                serieOriginal.diasDaSemana(), serieOriginal.modoMensal(), serieOriginal.diaDoMes(),
                serieOriginal.ordinalSemana(), serieOriginal.diaSemanaOrdinal(), TarefaSupport.VALOR_EM_DATA,
                serieOriginal.dataInicio(), corte, null, corte, serieOriginal.totalGeradas(),
                serieAtiva, serieOriginal.modoLembrete(), serieOriginal.lembreteMinutosAntes(),
                serieOriginal.fusoAgendamento(), serieOriginal.origemOperacaoId(), serieOriginal.deletedAt(),
                serieOriginal.expiresAt(), serieOriginal.loteExclusaoId(), serieOriginal.criadoEm(), Instant.now()
        );
    }

    static List<Subtarefa> converterSubtarefas(List<SubtarefaDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return IntStream.range(0, dtos.size())
                .mapToObj(ordem -> criarSubtarefa(dtos.get(ordem), ordem))
                .toList();
    }

    private static Subtarefa criarSubtarefa(SubtarefaDTO dto, int ordem) {
        String id = dto.id() != null && !dto.id().isBlank() ? dto.id() : new ObjectId().toHexString();
        return new Subtarefa(id, dto.titulo(), Boolean.TRUE.equals(dto.concluida()), ordem);
    }

    static List<Subtarefa> atualizarSubtarefaNaLista(
            List<Subtarefa> subtarefas, String subtarefaIdStr, boolean concluida) {
        List<Subtarefa> novasSubtarefas = new ArrayList<>();
        boolean encontrada = false;
        for (Subtarefa sub : subtarefas) {
            if (sub.id().equals(subtarefaIdStr)) {
                novasSubtarefas.add(new Subtarefa(sub.id(), sub.titulo(), concluida, sub.ordem()));
                encontrada = true;
            } else {
                novasSubtarefas.add(sub);
            }
        }
        if (!encontrada) {
            throw new NotFoundException("Subtarefa não encontrada.");
        }
        return novasSubtarefas;
    }

    static TarefasHojeResponse montarTarefasHojeResponse(
            LocalDate hojeLocal,
            List<Tarefa> atrasadas,
            List<Tarefa> hoje,
            List<Tarefa> semData,
            List<Tarefa> concluidasHoje) {
        List<Tarefa> atrasadasList = new ArrayList<>(atrasadas);
        List<Tarefa> hojeList = new ArrayList<>(hoje);
        List<Tarefa> semDataList = new ArrayList<>(semData);

        atrasadasList.sort(Comparator
                .comparing(Tarefa::dataVencimento, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        hojeList.sort(Comparator
                .comparing(Tarefa::horario, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        semDataList.sort(Comparator
                .comparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        return new TarefasHojeResponse(
                hojeLocal.toString(),
                atrasadasList.stream().map(TarefaMapper::paraTarefaResponse).toList(),
                hojeList.stream().map(TarefaMapper::paraTarefaResponse).toList(),
                semDataList.stream().map(TarefaMapper::paraTarefaResponse).toList(),
                concluidasHoje.stream().map(TarefaMapper::paraTarefaResponse).toList()
        );
    }

    static ListaTarefasResponse paraListaResponse(ListaTarefas l, long totalTarefas) {
        return new ListaTarefasResponse(
                l.id().toHexString(),
                l.usuarioId().toHexString(),
                l.nome(),
                l.cor(),
                l.ordem(),
                totalTarefas,
                l.criadoEm(),
                l.atualizadoEm()
        );
    }

    static TarefaResponse paraTarefaResponse(Tarefa t) {
        return paraTarefaResponse(t, List.of());
    }

    static TarefaResponse paraTarefaResponse(Tarefa t, List<String> antecessorasIds) {
        List<SubtarefaDTO> subs = t.subtarefas().stream()
                .map(s -> new SubtarefaDTO(s.id(), s.titulo(), s.concluida(), s.ordem()))
                .toList();

        List<String> sucessoras = t.sucessorasIds().stream()
                .map(ObjectId::toHexString)
                .toList();
        List<String> pares = t.paresIds().stream()
                .map(ObjectId::toHexString)
                .toList();
        PosicaoFluxoDTO posicaoDto = t.posicaoFluxo() != null
                ? new PosicaoFluxoDTO(t.posicaoFluxo().x(), t.posicaoFluxo().y())
                : null;

        return new TarefaResponse(
                t.id().toHexString(),
                t.usuarioId().toHexString(),
                t.listaId() != null ? t.listaId().toHexString() : null,
                t.serieId() != null ? t.serieId().toHexString() : null,
                BsonDates.toIso(t.dataOriginalOcorrencia()),
                t.desvinculadaDaSerie(),
                t.titulo(),
                t.descricao(),
                t.prioridade().getValor(),
                BsonDates.toIso(t.dataVencimento()),
                t.horario(),
                t.estado(),
                t.concluida(),
                t.concluidaEm(),
                t.ordem(),
                subs,
                t.modoLembrete(),
                t.lembreteMinutosAntes(),
                t.fusoAgendamento(),
                t.deletedAt(),
                t.expiresAt(),
                t.loteExclusaoId(),
                t.noFluxo(),
                sucessoras,
                antecessorasIds != null ? antecessorasIds : List.of(),
                pares,
                posicaoDto,
                t.criadoEm(),
                t.atualizadoEm()
        );
    }

    static SerieRecorrenteResponse paraSerieResponse(SerieRecorrente s) {
        List<SubtarefaDTO> subs = s.subtarefasModelo().stream()
                .map(sub -> new SubtarefaDTO(sub.id(), sub.titulo(), sub.concluida(), sub.ordem()))
                .toList();

        return new SerieRecorrenteResponse(
                s.id().toHexString(),
                s.usuarioId().toHexString(),
                s.listaId() != null ? s.listaId().toHexString() : null,
                s.titulo(),
                s.descricao(),
                s.prioridade().getValor(),
                s.horario(),
                subs,
                s.frequencia(),
                s.intervalo(),
                s.diasDaSemana(),
                s.modoMensal(),
                s.diaDoMes(),
                s.ordinalSemana(),
                s.diaSemanaOrdinal(),
                s.tipoTermino(),
                BsonDates.toIso(s.dataInicio()),
                BsonDates.toIso(s.dataFim()),
                s.maxOcorrencias(),
                BsonDates.toIso(s.geradoAte()),
                s.totalGeradas(),
                s.ativa(),
                s.modoLembrete(),
                s.lembreteMinutosAntes(),
                s.fusoAgendamento(),
                s.origemOperacaoId(),
                s.deletedAt(),
                s.expiresAt(),
                s.loteExclusaoId(),
                s.criadoEm(),
                s.atualizadoEm()
        );
    }
}
