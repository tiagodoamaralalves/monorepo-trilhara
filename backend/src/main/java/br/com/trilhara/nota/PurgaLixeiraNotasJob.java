package br.com.trilhara.nota;

import java.time.Instant;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Disparo diário da purga da lixeira de Notas.
 *
 * <p>O job não faz a operação: delega a {@link NotaPurgaService}, que é a classe
 * {@code @SystemScope} autorizada a varrer todos os tenants por {@code expiresAt}.</p>
 */
@ApplicationScoped
public class PurgaLixeiraNotasJob {

    private final NotaPurgaService purgaService;

    @Inject
    public PurgaLixeiraNotasJob(NotaPurgaService purgaService) {
        this.purgaService = purgaService;
    }

    @Scheduled(cron = "${trilhara.jobs.purga-lixeira-notas.cron:0 30 3 * * ?}")
    public void executarAgendado() {
        executarPurga(Instant.now());
    }

    public int executarPurga(Instant agora) {
        return purgaService.purgarExpirados(agora);
    }
}
