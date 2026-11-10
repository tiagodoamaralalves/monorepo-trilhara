package br.com.trilhara.nota;

import java.time.Instant;
import java.util.ArrayList;
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
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TopicoEmbutido;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class NotaSincronizacaoService {

    private static final String TEXTO_0284C7 = "#0284c7";

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

    public CadernoResponse importarTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha de estudo não encontrada."));

        String nomeTrilha = trilhaDoc.getString("nome");
        String corTrilha = trilhaDoc.getString("cor");

        desvincularCadernoExistenteSeHouver(usuarioId, trilhaId);

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
        List<SecaoResponse> secoesCriadas = new ArrayList<>();
        long totalNotasCriadas = 0;

        for (int i = 0; i < disciplinas.size(); i++) {
            Disciplina d = disciplinas.get(i);
            Secao novaSecao = new Secao(
                    new ObjectId(),
                    usuarioId,
                    novoCaderno.id(),
                    d.nome(),
                    d.ordem() != null ? d.ordem() : i,
                    d.id(),
                    null,
                    null,
                    null,
                    agora,
                    agora
            );
            secaoRepository.inserir(novaSecao);

            long notasNestaSecao = criarNotasParaTopicos(usuarioId, novoCaderno.id(), novaSecao.id(), d.topicos(), agora);
            totalNotasCriadas += notasNestaSecao;
            secoesCriadas.add(paraSecaoResponse(novaSecao, notasNestaSecao));
        }

        return paraCadernoResponse(novoCaderno, secoesCriadas.size(), totalNotasCriadas, secoesCriadas);
    }

    private void desvincularCadernoExistenteSeHouver(ObjectId usuarioId, ObjectId trilhaId) {
        Optional<Caderno> cadernoExistente = cadernoRepository.buscarAtivoPorOrigemTrilha(usuarioId, trilhaId);
        if (cadernoExistente.isPresent()) {
            Caderno antigo = cadernoExistente.get();
            cadernoRepository.desvincularTrilha(usuarioId, trilhaId);
            secaoRepository.desvincularOrigemDisciplinaPorCaderno(usuarioId, antigo.id());
        }
    }

    public SincronizacaoTrilhaResponse sincronizarTrilha(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException("Caderno não encontrado."));

        if (caderno.origemTrilhaId() == null) {
            throw new BadRequestException("O caderno selecionado não está vinculado a nenhuma trilha de estudo.");
        }

        ObjectId trilhaId = caderno.origemTrilhaId();
        Optional<Document> trilhaOpt = trilhaRepository.buscarEntidade(usuarioId, trilhaId);

        if (trilhaOpt.isEmpty()) {
            cadernoRepository.desvincularTrilha(usuarioId, trilhaId);
            secaoRepository.desvincularOrigemDisciplinaPorCaderno(usuarioId, cadernoId);
            return new SincronizacaoTrilhaResponse(cadernoId.toHexString(), 0, 0, 0);
        }

        atualizarTituloCadernoSeMudou(caderno, trilhaOpt.get(), usuarioId);

        List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        Map<ObjectId, Disciplina> disciplinasMap = new HashMap<>();
        for (Disciplina d : disciplinasTrilha) {
            disciplinasMap.put(d.id(), d);
        }

        ResultadoProcessamentoSecoes procSecoes = processarSecoesAtivas(usuarioId, cadernoId, disciplinasMap);
        ResultadoCriacaoNovasSecoes criadas = criarSecoesParaNovasDisciplinas(
                usuarioId, cadernoId, disciplinasTrilha, procSecoes.disciplinasJaPresentes());

        return new SincronizacaoTrilhaResponse(
                cadernoId.toHexString(),
                criadas.secoesCriadas(),
                criadas.paginasCriadas(),
                procSecoes.secoesDesvinculadas()
        );
    }

    private void atualizarTituloCadernoSeMudou(Caderno caderno, Document trilhaDoc, ObjectId usuarioId) {
        String nomeAtualTrilha = trilhaDoc.getString("nome");
        if (!Objects.equals(caderno.titulo(), nomeAtualTrilha)) {
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
    }

    private record ResultadoProcessamentoSecoes(Set<ObjectId> disciplinasJaPresentes, int secoesDesvinculadas) {
    }

    private ResultadoProcessamentoSecoes processarSecoesAtivas(
            ObjectId usuarioId,
            ObjectId cadernoId,
            Map<ObjectId, Disciplina> disciplinasMap) {
        List<Secao> secoesAtivas = secaoRepository.listarPorCaderno(usuarioId, cadernoId);
        Set<ObjectId> disciplinasJaPresentes = new HashSet<>();
        int secoesDesvinculadas = 0;

        for (Secao secao : secoesAtivas) {
            if (secao.origemDisciplinaId() != null) {
                Disciplina disc = disciplinasMap.get(secao.origemDisciplinaId());
                if (disc != null) {
                    disciplinasJaPresentes.add(disc.id());
                    atualizarTituloSecaoSeMudou(usuarioId, secao, disc.nome());
                } else {
                    secaoRepository.desvincularOrigemDisciplina(usuarioId, secao.id());
                    secoesDesvinculadas++;
                }
            }
        }
        return new ResultadoProcessamentoSecoes(disciplinasJaPresentes, secoesDesvinculadas);
    }

    private void atualizarTituloSecaoSeMudou(ObjectId usuarioId, Secao secao, String nomeDisciplina) {
        if (!Objects.equals(secao.titulo(), nomeDisciplina)) {
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
        }
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

        for (Disciplina disc : disciplinasTrilha) {
            if (!disciplinasJaPresentes.contains(disc.id())) {
                int proximaOrdem = secaoRepository.proximaOrdem(usuarioId, cadernoId);
                Secao novaSecao = new Secao(
                        new ObjectId(),
                        usuarioId,
                        cadernoId,
                        disc.nome(),
                        proximaOrdem,
                        disc.id(),
                        null,
                        null,
                        null,
                        agora,
                        agora
                );
                secaoRepository.inserir(novaSecao);
                secoesCriadas++;

                paginasCriadas += criarNotasParaTopicos(usuarioId, cadernoId, novaSecao.id(), disc.topicos(), agora);
            }
        }
        return new ResultadoCriacaoNovasSecoes(secoesCriadas, paginasCriadas);
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

    private SecaoResponse paraSecaoResponse(Secao s, long totalNotas) {
        return new SecaoResponse(
                s.idHex(),
                s.usuarioIdHex(),
                s.cadernoIdHex(),
                s.titulo(),
                s.ordem(),
                s.origemDisciplinaIdHex(),
                totalNotas,
                s.criadoEm(),
                s.atualizadoEm()
        );
    }

    private CadernoResponse paraCadernoResponse(
            Caderno c,
            long totalSecoes,
            long totalNotas,
            List<SecaoResponse> secoes) {
        return new CadernoResponse(
                c.idHex(),
                c.usuarioIdHex(),
                c.titulo(),
                c.cor(),
                c.icone(),
                c.origemTrilhaIdHex(),
                c.origemTrilhaId() != null,
                totalSecoes,
                totalNotas,
                secoes,
                c.criadoEm(),
                c.atualizadoEm()
        );
    }
}
