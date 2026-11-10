package br.com.trilhara.shared.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;

import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Inicializador centralizado e idempotente dos índices das 19 coleções do sistema.
 * 
 * Executa no startup da aplicação e valida compatibilidade de índices existentes antes da criação,
 * impedindo execução caso detecte conflitos de especificação sem apagar índices automaticamente.
 */
@ApplicationScoped
public class MongoIndexInitializer {

    private static final Logger LOG = Logger.getLogger(MongoIndexInitializer.class);

    private final MongoClient mongoClient;
    private final String databaseName;

    private volatile boolean inicializadoSucesso = false;
    private volatile Throwable erroInicializacao = null;

    @Inject
    public MongoIndexInitializer(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    void onStart(@Observes StartupEvent event) {
        try {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            inicializarIndices(database);
            this.inicializadoSucesso = true;
            this.erroInicializacao = null;
            LOG.info("Todos os índices das 19 coleções do MongoDB foram inicializados e validados com sucesso.");
        } catch (Throwable t) {
            this.inicializadoSucesso = false;
            this.erroInicializacao = t;
            LOG.errorf(t, "FALHA CRÍTICA NA INICIALIZAÇÃO DE ÍNDICES DO MONGODB: %s", t.getMessage());
            throw new IllegalStateException("Falha ao inicializar índices do MongoDB: " + t.getMessage(), t);
        }
    }

    public boolean isInicializadoSucesso() {
        return inicializadoSucesso;
    }

    public Throwable getErroInicializacao() {
        return erroInicializacao;
    }

    /**
     * Cria e valida todos os índices das 19 coleções de forma centralizada e idempotente.
     */
    public void inicializarIndices(MongoDatabase database) {
        // 1. usuarios
        garantirIndice(database, "usuarios",
                Indexes.ascending("email"),
                new IndexOptions().name("idx_usuarios_email_unique").unique(true));
        garantirIndice(database, "usuarios",
                Indexes.descending("criadoEm"),
                new IndexOptions().name("idx_usuarios_criadoEm_desc"));

        // 2. refresh_tokens
        garantirIndice(database, "refresh_tokens",
                Indexes.ascending("tokenHash"),
                new IndexOptions().name("idx_refresh_tokens_tokenHash_unique").unique(true));
        garantirIndice(database, "refresh_tokens",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("familiaId")),
                new IndexOptions().name("idx_refresh_tokens_usuario_familia"));
        garantirIndice(database, "refresh_tokens",
                Indexes.ascending("expiraEm"),
                new IndexOptions().name("idx_refresh_tokens_expiraEm"));

        // 3. tokens_recuperacao_senha
        garantirIndice(database, "tokens_recuperacao_senha",
                Indexes.ascending("tokenHash"),
                new IndexOptions().name("idx_tokens_recuperacao_tokenHash_unique").unique(true));
        garantirIndice(database, "tokens_recuperacao_senha",
                Indexes.ascending("usuarioId"),
                new IndexOptions().name("idx_tokens_recuperacao_usuario"));
        garantirIndice(database, "tokens_recuperacao_senha",
                Indexes.ascending("expiraEm"),
                new IndexOptions().name("idx_tokens_recuperacao_expiraEm"));

        // 4. tokens_confirmacao_email
        garantirIndice(database, "tokens_confirmacao_email",
                Indexes.ascending("tokenHash"),
                new IndexOptions().name("idx_tokens_confirmacao_tokenHash_unique").unique(true));
        garantirIndice(database, "tokens_confirmacao_email",
                Indexes.ascending("usuarioId"),
                new IndexOptions().name("idx_tokens_confirmacao_usuario"));
        garantirIndice(database, "tokens_confirmacao_email",
                Indexes.ascending("expiraEm"),
                new IndexOptions().name("idx_tokens_confirmacao_expiraEm"));

        // 5. editais
        garantirIndice(database, "editais",
                Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("esfera"), Indexes.descending("atualizadoEm")),
                new IndexOptions().name("idx_editais_status_esfera_atualizadoEm"));

        // 6. trilhas
        garantirIndice(database, "trilhas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.descending("atualizadoEm")),
                new IndexOptions().name("idx_trilhas_usuario_atualizadoEm"));
        garantirIndice(database, "trilhas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("origemEditalId")),
                new IndexOptions().name("idx_trilhas_usuario_origemEdital"));
        garantirIndice(database, "trilhas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("cronogramaAtivoId")),
                new IndexOptions().name("idx_trilhas_usuario_cronogramaAtivo"));

        // 7. disciplinas
        garantirIndice(database, "disciplinas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("trilhaId"), Indexes.ascending("ordem")),
                new IndexOptions().name("idx_disciplinas_usuario_trilha_ordem"));

