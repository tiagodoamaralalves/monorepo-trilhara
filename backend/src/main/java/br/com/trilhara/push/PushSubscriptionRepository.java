package br.com.trilhara.push;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PushSubscriptionRepository {

    private static final String CAMPO_ENDPOINT = "endpoint";
    private static final String CAMPO_USUARIO_ID = "usuarioId";

    private final MongoCollection<Document> collection;

    @Inject
    public PushSubscriptionRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("push_subscriptions");
    }

    /**
 * Upsert por {@code endpoint} (chave única {@code uk_push_subscriptions_endpoint}).
 *
 * <p>Um {@code endpoint} já existente é <strong>reatribuído</strong> ao usuário informado
 *: o dispositivo físico mudou de conta e não pode continuar recebendo notificações
 * da conta anterior.</p>
 *
 * @return o {@code _id} efetivamente persistido (o mesmo documento em reupsert)
 */
    public String salvarOuAtualizar(PushSubscription sub) {
        Object agora = BsonDates.toBsonDate(Instant.now());

        Document resultado = collection.findOneAndUpdate(
                eq(CAMPO_ENDPOINT, sub.endpoint()),
                Updates.combine(
                        Updates.set(CAMPO_USUARIO_ID, sub.usuarioId()),
                        Updates.set("p256dh", sub.p256dh()),
                        Updates.set("auth", sub.auth()),
                        Updates.set("userAgent", sub.userAgent()),
                        Updates.set("atualizadoEm", agora),
                        Updates.setOnInsert(
                                "criadoEm",
                                sub.criadoEm() != null ? BsonDates.toBsonDate(sub.criadoEm()) : agora)),
                new FindOneAndUpdateOptions()
                        .upsert(true)
                        .returnDocument(ReturnDocument.AFTER));

        return resultado != null && resultado.getObjectId("_id") != null
                ? resultado.getObjectId("_id").toHexString()
                : sub.id().toHexString();
    }

    public boolean removerPorEndpoint(ObjectId usuarioId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        return collection.deleteOne(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_ENDPOINT, endpoint.trim()))).getDeletedCount() > 0;
    }

    /**
 * Remove a inscrição <strong>morta</strong> informada pelo provedor ({@code 404}/{@code 410},
 * §11). Operação de sistema: o {@code endpoint} é único globalmente e a inscrição não pode
 * continuar na coleção, mesmo que já tenha sido reatribuída a outro usuário no intervalo.
 */
    public boolean removerEndpointInvalido(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        return collection.deleteOne(eq(CAMPO_ENDPOINT, endpoint.trim())).getDeletedCount() > 0;
    }

    public List<PushSubscription> listarPorUsuario(ObjectId usuarioId) {
        List<PushSubscription> lista = new ArrayList<>();
        collection.find(eq(CAMPO_USUARIO_ID, usuarioId)).forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public Optional<PushSubscription> buscarPorEndpoint(ObjectId usuarioId, String endpoint) {
        Document doc = collection
                .find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_ENDPOINT, endpoint)))
                .first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq(CAMPO_USUARIO_ID, usuarioId)).getDeletedCount();
    }

    private PushSubscription paraEntidade(Document doc) {
        return new PushSubscription(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getString(CAMPO_ENDPOINT),
                doc.getString("p256dh"),
                doc.getString("auth"),
                doc.getString("userAgent"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
