package br.com.trilhara.auth;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AccountDeletionJob {

    private static final Logger LOG = Logger.getLogger(AccountDeletionJob.class);

    private final AccountDeletionService accountDeletionService;

    @Inject
    public AccountDeletionJob(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    @Scheduled(every = "{trilhara.jobs.account-deletion.every:5m}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void reconciliarExclusoesInterrompidas() {
        LOG.debug("Iniciando varredura periódica de exclusões de conta pendentes...");
        int processadas = accountDeletionService.processarExclusoesPendentes();
        if (processadas > 0) {
            LOG.infof("Varredura de exclusões pendentes concluiu a limpeza de %d conta(s).", processadas);
        }
    }
}
