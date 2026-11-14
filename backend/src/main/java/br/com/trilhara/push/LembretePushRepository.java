package br.com.trilhara.push;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Acesso <strong>por tenant</strong> a {@code lembretes_push}.
 *
 * <p>Toda leitura/escrita começa por {@code ObjectId usuarioId}. A varredura multi-tenant do
 * scheduler vive em {@link LembretePushFilaRepository} ({@code @SystemScope}), nunca aqui.</p>
 */
@ApplicationScoped
public class LembretePushRepository {

    private final MongoCollection<Document> collection;

    @Inject
    public LembretePushRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("lembretes_push");
    }

    /**
 * Insere uma nova versão do agendamento. O índice único
 * {@code {tarefaId, versaoAgendamento}} é a chave idempotente por
 * {@code (tarefaId, versaoAgendamento)}: reinserir a mesma versão por corrida entre
 * duas requisições é no-op silencioso (nunca 500).
 *
 * @return {@code true} se a versão foi realmente inserida
 */
    public boolean inserirNovaVersao(LembretePush lembrete) {
        Instant agora = Instant.now();
        try {
            collection.insertOne(paraDocumento(lembrete, Date.from(agora)));
            return true;
        } catch (com.mongodb.MongoWriteException e) {
            if (e.getError().getCode() == 11000) {
                return false;
            }
            throw e;
        }
    }

    /** Versão vigente (maior {@code versaoAgendamento}) da tarefa no tenant. */
    public Optional<LembretePush> buscarVersaoVigente(ObjectId usuarioId, ObjectId tarefaId) {
        Document doc = collection
                .find(and(eq("usuarioId", usuarioId), eq("tarefaId", tarefaId)))
                .sort(Sorts.descending("versaoAgendamento"))
                .first();
        return Optional.ofNullable(doc).map(LembretePushRepository::paraEntidade);
    }

    /**
 * Cancela as versões {@code PENDENTE} da tarefa que não sejam a versão informada.
 *
 * @return quantidade de documentos cancelados
 */
    public long cancelarPendentesAnteriores(ObjectId usuarioId, ObjectId tarefaId, long versaoMantida) {
        return cancelar(and(
                eq("usuarioId", usuarioId),
                eq("tarefaId", tarefaId),
                eq("estado", LembretePush.ESTADO_PENDENTE),
                ne("versaoAgendamento", versaoMantida)));
    }

    /** Cancela todas as versões {@code PENDENTE} da tarefa (suspensão, conclusão, lixeira). */
    public long cancelarPendentes(ObjectId usuarioId, ObjectId tarefaId) {
        return cancelar(and(
                eq("usuarioId", usuarioId),
                eq("tarefaId", tarefaId),
                eq("estado", LembretePush.ESTADO_PENDENTE)));
    }

    private long cancelar(org.bson.conversions.Bson filtro) {
        return collection.updateMany(
                filtro,
                Updates.combine(
                        Updates.set("estado", LembretePush.ESTADO_CANCELADO),
                        Updates.set("leaseToken", null),
                        Updates.set("leaseAte", null),
                        Updates.set("atualizadoEm", Date.from(Instant.now()))))
                .getModifiedCount();
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq("usuarioId", usuarioId)).getDeletedCount();
    }

    static Document paraDocumento(LembretePush lembrete, Date agora) {
        return new Document()
                .append("_id", lembrete.id() != null ? lembrete.id() : new ObjectId())
                .append("usuarioId", lembrete.usuarioId())
                .append("tarefaId", lembrete.tarefaId())
                .append("versaoAgendamento", lembrete.versaoAgendamento())
                .append("instanteEntregaUtc", Date.from(lembrete.instanteEntregaUtc()))
                .append("estado", lembrete.estado())
                .append("tentativas", lembrete.tentativas())
                .append("leaseToken", lembrete.leaseToken())
                .append("leaseAte", lembrete.leaseAte() != null ? Date.from(lembrete.leaseAte()) : null)
                .append("criadoEm", lembrete.criadoEm() != null ? Date.from(lembrete.criadoEm()) : agora)
                .append("atualizadoEm", lembrete.atualizadoEm() != null ? Date.from(lembrete.atualizadoEm()) : agora);
    }

    static LembretePush paraEntidade(Document doc) {
        return new LembretePush(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("tarefaId"),
                doc.get("versaoAgendamento") instanceof Number n ? n.longValue() : LembretePush.VERSAO_INICIAL,
                paraInstant(doc.getDate("instanteEntregaUtc")),
                doc.getString("estado"),
                doc.getInteger("tentativas", 0),
                doc.getString("leaseToken"),
                paraInstant(doc.getDate("leaseAte")),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("atualizadoEm")));
    }

    static Instant paraInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }
}
