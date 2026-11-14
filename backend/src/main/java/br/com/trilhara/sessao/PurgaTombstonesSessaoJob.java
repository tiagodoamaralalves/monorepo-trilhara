package br.com.trilhara.sessao;

import java.time.Instant;
import java.util.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Job horário de purga de tombstones de sessão.
 *
 * <p>Remove os tombstones com {@code deletedAt < agora - 24h}. A execução agendada é apenas
 * um gatilho; a lógica vive em {@link SessaoPurgaService} ({@code @SystemScope}) e expõe
 * {@link #executarPurga(Instant)} para teste determinístico com instante injetado.</p>
 */
@ApplicationScoped
public class PurgaTombstonesSessaoJob {

    private static final Logger LOG = Logger.getLogger(PurgaTombstonesSessaoJob.class.getName());

    private final SessaoPurgaService purgaService;

    public PurgaTombstonesSessaoJob(SessaoPurgaService purgaService) {
        this.purgaService = purgaService;
    }

    @Scheduled(
            every = "{trilhara.jobs.purga-tombstones-sessao.every:1h}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void executarAgendado() {
        executarPurga(Instant.now());
    }

    public long executarPurga(Instant agora) {
        LOG.fine("Iniciando purga horária de tombstones de sessão...");
        long totalPurgados = purgaService.purgarTombstones(agora);
        LOG.fine("Purga de tombstones de sessão concluída. Removidos: " + totalPurgados);
        return totalPurgados;
    }
}
