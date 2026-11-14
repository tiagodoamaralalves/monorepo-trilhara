package br.com.trilhara.tarefa;

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
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SerieRecorrenteRepository {

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_ORIGEM_OPERACAO_ID = "origemOperacaoId";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_ATIVA = "ativa";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_LISTA_ID = "listaId";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_TITULO = "titulo";

    private final MongoCollection<Document> collection;

    public SerieRecorrenteRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String database) {
        this.collection = client.getDatabase(database).getCollection("series_recorrentes");
    }

    public void inserir(SerieRecorrente serie) {
        Document doc = paraDocumento(serie);
        collection.insertOne(doc);
    }

    public boolean atualizar(SerieRecorrente serie) {
        Document doc = paraDocumento(serie);
        return collection.replaceOne(
                and(eq("_id", serie.id()), eq(CAMPO_USUARIO_ID, serie.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<SerieRecorrente> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<SerieRecorrente> buscarPorOrigemOperacaoId(ObjectId usuarioId, String origemOperacaoId) {
        if (origemOperacaoId == null || origemOperacaoId.isBlank()) {
            return Optional.empty();
        }
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_ORIGEM_OPERACAO_ID, origemOperacaoId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<SerieRecorrente> listarAtivas(ObjectId usuarioId) {
        List<SerieRecorrente> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_ATIVA, true)
        )).sort(Sorts.descending(CAMPO_CRIADO_EM))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    /** Séries ativas vinculadas a uma lista (impacto de exclusão). */
    public long contarAtivasPorLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_LISTA_ID, listaId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_ATIVA, true)
        ));
    }

    public List<SerieRecorrente> listarTodas(ObjectId usuarioId) {
        List<SerieRecorrente> resultado = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)))
                .sort(Sorts.descending(CAMPO_CRIADO_EM))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<SerieRecorrente> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<SerieRecorrente> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending(CAMPO_DELETED_AT))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.set(CAMPO_ATIVA, false),
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

    public long desvincularLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, listaId)),
                Updates.combine(
                        Updates.unset(CAMPO_LISTA_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null)
        )).getDeletedCount();
    }

    public long purgarExpiradas(Instant agora) {
        return collection.deleteMany(and(
                ne(CAMPO_DELETED_AT, null),
                lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).getDeletedCount();
    }

    public boolean removerDefinitivamente(ObjectId usuarioId, ObjectId id) {
        return collection.deleteOne(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).getDeletedCount() > 0;
    }

    public Document paraDocumento(SerieRecorrente s) {
        Document doc = new Document();
        colocarSePresente(doc, "_id", s.id());
        doc.put(CAMPO_USUARIO_ID, s.usuarioId());
        colocarSePresente(doc, CAMPO_LISTA_ID, s.listaId());
        doc.put(CAMPO_TITULO, s.titulo());
        colocarSePresente(doc, "descricao", s.descricao());
        doc.put("prioridade", s.prioridade().getValor());
        colocarSePresente(doc, "horario", s.horario());
        doc.put("subtarefasModelo", converterSubtarefasParaDoc(s.subtarefasModelo()));
        preencherRegraRecorrenciaDoc(doc, s);
        preencherLembreteELixeiraDoc(doc, s);
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(s.criadoEm()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(s.atualizadoEm()));
        return doc;
    }

    private List<Document> converterSubtarefasParaDoc(List<Subtarefa> subtarefas) {
        List<Document> subtarefasDocs = new ArrayList<>();
        if (subtarefas != null) {
            for (Subtarefa sub : subtarefas) {
                subtarefasDocs.add(new Document()
                        .append("id", sub.id())
                        .append(CAMPO_TITULO, sub.titulo())
                        .append("concluida", sub.concluida())
                        .append("ordem", sub.ordem()));
            }
        }
        return subtarefasDocs;
    }

    private void preencherRegraRecorrenciaDoc(Document doc, SerieRecorrente s) {
        doc.put("frequencia", s.frequencia());
        doc.put("intervalo", s.intervalo());
        doc.put("diasDaSemana", s.diasDaSemana());
        colocarSePresente(doc, "modoMensal", s.modoMensal());
        colocarSePresente(doc, "diaDoMes", s.diaDoMes());
        colocarSePresente(doc, "ordinalSemana", s.ordinalSemana());
        colocarSePresente(doc, "diaSemanaOrdinal", s.diaSemanaOrdinal());
        doc.put("tipoTermino", s.tipoTermino());
        doc.put("dataInicio", BsonDates.toIso(s.dataInicio()));
        colocarSePresente(doc, "dataFim", BsonDates.toIso(s.dataFim()));
        colocarSePresente(doc, "maxOcorrencias", s.maxOcorrencias());
        colocarSePresente(doc, "geradoAte", BsonDates.toIso(s.geradoAte()));
        doc.put("totalGeradas", s.totalGeradas());
        doc.put(CAMPO_ATIVA, s.ativa());
    }

    private void preencherLembreteELixeiraDoc(Document doc, SerieRecorrente s) {
        doc.put("modoLembrete", s.modoLembrete().name());
        colocarSePresente(doc, "lembreteMinutosAntes", s.lembreteMinutosAntes());
        colocarSePresente(doc, "fusoAgendamento", s.fusoAgendamento());
        colocarSePresente(doc, CAMPO_ORIGEM_OPERACAO_ID, s.origemOperacaoId());
        colocarSePresente(doc, CAMPO_DELETED_AT, BsonDates.toBsonDate(s.deletedAt()));
        colocarSePresente(doc, CAMPO_EXPIRES_AT, BsonDates.toBsonDate(s.expiresAt()));
        colocarSePresente(doc, CAMPO_LOTE_EXCLUSAO_ID, s.loteExclusaoId());
    }

    private static void colocarSePresente(Document doc, String chave, Object valor) {
        if (valor != null) {
            doc.put(chave, valor);
        }
    }

    public SerieRecorrente paraEntidade(Document doc) {
        List<Subtarefa> subtarefas = new ArrayList<>();
        List<Document> subsDocs = doc.getList("subtarefasModelo", Document.class);
        if (subsDocs != null) {
            for (Document subDoc : subsDocs) {
                subtarefas.add(new Subtarefa(
                        subDoc.getString("id"),
                        subDoc.getString(CAMPO_TITULO),
                        Boolean.TRUE.equals(subDoc.getBoolean("concluida")),
                        subDoc.getInteger("ordem", 0)
                ));
            }
        }

        List<Integer> dias = doc.getList("diasDaSemana", Integer.class);

        return new SerieRecorrente(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_LISTA_ID),
                doc.getString(CAMPO_TITULO),
                doc.getString("descricao"),
                PrioridadeTarefa.fromString(doc.getString("prioridade")),
                doc.getString("horario"),
                subtarefas,
                doc.getString("frequencia"),
                doc.getInteger("intervalo", 1),
                dias != null ? dias : List.of(),
                doc.getString("modoMensal"),
                doc.getInteger("diaDoMes"),
                doc.getInteger("ordinalSemana"),
                doc.getInteger("diaSemanaOrdinal"),
                doc.getString("tipoTermino"),
                BsonDates.lerLocalDate(doc, "dataInicio"),
                BsonDates.lerLocalDate(doc, "dataFim"),
                doc.getInteger("maxOcorrencias"),
                BsonDates.lerLocalDate(doc, "geradoAte"),
                doc.getInteger("totalGeradas", 0),
                Boolean.TRUE.equals(doc.getBoolean(CAMPO_ATIVA, true)),
                ModoLembrete.fromString(doc.getString("modoLembrete")),
                doc.getInteger("lembreteMinutosAntes"),
                doc.getString("fusoAgendamento"),
                doc.getString(CAMPO_ORIGEM_OPERACAO_ID),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
