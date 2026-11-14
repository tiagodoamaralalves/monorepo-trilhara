package br.com.trilhara.nota;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;

import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TopicoEmbutido;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Importação, reimportação e sincronização explícita de trilhas de Estudos.
 *
 * <p>Regras cravadas:</p>
 * <ul>
 * <li>Importar sem {@code reimportar=true} quando já existe caderno ativo vinculado responde
 * {@code 409 TRAIL_ALREADY_IMPORTED} com {@code errors.cadernoId} — nunca cria
 * silenciosamente um segundo caderno;</li>
 * <li>Reimportar desvincula o caderno antigo (limpando {@code origemTrilhaId} e
 * {@code origemDisciplinaId} das seções dele) sem apagar conteúdo, e cria um novo caderno
 * com {@code titulo = trilha.nome} — sem sufixo de data;</li>
 * <li>Sincronizar renomeia caderno/seções vinculadas, cria seções com páginas vazias apenas
 * para disciplinas novas e desvincula disciplinas removidas. <strong>Nunca</strong> cria
 * nem altera notas de tópicos em seções existentes.</li>
 * </ul>
 *
 * <p>Não há transação multi-documento: a importação grava caderno, seções e páginas em
 * sequência. Uma falha no meio deixa um caderno parcial, identificável pelo próprio vínculo e
 * recuperável pela reimportação — a operação é composta e o efeito parcial é esse, nunca
 * atomicidade presumida.</p>
 */
@ApplicationScoped
public class NotaSincronizacaoService {

    private static final String TEXTO_0284C7 = "#0284c7";
    private static final String ERRO_CADERNO_NAO_ENCONTRADO = "Caderno não encontrado.";
    private static final String CAMPO_CADERNO_ID = "cadernoId";

    private final CadernoRepository cadernoRepository;
    private final SecaoRepository secaoRepository;
    private final NotaRepository notaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final TrilhaRepository trilhaRepository;

    @Inject
    public NotaSincronizacaoService(
            CadernoRepository cadernoRepository,
            SecaoRepository secaoRepository,
            NotaRepository notaRepository,
            DisciplinaRepository disciplinaRepository,
            TrilhaRepository trilhaRepository) {
        this.cadernoRepository = cadernoRepository;
        this.secaoRepository = secaoRepository;
        this.notaRepository = notaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.trilhaRepository = trilhaRepository;
    }

    /**
 * Importa (ou reimporta) uma trilha como caderno novo.
 *
 * @param reimportar quando {@code true}, desvincula o caderno ativo existente e cria outro;
 * quando {@code false} e já existe vínculo ativo, responde 409
 */
    public CadernoResponse importarTrilha(ObjectId usuarioId, ObjectId trilhaId, boolean reimportar) {
        Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha de estudo não encontrada."));

        Optional<Caderno> cadernoVinculado = cadernoRepository.buscarAtivoPorOrigemTrilha(usuarioId, trilhaId);
        if (cadernoVinculado.isPresent() && !reimportar) {
            throw DomainConflictException.campo(
                    CodigoErro.TRAIL_ALREADY_IMPORTED,
                    "Esta trilha já possui um caderno vinculado. Use a reimportação para criar "
                            + "uma nova cópia; o caderno atual será mantido e desvinculado.",
                    CAMPO_CADERNO_ID,
                    "Trilha já importada.");
        }
        cadernoVinculado.ifPresent(antigo -> desvincularCaderno(usuarioId, antigo));

        String nomeTrilha = trilhaDoc.getString("nome");
        String corTrilha = trilhaDoc.getString("cor");
        Instant agora = Instant.now();

        Caderno novoCaderno = new Caderno(
                new ObjectId(),
                usuarioId,
                nomeTrilha,
                corTrilha != null && !corTrilha.isBlank() ? corTrilha : TEXTO_0284C7,
                "book-open",
                trilhaId,
                null,
                null,
                null,
                agora,
                agora
        );
        cadernoRepository.inserir(novoCaderno);

        List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        long totalPaginasCriadas = 0;

        for (int i = 0; i < disciplinas.size(); i++) {
            Disciplina disciplina = disciplinas.get(i);
            Secao novaSecao = new Secao(
                    new ObjectId(),
                    usuarioId,
                    novoCaderno.id(),
                    disciplina.nome(),
                    disciplina.ordem() != null ? disciplina.ordem() : i,
                    disciplina.id(),
                    null,
                    null,
                    null,
                    agora,
                    agora
            );
            secaoRepository.inserir(novaSecao);
            totalPaginasCriadas += criarNotasParaTopicos(
                    usuarioId, novoCaderno.id(), novaSecao.id(), disciplina.topicos(), agora);
        }

        return new CadernoResponse(
                novoCaderno.idHex(),
                novoCaderno.titulo(),
                novoCaderno.cor(),
                novoCaderno.icone(),
                novoCaderno.origemTrilhaIdHex(),
                nomeTrilha,
                disciplinas.size(),
                totalPaginasCriadas,
                novoCaderno.criadoEm(),
                novoCaderno.atualizadoEm()
        );
    }

