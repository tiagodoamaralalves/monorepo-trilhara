package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenRecuperacaoRepository {

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
        return Optional.ofNullable(tokens.find(eq("tokenHash", tokenHash)).first())
                .map(this::paraToken);
    }

    public boolean marcarUtilizado(String id, Instant agora) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        return tokens.updateOne(
                and(eq("_id", new ObjectId(id)), Filters.eq("utilizadoEm", null)),
                Updates.set("utilizadoEm", Date.from(agora)))
                .getModifiedCount() > 0;
    }

    private static Bson filtroUsuario(String usuarioId) {
        if (ObjectId.isValid(usuarioId)) {
            return Filters.or(eq("usuarioId", new ObjectId(usuarioId)), eq("usuarioId", usuarioId));
        }
        return eq("usuarioId", usuarioId);
    }

    public void invalidarPendentesDoUsuario(String usuarioId, Instant agora) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return;
        }
        tokens.updateMany(
                and(filtroUsuario(usuarioId), Filters.eq("utilizadoEm", null)),
                Updates.set("utilizadoEm", Date.from(agora)));
    }

    private Document paraDocumento(TokenRecuperacaoSenha t) {
        Document doc = new Document();
        doc.put("_id", new ObjectId(t.id()));
        doc.put("tokenHash", t.tokenHash());
        if (ObjectId.isValid(t.usuarioId())) {
            doc.put("usuarioId", new ObjectId(t.usuarioId()));
        } else {
            doc.put("usuarioId", t.usuarioId());
        }
        doc.put("ip", t.ip());
        doc.put("criadoEm", Date.from(t.criadoEm()));
        doc.put("expiraEm", Date.from(t.expiraEm()));
        doc.put("utilizadoEm", t.utilizadoEm() != null ? Date.from(t.utilizadoEm()) : null);
        return doc;
    }

    private TokenRecuperacaoSenha paraToken(Document doc) {
        return new TokenRecuperacaoSenha(
                doc.getObjectId("_id").toHexString(),
                doc.getString("tokenHash"),
                extrairUsuarioId(doc),
                doc.getString("ip"),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("expiraEm")),
                paraInstant(doc.getDate("utilizadoEm")));
    }

    private static String extrairUsuarioId(Document doc) {
        Object val = doc.get("usuarioId");
        if (val instanceof ObjectId oid) {
            return oid.toHexString();
        }
        return doc.getString("usuarioId");
    }

    private static Instant paraInstant(Date data) {
        return data != null ? data.toInstant() : null;
    }


}

