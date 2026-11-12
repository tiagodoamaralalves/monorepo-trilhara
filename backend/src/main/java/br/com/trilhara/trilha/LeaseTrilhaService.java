package br.com.trilhara.trilha;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

/**
 * Aquisição e liberação do lease de coordenação de uma trilha
 * ({@code geracaoCronogramaLeaseToken} + {@code geracaoCronogramaEmAndamentoAte}).
 *
 * <p>Requisitos §5.4: token de fencing UUID, expiração em 2 minutos e operação
 * concorrente respondendo {@code 409 SCHEDULE_GENERATION_IN_PROGRESS}. O lease é o
 * mesmo para criação/regeneração/ativação/exclusão de cronograma e para as exclusões
 * de disciplina e de trilha, de modo que essas operações nunca se sobreponham.</p>
 *
 * <p>A aquisição distingue trilha inexistente ({@code 404}, sem revelar se pertence a
 * outro usuário) de trilha ocupada ({@code 409}).</p>
 */
@ApplicationScoped
public class LeaseTrilhaService {

    /** Requisitos §5.4: lease expira em 2 minutos. */
    public static final long TTL_SEGUNDOS = 120L;

    static final String MSG_CONFLITO_LEASE =
            "Geração de cronograma ou modificação concorrente em andamento para esta trilha.";
    private static final String MSG_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";

    private final TrilhaRepository trilhaRepository;

    public LeaseTrilhaService(TrilhaRepository trilhaRepository) {
        this.trilhaRepository = trilhaRepository;
    }

    /**
     * Adquire o lease ou falha com {@code 409 SCHEDULE_GENERATION_IN_PROGRESS}
     * (ou {@code 404} quando a trilha não existe para o usuário).
     */
    public String adquirirOuFalhar(ObjectId usuarioId, ObjectId trilhaId) {
        return adquirirOuFalhar(usuarioId, trilhaId, UUID.randomUUID().toString());
    }

    /**
     * Adquire o lease com um token de fencing já conhecido pelo chamador, que precisa reutilizá-lo
     * nas liberações posteriores (operações compostas de cronograma).
     */
    public String adquirirOuFalhar(ObjectId usuarioId, ObjectId trilhaId, String leaseToken) {
        Instant expiraEm = Instant.now().plusSeconds(TTL_SEGUNDOS);

        if (!trilhaRepository.adquirirLease(usuarioId, trilhaId, leaseToken, expiraEm)) {
            trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(MSG_TRILHA_NAO_ENCONTRADA));
            throw new DomainConflictException(CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, MSG_CONFLITO_LEASE);
        }
        return leaseToken;
    }

    /** Libera o lease apenas se o token ainda for o dono (fencing). */
    public void liberar(ObjectId usuarioId, ObjectId trilhaId, String leaseToken) {
        trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
    }

    /**
     * Executa a operação sob lease, liberando em {@code finally} em qualquer desfecho.
     *
     * <p>Não há transação multi-documento: uma falha no meio deixa os efeitos parciais
     * descritos no Javadoc de quem chama. A liberação do lease é a única garantia de
     * que a próxima operação concorrente poderá tentar novamente.</p>
     */
    public void executarComLease(ObjectId usuarioId, ObjectId trilhaId, Runnable operacao) {
        String leaseToken = adquirirOuFalhar(usuarioId, trilhaId);
        try {
            operacao.run();
        } finally {
            liberar(usuarioId, trilhaId, leaseToken);
        }
    }
}