    /**
 * Desvincula o caderno de uma trilha preservando todo o conteúdo: o caderno deixa de
 * referenciar a trilha e suas seções perdem {@code origemDisciplinaId}. As notas permanecem
 * intactas, e a referência de origem de tópico não é tocada aqui — ela só é limpada quando
 * a trilha é excluída.
 */
    private void desvincularCaderno(ObjectId usuarioId, Caderno caderno) {
        cadernoRepository.desvincularTrilha(usuarioId, caderno.origemTrilhaId());
        secaoRepository.desvincularOrigemDisciplinaPorCaderno(usuarioId, caderno.id());
    }

    private int criarNotasParaTopicos(
            ObjectId usuarioId,
            ObjectId cadernoId,
            ObjectId secaoId,
            List<TopicoEmbutido> topicos,
            Instant agora) {
        if (topicos == null || topicos.isEmpty()) {
            return 0;
        }
        for (int j = 0; j < topicos.size(); j++) {
            TopicoEmbutido topico = topicos.get(j);
            Nota novaNota = new Nota(
                    new ObjectId(),
                    usuarioId,
                    cadernoId,
                    secaoId,
                    topico.nome(),
                    "",
                    topico.ordem() != null ? topico.ordem() : j,
                    topico.id(),
                    null,
                    null,
                    null,
                    agora,
                    agora
            );
            notaRepository.inserir(novaNota);
        }
        return topicos.size();
    }

    /**
 * Sincronização explícita: renomeia o caderno e as seções vinculadas, cria seções com
 * uma página vazia por tópico apenas para disciplinas novas, e desvincula seções de
 * disciplinas removidas. Páginas de seções existentes nunca são criadas nem alteradas.
 */
    public SincronizacaoTrilhaResponse sincronizarTrilha(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(ERRO_CADERNO_NAO_ENCONTRADO));

        if (caderno.origemTrilhaId() == null) {
            throw new ValidationException(
                    CAMPO_CADERNO_ID, "Caderno não vinculado a uma trilha.");
        }

        ObjectId trilhaId = caderno.origemTrilhaId();
        Optional<Document> trilhaOpt = trilhaRepository.buscarEntidade(usuarioId, trilhaId);

        if (trilhaOpt.isEmpty()) {
            // A trilha foi excluída em Estudos: preserva caderno e páginas, remove as origens.
            desvincularCaderno(usuarioId, caderno);
            return new SincronizacaoTrilhaResponse(cadernoId.toHexString(), 0, 0, 0, 0);
        }

        atualizarTituloCadernoSeMudou(caderno, trilhaOpt.get(), usuarioId);

        List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        Map<ObjectId, Disciplina> disciplinasMap = new HashMap<>();
        for (Disciplina disciplina : disciplinasTrilha) {
            disciplinasMap.put(disciplina.id(), disciplina);
        }

        ResultadoProcessamentoSecoes processadas =
                processarSecoesAtivas(usuarioId, cadernoId, disciplinasMap);
        ResultadoCriacaoNovasSecoes criadas = criarSecoesParaNovasDisciplinas(
                usuarioId, cadernoId, disciplinasTrilha, processadas.disciplinasJaPresentes());

