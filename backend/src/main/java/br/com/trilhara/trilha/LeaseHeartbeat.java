package br.com.trilhara.trilha;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;

/**
 * Heartbeat de renovação do lease de uma trilha.
 *
 * <p>Enquanto uma publicação versionada está em andamento, o lease precisa continuar pertencendo
 * ao mesmo token. Um {@link ScheduledExecutorService} renova o lease a cada
 * {@link LeaseTrilhaService#INTERVALO_RENOVACAO_SEGUNDOS} segundos. Se a renovação falhar (token
 * roubado após expiração), o heartbeat marca a perda e {@link #verificarVivo} passa a lançar
 * {@code 409 SCHEDULE_GENERATION_IN_PROGRESS}: a publicação é abortada <strong>sem</strong> trocar
 * o ponteiro.</p>
 *
 * <p>Os testes usam o construtor com intervalo explícito para não esperar 30 s.</p>
 */
public final class LeaseHeartbeat implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LeaseHeartbeat.class.getName());

    private final LeaseTrilhaService leaseService;
    private final ObjectId usuarioId;
    private final ObjectId trilhaId;
    private final String token;
    private final long intervaloSegundos;
    private final AtomicBoolean vivo = new AtomicBoolean(true);
    private final ScheduledExecutorService scheduler;

    private LeaseHeartbeat(
            LeaseTrilhaService leaseService,
            ObjectId usuarioId,
            ObjectId trilhaId,
            String token,
            long intervaloSegundos) {
        this.leaseService = leaseService;
        this.usuarioId = usuarioId;
        this.trilhaId = trilhaId;
        this.token = token;
        this.intervaloSegundos = intervaloSegundos;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lease-heartbeat-" + trilhaId.toHexString());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Inicia o heartbeat com o intervalo normativo de 30 s. */
    public static LeaseHeartbeat iniciar(
            LeaseTrilhaService leaseService, ObjectId usuarioId, ObjectId trilhaId, String token) {
        return iniciar(leaseService, usuarioId, trilhaId, token, LeaseTrilhaService.INTERVALO_RENOVACAO_SEGUNDOS);
    }

    /** Inicia o heartbeat com intervalo explícito (usado por testes). */
    public static LeaseHeartbeat iniciar(
            LeaseTrilhaService leaseService,
            ObjectId usuarioId,
            ObjectId trilhaId,
            String token,
            long intervaloSegundos) {
        LeaseHeartbeat heartbeat = new LeaseHeartbeat(leaseService, usuarioId, trilhaId, token, intervaloSegundos);
        heartbeat.agendar();
        return heartbeat;
    }

    private void agendar() {
        ScheduledFuture<?> futuro = scheduler.scheduleAtFixedRate(
                this::renovar, intervaloSegundos, intervaloSegundos, TimeUnit.SECONDS);
        if (futuro.isCancelled()) {
            vivo.set(false);
        }
    }

    private void renovar() {
        if (!vivo.get()) {
            return;
        }
        try {
            if (!leaseService.renovar(usuarioId, trilhaId, token)) {
                vivo.set(false);
                LOG.warning(() -> "Lease perdido durante a publicação; renovação recusada (fencing).");
            }
        } catch (RuntimeException ex) {
            vivo.set(false);
            LOG.log(Level.WARNING, "Falha ao renovar o lease; publicação será abortada.", ex);
        }
    }

    /** {@code true} enquanto o token continuar sendo o dono do lease. */
    public boolean vivo() {
        return vivo.get();
    }

    /**
 * Confirma, contra o banco, que o token ainda é o dono. Chamado antes de cada etapa da
 * publicação: barra tanto a perda detectada pelo heartbeat quanto a expiração silenciosa.
 */
    public void verificarVivo() {
        if (!vivo.get() || !leaseService.aindaDetem(usuarioId, trilhaId, token)) {
            vivo.set(false);
            throw new DomainConflictException(
                    CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, LeaseTrilhaService.MSG_LEASE_PERDIDO);
        }
    }

    public String token() {
        return token;
    }

    /** Marca o lease como perdido sem esperar a próxima renovação (usado por testes e falhas injetadas). */
    public void marcarPerdido() {
        vivo.set(false);
    }

    @Override
    public void close() {
        vivo.set(false);
        scheduler.shutdownNow();
    }
}
