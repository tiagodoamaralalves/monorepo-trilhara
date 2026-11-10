package br.com.trilhara.nota;

import java.time.Instant;
import java.util.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PurgaLixeiraNotasJob {

    private static final Logger LOG = Logger.getLogger(PurgaLixeiraNotasJob.class.getName());

    private final NotaService notaService;

    @Inject
    public PurgaLixeiraNotasJob(NotaService notaService) {
        this.notaService = notaService;
    }

    @Scheduled(cron = "{trilhara.jobs.purga-lixeira-notas.cron:0 30 3 * * ?}")
    public void executarAgendado() {
        executarPurga(Instant.now());
    }

    public int executarPurga(Instant agora) {
        LOG.fine("Iniciando purga diária de itens expirados da lixeira de notas...");
        int totalPurgados = notaService.purgarExpiradasGlobal(agora);
        LOG.fine("Purga de lixeira de notas concluída. Itens removidos definitivamente: " + totalPurgados);
        return totalPurgados;
    }
}
