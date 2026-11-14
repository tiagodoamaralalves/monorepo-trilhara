package br.com.trilhara.nota;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.trilhara.shared.SystemScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Purga definitiva da lixeira de Notas.
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

    private final NotaLixeiraService notaLixeiraService;

    @Inject
    public NotaPurgaService(NotaLixeiraService notaLixeiraService) {
        this.notaLixeiraService = notaLixeiraService;
    }

    /** Remove definitivamente páginas, seções e cadernos com {@code expiresAt <= agora}. */
    public int purgarExpirados(Instant agora) {
        int total = notaLixeiraService.purgarExpiradasGlobal(agora);
        LOG.log(Level.FINE, "Purga da lixeira de Notas concluída. Itens removidos: {0}", total);
        return total;
    }
}
