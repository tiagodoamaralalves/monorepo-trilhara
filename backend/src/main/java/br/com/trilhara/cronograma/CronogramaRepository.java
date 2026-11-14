package br.com.trilhara.cronograma;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class CronogramaRepository {

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_ESTADO_PUBLICACAO = "estadoPublicacao";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_ATIVO = "ativo";
    private static final String CAMPO_MODALIDADE = "modalidade";
    private static final String CAMPO_VERSAO_ATIVA = "versaoAtiva";
    private static final String CAMPO_VERSAO_PREPARADA = "versaoPreparada";
    private static final String CAMPO_CRIADO_EM = "criadoEm";

    private final MongoCollection<Document> collection;

    public CronogramaRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("cronogramas");
    }

    public void inserir(Cronograma cronograma) {
        Document doc = paraDocumento(cronograma);
        collection.insertOne(doc);
    }

    public boolean atualizar(Cronograma cronograma) {
        Document doc = paraDocumento(cronograma);
        return collection.replaceOne(
                and(eq("_id", cronograma.id()), eq(CAMPO_USUARIO_ID, cronograma.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Cronograma> buscarPorId(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        Document doc = collection.find(and(
                eq("_id", cronogramaId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Cronograma> buscarPorId(ObjectId usuarioId, ObjectId cronogramaId) {
        Document doc = collection.find(and(
                eq("_id", cronogramaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    /** Lista cronogramas em um estado de publicação em qualquer tenant (reconciliador, ). */
    public List<Cronograma> listarPorEstadoPublicacao(String estadoPublicacao) {
        List<Cronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(eq(CAMPO_ESTADO_PUBLICACAO, estadoPublicacao))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    /** Atualiza somente o estado de publicação, preservando o restante do documento. */
    public boolean atualizarEstadoPublicacao(
            ObjectId usuarioId, ObjectId cronogramaId, String estadoPublicacao) {
        return collection.updateOne(
                and(eq("_id", cronogramaId), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.set(CAMPO_ESTADO_PUBLICACAO, estadoPublicacao),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now())))
        ).getMatchedCount() > 0;
    }

    public Optional<Cronograma> buscarAtivoPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        Document doc = collection.find(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_ATIVO, true)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Cronograma> buscarPorModalidade(ObjectId usuarioId, ObjectId trilhaId, ModalidadeCronograma modalidade) {
        Document doc = collection.find(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_MODALIDADE, modalidade.name())
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Cronograma> buscarPorModalidadeExceto(
            ObjectId usuarioId, ObjectId trilhaId, ModalidadeCronograma modalidade, ObjectId excetoCronogramaId) {
        Document doc = collection.find(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_MODALIDADE, modalidade.name()),
                ne("_id", excetoCronogramaId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Cronograma> listarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        List<Cronograma> lista = new ArrayList<>();
        for (Document doc : collection.find(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).sort(Sorts.ascending(CAMPO_MODALIDADE, "_id"))) {
            lista.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(lista);
    }

    public Optional<Cronograma> buscarMaisRecenteRestante(ObjectId usuarioId, ObjectId trilhaId) {
        Document doc = collection.find(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).sort(Sorts.descending(CAMPO_ATUALIZADO_EM, "_id")).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public long desativarOutros(ObjectId usuarioId, ObjectId trilhaId, ObjectId excetoCronogramaId) {
        return collection.updateMany(
                and(
                        eq(CAMPO_TRILHA_ID, trilhaId),
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        ne("_id", excetoCronogramaId),
                        eq(CAMPO_ATIVO, true)
                ),
                Updates.combine(
                        Updates.set(CAMPO_ATIVO, false),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public boolean excluir(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        return collection.deleteOne(and(
                eq("_id", cronogramaId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).getDeletedCount() > 0;
    }

    public long excluirPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).getDeletedCount();
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq(CAMPO_USUARIO_ID, usuarioId)).getDeletedCount();
    }

    public long contarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.countDocuments(and(
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        ));
    }

    private Document paraDocumento(Cronograma c) {
        Document doc = new Document();
        if (c.id() != null) {
            doc.put("_id", c.id());
        }
        doc.put(CAMPO_USUARIO_ID, c.usuarioId());
        doc.put(CAMPO_TRILHA_ID, c.trilhaId());
        doc.put(CAMPO_MODALIDADE, c.modalidade().name());
        doc.put(CAMPO_ATIVO, c.ativo());
        doc.put(CAMPO_VERSAO_ATIVA, c.versaoAtiva());
        if (c.versaoPreparada() != null) {
            doc.put(CAMPO_VERSAO_PREPARADA, c.versaoPreparada());
        }
        doc.put(CAMPO_ESTADO_PUBLICACAO, c.estadoPublicacao());
        doc.put("duracaoBlocoMinutos", c.duracaoBlocoMinutos());
        doc.put("tipoPeriodo", c.tipoPeriodo());
        doc.put("dataInicio", c.dataInicio() != null ? c.dataInicio().toString() : null);
        doc.put("dataFim", c.dataFim() != null ? c.dataFim().toString() : null);
        doc.put("diasAtivosSemana", c.diasAtivosSemana());
        if (c.blocosPorDia() != null) {
            doc.put("blocosPorDia", c.blocosPorDia());
        }
        doc.put("disciplinasParticipantesIds", c.disciplinasParticipantesIds());

        if (c.semanaModeloManual() != null && !c.semanaModeloManual().isEmpty()) {
            Document manualDoc = new Document();
            c.semanaModeloManual().forEach((dia, ids) -> manualDoc.put(String.valueOf(dia), ids));
            doc.put("semanaModeloManual", manualDoc);
        }

        if (c.configuracaoAtiva() != null) {
            doc.put("configuracaoAtiva", c.configuracaoAtiva());
        }
        if (c.configuracaoPreparada() != null) {
            doc.put("configuracaoPreparada", c.configuracaoPreparada());
        }

        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(c.criadoEm() != null ? c.criadoEm() : Instant.now()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(c.atualizadoEm() != null ? c.atualizadoEm() : Instant.now()));
        return doc;
    }

    private Cronograma paraEntidade(Document doc) {
        ObjectId id = doc.getObjectId("_id");
        ObjectId usuarioId = doc.getObjectId(CAMPO_USUARIO_ID);
        ObjectId trilhaId = doc.getObjectId(CAMPO_TRILHA_ID);
        ModalidadeCronograma modalidade = ModalidadeCronograma.valueOf(doc.getString(CAMPO_MODALIDADE));
        boolean ativo = doc.getBoolean(CAMPO_ATIVO, false);
        long versaoAtiva = doc.getLong(CAMPO_VERSAO_ATIVA) != null ? doc.getLong(CAMPO_VERSAO_ATIVA) : 1L;
        Long versaoPreparada = doc.getLong(CAMPO_VERSAO_PREPARADA);
        String estadoPublicacao = doc.getString(CAMPO_ESTADO_PUBLICACAO);
        int duracaoBlocoMinutos = doc.getInteger("duracaoBlocoMinutos", 60);
        String tipoPeriodo = doc.getString("tipoPeriodo");
        LocalDate dataInicio = BsonDates.toLocalDate(doc.getString("dataInicio"));
        LocalDate dataFim = BsonDates.toLocalDate(doc.getString("dataFim"));
        List<Integer> diasAtivosSemana = doc.getList("diasAtivosSemana", Integer.class);
        Integer blocosPorDia = doc.getInteger("blocosPorDia");
        List<ObjectId> disciplinasParticipantesIds = doc.getList("disciplinasParticipantesIds", ObjectId.class);

        Map<Integer, List<String>> semanaModeloManual = new HashMap<>();
        Document manualDoc = doc.get("semanaModeloManual", Document.class);
        if (manualDoc != null) {
            for (String key : manualDoc.keySet()) {
                try {
                    int dia = Integer.parseInt(key);
                    List<String> ids = manualDoc.getList(key, String.class);
                    if (ids != null) {
                        semanaModeloManual.put(dia, ids);
                    }
                } catch (NumberFormatException _) {
                    // Ignora chaves não numéricas
                }
            }
        }

        Document configuracaoAtiva = doc.get("configuracaoAtiva", Document.class);
        Document configuracaoPreparada = doc.get("configuracaoPreparada", Document.class);
        Instant criadoEm = BsonDates.lerInstant(doc, CAMPO_CRIADO_EM);
        Instant atualizadoEm = BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM);

        return new Cronograma(
                id,
                usuarioId,
                trilhaId,
                modalidade,
                ativo,
                versaoAtiva,
                versaoPreparada,
                estadoPublicacao,
                duracaoBlocoMinutos,
                tipoPeriodo,
                dataInicio,
                dataFim,
                diasAtivosSemana,
                blocosPorDia,
                disciplinasParticipantesIds,
                semanaModeloManual,
                configuracaoAtiva,
                configuracaoPreparada,
                criadoEm,
                atualizadoEm
        );
    }
}
