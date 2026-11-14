package br.com.trilhara.tarefa;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import org.bson.types.ObjectId;

import br.com.trilhara.push.InvalidLocalDatetimeException;
import br.com.trilhara.push.LembretePushCalculator;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.BsonIds;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

final class TarefaSupport {

    static final String CAMPO_LISTA_ID = "listaId";
    static final String CAMPO_SERIE_ID = "serieId";
    static final String CAMPO_TAREFA_ID = "tarefaId";
    static final String CAMPO_USUARIO_ID = "usuarioId";
    static final String VALOR_EM_DATA = "EM_DATA";

    private static final String ERRO_LISTA_INFORMADA_INEXISTENTE_OU_NA_LIXEIRA =
            "A lista informada não existe ou está na lixeira.";
    private static final int TAMANHO_PADRAO_LISTAGEM = 50;
    private static final int TAMANHO_MAXIMO_LISTAGEM = 100;
    private static final Set<String> FILTROS_LISTAGEM = Set.of("TODAS", "FUTURO", "CONCLUIDAS");
    private static final Set<Integer> MINUTOS_LEMBRETE_PERMITIDOS = Set.of(0, 5, 15, 30, 60);

    record PaginacaoValidada(int pagina, int tamanho) {
    }

    record FiltroListaResolvido(ObjectId listaId, boolean apenasSemLista) {
    }

    private TarefaSupport() {
    }

    static ObjectId validarObjectId(String idStr, String nomeCampo) {
        return BsonIds.parse(idStr, nomeCampo);
    }

    static LocalDate paraLocalDate(String valor) {
        return BsonDates.toLocalDate(valor);
    }

    static String normalizarFiltroListagem(String filtroBruto) {
        String filtroNome = filtroBruto != null && !filtroBruto.isBlank()
                ? filtroBruto.trim().toUpperCase(Locale.ROOT)
                : null;
        if (filtroNome != null && !FILTROS_LISTAGEM.contains(filtroNome)) {
            throw new ValidationException("filtro", "Valor inválido. Use TODAS, FUTURO ou CONCLUIDAS.");
        }
        return filtroNome;
    }

    static FiltroListaResolvido resolverFiltroLista(String listaIdStr) {
        if (listaIdStr == null || listaIdStr.isBlank()) {
            return new FiltroListaResolvido(null, false);
        }
        if ("sem_lista".equalsIgnoreCase(listaIdStr)
                || "null".equalsIgnoreCase(listaIdStr)
                || "geral".equalsIgnoreCase(listaIdStr)) {
            return new FiltroListaResolvido(null, true);
        }
        return new FiltroListaResolvido(validarObjectId(listaIdStr, CAMPO_LISTA_ID), false);
    }

    static PaginacaoValidada validarPaginacao(Integer paginaParam, Integer tamanhoParam) {
        int pagina = paginaParam != null ? paginaParam : 1;
        int tamanho = tamanhoParam != null ? tamanhoParam : TAMANHO_PADRAO_LISTAGEM;
        if (pagina < 1 || tamanho < 1 || tamanho > TAMANHO_MAXIMO_LISTAGEM) {
            var problem = ProblemDetailsFactory.criar(
                    CodigoErro.INVALID_PAGINATION,
                    "Parâmetros de paginação inválidos. 'pagina' deve ser >= 1 e 'tamanho' deve estar entre 1 e "
                            + TAMANHO_MAXIMO_LISTAGEM + ".",
                    null);
            throw new WebApplicationException(
                    Response.status(problem.status())
                            .type(ProblemDetails.MEDIA_TYPE)
                            .entity(problem)
                            .build());
        }
        return new PaginacaoValidada(pagina, tamanho);
    }

