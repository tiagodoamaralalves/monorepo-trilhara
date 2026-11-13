package br.com.trilhara.push;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.SystemScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fila multi-tenant de entrega de lembretes (D-36b, D-06b).
 *
 * <p>A varredura atravessa todos os tenants por {@code {estado, instanteEntregaUtc}} e por isso
 * vive em uma classe {@link SystemScope}, sem exposição HTTP. O acesso por tenant permanece em
 * {@link LembretePushRepository}.</p>
 *
 * <p><strong>Backoff sem reescrever o agendamento.</strong> {@code instanteEntregaUtc} é
 * imutável na versão. Uma retentativa {@code n} só fica elegível quando
 * {@code instanteEntregaUtc + min(2^n, 900) segundos <= agora}; a janela útil de entrega é
 * medida a partir do mesmo {@code instanteEntregaUtc}, então ela nunca desliza para frente.</p>
 */
@SystemScope
@ApplicationScoped
public class LembretePushFilaRepository {

    /** Teto do backoff em segundos: nunca ultrapassa a janela útil de 15 minutos (900 s). */
    static final long BACKOFF_MAXIMO_SEGUNDOS = 900L;

    private final MongoCollection<Document> collection;

    @Inject
    public LembretePushFilaRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("lembretes_push");
    }

    /**
     * Candidatos prontos para processamento: {@code PENDENTE} cujo backoff já venceu, ou
     * {@code PROCESSANDO} com lease expirado (worker morto).
     */
    public List<LembretePush> listarCandidatosEnvio(Instant agora, int limite) {
        List<LembretePush> lista = new ArrayList<>();
        collection.find(filtroCandidatos(agora))
                .sort(Sorts.ascending("instanteEntregaUtc"))
                .limit(limite)
                .forEach(doc -> lista.add(LembretePushRepository.paraEntidade(doc)));
        return lista;
    }

    /** Filtro de elegibilidade da fila, compartilhado por leitura e claim. */
    static org.bson.conversions.Bson filtroCandidatos(Instant agora) {
        Date dateAgora = Date.from(agora);
        long agoraMillis = agora.toEpochMilli();

        // Toda a aritmética é feita em milissegundos (long): somar double a Date no $add do
        // MongoDB é erro de tipo. `tentativas` é limitado a 30 para evitar overflow de $pow.
        //
        // A base do backoff é `atualizadoEm` (última transição de estado), nunca
        // `instanteEntregaUtc`: usar o instante de entrega faria a retentativa ficar elegível
        // imediatamente, porque ele está no passado. Assim o agendamento permanece imutável e a
        // janela útil continua medida por ele.
        Document baseAtualizacao = new Document("$ifNull", List.of("$atualizadoEm", "$instanteEntregaUtc"));
        Document backoffVencidoExpr = new Document("$lte", List.of(
                new Document("$add", List.of(
                        new Document("$toLong", baseAtualizacao),
                        new Document("$multiply", List.of(
                                new Document("$min", List.of(
                                        new Document("$pow", List.of(2,
                                                new Document("$min", List.of("$tentativas", 30)))),
                                        BACKOFF_MAXIMO_SEGUNDOS)),
                                1000L)))),
                agoraMillis));

        // Janela útil fechada: precisa ser visitado pelo scheduler para virar
        // EXPIRADO_FORA_DA_JANELA — senão o documento ficaria órfão em PENDENTE.
        long janelaMillis = agoraMillis - BACKOFF_MAXIMO_SEGUNDOS * 1000L;
        Document janelaFechada = new Document("$expr", new Document("$lt", List.of(
                new Document("$toLong", "$instanteEntregaUtc"), janelaMillis)));

        // O backoff só existe para retentativas: a primeira tentativa (tentativas == 0) é
        // imediata assim que `instanteEntregaUtc` vence.
        Document primeiraTentativa = new Document("$eq", List.of("$tentativas", 0));
        Document podeProcessar = new Document("$expr", new Document("$or", List.of(
                primeiraTentativa, backoffVencidoExpr)));

        return or(
                and(eq("estado", LembretePush.ESTADO_PENDENTE),
                        lte("instanteEntregaUtc", dateAgora),
                        podeProcessar),
                and(eq("estado", LembretePush.ESTADO_PENDENTE), janelaFechada),
                and(eq("estado", LembretePush.ESTADO_PROCESSANDO),
                        lte("leaseAte", dateAgora)));
    }

    /**
     * Claim atômico {@code PENDENTE|LEASE_EXPIRADO -> PROCESSANDO} com lease.
     *
     * <p>{@code findOneAndUpdate} garante que dois workers nunca processam o mesmo item: apenas
     * o primeiro encontra o documento no estado esperado. {@code tentativas} é incrementado no
     * claim, portanto a versão devolvida já carrega o número da tentativa em curso.</p>
     */
    public Optional<LembretePush> claim(ObjectId id, String leaseToken, Instant agora, Instant leaseAte) {
        var filtro = and(eq("_id", id), filtroCandidatos(agora));
        var update = Updates.combine(
                Updates.set("estado", LembretePush.ESTADO_PROCESSANDO),
                Updates.set("leaseToken", leaseToken),
                Updates.set("leaseAte", Date.from(leaseAte)),
                Updates.inc("tentativas", 1),
                Updates.set("atualizadoEm", Date.from(agora)));

        Document doc = collection.findOneAndUpdate(
                filtro,
                update,
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return Optional.ofNullable(doc).map(LembretePushRepository::paraEntidade);
    }

    /**
     * Conclusão definitiva. Somente o detentor do lease pode concluir — o filtro por
     * {@code leaseToken} é o fencing da concorrência.
     */
    public boolean concluir(ObjectId id, String leaseToken, String estado, Instant agora) {
        var update = Updates.combine(
                Updates.set("estado", estado),
                Updates.set("leaseToken", null),
                Updates.set("leaseAte", null),
                Updates.set("atualizadoEm", Date.from(agora)));
        return collection.updateOne(
                and(eq("_id", id), eq("leaseToken", leaseToken)), update).getModifiedCount() > 0;
    }

    /**
     * Devolve ao estado {@code PENDENTE} preservando {@code instanteEntregaUtc}: o backoff é
     * derivado de {@code tentativas}, já incrementado no claim.
     */
    public boolean devolverParaPendente(ObjectId id, String leaseToken, Instant agora) {
        var update = Updates.combine(
                Updates.set("estado", LembretePush.ESTADO_PENDENTE),
                Updates.set("leaseToken", null),
                Updates.set("leaseAte", null),
                Updates.set("atualizadoEm", Date.from(agora)));
        return collection.updateOne(
                and(eq("_id", id), eq("leaseToken", leaseToken)), update).getModifiedCount() > 0;
    }
}
