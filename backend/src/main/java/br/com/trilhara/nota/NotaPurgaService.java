package br.com.trilhara.nota;

import java.time.Instant;
import java.util.logging.Logger;

import br.com.trilhara.shared.SystemScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Purga definitiva da lixeira de Notas (D-06b/D-34).
 *
 * <p>É uma varredura multi-tenant por {@code expiresAt}: vive em uma classe {@link SystemScope},
 * sem exposição HTTP, e portanto não recebe {@code usuarioId}. O gatilho é o
 * {@link PurgaLixeiraNotasJob} agendado; o corpo da operação fica aqui para ser exercitável pelo
 * teste de integração sem depender do scheduler.</p>
 */
@SystemScope
@ApplicationScoped
public class NotaPurgaService {

    private static final Logger LOG = Logger.getLogger(NotaPurgaService.class.getName());

    private final NotaService notaService;

    @Inject
    public NotaPurgaService(NotaService notaService) {
        this.notaService = notaService;
    }

    /** Remove definitivamente páginas, seções e cadernos com {@code expiresAt <= agora}. */
    public int purgarExpirados(Instant agora) {
        int total = notaService.purgarExpiradasGlobal(agora);
        LOG.fine("Purga da lixeira de Notas concluída. Itens removidos: " + total);
        return total;
    }
}
