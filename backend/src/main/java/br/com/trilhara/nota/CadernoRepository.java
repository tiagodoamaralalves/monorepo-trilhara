package br.com.trilhara.nota;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

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
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Repositório direto BSON para a coleção normalizada 'cadernos'.
 */
@ApplicationScoped
public class CadernoRepository {

    /** Teto normativo da listagem de cadernos por usuário (500 cadernos/usuário). */
    public static final int LIMITE_LISTAGEM = 500;

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_ORIGEM_TRILHA_ID = "origemTrilhaId";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_TITULO = "titulo";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";
    private static final String CAMPO_CRIADO_EM = "criadoEm";

    private final MongoCollection<Document> collection;

    @Inject
    public CadernoRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("cadernos");
    }

    public void inserir(Caderno caderno) {
        collection.insertOne(paraDocumento(caderno));
    }

    public boolean atualizar(Caderno caderno) {
        Document doc = paraDocumento(caderno);
        return collection.replaceOne(
                and(eq("_id", caderno.id()), eq(CAMPO_USUARIO_ID, caderno.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Caderno> buscarAtivo(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(
                eq("_id", id),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Caderno> buscarQualquerStatus(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(
                eq("_id", id),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Caderno> buscarAtivoPorOrigemTrilha(ObjectId usuarioId, ObjectId origemTrilhaId) {
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_ORIGEM_TRILHA_ID, origemTrilhaId),
                eq(CAMPO_DELETED_AT, null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Caderno> listarAtivos(ObjectId usuarioId) {
        return listarAtivos(usuarioId, null, LIMITE_LISTAGEM);
    }

    /**
 * Cadernos ativos, opcionalmente filtrados pela trilha de origem.
 *
 * <p>{@code origemTrilhaId} nulo ou ausente não filtra. O teto de {@value #LIMITE_LISTAGEM}
 * é o limite normativo da listagem: cadernos são poucos por usuário (máximo 500) e a
 * listagem não é paginada.</p>
 */
    public List<Caderno> listarAtivos(ObjectId usuarioId, ObjectId origemTrilhaId, int limite) {
        List<Caderno> lista = new ArrayList<>();
        List<Bson> predicados = new ArrayList<>();
        predicados.add(eq(CAMPO_USUARIO_ID, usuarioId));
        predicados.add(eq(CAMPO_DELETED_AT, null));
        if (origemTrilhaId != null) {
            predicados.add(eq(CAMPO_ORIGEM_TRILHA_ID, origemTrilhaId));
        }
        collection.find(and(predicados))
                .sort(Sorts.orderBy(Sorts.descending(CAMPO_ATUALIZADO_EM), Sorts.descending("_id")))
                .limit(limite)
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public List<Caderno> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Caderno> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        ));
    }

    public List<Caderno> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Caderno> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
          .skip(Math.max(0, (pagina - 1) * tamanho))
          .limit(tamanho)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    /** Cadernos ativos do usuário — usado no teto de 500 cadernos. */
    public long contarAtivos(ObjectId usuarioId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    /** Títulos de cadernos ativos — usado para desempatar "Restauradas (2)", "(3)"…. */
    public List<String> titulosAtivos(ObjectId usuarioId) {
        List<String> titulos = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)))
                .projection(new Document(CAMPO_TITULO, 1))
                .forEach(doc -> {
                    String titulo = doc.getString(CAMPO_TITULO);
                    if (titulo != null) {
                        titulos.add(titulo);
                    }
                });
        return titulos;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id) {
        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public void desvincularTrilha(ObjectId usuarioId, ObjectId origemTrilhaId) {
        collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_ORIGEM_TRILHA_ID, origemTrilhaId)),
                Updates.combine(
                        Updates.unset(CAMPO_ORIGEM_TRILHA_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    /**
 * Cadernos ativos vinculados à trilha, exibidos na confirmação de exclusão da trilha.
 * O vínculo é apenas desfeito na exclusão da trilha — o caderno nunca é apagado por ela.
 * Assinatura começa por {@code usuarioId} e o filtro o inclui.
 */
    public long contarAtivosPorOrigemTrilha(ObjectId usuarioId, ObjectId origemTrilhaId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_ORIGEM_TRILHA_ID, origemTrilhaId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), ne(CAMPO_DELETED_AT, null))
        ).getDeletedCount();
    }

    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne(CAMPO_DELETED_AT, null), lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    public Document paraDocumento(Caderno c) {
        Document doc = new Document("_id", c.id() != null ? c.id() : new ObjectId())
                .append(CAMPO_USUARIO_ID, c.usuarioId())
                .append(CAMPO_TITULO, c.titulo())
                .append("cor", c.cor())
                .append(CAMPO_CRIADO_EM, BsonDates.toBsonDate(c.criadoEm()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(c.atualizadoEm()));

        if (c.icone() != null) {
            doc.append("icone", c.icone());
        }
        if (c.origemTrilhaId() != null) {
            doc.append(CAMPO_ORIGEM_TRILHA_ID, c.origemTrilhaId());
        }
        if (c.deletedAt() != null) {
            doc.append(CAMPO_DELETED_AT, BsonDates.toBsonDate(c.deletedAt()));
        }
        if (c.expiresAt() != null) {
            doc.append(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(c.expiresAt()));
        }
        if (c.loteExclusaoId() != null) {
            doc.append(CAMPO_LOTE_EXCLUSAO_ID, c.loteExclusaoId());
        }
        return doc;
    }

    public Caderno paraEntidade(Document doc) {
        if (doc == null) return null;
        return new Caderno(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getString(CAMPO_TITULO),
                doc.getString("cor"),
                doc.getString("icone"),
                doc.getObjectId(CAMPO_ORIGEM_TRILHA_ID),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
