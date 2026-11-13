package br.com.trilhara.trilha;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Repositório direto BSON para a coleção normalizada 'trilhas'.
 * Atende REQUISITOS-REESTRUTURACAO-DADOS.md §4.1 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.2.
 */
@ApplicationScoped
public class TrilhaRepository {

    public record DadosInsercaoTrilha(
            ObjectId usuarioId,
            ObjectId trilhaId,
            String nome,
            String cargo,
            String banca,
            LocalDate dataProva,
            String cor,
            ObjectId origemEditalId,
            long origemVersao,
            String informacoesComplementares,
            Instant agora) {
    }

    public record MetadadosTrilha(
            String nome,
            String cargo,
            String banca,
            LocalDate dataProva,
            String cor,
            Instant agora) {
    }

    private static final String CAMPO_AREA_CARGO = "areaCargo";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_BANCA = "banca";
    private static final String CAMPO_CARGO = "cargo";
    private static final String CAMPO_CRONOGRAMA_ATIVO_ID = "cronogramaAtivoId";
    private static final String CAMPO_DATA_PROVA = "dataProva";
    private static final String CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE = "geracaoCronogramaEmAndamentoAte";
    private static final String CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN = "geracaoCronogramaLeaseToken";
    private static final String CAMPO_ORIGEM_DISCIPLINA_ID = "origemDisciplinaId";
    private static final String CAMPO_ORIGEM_TRILHA_ID = "origemTrilhaId";
    private static final String CAMPO_PROXIMA_VERSAO_CRONOGRAMA = "proximaVersaoCronograma";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_USER_ID = "userId";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_VERSAO_ATIVA_CRONOGRAMA = "versaoAtivaCronograma";

    private final MongoClient mongoClient;
    private final String databaseName;
    private final MongoCollection<Document> trilhas;

