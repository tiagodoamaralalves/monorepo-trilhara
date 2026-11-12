package br.com.trilhara.cronograma;

import java.time.Instant;
import java.util.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Gatilho do reconciliador de cronogramas (D-18b).
 *
 * <p>Roda no <strong>startup</strong> (para fechar publicações interrompidas por um desligamento) e
 * a cada 5 minutos. A execução agendada é apenas o gatilho; a lógica vive em
 * {@link CronogramaReconciliacaoService} ({@code @SystemScope}) e expõe
 * {@link #executarReconciliacao(Instant)} para teste determinístico.</p>
 */
@ApplicationScoped
public class ReconciliadorCronogramasJob {

    private static final Logger LOG = Logger.getLogger(ReconciliadorCronogramasJob.class.getName());

    private final CronogramaReconciliacaoService reconciliacaoService;

    public ReconciliadorCronogramasJob(CronogramaReconciliacaoService reconciliacaoService) {
        this.reconciliacaoService = reconciliacaoService;
    }

    void aoIniciar(@Observes StartupEvent event) {
        executarReconciliacao(Instant.now());
    }

    @Scheduled(
            every = "{trilhara.jobs.reconciliador-cronogramas.every:5m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void executarAgendado() {
        executarReconciliacao(Instant.now());
    }

    public int executarReconciliacao(Instant agora) {
        int tratados = reconciliacaoService.reconciliar(agora);
        if (tratados > 0) {
            LOG.info(() -> "Reconciliação de cronogramas concluída. Tratados: " + tratados);
        }
        return tratados;
    }
}
