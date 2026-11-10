package br.com.trilhara.shared.mongo;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class MongoIndexHealthCheck implements HealthCheck {

    private final MongoIndexInitializer indexInitializer;

    @Inject
    public MongoIndexHealthCheck(MongoIndexInitializer indexInitializer) {
        this.indexInitializer = indexInitializer;
    }

    @Override
    public HealthCheckResponse call() {
        if (indexInitializer != null && indexInitializer.isInicializadoSucesso()) {
            return HealthCheckResponse.up("MongoDB Indexes (19 coleções)");
        }
        Throwable erro = indexInitializer != null ? indexInitializer.getErroInicializacao() : null;
        return HealthCheckResponse.named("MongoDB Indexes (19 coleções)")
                .down()
                .withData("erro", erro != null ? erro.getMessage() : "Índices ainda não inicializados")
                .build();
    }
}
