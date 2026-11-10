package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RefreshTokenRepository {

    private final MongoCollection<Document> tokens;

    public RefreshTokenRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.tokens = client.getDatabase(database).getCollection("refresh_tokens");
    }

    public void inserir(RefreshToken token) {
        tokens.insertOne(paraDocumento(token));
    }

    public boolean atualizar(RefreshToken token) {
        if (!ObjectId.isValid(token.id())) {
            return false;
        }
        return tokens.replaceOne(
                eq("_id", new ObjectId(token.id())),
                paraDocumento(token),
                new ReplaceOptions().upsert(false)).getMatchedCount() > 0;
    }

    public Optional<RefreshToken> buscarPorHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokens.find(eq("tokenHash", tokenHash)).first())
                .map(this::paraRefreshToken);
    }

    public long revogarFamilia(String familiaId, Instant agora, String motivo) {
        if (familiaId == null || familiaId.isBlank()) {
            return 0;
        }
        return tokens.updateMany(
                and(eq("familiaId", familiaId), Filters.eq("revogadoEm", null)),
                Updates.combine(
                        Updates.set("revogadoEm", Date.from(agora)),
                        Updates.set("motivoRevogacao", motivo)))
                .getModifiedCount();
    }

    private static Bson filtroUsuario(String usuarioId) {
        if (ObjectId.isValid(usuarioId)) {
            return Filters.or(eq("usuarioId", new ObjectId(usuarioId)), eq("usuarioId", usuarioId));
        }
        return eq("usuarioId", usuarioId);
    }

    public long revogarFamiliaDoUsuario(String usuarioId, String familiaId, Instant agora, String motivo) {
        if (usuarioId == null || familiaId == null) {
            return 0;
        }
        return tokens.updateMany(
                and(filtroUsuario(usuarioId), eq("familiaId", familiaId), Filters.eq("revogadoEm", null)),
                Updates.combine(
                        Updates.set("revogadoEm", Date.from(agora)),
                        Updates.set("motivoRevogacao", motivo)))
                .getModifiedCount();
    }

    public long revogarTodosDoUsuario(String usuarioId, Instant agora, String motivo) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return 0;
        }
        return tokens.updateMany(
                and(filtroUsuario(usuarioId), Filters.eq("revogadoEm", null)),
                Updates.combine(
                        Updates.set("revogadoEm", Date.from(agora)),
                        Updates.set("motivoRevogacao", motivo)))
                .getModifiedCount();
    }

    public List<RefreshToken> listarAtivosPorUsuario(String usuarioId, Instant agora) {
        List<RefreshToken> resultado = new ArrayList<>();
        if (usuarioId == null || usuarioId.isBlank()) {
            return resultado;
        }
        tokens.find(and(
                filtroUsuario(usuarioId),
                Filters.eq("revogadoEm", null),
                gt("expiraEm", Date.from(agora))))
                .sort(Sorts.descending("ultimoUsoEm"))
                .forEach(doc -> resultado.add(paraRefreshToken(doc)));
        return resultado;
    }

    private Document paraDocumento(RefreshToken t) {
        Document doc = new Document();
        doc.put("_id", new ObjectId(t.id()));
        doc.put("tokenHash", t.tokenHash());
        doc.put("familiaId", t.familiaId());
        if (ObjectId.isValid(t.usuarioId())) {
            doc.put("usuarioId", new ObjectId(t.usuarioId()));
        } else {
            doc.put("usuarioId", t.usuarioId());
        }
        doc.put("userAgent", t.userAgent());
        doc.put("ip", t.ip());
        doc.put("criadoEm", Date.from(t.criadoEm()));
        doc.put("ultimoUsoEm", Date.from(t.ultimoUsoEm() != null ? t.ultimoUsoEm() : t.criadoEm()));
        doc.put("expiraEm", Date.from(t.expiraEm()));
        doc.put("revogadoEm", t.revogadoEm() != null ? Date.from(t.revogadoEm()) : null);
        doc.put("substituidoPorHash", t.substituidoPorHash());
        doc.put("motivoRevogacao", t.motivoRevogacao());
        return doc;
    }

    private RefreshToken paraRefreshToken(Document doc) {
        return new RefreshToken(
                doc.getObjectId("_id").toHexString(),
                doc.getString("tokenHash"),
                doc.getString("familiaId"),
                extrairUsuarioId(doc),
                doc.getString("userAgent"),
                doc.getString("ip"),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("ultimoUsoEm")),
                paraInstant(doc.getDate("expiraEm")),
                paraInstant(doc.getDate("revogadoEm")),
                doc.getString("substituidoPorHash"),
                doc.getString("motivoRevogacao"));
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

