package br.com.trilhara.auth;

import org.jboss.logging.Logger;

import br.com.trilhara.shared.SystemScope;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Job de retomada da exclusão de conta.
 *
 * <p>{@code @SystemScope}: a varredura é multi-tenant — busca exclusões pendentes de
 * todos os usuários — e não há requisição HTTP de onde tirar {@code usuarioId}.</p>
 */
@SystemScope
@ApplicationScoped
public class AccountDeletionJob {

    private static final Logger LOG = Logger.getLogger(AccountDeletionJob.class);

    private final AccountDeletionService accountDeletionService;

    @Inject
    public AccountDeletionJob(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /**
 * Retomada periódica: a cada 30 s o job varre as contas em exclusão cujo
 * {@code proximaTentativa} já venceu. O backoff (1 s→30 s nas tentativas rápidas, 1 h em
 * {@code RETENTANDO_LENTO}) é decidido por {@code proximaTentativa}, portanto o intervalo
 * curto do job não acelera as retentativas.
 */
    @Scheduled(every = "{trilhara.jobs.account-deletion.every:30s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void reconciliarExclusoesInterrompidas() {
        int processadas = accountDeletionService.processarExclusoesPendentes();
        if (processadas > 0) {
            LOG.infof("event=ACCOUNT_DELETION_SWEEP processadas=%d", processadas);
        }
    }
}
