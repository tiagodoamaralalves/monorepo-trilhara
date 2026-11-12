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
 * Atende REQUISITOS-REESTRUTURACAO-DADOS.md §4.1 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.2.
 */
@ApplicationScoped
public class DisciplinaRepository {

    private final MongoCollection<Document> collection;

    public DisciplinaRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
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
        collection.find(and(eq("usuarioId", usuarioId), eq("trilhaId", trilhaId)))
                .sort(Sorts.ascending("ordem"))
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public Optional<Disciplina> buscarPorId(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId) {
        Document doc = collection.find(and(
                eq("_id", disciplinaId),
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Disciplina> buscarPorTopicoId(ObjectId usuarioId, ObjectId trilhaId, String topicoId) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                elemMatch("topicos", eq("id", topicoId)))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public long contarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.countDocuments(and(eq("usuarioId", usuarioId), eq("trilhaId", trilhaId)));
    }

    public boolean atualizar(Disciplina disciplina) {
        List<Document> topicosDoc = new ArrayList<>();
        for (TopicoEmbutido topico : disciplina.topicos()) {
            topicosDoc.add(new Document("id", topico.id())
                    .append("nome", topico.nome())
                    .append("ordem", topico.ordem()));
        }

        Document setDoc = new Document("nome", disciplina.nome())
                .append("cor", disciplina.cor())
                .append("peso", disciplina.peso())
                .append("ordem", disciplina.ordem())
                .append("topicos", topicosDoc)
                .append("atualizadoEm", BsonDates.toBsonDate(disciplina.atualizadoEm() != null ? disciplina.atualizadoEm() : Instant.now()));

        return collection.updateOne(
                and(eq("_id", disciplina.id()), eq("usuarioId", disciplina.usuarioId()), eq("trilhaId", disciplina.trilhaId())),
                new Document("$set", setDoc)).getMatchedCount() > 0;
    }

    public boolean atualizarOrdem(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, int novaOrdem) {
        return collection.updateOne(
                and(eq("_id", disciplinaId), eq("usuarioId", usuarioId), eq("trilhaId", trilhaId)),
                Updates.combine(
                        Updates.set("ordem", novaOrdem),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )).getMatchedCount() > 0;
    }

    public boolean excluir(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId) {
        return collection.deleteOne(and(
                eq("_id", disciplinaId),
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId))).getDeletedCount() > 0;
    }

    public long excluirTodasDaTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(eq("usuarioId", usuarioId), eq("trilhaId", trilhaId))).getDeletedCount();
    }

    private Document paraDocumento(Disciplina disciplina) {
        List<Document> topicosDoc = new ArrayList<>();
        for (TopicoEmbutido topico : disciplina.topicos()) {
            topicosDoc.add(new Document("id", topico.id())
                    .append("nome", topico.nome())
                    .append("ordem", topico.ordem()));
        }
        return new Document("_id", disciplina.id())
                .append("usuarioId", disciplina.usuarioId())
                .append("trilhaId", disciplina.trilhaId())
                .append("nome", disciplina.nome())
                .append("cor", disciplina.cor())
                .append("peso", disciplina.peso())
                .append("ordem", disciplina.ordem())
                .append("topicos", topicosDoc)
                .append("criadoEm", BsonDates.toBsonDate(disciplina.criadoEm() != null ? disciplina.criadoEm() : Instant.now()))
                .append("atualizadoEm", BsonDates.toBsonDate(disciplina.atualizadoEm() != null ? disciplina.atualizadoEm() : Instant.now()));
    }

    private Disciplina paraEntidade(Document doc) {
        ObjectId id = doc.getObjectId("_id");
        ObjectId usuarioId = doc.getObjectId("usuarioId");
        ObjectId trilhaId = doc.getObjectId("trilhaId");
        String nome = doc.getString("nome");
        String cor = doc.getString("cor");
        Integer peso = doc.getInteger("peso", 1);
        Integer ordem = doc.getInteger("ordem", 0);

        List<TopicoEmbutido> topicos = new ArrayList<>();
        List<Document> topicosDoc = doc.getList("topicos", Document.class);
        if (topicosDoc != null) {
            for (Document tDoc : topicosDoc) {
                topicos.add(new TopicoEmbutido(
                        tDoc.getString("id"),
                        tDoc.getString("nome"),
                        tDoc.getInteger("ordem", 0)));
            }
        }
        Instant criadoEm = BsonDates.lerInstant(doc, "criadoEm");
        Instant atualizadoEm = BsonDates.lerInstant(doc, "atualizadoEm");

        return new Disciplina(id, usuarioId, trilhaId, nome, cor, peso, ordem, topicos, criadoEm, atualizadoEm);
    }
}
