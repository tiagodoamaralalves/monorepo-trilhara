package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenRecuperacaoRepository {

    private static final String CAMPO_TOKEN_HASH = "tokenHash";
    private static final String CAMPO_UTILIZADO_EM = "utilizadoEm";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_EXPIRA_EM = "expiraEm";

    private final MongoCollection<Document> tokens;

    public TokenRecuperacaoRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.tokens = client.getDatabase(database).getCollection("tokens_recuperacao_senha");
    }

    public void inserir(TokenRecuperacaoSenha token) {
        tokens.insertOne(paraDocumento(token));
    }

    public Optional<TokenRecuperacaoSenha> buscarPorHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokens.find(eq(CAMPO_TOKEN_HASH, tokenHash)).first())
                .map(this::paraToken);
    }

    public boolean marcarUtilizado(String id, Instant agora) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        return tokens.updateOne(
                and(eq("_id", new ObjectId(id)), Filters.eq(CAMPO_UTILIZADO_EM, null)),
                Updates.set(CAMPO_UTILIZADO_EM, BsonDates.toBsonDate(agora)))
                .getModifiedCount() > 0;
    }

    private static Bson filtroUsuario(String usuarioId) {
        if (ObjectId.isValid(usuarioId)) {
            return Filters.or(eq(CAMPO_USUARIO_ID, new ObjectId(usuarioId)), eq(CAMPO_USUARIO_ID, usuarioId));
        }
        return eq(CAMPO_USUARIO_ID, usuarioId);
    }

    public void invalidarPendentesDoUsuario(String usuarioId, Instant agora) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return;
        }
        tokens.updateMany(
                and(filtroUsuario(usuarioId), Filters.eq(CAMPO_UTILIZADO_EM, null)),
                Updates.set(CAMPO_UTILIZADO_EM, BsonDates.toBsonDate(agora)));
    }

    private Document paraDocumento(TokenRecuperacaoSenha t) {
        Document doc = new Document();
        doc.put("_id", new ObjectId(t.id()));
        doc.put(CAMPO_TOKEN_HASH, t.tokenHash());
        if (ObjectId.isValid(t.usuarioId())) {
            doc.put(CAMPO_USUARIO_ID, new ObjectId(t.usuarioId()));
        } else {
            doc.put(CAMPO_USUARIO_ID, t.usuarioId());
        }
        doc.put("ip", t.ip());
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(t.criadoEm()));
        doc.put(CAMPO_EXPIRA_EM, BsonDates.toBsonDate(t.expiraEm()));
        doc.put(CAMPO_UTILIZADO_EM, BsonDates.toBsonDate(t.utilizadoEm()));
        return doc;
    }

    private TokenRecuperacaoSenha paraToken(Document doc) {
        return new TokenRecuperacaoSenha(
                doc.getObjectId("_id").toHexString(),
                doc.getString(CAMPO_TOKEN_HASH),
                extrairUsuarioId(doc),
                doc.getString("ip"),
                BsonDates.toInstant(doc.getDate(CAMPO_CRIADO_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_EXPIRA_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_UTILIZADO_EM)));
    }

    private static String extrairUsuarioId(Document doc) {
        Object val = doc.get(CAMPO_USUARIO_ID);
        if (val instanceof ObjectId oid) {
            return oid.toHexString();
        }
        return doc.getString(CAMPO_USUARIO_ID);
    }
}

