package br.com.trilhara.auth;

import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoDatabase;

import br.com.trilhara.shared.mongo.ColecaoTrilhara;

/**
 * Fixtures de AccountDeletionIntegrationTest (R10/D-35).
 *
 * <p>O mapa de populadores e dirigido pelo enum: cada colecao filha privada de
 * {@link ColecaoTrilhara#filhasPrivadas()} precisa de exatamente um populador, e
 * {@link #validarCobertura()} falha quando uma colecao nova e declarada sem fixture.</p>
 */
final class FixturesConta {

    private FixturesConta() {
    }

    /** Falha explicitamente se faltar (ou sobrar) fixture para alguma colecao filha. */
    static void validarCobertura() {
        Set<ColecaoTrilhara> filhas = EnumSet.copyOf(ColecaoTrilhara.filhasPrivadas());
        Set<ColecaoTrilhara> faltantes = EnumSet.copyOf(filhas);
        faltantes.removeAll(populadores().keySet());
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Fixtures ausentes para as colecoes filhas: " + faltantes);
        }
        Set<ColecaoTrilhara> extras = EnumSet.copyOf(populadores().keySet());
        extras.removeAll(filhas);
        if (!extras.isEmpty()) {
            throw new IllegalStateException("Fixtures para colecoes que nao sao filhas privadas: " + extras);
        }
    }

    /** Popula as 18 colecoes filhas do usuario informado e cria o documento raiz. */
    static void popularColecoesFilhas(MongoDatabase db, ObjectId usuarioId, String senhaHash, String email) {
        validarCobertura();

        Date agora = Date.from(Instant.now());
        Document contexto = new Document()
                .append("usuarioId", usuarioId)
                .append("agora", agora)
                .append("expiraEm", Date.from(Instant.now().plusSeconds(3600)))
                .append("trilhaId", new ObjectId())
                .append("disciplinaId", new ObjectId())
                .append("cronogramaId", new ObjectId())
                .append("listaId", new ObjectId())
                .append("serieId", new ObjectId())
                .append("cadernoId", new ObjectId())
                .append("secaoId", new ObjectId());

        for (ColecaoTrilhara colecao : ColecaoTrilhara.filhasPrivadas()) {
            populadores().get(colecao).accept(db, contexto);
        }

        db.getCollection(ColecaoTrilhara.USUARIOS.nome()).insertOne(new Document()
                .append("_id", usuarioId)
                .append("nome", "Usuário Teste")
                .append("email", email)
                .append("senhaHash", senhaHash)
                .append("roles", List.of("USER"))
                .append("ativo", true)
                .append("emailVerificado", true)
                .append("criadoEm", agora)
                .append("atualizadoEm", agora));
    }

    static Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> populadores() {
        Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa = new EnumMap<>(ColecaoTrilhara.class);
        registrarTokens(mapa);
        registrarEstudos(mapa);
        registrarTarefas(mapa);
        registrarNotas(mapa);
        registrarPush(mapa);
        return mapa;
    }

    private static void registrarTokens(Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa) {
        mapa.put(ColecaoTrilhara.REFRESH_TOKENS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.REFRESH_TOKENS.nome())
                .insertOne(tokens(ctx).append("familiaId", UUID.randomUUID().toString())));
        mapa.put(ColecaoTrilhara.TOKENS_RECUPERACAO_SENHA, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.TOKENS_RECUPERACAO_SENHA.nome())
                .insertOne(tokens(ctx)));
        mapa.put(ColecaoTrilhara.TOKENS_CONFIRMACAO_EMAIL, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.TOKENS_CONFIRMACAO_EMAIL.nome())
                .insertOne(tokens(ctx)));
        mapa.put(ColecaoTrilhara.TOKENS_HANDOFF_SSO, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.TOKENS_HANDOFF_SSO.nome())
                .insertOne(new Document("_id", new ObjectId())
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("codigoHash", UUID.randomUUID().toString())
                        .append("origemDestino", "http://localhost:5174/")
                        .append("criadoEm", ctx.getDate("agora"))
                        .append("expiraEm", ctx.getDate("expiraEm"))
                        .append("consumidoEm", null)));
    }

    /** Documento base comum as quatro colecoes de token (todas com usuarioId + expiraEm). */
    private static Document tokens(Document ctx) {
        return new Document()
                .append("usuarioId", ctx.getObjectId("usuarioId"))
                .append("tokenHash", UUID.randomUUID().toString())
                .append("criadoEm", ctx.getDate("agora"))
                .append("expiraEm", ctx.getDate("expiraEm"));
    }

    private static void registrarEstudos(Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa) {
        mapa.put(ColecaoTrilhara.TRILHAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.TRILHAS.nome())
                .insertOne(new Document("_id", ctx.getObjectId("trilhaId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("nome", "Trilha Auditor Fiscal")
                        .append("atualizadoEm", ctx.getDate("agora"))));
        mapa.put(ColecaoTrilhara.DISCIPLINAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.DISCIPLINAS.nome())
                .insertOne(new Document("_id", ctx.getObjectId("disciplinaId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("trilhaId", ctx.getObjectId("trilhaId"))
                        .append("nome", "Direito Constitucional")
                        .append("ordem", 0)));
        mapa.put(ColecaoTrilhara.PROGRESSO_TOPICOS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.PROGRESSO_TOPICOS.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("trilhaId", ctx.getObjectId("trilhaId"))
                        .append("topicoId", "topico-1")
                        .append("concluido", true)));
        mapa.put(ColecaoTrilhara.CRONOGRAMAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.CRONOGRAMAS.nome())
                .insertOne(new Document("_id", ctx.getObjectId("cronogramaId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("trilhaId", ctx.getObjectId("trilhaId"))
                        .append("modalidade", "CICLO")
                        .append("ativo", true)));
        mapa.put(ColecaoTrilhara.BLOCOS_CRONOGRAMA, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.BLOCOS_CRONOGRAMA.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("trilhaId", ctx.getObjectId("trilhaId"))
                        .append("cronogramaId", ctx.getObjectId("cronogramaId"))
                        .append("versaoGeracao", 1)
                        .append("data", "2026-11-10")
                        .append("ordem", 0)
                        .append("concluido", false)));
        mapa.put(ColecaoTrilhara.SESSOES, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.SESSOES.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("trilhaId", ctx.getObjectId("trilhaId"))
                        .append("disciplinaId", ctx.getObjectId("disciplinaId"))
                        .append("duracaoSegundos", 1500)
                        .append("dataSessao", "2026-11-10")
                        .append("criadoEm", ctx.getDate("agora"))));
    }

    private static void registrarTarefas(Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa) {
        mapa.put(ColecaoTrilhara.LISTAS_TAREFAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.LISTAS_TAREFAS.nome())
                .insertOne(new Document("_id", ctx.getObjectId("listaId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("nome", "Tarefas da Semana")
                        .append("ordem", 0)));
        mapa.put(ColecaoTrilhara.SERIES_RECORRENTES, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.SERIES_RECORRENTES.nome())
                .insertOne(new Document("_id", ctx.getObjectId("serieId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("titulo", "Série Semanal de Revisão")
                        .append("ativa", true)));
        mapa.put(ColecaoTrilhara.TAREFAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.TAREFAS.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("listaId", ctx.getObjectId("listaId"))
                        .append("serieId", ctx.getObjectId("serieId"))
                        .append("titulo", "Estudar Direito Tributário")
                        .append("concluida", false)
                        .append("ordem", 0)));
    }

    private static void registrarNotas(Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa) {
        mapa.put(ColecaoTrilhara.CADERNOS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.CADERNOS.nome())
                .insertOne(new Document("_id", ctx.getObjectId("cadernoId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("titulo", "Meu Caderno de Estudos")
                        .append("atualizadoEm", ctx.getDate("agora"))));
        mapa.put(ColecaoTrilhara.SECOES, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.SECOES.nome())
                .insertOne(new Document("_id", ctx.getObjectId("secaoId"))
                        .append("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("cadernoId", ctx.getObjectId("cadernoId"))
                        .append("titulo", "Direito Tributário")
                        .append("ordem", 0)));
        mapa.put(ColecaoTrilhara.NOTAS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.NOTAS.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("cadernoId", ctx.getObjectId("cadernoId"))
                        .append("secaoId", ctx.getObjectId("secaoId"))
                        .append("titulo", "Resumo de Tributos")
                        .append("conteudoMarkdown", "# Resumo")));
    }

    private static void registrarPush(Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> mapa) {
        mapa.put(ColecaoTrilhara.PUSH_SUBSCRIPTIONS, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.PUSH_SUBSCRIPTIONS.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("endpoint", "https://push.example.com/" + UUID.randomUUID())
                        .append("p256dh", "dummy-p256dh")
                        .append("auth", "dummy-auth")));
        mapa.put(ColecaoTrilhara.LEMBRETES_PUSH, (db, ctx) -> db
                .getCollection(ColecaoTrilhara.LEMBRETES_PUSH.nome())
                .insertOne(new Document("usuarioId", ctx.getObjectId("usuarioId"))
                        .append("tarefaId", new ObjectId())
                        .append("versaoAgendamento", 1L)
                        .append("estado", "PENDENTE")
                        .append("instanteEntregaUtc", ctx.getDate("expiraEm"))));
    }
}
