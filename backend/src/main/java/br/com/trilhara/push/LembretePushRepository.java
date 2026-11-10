package br.com.trilhara.push;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LembretePushRepository {

    private final MongoCollection<Document> collection;

    @Inject
    public LembretePushRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("lembretes_push");
    }

    public void salvarOuAtualizar(LembretePush lembrete) {
        Date agora = Date.from(Instant.now());
        collection.updateOne(
                and(eq("usuarioId", lembrete.usuarioId()), eq("tarefaId", lembrete.tarefaId())),
                Updates.combine(
                        Updates.set("versaoAgendamento", lembrete.versaoAgendamento()),
                        Updates.set("instanteEntregaUtc", Date.from(lembrete.instanteEntregaUtc())),
                        Updates.set("minutosAntes", lembrete.minutosAntes()),
                        Updates.set("fusoAgendamento", lembrete.fusoAgendamento()),
                        Updates.set("estado", lembrete.estado()),
                        Updates.set("tituloTarefa", lembrete.tituloTarefa()),
                        Updates.set("tentativas", lembrete.tentativas()),
                        Updates.set("proximaTentativaEm", lembrete.proximaTentativaEm() != null ? Date.from(lembrete.proximaTentativaEm()) : null),
                        Updates.set("leaseOwner", lembrete.leaseOwner()),
                        Updates.set("leaseExpiraEm", lembrete.leaseExpiraEm() != null ? Date.from(lembrete.leaseExpiraEm()) : null),
                        Updates.set("ultimoErro", lembrete.ultimoErro()),
                        Updates.set("atualizadoEm", agora),
                        Updates.setOnInsert("criadoEm", lembrete.criadoEm() != null ? Date.from(lembrete.criadoEm()) : agora)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    public Optional<LembretePush> buscarPorTarefa(ObjectId usuarioId, ObjectId tarefaId) {
        Document doc = collection.find(and(eq("usuarioId", usuarioId), eq("tarefaId", tarefaId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<LembretePush> buscarPorId(ObjectId id) {
        Document doc = collection.find(eq("_id", id)).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public void cancelarPorTarefa(ObjectId usuarioId, ObjectId tarefaId) {
        collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("tarefaId", tarefaId)),
                Updates.combine(
                        Updates.set("estado", LembretePush.ESTADO_CANCELADO),
                        Updates.set("atualizadoEm", Date.from(Instant.now()))
                )
        );
    }

    public void removerPorTarefa(ObjectId usuarioId, ObjectId tarefaId) {
        collection.deleteMany(and(eq("usuarioId", usuarioId), eq("tarefaId", tarefaId)));
    }

    public List<LembretePush> listarCandidatosEnvio(Instant agora, int limite) {
        List<LembretePush> lista = new ArrayList<>();
        Date dateAgora = Date.from(agora);

        var filtro = or(
                and(eq("estado", LembretePush.ESTADO_PENDENTE), lte("instanteEntregaUtc", dateAgora)),
                and(eq("estado", LembretePush.ESTADO_PROCESSANDO), lte("leaseExpiraEm", dateAgora))
        );

        collection.find(filtro)
                .sort(Sorts.ascending("instanteEntregaUtc"))
                .limit(limite)
                .forEach(doc -> lista.add(paraEntidade(doc)));

        return lista;
    }

    public Optional<LembretePush> claimLembrete(ObjectId id, String workerId, Instant agora, Instant leaseAte) {
        Date dateAgora = Date.from(agora);
        var filtro = and(
                eq("_id", id),
                or(
                        eq("estado", LembretePush.ESTADO_PENDENTE),
                        and(eq("estado", LembretePush.ESTADO_PROCESSANDO), lte("leaseExpiraEm", dateAgora))
                )
        );

        var update = Updates.combine(
                Updates.set("estado", LembretePush.ESTADO_PROCESSANDO),
                Updates.set("leaseOwner", workerId),
                Updates.set("leaseExpiraEm", Date.from(leaseAte)),
                Updates.inc("tentativas", 1),
                Updates.set("atualizadoEm", dateAgora)
        );

        Document doc = collection.findOneAndUpdate(
                filtro,
                update,
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );

        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public boolean concluirLembrete(ObjectId id, String workerId, String estado, String erro, Instant agora) {
        var filtro = and(eq("_id", id), eq("leaseOwner", workerId));
        var update = Updates.combine(
                Updates.set("estado", estado),
                Updates.set("ultimoErro", erro),
                Updates.set("leaseOwner", null),
                Updates.set("leaseExpiraEm", null),
                Updates.set("atualizadoEm", Date.from(agora))
        );
        return collection.updateOne(filtro, update).getModifiedCount() > 0;
    }

    public boolean remarcarFalhaTemporaria(ObjectId id, String workerId, Instant proximaTentativaEm, String erro, Instant agora) {
        var filtro = and(eq("_id", id), eq("leaseOwner", workerId));
        var update = Updates.combine(
                Updates.set("estado", LembretePush.ESTADO_PENDENTE),
                Updates.set("instanteEntregaUtc", Date.from(proximaTentativaEm)),
                Updates.set("proximaTentativaEm", Date.from(proximaTentativaEm)),
                Updates.set("ultimoErro", erro),
                Updates.set("leaseOwner", null),
                Updates.set("leaseExpiraEm", null),
                Updates.set("atualizadoEm", Date.from(agora))
        );
        return collection.updateOne(filtro, update).getModifiedCount() > 0;
    }

    public boolean atualizarPendente(ObjectId id, Instant novoInstanteEntregaUtc, int novoMinutosAntes) {
        var update = Updates.combine(
                Updates.set("instanteEntregaUtc", Date.from(novoInstanteEntregaUtc)),
                Updates.set("minutosAntes", novoMinutosAntes),
                Updates.set("atualizadoEm", Date.from(Instant.now()))
        );
        return collection.updateOne(and(eq("_id", id), eq("estado", LembretePush.ESTADO_PENDENTE)), update).getModifiedCount() > 0;
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq("usuarioId", usuarioId)).getDeletedCount();
    }

    private LembretePush paraEntidade(Document doc) {
        return new LembretePush(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("tarefaId"),
                doc.getInteger("versaoAgendamento", 1),
                paraInstant(doc.getDate("instanteEntregaUtc")),
                doc.getInteger("minutosAntes"),
                doc.getString("fusoAgendamento"),
                doc.getString("estado"),
                doc.getString("tituloTarefa"),
                doc.getInteger("tentativas", 0),
                paraInstant(doc.getDate("proximaTentativaEm")),
                doc.getString("leaseOwner"),
                paraInstant(doc.getDate("leaseExpiraEm")),
                doc.getString("ultimoErro"),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("atualizadoEm"))
        );
    }

    private static Instant paraInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }
}
