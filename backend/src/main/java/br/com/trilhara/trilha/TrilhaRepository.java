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
import br.com.trilhara.shared.mongo.BsonIds;
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
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_USER_ID = "userId";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_VERSAO_ATIVA_CRONOGRAMA = "versaoAtivaCronograma";

    private final MongoClient mongoClient;
    private final String databaseName;
    private final MongoCollection<Document> trilhas;
    private final DisciplinaRepository disciplinaRepository;

    @Inject
    public TrilhaRepository(
            MongoClient client,
            DisciplinaRepository disciplinaRepository,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.mongoClient = client;
        this.databaseName = database;
        this.trilhas = client.getDatabase(database).getCollection("trilhas");
        this.disciplinaRepository = disciplinaRepository;
    }

    public void inserir(DadosInsercaoTrilha dados) {
        Instant agora = dados.agora() != null ? dados.agora() : Instant.now();
        Document doc = new Document("_id", dados.trilhaId())
                .append(CAMPO_USUARIO_ID, dados.usuarioId())
                .append("donoId", dados.usuarioId().toHexString()) // compatibilidade com buscas legadas
                .append("nome", dados.nome())
                .append(CAMPO_CARGO, dados.cargo())
                .append(CAMPO_AREA_CARGO, dados.cargo())
                .append(CAMPO_BANCA, dados.banca())
                .append(CAMPO_DATA_PROVA, BsonDates.toIsoString(dados.dataProva()))
                .append("cor", dados.cor())
                .append("origemEditalId", dados.origemEditalId())
                .append("origemVersao", dados.origemVersao())
                .append(CAMPO_CRONOGRAMA_ATIVO_ID, null)
                .append("informacoesComplementares", dados.informacoesComplementares())
                .append(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN, null)
                .append(CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE, null)
                .append("criadoEm", BsonDates.toBsonDate(agora))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora));
        trilhas.insertOne(doc);
    }

    public void inserir(Trilha trilha) {
        ObjectId usuarioId;
        if (BsonIds.isValid(trilha.usuarioId())) {
            usuarioId = new ObjectId(trilha.usuarioId());
        } else if (BsonIds.isValid(trilha.donoId())) {
            usuarioId = new ObjectId(trilha.donoId());
        } else {
            usuarioId = new ObjectId();
        }
        ObjectId trilhaId = BsonIds.isValid(trilha.id()) ? new ObjectId(trilha.id()) : new ObjectId();
        ObjectId origemEditalId = BsonIds.isValid(trilha.origemEditalId()) ? new ObjectId(trilha.origemEditalId()) : null;

        inserir(new DadosInsercaoTrilha(
                usuarioId,
                trilhaId,
                trilha.nome(),
                trilha.cargo() != null ? trilha.cargo() : trilha.areaCargo(),
                trilha.banca(),
                trilha.dataProva(),
                trilha.cor(),
                origemEditalId,
                trilha.origemVersao(),
                trilha.informacoesComplementares(),
                trilha.criadoEm()
        ));

        persistirDisciplinasTrilha(usuarioId, trilhaId, trilha.disciplinas(), trilha.criadoEm());
        persistirCiclosTrilha(trilhaId, trilha.ciclos(), trilha.cicloAtivoId());
    }

    private void persistirDisciplinasTrilha(ObjectId usuarioId, ObjectId trilhaId, List<Trilha.Disciplina> disciplinas, Instant criadoEm) {
        if (disciplinas == null || disciplinas.isEmpty()) {
            return;
        }
        int ordem = 0;
        List<Disciplina> normalizadas = new ArrayList<>();
        for (Trilha.Disciplina d : disciplinas) {
            ObjectId dId = BsonIds.isValid(d.id()) ? new ObjectId(d.id()) : new ObjectId();
            List<TopicoEmbutido> topicos = new ArrayList<>();
            if (d.topicos() != null) {
                int tOrdem = 0;
                for (var t : d.topicos()) {
                    String tId = (t.id() != null && BsonIds.isValid(t.id())) ? t.id() : BsonIds.novoIdHex();
                    topicos.add(new TopicoEmbutido(tId, t.nome(), tOrdem++));
                }
            }
            normalizadas.add(new Disciplina(dId, usuarioId, trilhaId, d.nome(), d.cor(), d.peso(), ordem++, d.cadernoLink(), topicos, criadoEm, criadoEm));
        }
        disciplinaRepository.inserirEmLote(normalizadas);
    }

    private void persistirCiclosTrilha(ObjectId trilhaId, List<Ciclo> ciclos, String cicloAtivoId) {
        if (ciclos == null || ciclos.isEmpty()) {
            return;
        }
        List<Document> ciclosDoc = new ArrayList<>();
        for (Ciclo c : ciclos) {
            List<Document> configsDoc = new ArrayList<>();
            if (c.disciplinasConfig() != null) {
                for (var dc : c.disciplinasConfig()) {
                    configsDoc.add(new Document("disciplinaId", dc.disciplinaId()).append("participa", dc.participa()));
                }
            }
            ciclosDoc.add(new Document("id", c.id())
                    .append("nome", c.nome())
                    .append("tipo", c.tipo())
                    .append("duracaoBlocoMin", c.duracaoBlocoMin())
                    .append("dataInicio", c.dataInicio())
                    .append("dataFim", c.dataFim())
                    .append("blocosSemana", c.blocosSemana())
                    .append("disciplinasConfig", configsDoc));
        }
        trilhas.updateOne(eq("_id", trilhaId), Updates.combine(
                Updates.set("ciclos", ciclosDoc),
                Updates.set("cicloAtivoId", cicloAtivoId)
        ));
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

    public Optional<Trilha> buscar(String usuarioIdStr, String trilhaIdStr) {
        if (!BsonIds.isValid(trilhaIdStr) || !BsonIds.isValid(usuarioIdStr)) {
            return Optional.empty();
        }
        ObjectId usuarioId = new ObjectId(usuarioIdStr);
        ObjectId trilhaId = new ObjectId(trilhaIdStr);

        return buscarEntidade(usuarioId, trilhaId).map(doc -> paraTrilhaComDisciplinas(doc, usuarioId, trilhaId));
    }

    public List<Trilha> listar(String usuarioIdStr) {
        if (!BsonIds.isValid(usuarioIdStr)) {
            return List.of();
        }
        ObjectId usuarioId = new ObjectId(usuarioIdStr);
        List<Trilha> resultado = new ArrayList<>();
        for (Document doc : listarEntidades(usuarioId)) {
            ObjectId trilhaId = doc.getObjectId("_id");
            resultado.add(paraTrilhaComDisciplinas(doc, usuarioId, trilhaId));
        }
        return resultado;
    }

    public Optional<Trilha> atualizarMetadados(ObjectId usuarioId, ObjectId trilhaId, MetadadosTrilha meta) {
        Instant agora = meta.agora() != null ? meta.agora() : Instant.now();
        Document atualizacoes = new Document("nome", meta.nome())
                .append(CAMPO_CARGO, meta.cargo())
                .append(CAMPO_AREA_CARGO, meta.cargo())
                .append(CAMPO_BANCA, meta.banca())
                .append(CAMPO_DATA_PROVA, BsonDates.toIsoString(meta.dataProva()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora));

        if (meta.cor() != null && !meta.cor().isBlank()) {
            atualizacoes.append("cor", meta.cor());
        }

        Document docAtualizado = trilhas.findOneAndUpdate(
                and(eq("_id", trilhaId), filtroUsuario(usuarioId)),
                new Document("$set", atualizacoes),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

        return Optional.ofNullable(docAtualizado).map(doc -> paraTrilhaComDisciplinas(doc, usuarioId, trilhaId));
    }

    public boolean atualizar(Trilha trilha) {
        if (!BsonIds.isValid(trilha.id()) || !BsonIds.isValid(trilha.usuarioId() != null ? trilha.usuarioId() : trilha.donoId())) {
            return false;
        }
        ObjectId usuarioId = new ObjectId(trilha.usuarioId() != null ? trilha.usuarioId() : trilha.donoId());
        ObjectId trilhaId = new ObjectId(trilha.id());

        Instant agora = Instant.now();
        var opt = atualizarMetadados(usuarioId, trilhaId, new MetadadosTrilha(
                trilha.nome(),
                trilha.cargo() != null ? trilha.cargo() : trilha.areaCargo(),
                trilha.banca(),
                trilha.dataProva(),
                trilha.cor(),
                agora
        ));
        return opt.isPresent();
    }

    public long contar(ObjectId usuarioId) {
        return trilhas.countDocuments(filtroUsuario(usuarioId));
    }

    public long contar(String usuarioIdStr) {
        if (!BsonIds.isValid(usuarioIdStr)) {
            return 0;
        }
        return contar(new ObjectId(usuarioIdStr));
    }

    public boolean excluir(ObjectId usuarioId, ObjectId trilhaId) {
        return trilhas.deleteOne(and(eq("_id", trilhaId), filtroUsuario(usuarioId))).getDeletedCount() > 0;
    }

    public boolean excluir(String usuarioIdStr, String trilhaIdStr) {
        if (!BsonIds.isValid(trilhaIdStr) || !BsonIds.isValid(usuarioIdStr)) {
            return false;
        }
        return excluir(new ObjectId(usuarioIdStr), new ObjectId(trilhaIdStr));
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

    public boolean atualizarPonteiroCronogramaAtivo(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaAtivoId, Long versaoAtivaCronograma) {
        Bson filtro = and(
                eq("_id", trilhaId),
                filtroUsuario(usuarioId)
        );
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

        // 1. Remove cronogramas e planejamentos associados
        db.getCollection("cronogramas").deleteMany(new Document(CAMPO_USUARIO_ID, usuarioId).append(CAMPO_TRILHA_ID, trilhaId));
        db.getCollection("blocos_cronograma").deleteMany(new Document(CAMPO_USUARIO_ID, usuarioId).append(CAMPO_TRILHA_ID, trilhaId));
        db.getCollection("planejamento_ciclo").deleteMany(new Document(CAMPO_USUARIO_ID, usuarioId.toHexString()).append(CAMPO_TRILHA_ID, trilhaId.toHexString()));

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

        // 4. Desvincula notas
        if (topicoIds != null && !topicoIds.isEmpty()) {
            db.getCollection("notas").updateMany(
                    and(
                            or(
                                    eq(CAMPO_USUARIO_ID, usuarioId),
                                    eq(CAMPO_USER_ID, usuarioId.toHexString())
                            ),
                            or(
                                    in("origemTopicoId", topicoIds),
                                    eq(CAMPO_ORIGEM_TRILHA_ID, trilhaId.toHexString())
                            )
                    ),
                    Updates.combine(Updates.unset(CAMPO_ORIGEM_TRILHA_ID), Updates.unset(CAMPO_ORIGEM_DISCIPLINA_ID), Updates.unset("origemTopicoId")));
        }
    }

    private Bson filtroUsuario(ObjectId usuarioId) {
        return or(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq("donoId", usuarioId.toHexString()),
                eq(CAMPO_USUARIO_ID, usuarioId.toHexString())
        );
    }

    private Trilha paraTrilhaComDisciplinas(Document doc, ObjectId usuarioId, ObjectId trilhaId) {
        List<Disciplina> disciplinasNormalizadas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        List<Trilha.Disciplina> disciplinasTrilha = converterDisciplinas(disciplinasNormalizadas);

        String idHex = trilhaId.toHexString();
        String usuarioIdHex = usuarioId.toHexString();
        ObjectId origemEditalId = doc.getObjectId("origemEditalId");
        Long origemVersao = doc.getLong("origemVersao");
        ObjectId cronogramaAtivoId = doc.getObjectId(CAMPO_CRONOGRAMA_ATIVO_ID);

        List<Ciclo> ciclos = converterCiclos(doc.getList("ciclos", Document.class));
        String cicloAtivoId = doc.getString("cicloAtivoId");

        String cargoVal = doc.getString(CAMPO_CARGO);
        if (cargoVal == null) {
            cargoVal = doc.getString(CAMPO_AREA_CARGO);
        }

        return new Trilha(
                idHex,
                usuarioIdHex,
                origemEditalId != null ? origemEditalId.toHexString() : null,
                origemVersao != null ? origemVersao : 0L,
                doc.getString("nome"),
                cargoVal,
                doc.getString(CAMPO_BANCA),
                BsonDates.toLocalDate(doc.getString(CAMPO_DATA_PROVA)),
                doc.getString("cor"),
                doc.getString("informacoesComplementares"),
                cronogramaAtivoId != null ? cronogramaAtivoId.toHexString() : null,
                doc.getLong(CAMPO_VERSAO_ATIVA_CRONOGRAMA),
                doc.getString(CAMPO_GERACAO_CRONOGRAMA_LEASE_TOKEN),
                BsonDates.toInstant(doc, CAMPO_GERACAO_CRONOGRAMA_EM_ANDAMENTO_ATE),
                disciplinasTrilha,
                java.util.Map.of(),
                ciclos,
                cicloAtivoId,
                BsonDates.toInstant(doc, "criadoEm"),
                BsonDates.toInstant(doc, CAMPO_ATUALIZADO_EM));
    }

    private List<Trilha.Disciplina> converterDisciplinas(List<Disciplina> disciplinasNormalizadas) {
        return disciplinasNormalizadas.stream()
                .map(d -> {
                    List<br.com.trilhara.edital.Edital.Topico> topicosEdital = d.topicos().stream()
                            .map(t -> new br.com.trilhara.edital.Edital.Topico(t.id(), t.nome()))
                            .toList();
                    return new Trilha.Disciplina(
                            d.id().toHexString(),
                            d.nome(),
                            d.cor(),
                            d.peso(),
                            d.cadernoLink(),
                            topicosEdital);
                })
                .toList();
    }

    private List<Ciclo> converterCiclos(List<Document> ciclosDoc) {
        if (ciclosDoc == null) {
            return List.of();
        }
        return ciclosDoc.stream()
                .map(cd -> {
                    List<Document> configsDoc = cd.getList("disciplinasConfig", Document.class);
                    List<Ciclo.DisciplinaConfig> configs = configsDoc != null
                            ? configsDoc.stream()
                                    .map(cfg -> new Ciclo.DisciplinaConfig(cfg.getString("disciplinaId"), cfg.getBoolean("participa", true)))
                                    .toList()
                            : List.of();
                    return new Ciclo(
                            cd.getString("id"),
                            cd.getString("nome"),
                            cd.getString("tipo"),
                            cd.getInteger("duracaoBlocoMin", 50),
                            cd.getString("dataInicio"),
                            cd.getString("dataFim"),
                            cd.getList("blocosSemana", Integer.class),
                            configs
                    );
                })
                .toList();
    }
}
