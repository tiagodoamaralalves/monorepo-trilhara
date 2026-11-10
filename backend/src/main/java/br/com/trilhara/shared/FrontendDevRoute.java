package br.com.trilhara.shared;

import java.nio.file.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * No modo dev, serve o frontend direto da pasta de origem para que alterações em
 * HTML/CSS/JS apareçam com um simples refresh, sem reiniciar o Quarkus.
 */
@IfBuildProfile("dev")
@ApplicationScoped
public class FrontendDevRoute {

    void registrar(@Observes Router router,
            @ConfigProperty(name = "app.frontend.dir", defaultValue = "../frontend-trilhara-javascript") String dir) {
        String raiz = Path.of(dir).toAbsolutePath().normalize().toString();
        router.route().order(Integer.MIN_VALUE)
                .handler(StaticHandler.create(FileSystemAccess.ROOT, raiz).setCachingEnabled(false));
    }
}