        return new SincronizacaoTrilhaResponse(
                cadernoId.toHexString(),
                criadas.secoesCriadas(),
                processadas.secoesRenomeadas(),
                processadas.secoesDesvinculadas(),
                criadas.paginasCriadas()
        );
    }

    private void atualizarTituloCadernoSeMudou(Caderno caderno, Document trilhaDoc, ObjectId usuarioId) {
        String nomeAtualTrilha = trilhaDoc.getString("nome");
        if (Objects.equals(caderno.titulo(), nomeAtualTrilha)) {
            return;
        }
        Caderno renomeado = new Caderno(
                caderno.id(),
                usuarioId,
                nomeAtualTrilha,
                caderno.cor(),
                caderno.icone(),
                caderno.origemTrilhaId(),
                caderno.deletedAt(),
                caderno.expiresAt(),
                caderno.loteExclusaoId(),
                caderno.criadoEm(),
                Instant.now()
        );
        cadernoRepository.atualizar(renomeado);
    }

    private record ResultadoProcessamentoSecoes(
            Set<ObjectId> disciplinasJaPresentes, int secoesRenomeadas, int secoesDesvinculadas) {
    }

    private ResultadoProcessamentoSecoes processarSecoesAtivas(
            ObjectId usuarioId, ObjectId cadernoId, Map<ObjectId, Disciplina> disciplinasMap) {
        List<Secao> secoesAtivas = secaoRepository.listarPorCaderno(usuarioId, cadernoId);
        Set<ObjectId> disciplinasJaPresentes = new HashSet<>();
        int secoesRenomeadas = 0;
        int secoesDesvinculadas = 0;

        for (Secao secao : secoesAtivas) {
            if (secao.origemDisciplinaId() == null) {
                // Seção criada localmente pelo usuário: permanece intocada.
                continue;
            }
            Disciplina disciplina = disciplinasMap.get(secao.origemDisciplinaId());
            if (disciplina != null) {
                disciplinasJaPresentes.add(disciplina.id());
                if (atualizarTituloSecaoSeMudou(usuarioId, secao, disciplina.nome())) {
                    secoesRenomeadas++;
                }
            } else {
                secaoRepository.desvincularOrigemDisciplina(usuarioId, secao.id());
                secoesDesvinculadas++;
            }
        }
        return new ResultadoProcessamentoSecoes(
                disciplinasJaPresentes, secoesRenomeadas, secoesDesvinculadas);
    }

    private boolean atualizarTituloSecaoSeMudou(ObjectId usuarioId, Secao secao, String nomeDisciplina) {
        if (Objects.equals(secao.titulo(), nomeDisciplina)) {
            return false;
        }
        Secao atualizada = new Secao(
                secao.id(),
                usuarioId,
                secao.cadernoId(),
                nomeDisciplina,
                secao.ordem(),
                secao.origemDisciplinaId(),
                secao.deletedAt(),
                secao.expiresAt(),
                secao.loteExclusaoId(),
                secao.criadoEm(),
                Instant.now()
        );
        secaoRepository.atualizar(atualizada);
        return true;
    }

    private record ResultadoCriacaoNovasSecoes(int secoesCriadas, int paginasCriadas) {
    }

    private ResultadoCriacaoNovasSecoes criarSecoesParaNovasDisciplinas(
            ObjectId usuarioId,
            ObjectId cadernoId,
            List<Disciplina> disciplinasTrilha,
            Set<ObjectId> disciplinasJaPresentes) {
        Instant agora = Instant.now();
        int secoesCriadas = 0;
        int paginasCriadas = 0;

        for (Disciplina disciplina : disciplinasTrilha) {
            if (disciplinasJaPresentes.contains(disciplina.id())) {
                continue;
            }
            int proximaOrdem = secaoRepository.proximaOrdem(usuarioId, cadernoId);
            Secao novaSecao = new Secao(
                    new ObjectId(),
                    usuarioId,
                    cadernoId,
                    disciplina.nome(),
                    proximaOrdem,
                    disciplina.id(),
                    null,
                    null,
                    null,
                    agora,
                    agora
            );
            secaoRepository.inserir(novaSecao);
            secoesCriadas++;
            paginasCriadas += criarNotasParaTopicos(
                    usuarioId, cadernoId, novaSecao.id(), disciplina.topicos(), agora);
        }
        return new ResultadoCriacaoNovasSecoes(secoesCriadas, paginasCriadas);
    }
}
