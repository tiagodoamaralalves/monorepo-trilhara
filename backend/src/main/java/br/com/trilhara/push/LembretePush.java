package br.com.trilhara.push;

import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Versão imutável de um agendamento de lembrete push (D-36a).
 *
 * <p>Cada alteração relevante da tarefa ({@code dataVencimento}, {@code horario},
 * {@code modoLembrete}, {@code lembreteMinutosAntes}, {@code fusoAgendamento}, {@code estado},
 * {@code deletedAt}) incrementa {@code versaoAgendamento}, cancela as versões anteriores
 * {@code PENDENTE} e cria uma nova. O documento é portanto um <em>fato histórico</em>: o
 * {@code instanteEntregaUtc} de uma versão nunca é reescrito — retentativas são derivadas de
 * {@code tentativas} + {@code instanteEntregaUtc} por {@link LembretePushFilaRepository}.</p>
 *
 * <p>O título da tarefa <strong>não</strong> é denormalizado aqui: o scheduler lê a tarefa no
 * momento do envio, para que renomeá-la nunca entregue um título velho.</p>
 */
public record LembretePush(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId tarefaId,
        long versaoAgendamento,
        Instant instanteEntregaUtc,
        String estado,
        int tentativas,
        String leaseToken,
        Instant leaseAte,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static final String ESTADO_PENDENTE = "PENDENTE";
    public static final String ESTADO_PROCESSANDO = "PROCESSANDO";
    public static final String ESTADO_ENVIADO = "ENVIADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";
    public static final String ESTADO_EXPIRADO_FORA_DA_JANELA = "EXPIRADO_FORA_DA_JANELA";
    public static final String ESTADO_FALHA_FINAL = "FALHA_FINAL";

    /** Versão inicial de todo agendamento. */
    public static final long VERSAO_INICIAL = 1L;
}
