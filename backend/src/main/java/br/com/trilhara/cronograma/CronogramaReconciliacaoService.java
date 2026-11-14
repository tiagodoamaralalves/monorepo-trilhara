package br.com.trilhara.cronograma;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.SystemScope;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reconciliação idempotente de cronogramas interrompidos.
 *
 * <p>Executa fora do escopo de um tenant ({@link SystemScope}) e trata os dois estados
 * intermediários da publicação versionada:</p>
 * <ul>
 * <li>{@code PREPARANDO} com lease expirado → o worker morreu antes de publicar; o staging é
 * órfão e é removido, e o cronograma volta ao último estado publicado (ou é desativado, se
 * nunca chegou a publicar);</li>
 * <li>{@code RECUPERACAO_NECESSARIA} → o ponteiro da trilha já aponta para a versão nova, mas
 * flags/staging podem estar inconsistentes; a reconciliação reaplica ponteiro e flags e
 * limpa as versões antigas.</li>
 * </ul>
 *
 * <p>Todas as operações são idempotentes: rodar o reconciliador N vezes produz o mesmo resultado
 * de rodar uma vez.</p>
 */
@SystemScope
@ApplicationScoped
public class CronogramaReconciliacaoService {

    private static final Logger LOG = Logger.getLogger(CronogramaReconciliacaoService.class.getName());

    static final String ESTADO_ATIVO = "ATIVO";
    static final String ESTADO_PREPARANDO = "PREPARANDO";
    static final String ESTADO_RECUPERACAO = "RECUPERACAO_NECESSARIA";

    private final CronogramaRepository cronogramaRepository;
    private final BlocoCronogramaRepository blocoCronogramaRepository;
    private final TrilhaRepository trilhaRepository;

    public CronogramaReconciliacaoService(
            CronogramaRepository cronogramaRepository,
            BlocoCronogramaRepository blocoCronogramaRepository,
            TrilhaRepository trilhaRepository) {
        this.cronogramaRepository = cronogramaRepository;
        this.blocoCronogramaRepository = blocoCronogramaRepository;
        this.trilhaRepository = trilhaRepository;
    }

    /** Executa a reconciliação e devolve quantos cronogramas foram tratados. */
    public int reconciliar(Instant agora) {
        int tratados = 0;
        tratados += reconciliarPreparandoOrfaos();
        tratados += reconciliarRecuperacaoNecessaria();
        return tratados;
    }

    /**
 * {@code PREPARANDO} cujo lease da trilha expirou: nenhum worker vai publicar essa versão.
 * Remove os blocos do staging e devolve o cronograma ao estado publicado anterior.
 */
    int reconciliarPreparandoOrfaos() {
        int tratados = 0;
        for (Cronograma cronograma : cronogramaRepository.listarPorEstadoPublicacao(ESTADO_PREPARANDO)) {
            Long versaoPreparada = cronograma.versaoPreparada();
            if (versaoPreparada == null) {
                // Criação interrompida antes de qualquer bloco: desativa e limpa.
                blocoCronogramaRepository.excluirPorCronograma(
                        cronograma.usuarioId(), cronograma.trilhaId(), cronograma.id());
                cronogramaRepository.atualizarEstadoPublicacao(
                        cronograma.usuarioId(), cronograma.id(), ESTADO_ATIVO);
                continue;
            }
            if (leaseExpirado(cronograma.usuarioId(), cronograma.trilhaId())) {
                blocoCronogramaRepository.excluirPorCronograma(
                        cronograma.usuarioId(), cronograma.trilhaId(), cronograma.id());
                cronogramaRepository.atualizarEstadoPublicacao(
                        cronograma.usuarioId(), cronograma.id(), ESTADO_ATIVO);
                LOG.info(() -> "Staging órfão removido (cronogramaId=" + cronograma.idHex()
                        + ", versao=" + versaoPreparada + ")");
                tratados++;
            }
        }
        return tratados;
    }

    /**
 * {@code RECUPERACAO_NECESSARIA}: reaplica a publicação da {@code versaoAtiva} de forma
 * idempotente — ponteiro da trilha, flags {@code ativo} e limpeza das versões antigas.
 */
    int reconciliarRecuperacaoNecessaria() {
        int tratados = 0;
        for (Cronograma cronograma : cronogramaRepository.listarPorEstadoPublicacao(ESTADO_RECUPERACAO)) {
            ObjectId usuarioId = cronograma.usuarioId();
            ObjectId trilhaId = cronograma.trilhaId();
            ObjectId cronogramaId = cronograma.id();

            // Só publica se os blocos da versão ativa existirem por completo.
            long blocosDaVersaoAtiva = blocoCronogramaRepository.contarPorVersao(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva());
            boolean prontaParaPublicar = blocosDaVersaoAtiva > 0;
            if (!prontaParaPublicar) {
                continue;
            }

            trilhaRepository.atualizarPonteiroCronogramaAtivoSemFencing(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva());
            cronogramaRepository.desativarOutros(usuarioId, trilhaId, cronogramaId);
            blocoCronogramaRepository.excluirVersoesDiferentes(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva());
            cronogramaRepository.atualizarEstadoPublicacao(usuarioId, cronogramaId, ESTADO_ATIVO);
            LOG.info(() -> "Cronograma reconciliado (cronogramaId=" + cronograma.idHex()
                    + ", versao=" + cronograma.versaoAtiva() + ")");
            tratados++;
        }
        return tratados;
    }

    private boolean leaseExpirado(ObjectId usuarioId, ObjectId trilhaId) {
        return trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .map(doc -> {
                    java.util.Date ate = doc.getDate("geracaoCronogramaEmAndamentoAte");
                    return ate == null || ate.toInstant().isBefore(Instant.now());
                })
                .orElse(true);
    }
}
