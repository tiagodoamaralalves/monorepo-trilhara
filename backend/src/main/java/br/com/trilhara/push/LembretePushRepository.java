package br.com.trilhara.push;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
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

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_TAREFA_ID = "tarefaId";
    private static final String CAMPO_VERSAO_AGENDAMENTO = "versaoAgendamento";
    private static final String CAMPO_ESTADO = "estado";
    private static final String CAMPO_LEASE_TOKEN = "leaseToken";
    private static final String CAMPO_LEASE_ATE = "leaseAte";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_INSTANTE_ENTREGA_UTC = "instanteEntregaUtc";
    private static final String CAMPO_CRIADO_EM = "criadoEm";

    private final MongoCollection<Document> collection;

    @Inject
    public LembretePushRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String databaseName) {
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
            collection.insertOne(paraDocumento(lembrete, agora));
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
                .find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TAREFA_ID, tarefaId)))
                .sort(Sorts.descending(CAMPO_VERSAO_AGENDAMENTO))
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
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TAREFA_ID, tarefaId),
                eq(CAMPO_ESTADO, LembretePush.ESTADO_PENDENTE),
                ne(CAMPO_VERSAO_AGENDAMENTO, versaoMantida)));
    }

    /** Cancela todas as versões {@code PENDENTE} da tarefa (suspensão, conclusão, lixeira). */
    public long cancelarPendentes(ObjectId usuarioId, ObjectId tarefaId) {
        return cancelar(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TAREFA_ID, tarefaId),
                eq(CAMPO_ESTADO, LembretePush.ESTADO_PENDENTE)));
    }

    private long cancelar(org.bson.conversions.Bson filtro) {
        return collection.updateMany(
                filtro,
                Updates.combine(
                        Updates.set(CAMPO_ESTADO, LembretePush.ESTADO_CANCELADO),
                        Updates.set(CAMPO_LEASE_TOKEN, null),
                        Updates.set(CAMPO_LEASE_ATE, null),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))))
                .getModifiedCount();
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq(CAMPO_USUARIO_ID, usuarioId)).getDeletedCount();
    }

    static Document paraDocumento(LembretePush lembrete, Instant agora) {
        Object dataAgora = BsonDates.toBsonDate(agora);
        return new Document()
                .append("_id", lembrete.id() != null ? lembrete.id() : new ObjectId())
                .append(CAMPO_USUARIO_ID, lembrete.usuarioId())
                .append(CAMPO_TAREFA_ID, lembrete.tarefaId())
                .append(CAMPO_VERSAO_AGENDAMENTO, lembrete.versaoAgendamento())
                .append(CAMPO_INSTANTE_ENTREGA_UTC, BsonDates.toBsonDate(lembrete.instanteEntregaUtc()))
                .append(CAMPO_ESTADO, lembrete.estado())
                .append("tentativas", lembrete.tentativas())
                .append(CAMPO_LEASE_TOKEN, lembrete.leaseToken())
                .append(CAMPO_LEASE_ATE, BsonDates.toBsonDate(lembrete.leaseAte()))
                .append(CAMPO_CRIADO_EM, lembrete.criadoEm() != null ? BsonDates.toBsonDate(lembrete.criadoEm()) : dataAgora)
                .append(CAMPO_ATUALIZADO_EM, lembrete.atualizadoEm() != null ? BsonDates.toBsonDate(lembrete.atualizadoEm()) : dataAgora);
    }

    static LembretePush paraEntidade(Document doc) {
        return new LembretePush(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_TAREFA_ID),
                doc.get(CAMPO_VERSAO_AGENDAMENTO) instanceof Number n ? n.longValue() : LembretePush.VERSAO_INICIAL,
                BsonDates.lerInstant(doc, CAMPO_INSTANTE_ENTREGA_UTC),
                doc.getString(CAMPO_ESTADO),
                doc.getInteger("tentativas", 0),
                doc.getString(CAMPO_LEASE_TOKEN),
                BsonDates.lerInstant(doc, CAMPO_LEASE_ATE),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM));
    }
}
