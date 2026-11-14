package br.com.trilhara.tarefa;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Gatilho diário da purga da lixeira de tarefas.
 *
 * <p>A lógica multi-tenant vive em {@link TarefaPurgaService} ({@code @SystemScope}); este
 * job apenas agenda. Conclusão de tarefa nunca é purgada por idade: só documentos com
 * {@code expiresAt} vencido são removidos.</p>
 */
@ApplicationScoped
public class PurgaLixeiraTarefasJob {

    private static final Logger LOG = Logger.getLogger(PurgaLixeiraTarefasJob.class.getName());

    private final TarefaPurgaService purgaService;

    public PurgaLixeiraTarefasJob(TarefaPurgaService purgaService) {
        this.purgaService = purgaService;
    }

    @Scheduled(cron = "${trilhara.jobs.purga-lixeira-tarefas.cron:0 30 3 * * ?}")
    public void executarAgendado() {
        executarPurga(Instant.now());
    }

    public int executarPurga(Instant agora) {
        LOG.fine("Iniciando purga diária de itens expirados da lixeira de tarefas...");
        int totalPurgados = purgaService.purgarExpiradas(agora);
        LOG.log(Level.FINE, "Purga de lixeira concluída. Itens removidos definitivamente: {0}", totalPurgados);
        return totalPurgados;
    }
}
