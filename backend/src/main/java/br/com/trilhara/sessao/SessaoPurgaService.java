package br.com.trilhara.sessao;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

import br.com.trilhara.shared.SystemScope;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Purga de tombstones de sessão.
 *
 * <p>Um tombstone existe por {@value #RETENCAO_HORAS} horas para impedir recriação acidental em
 * retry de idempotência. Depois disso, a chave fica livre e o documento é removido
 * definitivamente. A operação é <strong>multi-tenant</strong> — não recebe {@code usuarioId} —
 * e por isso vive em uma classe {@link SystemScope}, sem exposição HTTP.</p>
 */
@SystemScope
@ApplicationScoped
public class SessaoPurgaService {

    /** Janela de retenção do tombstone, em horas. */
    public static final int RETENCAO_HORAS = 24;

    private static final Logger LOG = Logger.getLogger(SessaoPurgaService.class.getName());

    private final SessaoRepository repository;

    public SessaoPurgaService(SessaoRepository repository) {
        this.repository = repository;
    }

    /**
 * Remove tombstones cujo {@code deletedAt} é anterior a {@code agora - 24h}.
 *
 * @param agora instante de referência (injetável para teste determinístico)
 * @return quantidade de tombstones removidos definitivamente
 */
    public long purgarTombstones(Instant agora) {
        Instant referencia = agora != null ? agora : Instant.now();
        Instant limite = referencia.minus(Duration.ofHours(RETENCAO_HORAS));
        long removidos = repository.purgarTombstonesAnteriores(limite);
        if (removidos > 0) {
            LOG.fine("Purga de tombstones de sessão concluída. Tombstones removidos: " + removidos);
        }
        return removidos;
    }
}
