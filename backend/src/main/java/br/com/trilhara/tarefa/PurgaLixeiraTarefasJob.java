package br.com.trilhara.tarefa;

import java.time.Instant;
import java.util.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PurgaLixeiraTarefasJob {

    private static final Logger LOG = Logger.getLogger(PurgaLixeiraTarefasJob.class.getName());

    private final TarefaService tarefaService;

    public PurgaLixeiraTarefasJob(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    @Scheduled(cron = "{trilhara.jobs.purga-lixeira-tarefas.cron:0 30 3 * * ?}")
    public void executarAgendado() {
        executarPurga(Instant.now());
    }

    public int executarPurga(Instant agora) {
        LOG.fine("Iniciando purga diária de itens expirados da lixeira de tarefas...");
        int totalPurgados = tarefaService.purgarExpiradasGlobal(agora);
        LOG.fine("Purga de lixeira concluída. Itens removidos definitivamente: " + totalPurgados);
        return totalPurgados;
    }
}
