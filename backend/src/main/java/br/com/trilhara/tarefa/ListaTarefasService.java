package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;

import br.com.trilhara.tarefa.dto.AtualizarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.CriarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.ExclusaoListaResponse;
import br.com.trilhara.tarefa.dto.ListaImpactoExclusaoResponse;
import br.com.trilhara.tarefa.dto.ListaTarefasResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ListaTarefasService {

    private static final String ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL =
            "Lista não encontrada ou inacessível.";
    private static final String ERRO_NOME_LISTA_VAZIO =
            "O nome da lista não pode ser vazio.";

    private final ListaTarefasRepository listaRepository;
    private final TarefaRepository tarefaRepository;
    private final SerieRecorrenteRepository serieRepository;

    public ListaTarefasService(
            ListaTarefasRepository listaRepository,
            TarefaRepository tarefaRepository,
            SerieRecorrenteRepository serieRepository) {
        this.listaRepository = listaRepository;
        this.tarefaRepository = tarefaRepository;
        this.serieRepository = serieRepository;
    }

    public ListaTarefasResponse criarLista(CriarListaTarefasRequest request, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        if (request.nome() == null || request.nome().isBlank()) {
            throw new BadRequestException(ERRO_NOME_LISTA_VAZIO);
        }

        Instant agora = Instant.now();
        int ordem = request.ordem() != null ? request.ordem() : listaRepository.proximaOrdem(usuarioId);

        ListaTarefas lista = new ListaTarefas(
                new ObjectId(),
                usuarioId,
                request.nome().trim(),
                request.cor() != null ? request.cor().trim() : null,
                ordem,
                null,
                null,
                null,
                agora,
                agora
        );

        listaRepository.inserir(lista);
        return TarefaMapper.paraListaResponse(lista, 0);
    }

    public List<ListaTarefasResponse> listarListas(String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        return listaRepository.listarAtivas(usuarioId).stream()
                .map(l -> TarefaMapper.paraListaResponse(l, tarefaRepository.contarPorLista(usuarioId, l.id())))
                .toList();
    }

    public ListaTarefasResponse buscarLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId listaId = TarefaSupport.validarObjectId(listaIdStr, TarefaSupport.CAMPO_LISTA_ID);

        ListaTarefas lista = buscarListaAtiva(usuarioId, listaId);
        long total = tarefaRepository.contarPorLista(usuarioId, lista.id());
        return TarefaMapper.paraListaResponse(lista, total);
    }

    public ListaTarefasResponse atualizarLista(
            String listaIdStr, AtualizarListaTarefasRequest request, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId listaId = TarefaSupport.validarObjectId(listaIdStr, TarefaSupport.CAMPO_LISTA_ID);

        ListaTarefas existente = buscarListaAtiva(usuarioId, listaId);
        if (request.nome() == null || request.nome().isBlank()) {
            throw new BadRequestException(ERRO_NOME_LISTA_VAZIO);
        }

        ListaTarefas atualizada = new ListaTarefas(
                existente.id(),
                usuarioId,
                request.nome().trim(),
                request.cor() != null ? request.cor().trim() : existente.cor(),
                request.ordem() != null ? request.ordem() : existente.ordem(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        listaRepository.atualizar(atualizada);
        long total = tarefaRepository.contarPorLista(usuarioId, atualizada.id());
        return TarefaMapper.paraListaResponse(atualizada, total);
    }

    public ExclusaoListaResponse excluirLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId listaId = TarefaSupport.validarObjectId(listaIdStr, TarefaSupport.CAMPO_LISTA_ID);

        buscarListaAtiva(usuarioId, listaId);

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long tarefasAfetadas = tarefaRepository.moverPorListaParaLixeira(
                usuarioId, listaId, loteExclusaoId, agora, expiresAt);
        listaRepository.moverParaLixeira(usuarioId, listaId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoListaResponse(listaIdStr, (int) tarefasAfetadas, loteExclusaoId);
    }

    public ListaTarefasResponse restaurarLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId listaId = TarefaSupport.validarObjectId(listaIdStr, TarefaSupport.CAMPO_LISTA_ID);

        ListaTarefas existente = listaRepository.buscar(usuarioId, listaId)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));

        if (existente.deletedAt() == null) {
            long total = tarefaRepository.contarPorLista(usuarioId, listaId);
            return TarefaMapper.paraListaResponse(existente, total);
        }

        String loteExclusaoId = existente.loteExclusaoId();
        listaRepository.restaurar(usuarioId, listaId);

        if (loteExclusaoId != null) {
            tarefaRepository.restaurarPorLote(usuarioId, listaId, loteExclusaoId);
        }

        long total = tarefaRepository.contarPorLista(usuarioId, listaId);
        ListaTarefas restaurada = listaRepository.buscar(usuarioId, listaId).orElse(existente);
        return TarefaMapper.paraListaResponse(restaurada, total);
    }

    public ListaImpactoExclusaoResponse obterImpactoExclusaoLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId listaId = TarefaSupport.validarObjectId(listaIdStr, TarefaSupport.CAMPO_LISTA_ID);

        ListaTarefas lista = buscarListaAtiva(usuarioId, listaId);

        long ativas = tarefaRepository.contarPorListaPorConclusao(usuarioId, listaId, false);
        long concluidas = tarefaRepository.contarPorListaPorConclusao(usuarioId, listaId, true);
        long series = serieRepository.contarAtivasPorLista(usuarioId, listaId);

        return new ListaImpactoExclusaoResponse(
                lista.id().toHexString(), lista.nome(), ativas, concluidas, series);
    }

    private ListaTarefas buscarListaAtiva(ObjectId usuarioId, ObjectId listaId) {
        return listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));
    }
}
