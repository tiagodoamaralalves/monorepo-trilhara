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
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §10 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §8.2.
 */
@ApplicationScoped
public class CadernoRepository {

    /** Teto normativo da listagem de cadernos por usuário (500 cadernos/usuário). */
    public static final int LIMITE_LISTAGEM = 500;

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
                and(eq("_id", caderno.id()), eq("usuarioId", caderno.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Caderno> buscarAtivo(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(
                eq("_id", id),
                eq("usuarioId", usuarioId),
                eq("deletedAt", null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Caderno> buscarQualquerStatus(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(
                eq("_id", id),
                eq("usuarioId", usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Caderno> buscarAtivoPorOrigemTrilha(ObjectId usuarioId, ObjectId origemTrilhaId) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("origemTrilhaId", origemTrilhaId),
                eq("deletedAt", null)
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
        predicados.add(eq("usuarioId", usuarioId));
        predicados.add(eq("deletedAt", null));
        if (origemTrilhaId != null) {
            predicados.add(eq("origemTrilhaId", origemTrilhaId));
        }
        collection.find(and(predicados))
                .sort(Sorts.orderBy(Sorts.descending("atualizadoEm"), Sorts.descending("_id")))
                .limit(limite)
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public List<Caderno> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Caderno> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        ));
    }

    public List<Caderno> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Caderno> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
          .skip(Math.max(0, (pagina - 1) * tamanho))
          .limit(tamanho)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    /** Cadernos ativos do usuário — usado no teto de 500 cadernos. */
    public long contarAtivos(ObjectId usuarioId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null)
        ));
    }

    /** Títulos de cadernos ativos — usado para desempatar "Restauradas (2)", "(3)"…. */
    public List<String> titulosAtivos(ObjectId usuarioId) {
        List<String> titulos = new ArrayList<>();
        collection.find(and(eq("usuarioId", usuarioId), eq("deletedAt", null)))
                .projection(new Document("titulo", 1))
                .forEach(doc -> {
                    String titulo = doc.getString("titulo");
                    if (titulo != null) {
                        titulos.add(titulo);
                    }
                });
        return titulos;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id) {
        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public void desvincularTrilha(ObjectId usuarioId, ObjectId origemTrilhaId) {
        collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("origemTrilhaId", origemTrilhaId)),
                Updates.combine(
                        Updates.unset("origemTrilhaId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
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
                eq("usuarioId", usuarioId),
                eq("origemTrilhaId", origemTrilhaId),
                eq("deletedAt", null)
        ));
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq("usuarioId", usuarioId), ne("deletedAt", null))
        ).getDeletedCount();
    }

    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne("deletedAt", null), lte("expiresAt", BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    public Document paraDocumento(Caderno c) {
        Document doc = new Document("_id", c.id() != null ? c.id() : new ObjectId())
                .append("usuarioId", c.usuarioId())
                .append("titulo", c.titulo())
                .append("cor", c.cor())
                .append("criadoEm", BsonDates.toBsonDate(c.criadoEm()))
                .append("atualizadoEm", BsonDates.toBsonDate(c.atualizadoEm()));

        if (c.icone() != null) {
            doc.append("icone", c.icone());
        }
        if (c.origemTrilhaId() != null) {
            doc.append("origemTrilhaId", c.origemTrilhaId());
        }
        if (c.deletedAt() != null) {
            doc.append("deletedAt", BsonDates.toBsonDate(c.deletedAt()));
        }
        if (c.expiresAt() != null) {
            doc.append("expiresAt", BsonDates.toBsonDate(c.expiresAt()));
        }
        if (c.loteExclusaoId() != null) {
            doc.append("loteExclusaoId", c.loteExclusaoId());
        }
        return doc;
    }

    public Caderno paraEntidade(Document doc) {
        if (doc == null) return null;
        return new Caderno(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getString("titulo"),
                doc.getString("cor"),
                doc.getString("icone"),
                doc.getObjectId("origemTrilhaId"),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