        // 8. progresso_topicos
        garantirIndice(database, "progresso_topicos",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("trilhaId"), Indexes.ascending("topicoId")),
                new IndexOptions().name("idx_progresso_topicos_unico").unique(true));

        // 9. cronogramas
        garantirIndice(database, "cronogramas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("trilhaId"), Indexes.ascending("modalidade")),
                new IndexOptions().name("idx_cronogramas_usuario_trilha_modalidade_unique").unique(true));
        garantirIndice(database, "cronogramas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("trilhaId")),
                new IndexOptions().name("idx_cronogramas_usuario_trilha_ativo_unique").unique(true).partialFilterExpression(eq("ativo", true)));

        // 10. blocos_cronograma
        garantirIndice(database, "blocos_cronograma",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("trilhaId"),
                        Indexes.ascending("cronogramaId"),
                        Indexes.ascending("versaoGeracao"),
                        Indexes.ascending("data"),
                        Indexes.ascending("ordem")),
                new IndexOptions().name("idx_blocos_usuario_trilha_cronograma_versao_data_ordem_unique").unique(true));
        garantirIndice(database, "blocos_cronograma",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("data"), Indexes.ascending("concluido")),
                new IndexOptions().name("idx_blocos_usuario_data_concluido"));

        // 11. sessoes
        garantirIndice(database, "sessoes",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("trilhaId"),
                        Indexes.descending("dataSessao"),
                        Indexes.descending("criadoEm"),
                        Indexes.descending("_id")),
                new IndexOptions().name("idx_sessoes_usuario_trilha_data_criado_id"));
        garantirIndice(database, "sessoes",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("trilhaId"),
                        Indexes.ascending("disciplinaId"),
                        Indexes.descending("dataSessao")),
                new IndexOptions().name("idx_sessoes_usuario_trilha_disciplina_data"));
        garantirIndice(database, "sessoes",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("chaveIdempotencia")),
                new IndexOptions().name("idx_sessoes_usuario_chaveIdempotencia_unique").unique(true).partialFilterExpression(exists("chaveIdempotencia", true)));

        // 12. listas_tarefas
        garantirIndice(database, "listas_tarefas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("deletedAt"), Indexes.ascending("ordem")),
                new IndexOptions().name("idx_listas_tarefas_usuario_deletedAt_ordem"));

        // 13. series_recorrentes
        garantirIndice(database, "series_recorrentes",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("deletedAt"), Indexes.ascending("ativa")),
                new IndexOptions().name("idx_series_recorrentes_usuario_deletedAt_ativa"));

        // 14. tarefas
        garantirIndice(database, "tarefas",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("deletedAt"),
                        Indexes.ascending("listaId"),
                        Indexes.ascending("concluido"),
                        Indexes.ascending("ordem")),
                new IndexOptions().name("idx_tarefas_usuario_deleted_lista_concluida_ordem"));
        garantirIndice(database, "tarefas",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("deletedAt"),
                        Indexes.ascending("concluido"),
                        Indexes.ascending("dataVencimento")),
                new IndexOptions().name("idx_tarefas_usuario_deleted_concluida_vencimento"));
        garantirIndice(database, "tarefas",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("serieId"),
                        Indexes.ascending("dataOriginalOcorrencia")),
                new IndexOptions().name("idx_tarefas_usuario_serie_dataOriginal_unique").unique(true).partialFilterExpression(exists("serieId", true)));

        // 15. cadernos
        garantirIndice(database, "cadernos",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("deletedAt"), Indexes.descending("atualizadoEm")),
                new IndexOptions().name("idx_cadernos_usuario_deletedAt_atualizadoEm"));
        garantirIndice(database, "cadernos",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("origemTrilhaId")),
                new IndexOptions().name("idx_cadernos_usuario_origemTrilha_unique").unique(true)
                        .partialFilterExpression(and(eq("deletedAt", null), exists("origemTrilhaId", true))));

        // 16. secoes
        garantirIndice(database, "secoes",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("cadernoId"), Indexes.ascending("deletedAt"), Indexes.ascending("ordem")),
                new IndexOptions().name("idx_secoes_usuario_caderno_deletedAt_ordem"));
        garantirIndice(database, "secoes",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("cadernoId"), Indexes.ascending("origemDisciplinaId")),
                new IndexOptions().name("idx_secoes_usuario_caderno_origemDisciplina"));

        // 17. notas
        garantirIndice(database, "notas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("secaoId"), Indexes.ascending("deletedAt"), Indexes.ascending("ordem")),
                new IndexOptions().name("idx_notas_usuario_secao_deletedAt_ordem"));
        garantirIndice(database, "notas",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("cadernoId"), Indexes.ascending("deletedAt")),
                new IndexOptions().name("idx_notas_usuario_caderno_deletedAt"));
        garantirIndice(database, "notas",
                Indexes.compoundIndex(
                        Indexes.ascending("usuarioId"),
                        Indexes.ascending("deletedAt"),
                        Indexes.text("titulo"),
                        Indexes.text("conteudoMarkdown")),
                new IndexOptions().name("idx_notas_usuario_deletedAt_text")
                        .defaultLanguage("portuguese")
                        .weights(new Document("titulo", 10).append("conteudoMarkdown", 1)));

        // 18. push_subscriptions
        garantirIndice(database, "push_subscriptions",
                Indexes.ascending("usuarioId"),
                new IndexOptions().name("idx_push_subscriptions_usuario"));
        garantirIndice(database, "push_subscriptions",
                Indexes.ascending("endpoint"),
                new IndexOptions().name("idx_push_subscriptions_endpoint_unique").unique(true));

        // 19. lembretes_push
        garantirIndice(database, "lembretes_push",
                Indexes.compoundIndex(Indexes.ascending("estado"), Indexes.ascending("instanteEntregaUtc")),
                new IndexOptions().name("idx_lembretes_push_estado_instanteEntregaUtc"));
        garantirIndice(database, "lembretes_push",
                Indexes.compoundIndex(Indexes.ascending("usuarioId"), Indexes.ascending("tarefaId")),
                new IndexOptions().name("idx_lembretes_push_usuario_tarefa"));
    }

    private void garantirIndice(MongoDatabase database, String nomeColecao, Bson keys, IndexOptions options) {
        MongoCollection<Document> collection = database.getCollection(nomeColecao);
        String nomeEsperado = options.getName();
        org.bson.BsonDocument expectedKeysDoc = keys.toBsonDocument(org.bson.BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());

        Map<String, Document> indicesExistentes = new HashMap<>();
        for (Document docIndice : collection.listIndexes()) {
            String nome = docIndice.getString("name");
            indicesExistentes.put(nome, docIndice);

            // Verificar se chave idêntica já existe com outro nome
            Document rawKey = docIndice.get("key", Document.class);
            org.bson.BsonDocument existingKey = rawKey != null ? rawKey.toBsonDocument() : null;
            if (!nome.equals(nomeEsperado) && !nome.equals("_id_") && chavesEquivalentes(existingKey, expectedKeysDoc)) {
                throw new IllegalStateException(String.format(
                        "Conflito de índice detectado na coleção '%s': já existe índice '%s' com mesma chave do índice pretendido '%s'.",
                        nomeColecao, nome, nomeEsperado));
            }
        }

        Document existenteComMesmoNome = indicesExistentes.get(nomeEsperado);
        if (existenteComMesmoNome != null) {
            // Validar compatibilidade de chave
            Document rawKey = existenteComMesmoNome.get("key", Document.class);
            org.bson.BsonDocument existingKey = rawKey != null ? rawKey.toBsonDocument() : null;
            if (!chavesEquivalentes(existingKey, expectedKeysDoc)) {
                throw new IllegalStateException(String.format(
                        "Conflito de chave no índice '%s' da coleção '%s': esperado %s, encontrado %s",
                        nomeEsperado, nomeColecao, expectedKeysDoc.toJson(), existingKey != null ? existingKey.toJson() : "null"));
            }

            // Validar compatibilidade de unicidade
            boolean existenteUnique = existenteComMesmoNome.getBoolean("unique", false);
            boolean esperadoUnique = Boolean.TRUE.equals(options.isUnique());
            if (existenteUnique != esperadoUnique) {
                throw new IllegalStateException(String.format(
                        "Conflito de unicidade no índice '%s' da coleção '%s': esperado unique=%b, encontrado=%b",
                        nomeEsperado, nomeColecao, esperadoUnique, existenteUnique));
            }
            return;
        }

        try {
            collection.createIndex(keys, options);
            LOG.debugf("Índice '%s' criado com sucesso na coleção '%s'.", nomeEsperado, nomeColecao);
        } catch (Exception ex) {
            throw new IllegalStateException(String.format(
                    "Falha ao criar índice '%s' na coleção '%s': %s",
                    nomeEsperado, nomeColecao, ex.getMessage()), ex);
        }
    }

    private boolean chavesEquivalentes(org.bson.BsonDocument existingKey, org.bson.BsonDocument expectedKey) {
        if (existingKey == null || expectedKey == null) return false;
        if (existingKey.equals(expectedKey)) return true;

        // Caso especial: índices de texto no MongoDB utilizam internamente {"_fts": "text", "_ftsx": 1}
        boolean esperadoTemTexto = expectedKey.values().stream().anyMatch(v -> v.isString() && "text".equals(v.asString().getValue()));
        boolean existenteTemTexto = existingKey.containsKey("_fts");
        if (esperadoTemTexto && existenteTemTexto) {
            for (String k : expectedKey.keySet()) {
                if (!expectedKey.get(k).isString() || !"text".equals(expectedKey.get(k).asString().getValue())) {
                    if (!existingKey.containsKey(k) || !existingKey.get(k).equals(expectedKey.get(k))) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
}