    @Inject
    public TrilhaRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.mongoClient = client;
        this.databaseName = database;
        this.trilhas = client.getDatabase(database).getCollection("trilhas");
    }

    public void inserir(DadosInsercaoTrilha dados) {
        Instant agora = dados.agora() != null ? dados.agora() : Instant.now();
        Document doc = new Document("_id", dados.trilhaId())
                .append(CAMPO_USUARIO_ID, dados.usuarioId())
                .append("nome", dados.nome())
                .append(CAMPO_CARGO, dados.cargo())
                .append(CAMPO_AREA_CARGO, dados.cargo())
                .append(CAMPO_BANCA, dados.banca())
                .append(CAMPO_DATA_PROVA, BsonDates.toIso(dados.dataProva()))
                .append("cor", dados.cor())
                .append("origemEditalId", dados.origemEditalId())
                .append("origemVersao", dados.origemVersao())
                .append(CAMPO_CRONOGRAMA_ATIVO_ID, null)
                .append(CAMPO_PROXIMA_VERSAO_CRONOGRAMA, 0L)
                .append("informacoesComplementares", dados.informacoesComplementares())
                .append(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, null)
                .append(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, null)
                .append("criadoEm", BsonDates.toBsonDate(agora))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora));
        trilhas.insertOne(doc);
    }

    public Optional<Document> buscarEntidade(ObjectId usuarioId, ObjectId trilhaId) {
        Document doc = trilhas.find(and(eq("_id", trilhaId), filtroUsuario(usuarioId))).first();
        return Optional.ofNullable(doc);
    }

    public List<Document> listarEntidades(ObjectId usuarioId) {
        List<Document> resultado = new ArrayList<>();
        trilhas.find(filtroUsuario(usuarioId))
                .sort(Sorts.descending(CAMPO_ATUALIZADO_EM))
                .forEach(resultado::add);
        return resultado;
    }

    public boolean atualizarMetadados(ObjectId usuarioId, ObjectId trilhaId, MetadadosTrilha meta) {
        Instant agora = meta.agora() != null ? meta.agora() : Instant.now();
        Document atualizacoes = new Document("nome", meta.nome())
                .append(CAMPO_CARGO, meta.cargo())
                .append(CAMPO_AREA_CARGO, meta.cargo())
                .append(CAMPO_BANCA, meta.banca())
                .append(CAMPO_DATA_PROVA, BsonDates.toIso(meta.dataProva()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora));

        if (meta.cor() != null && !meta.cor().isBlank()) {
            atualizacoes.append("cor", meta.cor());
        }

        return trilhas.updateOne(
                and(eq("_id", trilhaId), filtroUsuario(usuarioId)),
                new Document("$set", atualizacoes)).getMatchedCount() > 0;
    }

    public long contar(ObjectId usuarioId) {
        return trilhas.countDocuments(filtroUsuario(usuarioId));
    }

    public boolean excluir(ObjectId usuarioId, ObjectId trilhaId) {
        return trilhas.deleteOne(and(eq("_id", trilhaId), filtroUsuario(usuarioId))).getDeletedCount() > 0;
    }

    public boolean adquirirLease(ObjectId usuarioId, ObjectId trilhaId, String leaseToken, Instant expiraEm) {
        Instant agora = Instant.now();
        Bson filtro = and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId),
                or(
                        eq(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, null),
                        com.mongodb.client.model.Filters.lt(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, BsonDates.toBsonDate(agora)),
                        eq(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken)
                )
        );
        Bson atualizacao = Updates.combine(
                Updates.set(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken),
                Updates.set(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, BsonDates.toBsonDate(expiraEm)),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
        );
        return trilhas.updateOne(filtro, atualizacao).getMatchedCount() > 0;
    }

    public boolean liberarLease(ObjectId usuarioId, ObjectId trilhaId, String leaseToken) {
        Bson filtro = and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId),
                eq(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken)
        );
        Bson atualizacao = Updates.combine(
                Updates.set(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, null),
                Updates.set(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, null),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
        );
        return trilhas.updateOne(filtro, atualizacao).getMatchedCount() > 0;
    }

    /**
     * Renova o lease <strong>somente</strong> se o token ainda for o dono (fencing).
     *
     * <p>Base do heartbeat de 30 s (D-18a): se a renovação retornar {@code false}, o token foi
     * perdido (lease expirado e retomado por outro worker) e a publicação precisa ser abortada
     * sem trocar o ponteiro.</p>
     */
    public boolean renovarLease(ObjectId usuarioId, ObjectId trilhaId, String leaseToken, Instant expiraEm) {
        Bson filtro = and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId),
                eq(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken)
        );
        Bson atualizacao = Updates.combine(
                Updates.set(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, BsonDates.toBsonDate(expiraEm)),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
        );
        return trilhas.updateOne(filtro, atualizacao).getMatchedCount() > 0;
    }

    /** {@code true} enquanto o token informado continuar sendo o dono do lease da trilha. */
    public boolean leaseAtivo(ObjectId usuarioId, ObjectId trilhaId, String leaseToken) {
        return trilhas.find(and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId),
                eq(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken)
        )).first() != null;
    }

    /**
     * Incrementa e devolve o contador atômico de versões do cronograma da trilha
     * ({@code trilhas.proximaVersaoCronograma}).
     *
     * <p>Garante que duas gerações concorrentes nunca reutilizem a mesma {@code versaoGeracao},
     * mesmo quando o lease é retomado após expiração. Retorna vazio quando a trilha não existe
     * para o usuário.</p>
     */
    public Optional<Long> proximaVersaoCronograma(ObjectId usuarioId, ObjectId trilhaId) {
        Document resultado = trilhas.findOneAndUpdate(
                and(eq("_id", trilhaId), filtroUsuario(usuarioId)),
                Updates.combine(
                        Updates.inc(CAMPO_PROXIMA_VERSAO_CRONOGRAMA, 1L),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))),
                new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER));
        if (resultado == null) {
            return Optional.empty();
        }
        Long valor = resultado.getLong(CAMPO_PROXIMA_VERSAO_CRONOGRAMA);
        return Optional.of(valor != null ? valor : 1L);
    }

    /**
     * Troca o ponteiro de leitura de blocos da trilha ({@code cronogramaAtivoId} +
     * {@code versaoAtivaCronograma}) em uma única operação atômica.
     *
     * <p><b>Fencing (D-18):</b> o filtro exige que o {@code leaseToken} ainda seja o dono do
     * lease. Um worker que perdeu o token não altera o ponteiro, mesmo que já tenha inserido
     * os blocos de staging.</p>
     */
    public boolean atualizarPonteiroCronogramaAtivo(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId cronogramaAtivoId,
            Long versaoAtivaCronograma,
            String leaseToken) {
        Bson filtro = and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId),
                eq(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, leaseToken)
        );
        Bson atualizacao = Updates.combine(
                cronogramaAtivoId != null ? Updates.set(CAMPO_CRONOGRAMA_ATIVO_ID, cronogramaAtivoId) : Updates.unset(CAMPO_CRONOGRAMA_ATIVO_ID),
                versaoAtivaCronograma != null ? Updates.set(CAMPO_VERSAO_ATIVA_CRONOGRAMA, versaoAtivaCronograma) : Updates.unset(CAMPO_VERSAO_ATIVA_CRONOGRAMA),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
        );
        return trilhas.updateOne(filtro, atualizacao).getMatchedCount() > 0;
    }

    /**
     * Variante sem fencing usada <strong>exclusivamente</strong> pelo reconciliador
     * ({@code @SystemScope}, D-18b): após a morte de um worker, o lease já não pertence a ninguém,
     * e a reconciliação precisa conseguir fechar a publicação.
     */
    public boolean atualizarPonteiroCronogramaAtivoSemFencing(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaAtivoId, Long versaoAtivaCronograma) {
        Bson filtro = and(eq("_id", trilhaId), filtroUsuario(usuarioId));
        Bson atualizacao = Updates.combine(
                cronogramaAtivoId != null ? Updates.set(CAMPO_CRONOGRAMA_ATIVO_ID, cronogramaAtivoId) : Updates.unset(CAMPO_CRONOGRAMA_ATIVO_ID),
                versaoAtivaCronograma != null ? Updates.set(CAMPO_VERSAO_ATIVA_CRONOGRAMA, versaoAtivaCronograma) : Updates.unset(CAMPO_VERSAO_ATIVA_CRONOGRAMA),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
        );
        return trilhas.updateOne(filtro, atualizacao).getMatchedCount() > 0;
    }

    public Optional<Document> buscarCronogramaResumo(ObjectId usuarioId, ObjectId cronogramaId) {
        var db = mongoClient.getDatabase(databaseName);
        Document cronoDoc = db.getCollection("cronogramas")
                .find(new Document("_id", cronogramaId).append(CAMPO_USUARIO_ID, usuarioId))
                .first();
        return Optional.ofNullable(cronoDoc);
    }

    public void desvincularDadosTrilhaENotas(ObjectId usuarioId, ObjectId trilhaId, List<ObjectId> discIds, List<String> topicoIds) {
        var db = mongoClient.getDatabase(databaseName);

        // 1. Cronogramas e blocos são removidos pelos próprios repositórios sob o lease da
        //    trilha (R5). Esta classe só desvincula referências de cadernos, seções e notas.

        // 2. Desvincula cadernos
        db.getCollection("cadernos").updateMany(
                and(
                        or(
                                eq(CAMPO_USUARIO_ID, usuarioId),
                                eq(CAMPO_USER_ID, usuarioId.toHexString())
                        ),
                        or(
                                eq(CAMPO_ORIGEM_TRILHA_ID, trilhaId),
                                eq(CAMPO_ORIGEM_TRILHA_ID, trilhaId.toHexString())
                        )
                ),
                Updates.unset(CAMPO_ORIGEM_TRILHA_ID));

        // 3. Desvincula seções
        if (discIds != null && !discIds.isEmpty()) {
            db.getCollection("secoes").updateMany(
                    and(
                            or(
                                    eq(CAMPO_USUARIO_ID, usuarioId),
                                    eq(CAMPO_USER_ID, usuarioId.toHexString())
                            ),
                            or(
                                    in(CAMPO_ORIGEM_DISCIPLINA_ID, discIds),
                                    eq(CAMPO_ORIGEM_TRILHA_ID, trilhaId.toHexString())
                            )
                    ),
                    Updates.combine(Updates.unset(CAMPO_ORIGEM_TRILHA_ID), Updates.unset(CAMPO_ORIGEM_DISCIPLINA_ID)));
        }

        // 4. Desvincula notas (referência de origem da importação de trilha, D-29).
        if (topicoIds != null && !topicoIds.isEmpty()) {
            db.getCollection("notas").updateMany(
                    and(
                            or(
                                    eq(CAMPO_USUARIO_ID, usuarioId),
                                    eq(CAMPO_USER_ID, usuarioId.toHexString())
                            ),
                            or(
                                    in("topicoOrigemId", topicoIds),
                                    eq(CAMPO_ORIGEM_TRILHA_ID, trilhaId.toHexString())
                            )
                    ),
                    Updates.combine(Updates.unset(CAMPO_ORIGEM_TRILHA_ID), Updates.unset(CAMPO_ORIGEM_DISCIPLINA_ID), Updates.unset("topicoOrigemId")));
        }
    }

    private Bson filtroUsuario(ObjectId usuarioId) {
        return eq(CAMPO_USUARIO_ID, usuarioId);
    }
}
