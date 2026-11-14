package br.com.trilhara.tarefa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import br.com.trilhara.push.LembretePushService;
import br.com.trilhara.shared.erro.TrashItemExpiredException;
import br.com.trilhara.tarefa.dto.LixeiraTarefasResponse;
import br.com.trilhara.tarefa.dto.RestauracaoTarefaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class TarefaLixeiraService {

    private record DestinoRestauracaoLista(ObjectId novaListaId, boolean realocadaParaListaGeral) {
    }

    private final ListaTarefasRepository listaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final TarefaRepository tarefaRepository;
    private final LembretePushService lembretePushService;

    public TarefaLixeiraService(
            ListaTarefasRepository listaRepository,
            SerieRecorrenteRepository serieRepository,
            TarefaRepository tarefaRepository,
            LembretePushService lembretePushService) {
        this.listaRepository = listaRepository;
        this.serieRepository = serieRepository;
        this.tarefaRepository = tarefaRepository;
        this.lembretePushService = lembretePushService;
    }

    public LixeiraTarefasResponse listarLixeira(
            String usuarioIdStr, Integer paginaParam, Integer tamanhoParam) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        Instant agora = Instant.now();

        TarefaSupport.PaginacaoValidada paginacao = TarefaSupport.validarPaginacao(paginaParam, tamanhoParam);
        var paginaTarefas = tarefaRepository.listarLixeiraPaginada(
                usuarioId, agora, paginacao.pagina(), paginacao.tamanho());

        List<ListaTarefas> listas = listaRepository.listarLixeira(usuarioId, agora);
        List<SerieRecorrente> series = serieRepository.listarLixeira(usuarioId, agora);

        return new LixeiraTarefasResponse(
                paginaTarefas.itens().stream().map(TarefaMapper::paraTarefaResponse).toList(),
                listas.stream().map(l -> TarefaMapper.paraListaResponse(l, 0)).toList(),
                series.stream().map(TarefaMapper::paraSerieResponse).toList(),
                paginaTarefas.pagina(),
                paginaTarefas.tamanho(),
                paginaTarefas.totalItens(),
                paginaTarefas.totalPaginas()
        );
    }

    public RestauracaoTarefaResponse restaurarTarefaDaLixeira(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);
        Instant agora = Instant.now();

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa não encontrada na lixeira."));

        if (existente.deletedAt() == null) {
            return new RestauracaoTarefaResponse(TarefaMapper.paraTarefaResponse(existente), false);
        }
        if (existente.expiresAt() != null && !existente.expiresAt().isAfter(agora)) {
            throw new TrashItemExpiredException("O prazo de 30 dias para restaurar esta tarefa expirou.");
        }

        DestinoRestauracaoLista destino = resolverDestinoRestauracaoLista(
                usuarioId, existente.listaId(), agora);

        tarefaRepository.restaurar(usuarioId, tarefaId, destino.novaListaId());
        Tarefa restaurada = tarefaRepository.buscar(usuarioId, tarefaId).orElse(existente);
        lembretePushService.agendarOuAtualizarLembrete(
                restaurada,
                restaurada.modoLembrete(),
                restaurada.lembreteMinutosAntes(),
                restaurada.fusoAgendamento()
        );
        return new RestauracaoTarefaResponse(
                TarefaMapper.paraTarefaResponse(restaurada),
                destino.realocadaParaListaGeral());
    }

    private DestinoRestauracaoLista resolverDestinoRestauracaoLista(
            ObjectId usuarioId, ObjectId listaOriginalId, Instant agora) {
        if (listaOriginalId == null) {
            return new DestinoRestauracaoLista(null, false);
        }
        Optional<ListaTarefas> listaOpt = listaRepository.buscarQualquerStatus(listaOriginalId);
        if (listaOpt.isEmpty()) {
            return new DestinoRestauracaoLista(null, true);
        }
        ListaTarefas lista = listaOpt.get();
        if (lista.deletedAt() == null) {
            return new DestinoRestauracaoLista(listaOriginalId, false);
        }
        if (lista.expiresAt() != null && !lista.expiresAt().isAfter(agora)) {
            return new DestinoRestauracaoLista(null, true);
        }
        listaRepository.restaurar(usuarioId, lista.id());
        return new DestinoRestauracaoLista(listaOriginalId, false);
    }

    public void esvaziarLixeira(String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);

        List<ListaTarefas> listasLixeira = listaRepository.listarLixeira(usuarioId, Instant.now());
        for (ListaTarefas l : listasLixeira) {
            serieRepository.desvincularLista(usuarioId, l.id());
        }

        tarefaRepository.esvaziarLixeira(usuarioId);
        serieRepository.esvaziarLixeira(usuarioId);
        listaRepository.esvaziarLixeira(usuarioId);
    }
}
