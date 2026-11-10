package br.com.trilhara.sessao;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.sessao.dto.EstatisticaDisciplinaDTO;
import br.com.trilhara.sessao.dto.EstatisticasTrilhaDTO;
import br.com.trilhara.sessao.dto.PaginaSessoesDTO;
import br.com.trilhara.sessao.dto.RemocaoSessoesDisciplinaResponse;
import br.com.trilhara.sessao.dto.SerieTemporalDiaDTO;
import br.com.trilhara.sessao.dto.SessaoDTO;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.BsonIds;
import br.com.trilhara.trilha.Disciplina;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SessaoRepository {

    private static final String EXPRESSAO_ACERTOS = "$acertos";
    private static final String EXPRESSAO_DURACAO_SEGUNDOS = "$duracaoSegundos";
    private static final String EXPRESSAO_QUESTOES = "$questoes";
    private static final String CAMPO_ACERTOS = "acertos";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_CHAVE_IDEMPOTENCIA = "chaveIdempotencia";
    private static final String CAMPO_COUNT = "count";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_DATA_SESSAO = "dataSessao";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_DISCIPLINA_COR_SNAPSHOT = "disciplinaCorSnapshot";
    private static final String CAMPO_DISCIPLINA_ID = "disciplinaId";
    private static final String CAMPO_DISCIPLINA_NOME_SNAPSHOT = "disciplinaNomeSnapshot";
    private static final String CAMPO_DURACAO_SEGUNDOS = "duracaoSegundos";
    private static final String CAMPO_QUESTOES = "questoes";
    private static final String CAMPO_TOTAL_ACERTOS = "totalAcertos";
    private static final String CAMPO_TOTAL_QUESTOES = "totalQuestoes";
    private static final String CAMPO_TOTAL_SEGUNDOS = "totalSegundos";
    private static final String CAMPO_TOTAL_SESSOES = "totalSessoes";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_TRILHA_NOME_SNAPSHOT = "trilhaNomeSnapshot";
    private static final String CAMPO_USUARIO_ID = "usuarioId";

    private final MongoCollection<Document> sessoes;

    public SessaoRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.sessoes = client.getDatabase(database).getCollection("sessoes");
    }

    public void inserir(Sessao sessao) {
        sessoes.insertOne(paraDocumento(sessao));
    }

    public Optional<Document> buscarDocumentoPorChaveIdempotencia(ObjectId usuarioId, String chaveIdempotencia) {
        Document doc = sessoes.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CHAVE_IDEMPOTENCIA, chaveIdempotencia)
        )).first();
        return Optional.ofNullable(doc);
    }

    public Optional<Sessao> buscarPrimeiraSessaoDaTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        Document doc = sessoes.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DELETED_AT, null)
        )).sort(Sorts.descending(CAMPO_DATA_SESSAO, CAMPO_CRIADO_EM, "_id")).first();
        return Optional.ofNullable(doc).map(this::paraSessao);
    }

    public Optional<Sessao> buscarPorId(ObjectId usuarioId, ObjectId trilhaId, ObjectId sessaoId) {
        Document doc = sessoes.find(and(
                eq("_id", sessaoId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DELETED_AT, null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraSessao);
    }

    public boolean atualizar(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId sessaoId,
            int duracaoSegundos,
            int questoes,
            int acertos,
            Instant atualizadoEm) {
        Bson filtro = and(
                eq("_id", sessaoId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DELETED_AT, null)
        );
        Bson updates = Updates.combine(
                Updates.set(CAMPO_DURACAO_SEGUNDOS, duracaoSegundos),
                Updates.set(CAMPO_QUESTOES, questoes),
                Updates.set(CAMPO_ACERTOS, acertos),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(atualizadoEm))
        );
        return sessoes.updateOne(filtro, updates).getModifiedCount() > 0;
    }

    public boolean marcarTombstone(ObjectId usuarioId, ObjectId trilhaId, ObjectId sessaoId, Instant agora) {
        Bson filtro = and(
                eq("_id", sessaoId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DELETED_AT, null)
        );
        Bson updates = Updates.combine(
                Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora)),
                Updates.unset(CAMPO_DURACAO_SEGUNDOS),
                Updates.unset(CAMPO_QUESTOES),
                Updates.unset(CAMPO_ACERTOS),
                Updates.unset(CAMPO_TRILHA_NOME_SNAPSHOT),
                Updates.unset(CAMPO_DISCIPLINA_NOME_SNAPSHOT),
                Updates.unset(CAMPO_DISCIPLINA_COR_SNAPSHOT),
                Updates.unset(CAMPO_DATA_SESSAO)
        );
        return sessoes.updateOne(filtro, updates).getModifiedCount() > 0;
    }

    public RemocaoSessoesDisciplinaResponse excluirSessoesDaDisciplina(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId disciplinaId) {
        Bson filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DISCIPLINA_ID, disciplinaId),
                eq(CAMPO_DELETED_AT, null)
        );

        List<Bson> pipeline = List.of(
                Aggregates.match(filtro),
                Aggregates.group(null,
                        Accumulators.sum(CAMPO_TOTAL_SEGUNDOS, EXPRESSAO_DURACAO_SEGUNDOS),
                        Accumulators.sum(CAMPO_COUNT, 1))
        );

        Document agg = sessoes.aggregate(pipeline).first();
        long sessoesRemovidas = (agg != null && agg.containsKey(CAMPO_COUNT))
                ? ((Number) agg.get(CAMPO_COUNT)).longValue()
                : 0L;
        long totalSegundosRemovidos = (agg != null && agg.containsKey(CAMPO_TOTAL_SEGUNDOS))
                ? ((Number) agg.get(CAMPO_TOTAL_SEGUNDOS)).longValue()
                : 0L;

        sessoes.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DISCIPLINA_ID, disciplinaId)
        ));

        return new RemocaoSessoesDisciplinaResponse(sessoesRemovidas, totalSegundosRemovidos);
    }

    public long excluirPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return sessoes.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId)
        )).getDeletedCount();
    }

    public record FiltroPaginacao(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId disciplinaId,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho) {
    }

    public PaginaSessoesDTO listarPaginado(
            FiltroPaginacao filtro,
            ObjectId primeiraSessaoId,
            Map<ObjectId, Disciplina> disciplinasAtivas) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_USUARIO_ID, filtro.usuarioId()));
        filtros.add(eq(CAMPO_TRILHA_ID, filtro.trilhaId()));
        filtros.add(eq(CAMPO_DELETED_AT, null));

        if (filtro.disciplinaId() != null) {
            filtros.add(eq(CAMPO_DISCIPLINA_ID, filtro.disciplinaId()));
        }
        if (filtro.dataInicio() != null) {
            filtros.add(gte(CAMPO_DATA_SESSAO, filtro.dataInicio().toString()));
        }
        if (filtro.dataFim() != null) {
            filtros.add(lte(CAMPO_DATA_SESSAO, filtro.dataFim().toString()));
        }

        Bson filtroFinal = and(filtros);
        long totalItens = sessoes.countDocuments(filtroFinal);
        int totalPaginas = totalItens > 0 ? (int) Math.ceil((double) totalItens / filtro.tamanho()) : 1;

        int skip = (filtro.pagina() - 1) * filtro.tamanho();
        List<SessaoDTO> itens = new ArrayList<>();

        sessoes.find(filtroFinal)
                .sort(Sorts.descending(CAMPO_DATA_SESSAO, CAMPO_CRIADO_EM, "_id"))
                .skip(skip)
                .limit(filtro.tamanho())
                .forEach(doc -> {
                    ObjectId id = doc.getObjectId("_id");
                    ObjectId dId = doc.getObjectId(CAMPO_DISCIPLINA_ID);
                    Disciplina disc = dId != null ? disciplinasAtivas.get(dId) : null;

                    boolean disciplinaRemovida = (disc == null);
                    String nome = (disc != null) ? disc.nome() : doc.getString(CAMPO_DISCIPLINA_NOME_SNAPSHOT);
                    String cor = (disc != null) ? disc.cor() : doc.getString(CAMPO_DISCIPLINA_COR_SNAPSHOT);

                    int duracao = doc.getInteger(CAMPO_DURACAO_SEGUNDOS, 0);
                    int questoes = doc.getInteger(CAMPO_QUESTOES, 0);
                    int acertos = doc.getInteger(CAMPO_ACERTOS, 0);
                    int erros = Math.max(0, questoes - acertos);
                    double percentual = questoes > 0 ? (acertos * 100.0 / questoes) : 0.0;
                    boolean editavel = (primeiraSessaoId != null && primeiraSessaoId.equals(id));

                    itens.add(new SessaoDTO(
                            BsonIds.toHex(id),
                            BsonIds.toHex(filtro.trilhaId()),
                            BsonIds.toHex(dId),
                            nome,
                            cor,
                            disciplinaRemovida,
                            doc.getString(CAMPO_DATA_SESSAO),
                            duracao,
                            questoes,
                            acertos,
                            erros,
                            percentual,
                            editavel,
                            doc.getString(CAMPO_CHAVE_IDEMPOTENCIA),
                            BsonDates.toInstant(doc, CAMPO_CRIADO_EM),
                            BsonDates.toInstant(doc, CAMPO_ATUALIZADO_EM)
                    ));
                });

        return new PaginaSessoesDTO(itens, filtro.pagina(), filtro.tamanho(), totalItens, totalPaginas);
    }

    private record TotaisGlobais(
            long totalSegundos,
            long totalSessoes,
            int totalQuestoes,
            int totalAcertos,
            int totalErros,
            double percentualAcertoGlobal) {
    }

    private TotaisGlobais calcularTotaisGlobais(Bson matchFiltro) {
        List<Bson> pipelineTotais = List.of(
                Aggregates.match(matchFiltro),
                Aggregates.group(null,
                        Accumulators.sum(CAMPO_TOTAL_SEGUNDOS, EXPRESSAO_DURACAO_SEGUNDOS),
                        Accumulators.sum(CAMPO_TOTAL_SESSOES, 1),
                        Accumulators.sum(CAMPO_TOTAL_QUESTOES, EXPRESSAO_QUESTOES),
                        Accumulators.sum(CAMPO_TOTAL_ACERTOS, EXPRESSAO_ACERTOS))
        );
        Document totaisDoc = sessoes.aggregate(pipelineTotais).first();
        long totalSegundos = totaisDoc != null && totaisDoc.containsKey(CAMPO_TOTAL_SEGUNDOS)
                ? ((Number) totaisDoc.get(CAMPO_TOTAL_SEGUNDOS)).longValue()
                : 0L;
        long totalSessoes = totaisDoc != null && totaisDoc.containsKey(CAMPO_TOTAL_SESSOES)
                ? ((Number) totaisDoc.get(CAMPO_TOTAL_SESSOES)).longValue()
                : 0L;
        int totalQuestoes = totaisDoc != null && totaisDoc.containsKey(CAMPO_TOTAL_QUESTOES)
                ? ((Number) totaisDoc.get(CAMPO_TOTAL_QUESTOES)).intValue()
                : 0;
        int totalAcertos = totaisDoc != null && totaisDoc.containsKey(CAMPO_TOTAL_ACERTOS)
                ? ((Number) totaisDoc.get(CAMPO_TOTAL_ACERTOS)).intValue()
                : 0;
        int totalErros = Math.max(0, totalQuestoes - totalAcertos);
        double percentualAcertoGlobal = totalQuestoes > 0 ? (totalAcertos * 100.0 / totalQuestoes) : 0.0;
        return new TotaisGlobais(totalSegundos, totalSessoes, totalQuestoes, totalAcertos, totalErros, percentualAcertoGlobal);
    }

    private List<EstatisticaDisciplinaDTO> calcularEstatisticasPorDisciplina(
            Bson matchFiltro,
            List<Disciplina> disciplinasTrilha) {
        Map<ObjectId, Disciplina> discMap = new HashMap<>();
        Map<ObjectId, Integer> ordemMap = new HashMap<>();
        for (Disciplina d : disciplinasTrilha) {
            discMap.put(d.id(), d);
            ordemMap.put(d.id(), d.ordem());
        }

        List<Bson> pipelineDisciplinas = List.of(
                Aggregates.match(matchFiltro),
                Aggregates.group("$disciplinaId",
                        Accumulators.sum(CAMPO_TOTAL_SEGUNDOS, EXPRESSAO_DURACAO_SEGUNDOS),
                        Accumulators.sum(CAMPO_TOTAL_SESSOES, 1),
                        Accumulators.sum(CAMPO_TOTAL_QUESTOES, EXPRESSAO_QUESTOES),
                        Accumulators.sum(CAMPO_TOTAL_ACERTOS, EXPRESSAO_ACERTOS),
                        Accumulators.first("snapshotNome", "$disciplinaNomeSnapshot"),
                        Accumulators.first("snapshotCor", "$disciplinaCorSnapshot"))
        );

        List<EstatisticaDisciplinaDTO> porDisciplina = new ArrayList<>();
        for (Document doc : sessoes.aggregate(pipelineDisciplinas)) {
            ObjectId dId = doc.getObjectId("_id");
            Disciplina disc = (dId != null) ? discMap.get(dId) : null;

            boolean disciplinaRemovida = (disc == null);
            String nome = (disc != null) ? disc.nome() : doc.getString("snapshotNome");
            String cor = (disc != null) ? disc.cor() : doc.getString("snapshotCor");

            long dSegundos = ((Number) doc.get(CAMPO_TOTAL_SEGUNDOS)).longValue();
            long dSessoes = ((Number) doc.get(CAMPO_TOTAL_SESSOES)).longValue();
            int dQuestoes = ((Number) doc.get(CAMPO_TOTAL_QUESTOES)).intValue();
            int dAcertos = ((Number) doc.get(CAMPO_TOTAL_ACERTOS)).intValue();
            int dErros = Math.max(0, dQuestoes - dAcertos);
            double dPercentual = dQuestoes > 0 ? (dAcertos * 100.0 / dQuestoes) : 0.0;

            porDisciplina.add(new EstatisticaDisciplinaDTO(
                    BsonIds.toHex(dId),
                    nome,
                    cor,
                    disciplinaRemovida,
                    dSegundos,
                    dSessoes,
                    dQuestoes,
                    dAcertos,
                    dErros,
                    dPercentual
            ));
        }

        ordenarEstatisticasDisciplina(porDisciplina, ordemMap);
        return porDisciplina;
    }

    private void ordenarEstatisticasDisciplina(List<EstatisticaDisciplinaDTO> porDisciplina, Map<ObjectId, Integer> ordemMap) {
        porDisciplina.sort((a, b) -> {
            if (a.disciplinaRemovida() != b.disciplinaRemovida()) {
                return a.disciplinaRemovida() ? 1 : -1;
            }
            if (!a.disciplinaRemovida()) {
                ObjectId idA = BsonIds.toObjectIdOrNotFound(a.disciplinaId());
                ObjectId idB = BsonIds.toObjectIdOrNotFound(b.disciplinaId());
                int oA = ordemMap.getOrDefault(idA, 0);
                int oB = ordemMap.getOrDefault(idB, 0);
                return Integer.compare(oA, oB);
            }
            String nA = a.nome() != null ? a.nome() : "";
            String nB = b.nome() != null ? b.nome() : "";
            return nA.compareToIgnoreCase(nB);
        });
    }

    private List<SerieTemporalDiaDTO> calcularSerieTemporal(Bson matchFiltro) {
        List<Bson> pipelineSerie = List.of(
                Aggregates.match(matchFiltro),
                Aggregates.group("$dataSessao",
                        Accumulators.sum(CAMPO_TOTAL_SEGUNDOS, EXPRESSAO_DURACAO_SEGUNDOS),
                        Accumulators.sum(CAMPO_TOTAL_QUESTOES, EXPRESSAO_QUESTOES),
                        Accumulators.sum(CAMPO_TOTAL_ACERTOS, EXPRESSAO_ACERTOS)),
                Aggregates.sort(Sorts.ascending("_id"))
        );

        List<SerieTemporalDiaDTO> serieTemporal = new ArrayList<>();
        for (Document doc : sessoes.aggregate(pipelineSerie)) {
            String dataStr = doc.getString("_id");
            if (dataStr != null) {
                long stSegundos = ((Number) doc.get(CAMPO_TOTAL_SEGUNDOS)).longValue();
                int stQuestoes = ((Number) doc.get(CAMPO_TOTAL_QUESTOES)).intValue();
                int stAcertos = ((Number) doc.get(CAMPO_TOTAL_ACERTOS)).intValue();
                serieTemporal.add(new SerieTemporalDiaDTO(dataStr, stSegundos, stQuestoes, stAcertos));
            }
        }
        return serieTemporal;
    }

    private int calcularStreakDias(ObjectId usuarioId, ObjectId trilhaId, LocalDate hojeLocal) {
        Set<LocalDate> diasComEstudo = new HashSet<>();
        sessoes.distinct(CAMPO_DATA_SESSAO, and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DELETED_AT, null)
        ), String.class).forEach(dStr -> {
            try {
                if (dStr != null) {
                    diasComEstudo.add(LocalDate.parse(dStr));
                }
            } catch (Exception _) {
                // ignora datas corrompidas no cálculo de streak
            }
        });

        int streakDias = 0;
        if (!diasComEstudo.isEmpty() && hojeLocal != null) {
            LocalDate cursor = hojeLocal;
            if (!diasComEstudo.contains(cursor)) {
                LocalDate ontem = hojeLocal.minusDays(1);
                cursor = diasComEstudo.contains(ontem) ? ontem : null;
            }
            while (cursor != null && diasComEstudo.contains(cursor)) {
                streakDias++;
                cursor = cursor.minusDays(1);
            }
        }
        return streakDias;
    }

    public EstatisticasTrilhaDTO obterEstatisticas(
            ObjectId usuarioId,
            ObjectId trilhaId,
            LocalDate dataInicio,
            LocalDate dataFim,
            LocalDate hojeLocal,
            List<Disciplina> disciplinasTrilha) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_USUARIO_ID, usuarioId));
        filtros.add(eq(CAMPO_TRILHA_ID, trilhaId));
        filtros.add(eq(CAMPO_DELETED_AT, null));

        if (dataInicio != null) {
            filtros.add(gte(CAMPO_DATA_SESSAO, dataInicio.toString()));
        }
        if (dataFim != null) {
            filtros.add(lte(CAMPO_DATA_SESSAO, dataFim.toString()));
        }

        Bson matchFiltro = and(filtros);

        TotaisGlobais totais = calcularTotaisGlobais(matchFiltro);
        List<EstatisticaDisciplinaDTO> porDisciplina = calcularEstatisticasPorDisciplina(matchFiltro, disciplinasTrilha);
        List<SerieTemporalDiaDTO> serieTemporal = calcularSerieTemporal(matchFiltro);
        int streakDias = calcularStreakDias(usuarioId, trilhaId, hojeLocal);

        return new EstatisticasTrilhaDTO(
                totais.totalSegundos(),
                totais.totalSessoes(),
                totais.totalQuestoes(),
                totais.totalAcertos(),
                totais.totalErros(),
                totais.percentualAcertoGlobal(),
                porDisciplina,
                serieTemporal,
                streakDias
        );
    }

    public Document paraDocumento(Sessao sessao) {
        Document doc = new Document();
        doc.put("_id", sessao.id());
        doc.put(CAMPO_USUARIO_ID, sessao.usuarioId());
        doc.put(CAMPO_TRILHA_ID, sessao.trilhaId());
        doc.put(CAMPO_DISCIPLINA_ID, sessao.disciplinaId());
        doc.put(CAMPO_TRILHA_NOME_SNAPSHOT, sessao.trilhaNomeSnapshot());
        doc.put(CAMPO_DISCIPLINA_NOME_SNAPSHOT, sessao.disciplinaNomeSnapshot());
        doc.put(CAMPO_DISCIPLINA_COR_SNAPSHOT, sessao.disciplinaCorSnapshot());
        doc.put(CAMPO_DATA_SESSAO, sessao.dataSessao() != null ? sessao.dataSessao().toString() : null);
        doc.put(CAMPO_DURACAO_SEGUNDOS, sessao.duracaoSegundos());
        doc.put(CAMPO_QUESTOES, sessao.questoes());
        doc.put(CAMPO_ACERTOS, sessao.acertos());
        doc.put(CAMPO_CHAVE_IDEMPOTENCIA, sessao.chaveIdempotencia());
        doc.put("requestHash", sessao.requestHash());
        doc.put(CAMPO_DELETED_AT, BsonDates.toBsonDate(sessao.deletedAt()));
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(sessao.criadoEm()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(sessao.atualizadoEm()));
        return doc;
    }

    public Sessao paraSessao(Document doc) {
        ObjectId id = doc.getObjectId("_id");
        ObjectId uId = doc.getObjectId(CAMPO_USUARIO_ID);
        ObjectId tId = doc.getObjectId(CAMPO_TRILHA_ID);
        ObjectId dId = doc.getObjectId(CAMPO_DISCIPLINA_ID);
        String tNome = doc.getString(CAMPO_TRILHA_NOME_SNAPSHOT);
        String dNome = doc.getString(CAMPO_DISCIPLINA_NOME_SNAPSHOT);
        String dCor = doc.getString(CAMPO_DISCIPLINA_COR_SNAPSHOT);
        String dataStr = doc.getString(CAMPO_DATA_SESSAO);
        LocalDate dataSessao = (dataStr != null) ? LocalDate.parse(dataStr) : null;
        Integer duracao = doc.getInteger(CAMPO_DURACAO_SEGUNDOS);
        int duracaoSegundos = (duracao != null) ? duracao : 0;
        Integer q = doc.getInteger(CAMPO_QUESTOES);
        int questoes = (q != null) ? q : 0;
        Integer a = doc.getInteger(CAMPO_ACERTOS);
        int acertos = (a != null) ? a : 0;
        String chave = doc.getString(CAMPO_CHAVE_IDEMPOTENCIA);
        String hash = doc.getString("requestHash");
        Instant deletedAt = BsonDates.toInstant(doc, CAMPO_DELETED_AT);
        Instant criadoEm = BsonDates.toInstant(doc, CAMPO_CRIADO_EM);
        Instant atualizadoEm = BsonDates.toInstant(doc, CAMPO_ATUALIZADO_EM);

        return new Sessao(
                id,
                uId,
                tId,
                dId,
                tNome,
                dNome,
                dCor,
                dataSessao,
                duracaoSegundos,
                questoes,
                acertos,
                chave,
                hash,
                deletedAt,
                criadoEm,
                atualizadoEm
        );
    }
}
