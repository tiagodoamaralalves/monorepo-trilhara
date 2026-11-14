package br.com.trilhara.tarefa;

import java.time.Instant;

import br.com.trilhara.shared.SystemScope;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Purga diária da lixeira de tarefas (30 dias, §7.3 e ).
 *
 * <p>A rotina varre todos os tenants por {@code expiresAt}, por definição uma operação
 * multi-tenant: vive em uma classe {@link SystemScope}, sem exposição HTTP. O gatilho de
 * agenda fica em {@link PurgaLixeiraTarefasJob}.</p>
 */
@SystemScope
@ApplicationScoped
public class TarefaPurgaService {

    private final ListaTarefasRepository listaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final TarefaRepository tarefaRepository;

    public TarefaPurgaService(
            ListaTarefasRepository listaRepository,
            SerieRecorrenteRepository serieRepository,
            TarefaRepository tarefaRepository) {
        this.listaRepository = listaRepository;
        this.serieRepository = serieRepository;
        this.tarefaRepository = tarefaRepository;
    }

    /**
 * Executa a purga dos itens expirados.
 *
 * <p>Estados intermediários (operação composta sem transação):
 * primeiro as séries sobreviventes perdem {@code listaId}, depois as listas são
 * apagadas; tarefas e séries expiradas são removidas em seguida. Repetir a rotina é
 * seguro — os filtros são apenas temporais e documentos já removidos não reaparecem.</p>
 *
 * @return total de documentos removidos definitivamente
 */
    public int purgarExpiradas(Instant agora) {
        var listasExpiradas = listaRepository.listarExpiradas(agora);
        for (ListaTarefas lista : listasExpiradas) {
            serieRepository.desvincularLista(lista.usuarioId(), lista.id());
        }

        long tarefasRemovidas = tarefaRepository.purgarExpiradas(agora);
        long seriesRemovidas = serieRepository.purgarExpiradas(agora);
        long listasRemovidas = listaRepository.purgarExpiradas(agora);

        return (int) (tarefasRemovidas + seriesRemovidas + listasRemovidas);
    }
}
