package br.com.trilhara.tarefa;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.tarefa.dto.AtualizarPosicaoFluxoRequest;
import br.com.trilhara.tarefa.dto.AtualizarPosicoesFluxoRequest;
import br.com.trilhara.tarefa.dto.CriarConexaoFluxoRequest;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class TarefaFluxoService {

    private static final String ERRO_TAREFA_NAO_ENCONTRADA = "Tarefa não encontrada ou inacessível.";
    private static final String CAMPO_TAREFA_ALVO_ID = "tarefaAlvoId";
    private static final String CAMPO_POSICOES = "posicoes";

    private final TarefaRepository tarefaRepository;

    public TarefaFluxoService(TarefaRepository tarefaRepository) {
        this.tarefaRepository = tarefaRepository;
    }

    public List<TarefaResponse> listarFluxo(String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        List<Tarefa> tarefas = tarefaRepository.listarNoFluxo(usuarioId);
        return montarRespostaFluxo(tarefas);
    }

    public TarefaResponse adicionarAoFluxo(
            String tarefaIdStr,
            AtualizarPosicaoFluxoRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        buscarTarefaAtiva(usuarioId, tarefaId);
        PosicaoFluxo posicao = request != null ? validarPosicao(request.x(), request.y()) : null;
        tarefaRepository.atualizarParticipacaoFluxo(usuarioId, tarefaId, true, posicao, Instant.now());

        Tarefa atualizada = buscarTarefaAtiva(usuarioId, tarefaId);
        Map<ObjectId, List<ObjectId>> antecessoras =
                tarefaRepository.mapearAntecessoras(usuarioId, List.of(tarefaId));
        return mapearComAntecessoras(atualizada, antecessoras);
    }

    public void removerDoFluxo(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        buscarTarefaAtiva(usuarioId, tarefaId);
        Instant agora = Instant.now();
        tarefaRepository.removerDoFluxoLimpo(usuarioId, tarefaId, agora);
        tarefaRepository.removerReferenciasDeFluxo(usuarioId, tarefaId, agora);
    }

    public List<TarefaResponse> criarConexao(
            String tarefaIdStr,
            CriarConexaoFluxoRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);
        ObjectId alvoId = TarefaSupport.validarObjectId(request.tarefaAlvoId(), CAMPO_TAREFA_ALVO_ID);

        if (tarefaId.equals(alvoId)) {
            throw new ValidationException(
                    CAMPO_TAREFA_ALVO_ID, "Não é possível conectar uma tarefa a ela mesma.");
        }

        Tarefa origem = buscarTarefaAtiva(usuarioId, tarefaId);
        Tarefa alvo = buscarTarefaAtivaOpcional(usuarioId, alvoId)
                .orElseThrow(() -> new ValidationException(
                        CAMPO_TAREFA_ALVO_ID, "Tarefa de destino não encontrada ou inacessível."));

        Instant agora = Instant.now();
        switch (request.tipo()) {
            case SUCESSORA -> conectarDirecionado(usuarioId, origem, alvo, agora);
            case ANTECESSORA -> conectarDirecionado(usuarioId, alvo, origem, agora);
            case PAR -> conectarPares(usuarioId, origem, alvo, agora);
        }

        return montarRespostaFluxo(tarefaRepository.listarNoFluxo(usuarioId));
    }

    public List<TarefaResponse> removerConexao(
            String tarefaIdStr,
            String alvoIdStr,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);
        ObjectId alvoId = TarefaSupport.validarObjectId(alvoIdStr, CAMPO_TAREFA_ALVO_ID);

        Tarefa origem = buscarTarefaAtiva(usuarioId, tarefaId);
        Instant agora = Instant.now();

        tarefaRepository.atualizarConexoesFluxo(
                usuarioId,
                origem.id(),
                origem.noFluxo(),
                removerId(origem.sucessorasIds(), alvoId),
                removerId(origem.paresIds(), alvoId),
                agora);

        buscarTarefaAtivaOpcional(usuarioId, alvoId).ifPresent(alvo ->
                tarefaRepository.atualizarConexoesFluxo(
                        usuarioId,
                        alvo.id(),
                        alvo.noFluxo(),
                        removerId(alvo.sucessorasIds(), tarefaId),
                        removerId(alvo.paresIds(), tarefaId),
                        agora));

        return montarRespostaFluxo(tarefaRepository.listarNoFluxo(usuarioId));
    }

    public TarefaResponse atualizarPosicao(
            String tarefaIdStr,
            AtualizarPosicaoFluxoRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        buscarTarefaAtiva(usuarioId, tarefaId);
        PosicaoFluxo posicao = validarPosicao(request.x(), request.y());
        tarefaRepository.atualizarPosicaoFluxo(usuarioId, tarefaId, posicao, Instant.now());

        Tarefa atualizada = buscarTarefaAtiva(usuarioId, tarefaId);
        Map<ObjectId, List<ObjectId>> antecessoras =
                tarefaRepository.mapearAntecessoras(usuarioId, List.of(tarefaId));
        return mapearComAntecessoras(atualizada, antecessoras);
    }

    public List<TarefaResponse> atualizarPosicoesEmLote(
            AtualizarPosicoesFluxoRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);

        Map<ObjectId, PosicaoFluxo> posicoesValidadas = new HashMap<>();
        for (var item : request.posicoes()) {
            ObjectId id = TarefaSupport.validarObjectId(item.tarefaId(), CAMPO_POSICOES);
            buscarTarefaAtivaOpcional(usuarioId, id)
                    .orElseThrow(() -> new ValidationException(
                            CAMPO_POSICOES, ERRO_TAREFA_NAO_ENCONTRADA));
            posicoesValidadas.put(id, validarPosicao(item.x(), item.y()));
        }

        Instant agora = Instant.now();
        for (var entry : posicoesValidadas.entrySet()) {
            tarefaRepository.atualizarPosicaoFluxo(usuarioId, entry.getKey(), entry.getValue(), agora);
        }

        return montarRespostaFluxo(tarefaRepository.listarNoFluxo(usuarioId));
    }

    private void conectarDirecionado(
            ObjectId usuarioId,
            Tarefa antecessora,
            Tarefa sucessora,
            Instant agora) {
        if (alcancaPorSucessoras(usuarioId, sucessora.id(), antecessora.id())) {
            throw new ValidationException(
                    CAMPO_TAREFA_ALVO_ID, "Esta conexão criaria um ciclo no fluxo.");
        }

        List<ObjectId> novasSucessoras = adicionarId(antecessora.sucessorasIds(), sucessora.id());
        List<ObjectId> novosParesAnt = removerId(antecessora.paresIds(), sucessora.id());
        tarefaRepository.atualizarConexoesFluxo(
                usuarioId, antecessora.id(), true, novasSucessoras, novosParesAnt, agora);

        List<ObjectId> novosParesSucc = removerId(sucessora.paresIds(), antecessora.id());
        tarefaRepository.atualizarConexoesFluxo(
                usuarioId, sucessora.id(), true, sucessora.sucessorasIds(), novosParesSucc, agora);
    }

    private void conectarPares(
            ObjectId usuarioId,
            Tarefa primeira,
            Tarefa segunda,
            Instant agora) {
        tarefaRepository.atualizarConexoesFluxo(
                usuarioId,
                primeira.id(),
                true,
                removerId(primeira.sucessorasIds(), segunda.id()),
                adicionarId(primeira.paresIds(), segunda.id()),
                agora);

        tarefaRepository.atualizarConexoesFluxo(
                usuarioId,
                segunda.id(),
                true,
                removerId(segunda.sucessorasIds(), primeira.id()),
                adicionarId(segunda.paresIds(), primeira.id()),
                agora);
    }

    private boolean alcancaPorSucessoras(ObjectId usuarioId, ObjectId origemId, ObjectId destinoId) {
        if (origemId.equals(destinoId)) {
            return true;
        }
        List<Tarefa> tarefasFluxo = tarefaRepository.listarNoFluxo(usuarioId);
        Map<ObjectId, List<ObjectId>> adj = new HashMap<>();
        for (Tarefa t : tarefasFluxo) {
            adj.put(t.id(), t.sucessorasIds());
        }

        Set<ObjectId> visitados = new HashSet<>();
        Queue<ObjectId> fila = new ArrayDeque<>();
        fila.add(origemId);
        visitados.add(origemId);

        while (!fila.isEmpty()) {
            ObjectId atual = fila.poll();
            for (ObjectId prox : adj.getOrDefault(atual, List.of())) {
                if (prox.equals(destinoId)) {
                    return true;
                }
                if (visitados.add(prox)) {
                    fila.add(prox);
                }
            }
        }
        return false;
    }

    private List<TarefaResponse> montarRespostaFluxo(List<Tarefa> tarefas) {
        Set<ObjectId> ativos = new HashSet<>();
        for (Tarefa t : tarefas) {
            ativos.add(t.id());
        }

        Map<ObjectId, List<String>> antecessorasPorId = new HashMap<>();
        for (Tarefa t : tarefas) {
            for (ObjectId succId : t.sucessorasIds()) {
                if (ativos.contains(succId)) {
                    antecessorasPorId
                            .computeIfAbsent(succId, k -> new ArrayList<>())
                            .add(t.id().toHexString());
                }
            }
        }

        return tarefas.stream()
                .map(t -> {
                    Tarefa filtrada = new Tarefa(
                            t.id(), t.usuarioId(), t.listaId(), t.serieId(),
                            t.dataOriginalOcorrencia(), t.desvinculadaDaSerie(),
                            t.titulo(), t.descricao(), t.prioridade(), t.dataVencimento(),
                            t.horario(), t.estado(), t.concluida(), t.concluidaEm(),
                            t.ordem(), t.subtarefas(), t.modoLembrete(),
                            t.lembreteMinutosAntes(), t.fusoAgendamento(),
                            t.deletedAt(), t.expiresAt(), t.loteExclusaoId(),
                            t.noFluxo(),
                            t.sucessorasIds().stream().filter(ativos::contains).toList(),
                            t.paresIds().stream().filter(ativos::contains).toList(),
                            t.posicaoFluxo(),
                            t.criadoEm(), t.atualizadoEm());
                    return TarefaMapper.paraTarefaResponse(
                            filtrada, antecessorasPorId.getOrDefault(t.id(), List.of()));
                })
                .toList();
    }

    private Tarefa buscarTarefaAtiva(ObjectId usuarioId, ObjectId tarefaId) {
        return buscarTarefaAtivaOpcional(usuarioId, tarefaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA));
    }

    private Optional<Tarefa> buscarTarefaAtivaOpcional(ObjectId usuarioId, ObjectId tarefaId) {
        return tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null);
    }

    private TarefaResponse mapearComAntecessoras(
            Tarefa tarefa,
            Map<ObjectId, List<ObjectId>> mapaAntecessoras) {
        List<String> antecessoras = mapaAntecessoras.getOrDefault(tarefa.id(), List.of()).stream()
                .map(ObjectId::toHexString)
                .toList();
        return TarefaMapper.paraTarefaResponse(tarefa, antecessoras);
    }

    private static PosicaoFluxo validarPosicao(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new ValidationException(CAMPO_POSICOES, "Coordenadas de posição inválidas.");
        }
        return new PosicaoFluxo(x, y);
    }

    private static List<ObjectId> adicionarId(List<ObjectId> lista, ObjectId novoId) {
        Set<ObjectId> conjunto = new LinkedHashSet<>(lista);
        conjunto.add(novoId);
        return List.copyOf(conjunto);
    }

    private static List<ObjectId> removerId(List<ObjectId> lista, ObjectId remover) {
        return lista.stream().filter(id -> !id.equals(remover)).toList();
    }
}
