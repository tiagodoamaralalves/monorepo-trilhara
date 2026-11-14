package br.com.trilhara.trilha;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
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
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório direto BSON para a coleção normalizada 'disciplinas'.
 */
@ApplicationScoped
public class DisciplinaRepository {

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_TOPICOS = "topicos";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";

    private final MongoCollection<Document> collection;

    public DisciplinaRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String database) {
        this.collection = client.getDatabase(database).getCollection("disciplinas");
    }

    public void inserir(Disciplina disciplina) {
        collection.insertOne(paraDocumento(disciplina));
    }

    public void inserirEmLote(List<Disciplina> disciplinas) {
        if (disciplinas == null || disciplinas.isEmpty()) {
            return;
        }
        List<Document> docs = disciplinas.stream().map(this::paraDocumento).toList();
        collection.insertMany(docs);
    }

    public List<Disciplina> listarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        List<Disciplina> lista = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)))
                .sort(Sorts.ascending(CAMPO_ORDEM))
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public Optional<Disciplina> buscarPorId(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId) {
        Document doc = collection.find(and(
                eq("_id", disciplinaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Disciplina> buscarPorTopicoId(ObjectId usuarioId, ObjectId trilhaId, String topicoId) {
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                elemMatch(CAMPO_TOPICOS, eq("id", topicoId)))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public long contarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.countDocuments(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)));
    }

    public boolean atualizar(Disciplina disciplina) {
        List<Document> topicosDoc = new ArrayList<>();
        for (TopicoEmbutido topico : disciplina.topicos()) {
            topicosDoc.add(new Document("id", topico.id())
                    .append("nome", topico.nome())
                    .append(CAMPO_ORDEM, topico.ordem()));
        }

        Document setDoc = new Document("nome", disciplina.nome())
                .append("cor", disciplina.cor())
                .append("peso", disciplina.peso())
                .append(CAMPO_ORDEM, disciplina.ordem())
                .append(CAMPO_TOPICOS, topicosDoc)
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(disciplina.atualizadoEm() != null ? disciplina.atualizadoEm() : Instant.now()));

        return collection.updateOne(
                and(eq("_id", disciplina.id()), eq(CAMPO_USUARIO_ID, disciplina.usuarioId()), eq(CAMPO_TRILHA_ID, disciplina.trilhaId())),
                new Document("$set", setDoc)).getMatchedCount() > 0;
    }

    public boolean atualizarOrdem(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, int novaOrdem) {
        return collection.updateOne(
                and(eq("_id", disciplinaId), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)),
                Updates.combine(
                        Updates.set(CAMPO_ORDEM, novaOrdem),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )).getMatchedCount() > 0;
    }

    public boolean excluir(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId) {
        return collection.deleteOne(and(
                eq("_id", disciplinaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId))).getDeletedCount() > 0;
    }

    public long excluirTodasDaTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId))).getDeletedCount();
    }

    private Document paraDocumento(Disciplina disciplina) {
        List<Document> topicosDoc = new ArrayList<>();
        for (TopicoEmbutido topico : disciplina.topicos()) {
            topicosDoc.add(new Document("id", topico.id())
                    .append("nome", topico.nome())
                    .append(CAMPO_ORDEM, topico.ordem()));
        }
        return new Document("_id", disciplina.id())
                .append(CAMPO_USUARIO_ID, disciplina.usuarioId())
                .append(CAMPO_TRILHA_ID, disciplina.trilhaId())
                .append("nome", disciplina.nome())
                .append("cor", disciplina.cor())
                .append("peso", disciplina.peso())
                .append(CAMPO_ORDEM, disciplina.ordem())
                .append(CAMPO_TOPICOS, topicosDoc)
                .append("criadoEm", BsonDates.toBsonDate(disciplina.criadoEm() != null ? disciplina.criadoEm() : Instant.now()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(disciplina.atualizadoEm() != null ? disciplina.atualizadoEm() : Instant.now()));
    }

    private Disciplina paraEntidade(Document doc) {
        ObjectId id = doc.getObjectId("_id");
        ObjectId usuarioId = doc.getObjectId(CAMPO_USUARIO_ID);
        ObjectId trilhaId = doc.getObjectId(CAMPO_TRILHA_ID);
        String nome = doc.getString("nome");
        String cor = doc.getString("cor");
        Integer peso = doc.getInteger("peso", 1);
        Integer ordem = doc.getInteger(CAMPO_ORDEM, 0);

        List<TopicoEmbutido> topicos = new ArrayList<>();
        List<Document> topicosDoc = doc.getList(CAMPO_TOPICOS, Document.class);
        if (topicosDoc != null) {
            for (Document tDoc : topicosDoc) {
                topicos.add(new TopicoEmbutido(
                        tDoc.getString("id"),
                        tDoc.getString("nome"),
                        tDoc.getInteger(CAMPO_ORDEM, 0)));
            }
        }
        Instant criadoEm = BsonDates.lerInstant(doc, "criadoEm");
        Instant atualizadoEm = BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM);

        return new Disciplina(id, usuarioId, trilhaId, nome, cor, peso, ordem, topicos, criadoEm, atualizadoEm);
    }
}
