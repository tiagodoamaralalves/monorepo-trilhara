package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;

import java.time.Instant;
import java.util.ArrayList;
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

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RefreshTokenRepository {

    private static final String CAMPO_TOKEN_HASH = "tokenHash";
    private static final String CAMPO_FAMILIA_ID = "familiaId";
    private static final String CAMPO_REVOGADO_EM = "revogadoEm";
    private static final String CAMPO_MOTIVO_REVOGACAO = "motivoRevogacao";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_EXPIRA_EM = "expiraEm";
    private static final String CAMPO_ULTIMO_USO_EM = "ultimoUsoEm";
    private static final String CAMPO_CRIADO_EM = "criadoEm";

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
        return Optional.ofNullable(tokens.find(eq(CAMPO_TOKEN_HASH, tokenHash)).first())
                .map(this::paraRefreshToken);
    }

    public long revogarFamilia(String familiaId, Instant agora, String motivo) {
        if (familiaId == null || familiaId.isBlank()) {
            return 0;
        }
        return tokens.updateMany(
                and(eq(CAMPO_FAMILIA_ID, familiaId), Filters.eq(CAMPO_REVOGADO_EM, null)),
                Updates.combine(
                        Updates.set(CAMPO_REVOGADO_EM, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_MOTIVO_REVOGACAO, motivo)))
                .getModifiedCount();
    }

    private static Bson filtroUsuario(String usuarioId) {
        if (ObjectId.isValid(usuarioId)) {
            return Filters.or(eq(CAMPO_USUARIO_ID, new ObjectId(usuarioId)), eq(CAMPO_USUARIO_ID, usuarioId));
        }
        return eq(CAMPO_USUARIO_ID, usuarioId);
    }

    public long revogarFamiliaDoUsuario(String usuarioId, String familiaId, Instant agora, String motivo) {
        if (usuarioId == null || familiaId == null) {
            return 0;
        }
        return tokens.updateMany(
                and(filtroUsuario(usuarioId), eq(CAMPO_FAMILIA_ID, familiaId), Filters.eq(CAMPO_REVOGADO_EM, null)),
                Updates.combine(
                        Updates.set(CAMPO_REVOGADO_EM, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_MOTIVO_REVOGACAO, motivo)))
                .getModifiedCount();
    }

    public long revogarTodosDoUsuario(String usuarioId, Instant agora, String motivo) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return 0;
        }
        return tokens.updateMany(
                and(filtroUsuario(usuarioId), Filters.eq(CAMPO_REVOGADO_EM, null)),
                Updates.combine(
                        Updates.set(CAMPO_REVOGADO_EM, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_MOTIVO_REVOGACAO, motivo)))
                .getModifiedCount();
    }

    public List<RefreshToken> listarAtivosPorUsuario(String usuarioId, Instant agora) {
        List<RefreshToken> resultado = new ArrayList<>();
        if (usuarioId == null || usuarioId.isBlank()) {
            return resultado;
        }
        tokens.find(and(
                filtroUsuario(usuarioId),
                Filters.eq(CAMPO_REVOGADO_EM, null),
                gt(CAMPO_EXPIRA_EM, BsonDates.toBsonDate(agora))))
                .sort(Sorts.descending(CAMPO_ULTIMO_USO_EM))
                .forEach(doc -> resultado.add(paraRefreshToken(doc)));
        return resultado;
    }

    private Document paraDocumento(RefreshToken t) {
        Document doc = new Document();
        doc.put("_id", new ObjectId(t.id()));
        doc.put(CAMPO_TOKEN_HASH, t.tokenHash());
        doc.put(CAMPO_FAMILIA_ID, t.familiaId());
        if (ObjectId.isValid(t.usuarioId())) {
            doc.put(CAMPO_USUARIO_ID, new ObjectId(t.usuarioId()));
        } else {
            doc.put(CAMPO_USUARIO_ID, t.usuarioId());
        }
        doc.put("userAgent", t.userAgent());
        doc.put("ip", t.ip());
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(t.criadoEm()));
        doc.put(CAMPO_ULTIMO_USO_EM, BsonDates.toBsonDate(t.ultimoUsoEm() != null ? t.ultimoUsoEm() : t.criadoEm()));
        doc.put(CAMPO_EXPIRA_EM, BsonDates.toBsonDate(t.expiraEm()));
        doc.put(CAMPO_REVOGADO_EM, BsonDates.toBsonDate(t.revogadoEm()));
        doc.put("substituidoPorHash", t.substituidoPorHash());
        doc.put(CAMPO_MOTIVO_REVOGACAO, t.motivoRevogacao());
        return doc;
    }

    private RefreshToken paraRefreshToken(Document doc) {
        return new RefreshToken(
                doc.getObjectId("_id").toHexString(),
                doc.getString(CAMPO_TOKEN_HASH),
                doc.getString(CAMPO_FAMILIA_ID),
                extrairUsuarioId(doc),
                doc.getString("userAgent"),
                doc.getString("ip"),
                BsonDates.toInstant(doc.getDate(CAMPO_CRIADO_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_ULTIMO_USO_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_EXPIRA_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_REVOGADO_EM)),
                doc.getString("substituidoPorHash"),
                doc.getString(CAMPO_MOTIVO_REVOGACAO));
    }

    private static String extrairUsuarioId(Document doc) {
        Object val = doc.get(CAMPO_USUARIO_ID);
        if (val instanceof ObjectId oid) {
            return oid.toHexString();
        }
        return doc.getString(CAMPO_USUARIO_ID);
    }
}