    static String exigirIdempotencyKey(String origemOperacaoId) {
        if (origemOperacaoId == null || origemOperacaoId.isBlank()) {
            var problem = ProblemDetailsFactory.criar(
                    CodigoErro.IDEMPOTENCY_KEY_REQUIRED,
                    "O cabeçalho Idempotency-Key é obrigatório para editar esta e as próximas ocorrências.",
                    null);
            throw new WebApplicationException(
                    Response.status(problem.status())
                            .type(ProblemDetails.MEDIA_TYPE)
                            .entity(problem)
                            .build());
        }
        return origemOperacaoId.trim();
    }

    static EscopoRecorrencia resolverEscopoRecorrenciaObrigatorio(String escopoRequest, String escopoParam) {
        String escopoStr = escopoRequest != null ? escopoRequest : escopoParam;
        EscopoRecorrencia escopo = EscopoRecorrencia.fromString(escopoStr);
        if (escopo == null) {
            throw new ValidationException(
                    "escopo",
                    "O escopo é obrigatório para tarefa recorrente. Use ESTA, ESTA_E_FUTURAS ou TODA_A_SERIE.");
        }
        return escopo;
    }

    static void proibirEscopoEmTarefaAvulsa(String escopoParam) {
        if (escopoParam != null && !escopoParam.isBlank()) {
            throw new ValidationException("escopo", "Tarefa avulsa não aceita escopo de recorrência.");
        }
    }

    static ObjectId validarEResolverListaId(
            ListaTarefasRepository listaRepository, ObjectId usuarioId, String listaIdStr) {
        if (listaIdStr == null || listaIdStr.isBlank() || "null".equalsIgnoreCase(listaIdStr)) {
            return null;
        }
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);
        listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new BadRequestException(ERRO_LISTA_INFORMADA_INEXISTENTE_OU_NA_LIXEIRA));
        return listaId;
    }

    static void validarPreferenciaLembrete(ModoLembrete modo, Integer minutos) {
        if (modo == null) {
            return;
        }
        if (modo == ModoLembrete.PERSONALIZADO) {
            if (minutos == null || !MINUTOS_LEMBRETE_PERMITIDOS.contains(minutos)) {
                throw new ValidationException(
                        "lembreteMinutosAntes",
                        "Lembrete personalizado exige 0, 5, 15, 30 ou 60 minutos antes.");
            }
            return;
        }
        if (minutos != null) {
            throw new ValidationException(
                    "lembreteMinutosAntes",
                    "Apenas o modo PERSONALIZADO aceita minutos de antecedência.");
        }
    }

    static String resolverFusoAgendamento(TimezoneContext timezoneContext, String fusoExplicito) {
        if (fusoExplicito != null && !fusoExplicito.isBlank()) {
            return TimezoneContext.resolverZoneId(fusoExplicito).getId();
        }
        try {
            return timezoneContext.zoneId().getId();
        } catch (Exception _) {
            return TimezoneContext.DEFAULT_ZONE.getId();
        }
    }

    static String resolverFusoEdicao(
            TimezoneContext timezoneContext, String fusoRequest, String fusoExistente) {
        if (fusoRequest != null) {
            return fusoRequest;
        }
        if (fusoExistente != null) {
            return fusoExistente;
        }
        return resolverFusoAgendamento(timezoneContext, null);
    }

    static void validarGapDst(
            TimezoneContext timezoneContext,
            LocalDate dataVencimento,
            String horario,
            String fusoAgendamento) {
        if (horario != null && !horario.isBlank() && dataVencimento != null) {
            ZoneId zoneId = fusoAgendamento != null && !fusoAgendamento.isBlank()
                    ? TimezoneContext.resolverZoneId(fusoAgendamento)
                    : timezoneContext.zoneId();
            try {
                LocalTime lt = LocalTime.parse(horario.trim());
                LembretePushCalculator.calcularInstanteEntregaUtc(dataVencimento, lt, zoneId, 0);
            } catch (InvalidLocalDatetimeException e) {
                throw e;
            } catch (Exception _) {
                // Formato de hora com outros erros ignorado aqui; tratado nos validadores de schema
            }
        }
    }
}
