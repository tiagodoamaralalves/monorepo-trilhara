package br.com.trilhara.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.auth.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Purga de tombstones de sessão.
 *
 * <p>O tombstone é retido por 24h para bloquear recriação acidental em retry de idempotência;
 * depois disso, o job horário ({@link PurgaTombstonesSessaoJob}, via {@code @SystemScope})
 * remove o documento definitivamente e libera a chave.</p>
 */
@QuarkusTest
@DisplayName("Purga de tombstones de sessão")
class PurgaTombstonesSessaoJobTest {

    @Inject
    SessaoRepository sessaoRepository;

    @Inject
    SessaoPurgaService purgaService;

    @Inject
    PurgaTombstonesSessaoJob job;

    @Inject
    UserRepository userRepository;

    @Inject
    MongoClient mongoClient;

    private MongoCollection<Document> colecaoSessoes() {
        return mongoClient.getDatabase("trilhara_test").getCollection("sessoes");
    }

    private ObjectId novoUsuario() {
        ObjectId usuarioId = new ObjectId();
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario Purga ",
                "purga-r4-" + usuarioId.toHexString() + "@trilhara.com.br",
                "$pbkdf2-sha512$mock",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        userRepository.inserir(u);
        return usuarioId;
    }

    /** Tombstone mínimo cru: apenas os campos que permite reter. */
    private ObjectId inserirTombstone(ObjectId usuarioId, Instant deletedAt) {
        ObjectId id = new ObjectId();
        colecaoSessoes().insertOne(new Document("_id", id)
                .append("usuarioId", usuarioId)
                .append("trilhaId", new ObjectId())
                .append("chaveIdempotencia", "chave-" + UUID.randomUUID())
                .append("requestHash", "hash-" + UUID.randomUUID())
                .append("deletedAt", Date.from(deletedAt)));
        return id;
    }

    @Test
    @DisplayName("Deve remover apenas tombstones com deletedAt anterior a agora-24h")
    void deveRemoverApenasTombstonesExpirados() {
        ObjectId usuarioId = novoUsuario();
        Instant agora = Instant.now();

        ObjectId expirado = inserirTombstone(usuarioId, agora.minus(Duration.ofHours(25)));
        ObjectId recente = inserirTombstone(usuarioId, agora.minus(Duration.ofHours(1)));
        ObjectId noLimite = inserirTombstone(usuarioId, agora.minus(Duration.ofHours(24)).plusSeconds(60));

        job.executarPurga(agora);

        assertEquals(0, colecaoSessoes().countDocuments(new Document("_id", expirado)),
                "tombstone de 25h deve ter sido removido");
        assertEquals(1, colecaoSessoes().countDocuments(new Document("_id", recente)),
                "tombstone de 1h deve ser preservado");
        assertEquals(1, colecaoSessoes().countDocuments(new Document("_id", noLimite)),
                "tombstone dentro das 24h deve ser preservado");
    }

    @Test
    @DisplayName("Purga não pode remover sessões vivas (sem deletedAt)")
    void naoDeveRemoverSessoesVivas() {
        ObjectId usuarioId = novoUsuario();
        ObjectId viva = new ObjectId();
        colecaoSessoes().insertOne(new Document("_id", viva)
                .append("usuarioId", usuarioId)
                .append("trilhaId", new ObjectId())
                .append("disciplinaId", new ObjectId())
                .append("dataSessao", "2026-11-12")
                .append("duracaoSegundos", 95)
                .append("questoes", 0)
                .append("acertos", 0)
                .append("chaveIdempotencia", "chave-viva-" + UUID.randomUUID())
                .append("requestHash", "hash-viva")
                .append("criadoEm", Date.from(Instant.now().minus(Duration.ofDays(40))))
                .append("atualizadoEm", Date.from(Instant.now())));

        job.executarPurga(Instant.now());

        assertEquals(1, colecaoSessoes().countDocuments(new Document("_id", viva)),
                "sessão viva nunca é tocada pela purga de tombstones");
    }

    @Test
    @DisplayName("Serviço de purga deve usar a janela de retenção de 24h")
    void deveUsarJanelaDeRetencaoDe24h() {
        assertEquals(24, SessaoPurgaService.RETENCAO_HORAS);

        ObjectId usuarioId = novoUsuario();
        Instant agora = Instant.now();

        ObjectId exatamente23h = inserirTombstone(usuarioId, agora.minus(Duration.ofHours(23)));
        ObjectId acimaDe24h = inserirTombstone(usuarioId,
                agora.minus(Duration.ofHours(24)).minusSeconds(5));

        purgaService.purgarTombstones(agora);

        assertEquals(1, colecaoSessoes().countDocuments(new Document("_id", exatamente23h)));
        assertEquals(0, colecaoSessoes().countDocuments(new Document("_id", acimaDe24h)));
    }

    @Test
    @DisplayName("Job deve ser idempotente quando não há tombstones expirados")
    void deveSerIdempotenteSemExpirados() {
        Instant agora = Instant.now();
        inserirTombstone(novoUsuario(), agora.minus(Duration.ofMinutes(5)));

        long primeira = purgaService.purgarTombstones(agora);
        long segunda = purgaService.purgarTombstones(agora);

        assertEquals(0, primeira, "sem tombstones expirados, nada é removido");
        assertEquals(0, segunda, "sem tombstones expirados, nada é removido");
    }
}
