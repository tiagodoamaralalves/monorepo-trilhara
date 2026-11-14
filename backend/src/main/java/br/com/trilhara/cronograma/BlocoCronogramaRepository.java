package br.com.trilhara.cronograma;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.or;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlocoCronogramaRepository {

    /** norma de paginação — máximo de 100 itens por página. */
    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_CRONOGRAMA_ID = "cronogramaId";
    private static final String CAMPO_VERSAO_GERACAO = "versaoGeracao";
    private static final String CAMPO_CONCLUIDO = "concluido";
    private static final String CAMPO_CONCLUIDO_EM = "concluidoEm";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_DISCIPLINA_ID = "disciplinaId";
    private static final String CAMPO_DISCIPLINA_NOME_SNAPSHOT = "disciplinaNomeSnapshot";
    private static final String CAMPO_DISCIPLINA_COR_SNAPSHOT = "disciplinaCorSnapshot";
    private static final String CAMPO_DATA = "data";
    private static final String CAMPO_CRIADO_EM = "criadoEm";

    public record FiltroPaginacaoBlocos(
            ObjectId trilhaId,
            ObjectId cronogramaId,
            long versaoGeracao,
            Boolean concluido,
            String statusData,
            LocalDate hojeLocal,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho) {
    }

    private final MongoCollection<Document> collection;

    public BlocoCronogramaRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("blocos_cronograma");
    }

    public void inserirLote(List<BlocoCronograma> blocos) {
        if (blocos == null || blocos.isEmpty()) {
            return;
        }
        int batchSize = 500;
        List<Document> docs = new ArrayList<>(batchSize);
        for (BlocoCronograma b : blocos) {
            docs.add(paraDocumento(b));
            if (docs.size() == batchSize) {
                collection.insertMany(docs);
                docs.clear();
            }
        }
        if (!docs.isEmpty()) {
            collection.insertMany(docs);
        }
    }

    public long contarPorVersao(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoGeracao) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_CRONOGRAMA_ID, cronogramaId),
                eq(CAMPO_VERSAO_GERACAO, versaoGeracao)
        ));
    }

    public long excluirVersoesDiferentes(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoManter) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_CRONOGRAMA_ID, cronogramaId),
                ne(CAMPO_VERSAO_GERACAO, versaoManter)
        )).getDeletedCount();
    }

    public long excluirPorCronograma(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_CRONOGRAMA_ID, cronogramaId)
        )).getDeletedCount();
    }

    public long excluirPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId)
        )).getDeletedCount();
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq(CAMPO_USUARIO_ID, usuarioId)).getDeletedCount();
    }

    public Optional<BlocoCronograma> buscarPorId(ObjectId usuarioId, ObjectId blocoId) {
        Document doc = collection.find(and(
                eq("_id", blocoId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public boolean atualizarConclusao(ObjectId usuarioId, ObjectId blocoId, boolean concluido, Instant concluidoEm) {
        Bson atualizacao = Updates.combine(
                Updates.set(CAMPO_CONCLUIDO, concluido),
                Updates.set(CAMPO_CONCLUIDO_EM, concluidoEm != null ? BsonDates.toBsonDate(concluidoEm) : null),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
        );
        return collection.updateOne(
                and(eq("_id", blocoId), eq(CAMPO_USUARIO_ID, usuarioId)),
                atualizacao
        ).getMatchedCount() > 0;
    }

    /**
 * Conclusão condicional usada pelo lote de atrasados: o filtro exige
 * {@code concluido: false}, de modo que uma repetição/retry nunca reescreve
 * {@code concluidoEm} de um bloco já concluído.
 *
 * @return {@code true} apenas quando este chamador efetivamente concluiu o bloco
 */
    public boolean concluirSePendente(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, ObjectId blocoId, Instant agora) {
        return collection.updateOne(
                and(
                        eq("_id", blocoId),
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_TRILHA_ID, trilhaId),
                        eq(CAMPO_CRONOGRAMA_ID, cronogramaId),
                        eq(CAMPO_CONCLUIDO, false)
                ),
                Updates.combine(
                        Updates.set(CAMPO_CONCLUIDO, true),
                        Updates.set(CAMPO_CONCLUIDO_EM, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public List<BlocoCronograma> listarHoje(ObjectId usuarioId, LocalDate hojeLocal, List<ObjectId> cronogramasAtivosIds) {
        if (cronogramasAtivosIds == null || cronogramasAtivosIds.isEmpty()) {
            return List.of();
        }
        Bson filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DATA, hojeLocal.toString()),
                eq(CAMPO_CONCLUIDO, false),
                in(CAMPO_CRONOGRAMA_ID, cronogramasAtivosIds)
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        // Note: Sort by ordem ASC, _id ASC (trilhaNome is sorted in service layer after joining trilhas)
        for (Document doc : collection.find(filtro).sort(Sorts.ascending(CAMPO_ORDEM, "_id"))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    /**
 * Blocos pendentes de hoje limitados ao par {@code cronogramaId + versaoAtiva} publicado no
 * ponteiro da trilha. Staging órfão de uma geração que perdeu o lease nunca aparece.
 */
    public List<BlocoCronograma> listarHoje(
            ObjectId usuarioId, LocalDate hojeLocal, Map<ObjectId, Long> versoesAtivasPorCronograma) {
        if (versoesAtivasPorCronograma == null || versoesAtivasPorCronograma.isEmpty()) {
            return List.of();
        }
        List<Bson> alvos = new ArrayList<>(versoesAtivasPorCronograma.size());
        versoesAtivasPorCronograma.forEach((cronogramaId, versao) -> alvos.add(and(
                eq(CAMPO_CRONOGRAMA_ID, cronogramaId),
                eq(CAMPO_VERSAO_GERACAO, versao != null ? versao : 1L))));

        Bson filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DATA, hojeLocal.toString()),
                eq(CAMPO_CONCLUIDO, false),
                or(alvos)
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(filtro).sort(Sorts.ascending(CAMPO_ORDEM, "_id"))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    public ResultadoPaginado<BlocoCronograma> listarPaginado(
            ObjectId usuarioId,
            FiltroPaginacaoBlocos filtro) {

        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_USUARIO_ID, usuarioId));
        filtros.add(eq(CAMPO_TRILHA_ID, filtro.trilhaId()));
        filtros.add(eq(CAMPO_CRONOGRAMA_ID, filtro.cronogramaId()));
        filtros.add(eq(CAMPO_VERSAO_GERACAO, filtro.versaoGeracao()));

        if (filtro.concluido() != null) {
            filtros.add(eq(CAMPO_CONCLUIDO, filtro.concluido()));
        }
        // statusData é derivado da relação com hojeLocal. "ATRASADO" exclui hoje por
        // definição, o que torna a contagem exata do lote (totalItens com tamanho=1) suficiente.
        if (filtro.statusData() != null && filtro.hojeLocal() != null) {
            switch (filtro.statusData()) {
                case "ATRASADO" -> filtros.add(lt(CAMPO_DATA, filtro.hojeLocal().toString()));
                case "HOJE" -> filtros.add(eq(CAMPO_DATA, filtro.hojeLocal().toString()));
                case "FUTURO" -> filtros.add(gt(CAMPO_DATA, filtro.hojeLocal().toString()));
                default -> {
                    // valor já validado na camada de serviço
                }
            }
        }
        if (filtro.dataInicio() != null) {
            filtros.add(gte(CAMPO_DATA, filtro.dataInicio().toString()));
        }
        if (filtro.dataFim() != null) {
            filtros.add(lte(CAMPO_DATA, filtro.dataFim().toString()));
        }

        Bson filtroComposto = and(filtros);
        long total = collection.countDocuments(filtroComposto);

        int p = Math.max(1, filtro.pagina());
        int t = Math.clamp(filtro.tamanho(), 1, TAMANHO_MAXIMO_PAGINA);
        int skip = (p - 1) * t;

        List<BlocoCronograma> itens = new ArrayList<>();
        for (Document doc : collection.find(filtroComposto)
                .sort(Sorts.ascending(CAMPO_DATA, CAMPO_ORDEM, "_id"))
                .skip(skip)
                .limit(t)) {
            itens.add(paraEntidade(doc));
        }

        return new ResultadoPaginado<>(itens, p, t, total);
    }

    public List<BlocoCronograma> listarAtrasadosPendentes(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoGeracao, LocalDate dataHoje) {
        Bson filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_CRONOGRAMA_ID, cronogramaId),
                eq(CAMPO_VERSAO_GERACAO, versaoGeracao),
                eq(CAMPO_CONCLUIDO, false),
                lt(CAMPO_DATA, dataHoje.toString())
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(filtro).sort(Sorts.ascending(CAMPO_DATA, CAMPO_ORDEM, "_id"))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    /**
 * Atualiza os snapshots de nome/cor de uma disciplina <strong>apenas</strong> nos blocos dos
 * cronogramas ativos da trilha (regra normativa §5.3). {@code disciplinaId} nunca é alterado e
 * blocos passados/concluídos de cronogramas inativos permanecem como histórico visual.
 */
    public long atualizarSnapshotsDisciplina(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId disciplinaId,
            String novoNome,
            String novaCor,
            List<ObjectId> cronogramasAtivosIds) {
        if (cronogramasAtivosIds == null || cronogramasAtivosIds.isEmpty()) {
            return 0L;
        }
        return collection.updateMany(
                and(
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_TRILHA_ID, trilhaId),
                        eq(CAMPO_DISCIPLINA_ID, disciplinaId),
                        in(CAMPO_CRONOGRAMA_ID, cronogramasAtivosIds)
                ),
                Updates.combine(
                        Updates.set(CAMPO_DISCIPLINA_NOME_SNAPSHOT, novoNome),
                        Updates.set(CAMPO_DISCIPLINA_COR_SNAPSHOT, novaCor),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long excluirBlocosFuturosPendentesDisciplina(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, LocalDate dataHoje) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DISCIPLINA_ID, disciplinaId),
                eq(CAMPO_CONCLUIDO, false),
                gte(CAMPO_DATA, dataHoje.toString())
        )).getDeletedCount();
    }

    public List<BlocoCronograma> listarFuturosPendentesAtivos(
            ObjectId usuarioId, LocalDate dataHoje, List<ObjectId> cronogramasAtivosIds) {
        if (cronogramasAtivosIds == null || cronogramasAtivosIds.isEmpty()) {
            return List.of();
        }
        Bson filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CONCLUIDO, false),
                gte(CAMPO_DATA, dataHoje.toString()),
                in(CAMPO_CRONOGRAMA_ID, cronogramasAtivosIds)
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(filtro)) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    private Document paraDocumento(BlocoCronograma b) {
        Document doc = new Document();
        if (b.id() != null) {
            doc.put("_id", b.id());
        }
        doc.put(CAMPO_USUARIO_ID, b.usuarioId());
        doc.put(CAMPO_TRILHA_ID, b.trilhaId());
        doc.put(CAMPO_CRONOGRAMA_ID, b.cronogramaId());
        doc.put(CAMPO_VERSAO_GERACAO, b.versaoGeracao());
        doc.put(CAMPO_DATA, b.data() != null ? b.data().toString() : null);
        doc.put(CAMPO_ORDEM, b.ordem());
        doc.put(CAMPO_DISCIPLINA_ID, b.disciplinaId());
        doc.put(CAMPO_DISCIPLINA_NOME_SNAPSHOT, b.disciplinaNomeSnapshot());
        doc.put(CAMPO_DISCIPLINA_COR_SNAPSHOT, b.disciplinaCorSnapshot());
        doc.put("duracaoMinutos", b.duracaoMinutos());
        doc.put(CAMPO_CONCLUIDO, b.concluido());
        if (b.concluidoEm() != null) {
            doc.put(CAMPO_CONCLUIDO_EM, BsonDates.toBsonDate(b.concluidoEm()));
        }
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(b.criadoEm() != null ? b.criadoEm() : Instant.now()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(b.atualizadoEm() != null ? b.atualizadoEm() : Instant.now()));
        return doc;
    }

    private BlocoCronograma paraEntidade(Document doc) {
        return new BlocoCronograma(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_TRILHA_ID),
                doc.getObjectId(CAMPO_CRONOGRAMA_ID),
                doc.getLong(CAMPO_VERSAO_GERACAO) != null ? doc.getLong(CAMPO_VERSAO_GERACAO) : 1L,
                BsonDates.toLocalDate(doc.getString(CAMPO_DATA)),
                doc.getInteger(CAMPO_ORDEM, 1),
                doc.getObjectId(CAMPO_DISCIPLINA_ID),
                doc.getString(CAMPO_DISCIPLINA_NOME_SNAPSHOT),
                doc.getString(CAMPO_DISCIPLINA_COR_SNAPSHOT),
                doc.getInteger("duracaoMinutos", 60),
                doc.getBoolean(CAMPO_CONCLUIDO, false),
                BsonDates.lerInstant(doc, CAMPO_CONCLUIDO_EM),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }

    public record ResultadoPaginado<T>(List<T> itens, int pagina, int tamanho, long total) {
        public int totalPaginas() {
            if (total == 0) return 1;
            return (int) Math.ceil((double) total / tamanho);
        }
    }
}
