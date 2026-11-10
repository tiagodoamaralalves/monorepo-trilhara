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
public class TokenConfirmacaoEmailRepository {

    private final MongoCollection<Document> tokens;

    public TokenConfirmacaoEmailRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.tokens = client.getDatabase(database).getCollection("tokens_confirmacao_email");
    }

    public void inserir(TokenConfirmacaoEmail token) {
        tokens.insertOne(paraDocumento(token));
    }

    public Optional<TokenConfirmacaoEmail> buscarPorHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokens.find(eq("tokenHash", tokenHash)).first())
                .map(this::paraToken);
    }

    public boolean marcarConfirmado(String id, Instant agora) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        return tokens.updateOne(
                and(eq("_id", new ObjectId(id)), Filters.eq("confirmadoEm", null)),
                Updates.set("confirmadoEm", Date.from(agora)))
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
                and(filtroUsuario(usuarioId), Filters.eq("confirmadoEm", null)),
                Updates.set("expiraEm", Date.from(agora)));
    }

    private Document paraDocumento(TokenConfirmacaoEmail t) {
        Document doc = new Document();
        if (t.id() != null && ObjectId.isValid(t.id())) {
            doc.put("_id", new ObjectId(t.id()));
        } else {
            doc.put("_id", new ObjectId());
        }
        doc.put("tokenHash", t.tokenHash());
        if (ObjectId.isValid(t.usuarioId())) {
            doc.put("usuarioId", new ObjectId(t.usuarioId()));
        } else {
            doc.put("usuarioId", t.usuarioId());
        }
        doc.put("email", t.email());
        doc.put("criadoEm", t.criadoEm() != null ? Date.from(t.criadoEm()) : Date.from(Instant.now()));
        doc.put("expiraEm", t.expiraEm() != null ? Date.from(t.expiraEm()) : null);
        doc.put("confirmadoEm", t.confirmadoEm() != null ? Date.from(t.confirmadoEm()) : null);
        return doc;
    }

    private TokenConfirmacaoEmail paraToken(Document doc) {
        ObjectId objectId = doc.getObjectId("_id");
        return new TokenConfirmacaoEmail(
                objectId != null ? objectId.toHexString() : null,
                doc.getString("tokenHash"),
                extrairUsuarioId(doc),
                doc.getString("email"),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("expiraEm")),
                paraInstant(doc.getDate("confirmadoEm")));
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
