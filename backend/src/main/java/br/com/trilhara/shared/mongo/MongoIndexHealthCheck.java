package br.com.trilhara.shared.mongo;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Readiness de índices.
 *
 * <p>{@code DOWN} se faltar índice declarado ou houver conflito de chave/opções. Índices
 * {@code idx_}/{@code uk_} não declarados são reportados como dado de diagnóstico (drift),
 * sem derrubar o serviço.</p>
 */
@Readiness
@ApplicationScoped
public class MongoIndexHealthCheck implements HealthCheck {

    static final String NOME = "MongoDB Indexes (20 coleções)";
    private static final String CHAVE_DRIFT = "drift";

    private final Supplier<DiagnosticoIndices> diagnosticoAtual;
    private final BooleanSupplier jaInicializado;
    private final Supplier<DiagnosticoIndices> revalidar;
    private final Duration intervaloRevalidacao;

    private volatile Instant ultimaVerificacao = Instant.EPOCH;

    @Inject
    public MongoIndexHealthCheck(
            MongoIndexInitializer indexInitializer,
            @ConfigProperty(name = "trilhara.mongo.health.interval-seconds", defaultValue = "30") long intervaloSegundos) {
        this(indexInitializer::getUltimoDiagnostico, indexInitializer::isInicializado,
                indexInitializer::verificarAgora, intervaloSegundos);
    }

    /** Construtor de teste: permite injetar o diagnóstico sem tocar no banco. */
    MongoIndexHealthCheck(
            Supplier<DiagnosticoIndices> diagnosticoAtual,
            BooleanSupplier jaInicializado,
            Supplier<DiagnosticoIndices> revalidar,
            long intervaloSegundos) {
        this.diagnosticoAtual = diagnosticoAtual;
        this.jaInicializado = jaInicializado;
        this.revalidar = revalidar;
        this.intervaloRevalidacao = Duration.ofSeconds(Math.max(1, intervaloSegundos));
    }

    @Override
    public HealthCheckResponse call() {
        DiagnosticoIndices diagnostico;
        try {
            diagnostico = resolverDiagnostico();
        } catch (RuntimeException ex) {
            return HealthCheckResponse.named(NOME)
                    .down()
                    .withData("erro", String.valueOf(ex.getMessage()))
                    .build();
        }

        if (!diagnostico.saudavel()) {
            HealthCheckResponseBuilder builder = HealthCheckResponse.named(NOME).down();
            for (ConflitoIndice conflito : diagnostico.conflitos()) {
                builder = builder.withData(conflito.colecao() + "." + conflito.indice(),
                        "esperado [" + conflito.esperado() + "] encontrado [" + conflito.encontrado() + "]");
            }
            return anexarDrift(builder, diagnostico).build();
        }

        return anexarDrift(HealthCheckResponse.named(NOME).up(), diagnostico).build();
    }

    private DiagnosticoIndices resolverDiagnostico() {
        Instant agora = Instant.now();
        if (jaInicializado.getAsBoolean()
                && Duration.between(ultimaVerificacao, agora).compareTo(intervaloRevalidacao) < 0) {
            return diagnosticoAtual.get();
        }
        DiagnosticoIndices revalidado = revalidar.get();
        ultimaVerificacao = Instant.now();
        return revalidado;
    }

    private static HealthCheckResponseBuilder anexarDrift(HealthCheckResponseBuilder builder,
            DiagnosticoIndices diagnostico) {
        if (!diagnostico.drift().isEmpty()) {
            return builder.withData(CHAVE_DRIFT, String.join(", ", diagnostico.drift()));
        }
        return builder;
    }
}
