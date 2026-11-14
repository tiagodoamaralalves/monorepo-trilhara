package br.com.trilhara.shared.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Inicializador centralizado e idempotente dos índices canônicos.
 *
 * <p>Única classe autorizada a criar índices. A tabela válida é a de
 * {@link IndicesCanonicos}; conflito de chave/opções ou índice ausente <strong>não</strong>
 * derruba o processo — é reportado em {@link DiagnosticoIndices} e exposto como
 * {@code DOWN} por {@link MongoIndexHealthCheck}. Índices {@code idx_}/{@code uk_} não
 * declarados são drift: aparecem em {@code WARN} e no health, jamais são removidos
 * automaticamente.</p>
 */
@ApplicationScoped
public class MongoIndexInitializer {

    private static final Logger LOG = Logger.getLogger(MongoIndexInitializer.class);

    private final MongoClient mongoClient;
    private final String databaseName;

    private volatile DiagnosticoIndices ultimoDiagnostico = DiagnosticoIndices.naoExecutado();
    private volatile Throwable erroInicializacao = null;
    private volatile boolean inicializado = false;

    @Inject
    public MongoIndexInitializer(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    void onStart(@Observes StartupEvent event) {
        try {
            verificarAgora();
        } catch (Throwable t) {
            this.erroInicializacao = t;
            LOG.errorf(t, "FALHA CRÍTICA NA INICIALIZAÇÃO DE ÍNDICES DO MONGODB: %s", t.getMessage());
        }
    }

    /** Reexecuta a reconciliação contra o banco configurado (usado pelo health check). */
    public DiagnosticoIndices verificarAgora() {
        return inicializarIndices(mongoClient.getDatabase(databaseName));
    }

    public DiagnosticoIndices getUltimoDiagnostico() {
        return ultimoDiagnostico;
    }

    public Throwable getErroInicializacao() {
        return erroInicializacao;
    }

    public boolean isInicializado() {
        return inicializado;
    }

    /** {@code true} apenas quando não há conflito, ausência nem falha de execução. */
    public boolean isInicializadoSucesso() {
        return inicializado && erroInicializacao == null && ultimoDiagnostico.saudavel();
    }

    /**
 * Cria os índices ausentes e valida os existentes contra a tabela canônica.
 *
 * @return diagnóstico com ausências, conflitos e drift (nunca {@code null})
 */
    public DiagnosticoIndices inicializarIndices(MongoDatabase database) {
        List<ConflitoIndice> conflitos = new ArrayList<>();
        Map<String, Set<String>> declaradosPorColecao = new HashMap<>();

        for (IndicesCanonicos.IndiceCanonico indice : IndicesCanonicos.todos()) {
            String colecao = indice.colecao().nome();
            declaradosPorColecao.computeIfAbsent(colecao, _ -> new HashSet<>()).add(indice.nome());

            MongoCollection<Document> collection = database.getCollection(colecao);
            Document existente = buscarPorNome(collection, indice.nome());

            if (existente == null) {
                Document conflitante = buscarPorChaveEquivalente(collection, indice);
                if (conflitante != null) {
                    conflitos.add(new ConflitoIndice(colecao, indice.nome(),
                            descreverEsperado(indice), descreverEncontrado(conflitante)));
                    continue;
                }
                try {
                    collection.createIndex(indice.chaves(), indice.opcoes());
                    LOG.debugf("Índice '%s' criado na coleção '%s'.", indice.nome(), colecao);
                } catch (RuntimeException ex) {
                    conflitos.add(new ConflitoIndice(colecao, indice.nome(),
                            descreverEsperado(indice), "falha ao criar: " + ex.getMessage()));
                }
                continue;
            }

            String divergencia = comparar(indice, existente);
            if (divergencia != null) {
                conflitos.add(new ConflitoIndice(colecao, indice.nome(),
                        descreverEsperado(indice), divergencia));
            }
        }

        List<String> drift = coletarDrift(database, declaradosPorColecao);
        DiagnosticoIndices diagnostico = new DiagnosticoIndices(conflitos, drift);

        this.ultimoDiagnostico = diagnostico;
        this.inicializado = true;
        this.erroInicializacao = null;

        if (!drift.isEmpty()) {
            LOG.warnf("Drift de índices não declarados (sem remoção automática): %s", drift);
        }
        if (!diagnostico.saudavel()) {
            LOG.warnf("Índices canônicos divergentes: %d conflito(s).", conflitos.size());
            conflitos.forEach(c -> LOG.warnf("  %s", c));
        } else {
            LOG.info("Todos os índices canônicos das 20 coleções estão presentes e compatíveis.");
        }
        return diagnostico;
    }

    private List<String> coletarDrift(MongoDatabase database, Map<String, Set<String>> declaradosPorColecao) {
        List<String> drift = new ArrayList<>();
        for (String colecao : ColecaoTrilhara.nomes()) {
            Set<String> declarados = declaradosPorColecao.getOrDefault(colecao, Set.of());
            for (Document doc : database.getCollection(colecao).listIndexes()) {
                String nome = doc.getString("name");
                if (nome == null || "_id_".equals(nome)) {
                    continue;
                }
                boolean prefixoApp = nome.startsWith("idx_") || nome.startsWith("uk_");
                if (prefixoApp && !declarados.contains(nome)) {
                    drift.add(colecao + "." + nome);
                }
            }
        }
        return drift;
    }

    private Document buscarPorNome(MongoCollection<Document> collection, String nome) {
        for (Document doc : collection.listIndexes()) {
            if (nome.equals(doc.getString("name"))) {
                return doc;
            }
        }
        return null;
    }

    private Document buscarPorChaveEquivalente(MongoCollection<Document> collection,
            IndicesCanonicos.IndiceCanonico indice) {
        BsonDocument esperadas = chavesBson(indice);
        for (Document doc : collection.listIndexes()) {
            if ("_id_".equals(doc.getString("name"))) {
                continue;
            }
            if (chavesEquivalentes(chavesDoDocumento(doc), esperadas)) {
                return doc;
            }
        }
        return null;
    }

    /**
 * Compara o índice declarado com o documento existente.
 *
 * @return {@code null} quando compatível; descrição da divergência caso contrário
 */
    private String comparar(IndicesCanonicos.IndiceCanonico indice, Document existente) {
        if (!chavesEquivalentes(chavesDoDocumento(existente), chavesBson(indice))) {
            return "key=" + json(chavesDoDocumento(existente));
        }

        boolean unicoExistente = Boolean.TRUE.equals(existente.getBoolean("unique", Boolean.FALSE));
        if (unicoExistente != indice.unico()) {
            return "unique=" + unicoExistente;
        }

        Long ttlExistente = lerTtl(existente);
        Integer ttlEsperado = indice.ttlSegundos();
        if (ttlEsperado == null && ttlExistente != null) {
            return "expireAfterSeconds=" + ttlExistente;
        }
        if (ttlEsperado != null && (ttlExistente == null || !ttlExistente.equals(ttlEsperado.longValue()))) {
            return "expireAfterSeconds=" + ttlExistente;
        }

        String parcialEsperadoJson = json(parcialEsperado(indice));
        String parcialExistenteJson = json(parcialDoDocumento(existente));
        if (!parcialEsperadoJson.equals(parcialExistenteJson)) {
            return "partialFilterExpression=" + parcialExistenteJson;
        }

        String idiomaExistente = existente.getString("default_language");
        if (indice.idioma() != null && !indice.idioma().equalsIgnoreCase(idiomaExistente)) {
            return "default_language=" + idiomaExistente;
        }

        return null;
    }

    private String descreverEsperado(IndicesCanonicos.IndiceCanonico indice) {
        return "key=" + json(chavesBson(indice))
                + ", unique=" + indice.unico()
                + ", expireAfterSeconds=" + indice.ttlSegundos()
                + ", partialFilterExpression=" + json(parcialEsperado(indice));
    }

    private String descreverEncontrado(Document existente) {
        return "key=" + json(chavesDoDocumento(existente))
                + ", unique=" + Boolean.TRUE.equals(existente.getBoolean("unique", Boolean.FALSE))
                + ", expireAfterSeconds=" + lerTtl(existente)
                + ", partialFilterExpression=" + json(parcialDoDocumento(existente));
    }

    /** O driver devolve {@code expireAfterSeconds} como Integer ou Long, conforme o valor. */
    private static Long lerTtl(Document doc) {
        Object valor = doc.get("expireAfterSeconds");
        return valor instanceof Number numero ? numero.longValue() : null;
    }

    private static BsonDocument chavesBson(IndicesCanonicos.IndiceCanonico indice) {
        return indice.chaves().toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static BsonDocument chavesDoDocumento(Document doc) {
        Document raw = doc.get("key", Document.class);
        return raw != null ? raw.toBsonDocument() : null;
    }

    private static BsonDocument parcialDoDocumento(Document doc) {
        Document raw = doc.get("partialFilterExpression", Document.class);
        return raw != null ? raw.toBsonDocument() : null;
    }

    private static BsonDocument parcialEsperado(IndicesCanonicos.IndiceCanonico indice) {
        return indice.filtroParcial() == null
                ? null
                : indice.filtroParcial().toBsonDocument(BsonDocument.class,
                        MongoClientSettings.getDefaultCodecRegistry());
    }

    private static String json(BsonDocument doc) {
        return doc != null ? doc.toJson() : "null";
    }

    /**
 * Índices de texto aparecem no MongoDB como {@code {_fts: "text", _ftsx: 1}} com os
 * campos reais em {@code weights}; nesse caso só os campos não-texto são comparados.
 */
    private static boolean chavesEquivalentes(BsonDocument existente, BsonDocument esperado) {
        if (existente == null || esperado == null) {
            return false;
        }
        if (existente.equals(esperado)) {
            return true;
        }
        boolean esperadoTemTexto = esperado.values().stream()
                .anyMatch(v -> v.isString() && "text".equals(v.asString().getValue()));
        if (!esperadoTemTexto || !existente.containsKey("_fts")) {
            return false;
        }
        for (String campo : esperado.keySet()) {
            if (esperado.get(campo).isString() && "text".equals(esperado.get(campo).asString().getValue())) {
                continue;
            }
            if (!existente.containsKey(campo) || !existente.get(campo).equals(esperado.get(campo))) {
                return false;
            }
        }
        return true;
    }
}

