package br.com.trilhara.edital;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validator;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class EditalService {

    private static final String MENSAGEM_EDITAL_NAO_ENCONTRADO = "Edital não encontrado";

    private final EditalRepository repository;
    private final Validator validator;

    public EditalService(EditalRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public Edital criar(Edital recebido) {
        validar(recebido);
        Instant agora = Instant.now();
        Edital edital = new Edital(
                new org.bson.types.ObjectId().toHexString(),
                recebido.nome(), recebido.area(), recebido.cargo(), recebido.orgao(), recebido.banca(),
                recebido.esfera(), normalizarUf(recebido), recebido.inicioInscricao(), recebido.fimInscricao(),
                recebido.dataProva(), recebido.status() == null ? StatusEdital.RASCUNHO : recebido.status(),
                1, recebido.disciplinas(), agora, agora);
        repository.salvar(edital);
        return edital;
    }

    public Edital atualizar(String id, Edital recebido) {
        Edital existente = repository.buscar(id)
                .orElseThrow(() -> new NotFoundException(MENSAGEM_EDITAL_NAO_ENCONTRADO));
        validar(recebido);
        if (recebido.status() != null && recebido.status() != existente.status()) {
            throw new BadRequestException("Use PATCH /{id}/status para alterar o status do edital.");
        }
        Edital edital = new Edital(
                id, recebido.nome(), recebido.area(), recebido.cargo(), recebido.orgao(), recebido.banca(),
                recebido.esfera(), normalizarUf(recebido), recebido.inicioInscricao(), recebido.fimInscricao(),
                recebido.dataProva(), existente.status(), existente.versao() + 1,
                recebido.disciplinas(), existente.criadoEm(), Instant.now());
        repository.salvar(edital);
        return edital;
    }

    public Edital alterarStatus(String id, StatusEdital novoStatus) {
        if (novoStatus == null) {
            throw new BadRequestException("O status é obrigatório.");
        }
        Edital existente = repository.buscar(id)
                .orElseThrow(() -> new NotFoundException(MENSAGEM_EDITAL_NAO_ENCONTRADO));
        boolean transicaoPermitida = (existente.status() == StatusEdital.RASCUNHO && novoStatus == StatusEdital.PUBLICADA)
                || (existente.status() == StatusEdital.PUBLICADA && novoStatus == StatusEdital.ARQUIVADA);
        if (!transicaoPermitida) {
            throw new BadRequestException("Transição de status não permitida: "
                    + existente.status() + " → " + novoStatus + ".");
        }
        Edital atualizado = new Edital(
                existente.id(), existente.nome(), existente.area(), existente.cargo(), existente.orgao(),
                existente.banca(), existente.esfera(), existente.uf(), existente.inicioInscricao(),
                existente.fimInscricao(), existente.dataProva(), novoStatus, existente.versao(),
                existente.disciplinas(), existente.criadoEm(), Instant.now());
        repository.salvar(atualizado);
        return atualizado;
    }

    public Edital buscarAdmin(String id) {
        return repository.buscar(id).orElseThrow(() -> new NotFoundException(MENSAGEM_EDITAL_NAO_ENCONTRADO));
    }

    public EditalPublico buscarPublico(String id) {
        Edital edital = repository.buscar(id)
                .filter(item -> item.status() == StatusEdital.PUBLICADA)
                .orElseThrow(() -> new NotFoundException("Edital publicado não encontrado"));
        return EditalPublico.de(edital);
    }

    public Pagina<Edital> listarAdmin(
            String statusTexto, String banca, String orgao, String cargo, int page, int size) {
        validarPaginacao(page, size);
        StatusEdital status = null;
        if (statusTexto != null && !statusTexto.isBlank()) {
            try {
                status = StatusEdital.valueOf(statusTexto.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Status inválido: " + statusTexto, exception);
            }
        }
        return repository.listarAdmin(status, banca, orgao, cargo, page, size);
    }

    public Pagina<EditalPublico> listarPublicos(FiltroEditalPublico filtro) {
        validarPaginacao(filtro.paginacao().page(), filtro.paginacao().size());
        Esfera esfera = null;
        if (filtro.esfera() != null && !filtro.esfera().isBlank()) {
            try {
                esfera = Esfera.valueOf(filtro.esfera().trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Esfera inválida: " + filtro.esfera(), exception);
            }
        }
        EditalRepository.FiltroPublico consulta = new EditalRepository.FiltroPublico(
                esfera, filtro, filtro.paginacao());
        Pagina<Edital> pagina = repository.listarPublicos(consulta);
        return new Pagina<>(pagina.itens().stream().map(EditalPublico::de).toList(),
                filtro.paginacao().page(), filtro.paginacao().size(), pagina.total());
    }

    public void excluir(String id) {
        if (!repository.excluir(id)) {
            throw new NotFoundException(MENSAGEM_EDITAL_NAO_ENCONTRADO);
        }
    }

    private void validar(Edital edital) {
        if (edital == null) {
            throw new BadRequestException("O edital é obrigatório.");
        }
        var violations = validator.validate(edital);
        if (!violations.isEmpty()) {
            throw new BadRequestException(violations.iterator().next().getMessage());
        }
        if (edital.esfera() == Esfera.FEDERAL && edital.uf() != null && !edital.uf().isBlank()) {
            throw new BadRequestException("Editais federais não devem informar UF.");
        }
        if (edital.esfera() != Esfera.FEDERAL && (edital.uf() == null || edital.uf().isBlank())) {
            throw new BadRequestException("UF é obrigatória para editais estaduais ou municipais.");
        }
        if (edital.inicioInscricao() != null && edital.fimInscricao() != null
                && edital.fimInscricao().isBefore(edital.inicioInscricao())) {
            throw new BadRequestException("O fim da inscrição não pode anteceder o início.");
        }
        if (edital.dataProva() != null && edital.fimInscricao() != null
                && edital.dataProva().isBefore(edital.fimInscricao())) {
            throw new BadRequestException("A data da prova não pode anteceder o fim das inscrições.");
        }
        validarIds(edital);
    }

    private String normalizarUf(Edital edital) {
        return edital.esfera() == Esfera.FEDERAL ? null : edital.uf().trim().toUpperCase();
    }

    private void validarIds(Edital edital) {
        Set<String> ids = new HashSet<>();
        for (Edital.Disciplina disciplina : edital.disciplinas()) {
            if (!ids.add(disciplina.id())) {
                throw new BadRequestException("ID duplicado no edital: " + disciplina.id());
            }
            for (Edital.Topico topico : disciplina.topicos()) {
                if (!ids.add(topico.id())) {
                    throw new BadRequestException("ID duplicado no edital: " + topico.id());
                }
            }
        }
    }

    private void validarPaginacao(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page deve ser maior ou igual a zero.");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size deve estar entre 1 e 100.");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page excede o limite permitido.");
        }
    }
}
