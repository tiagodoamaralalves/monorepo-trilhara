package br.com.trilhara.push;

import java.time.Instant;
import org.bson.types.ObjectId;

public record LembretePush(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId tarefaId,
        int versaoAgendamento,
        Instant instanteEntregaUtc,
        Integer minutosAntes,
        String fusoAgendamento,
        String estado,
        String tituloTarefa,
        int tentativas,
        Instant proximaTentativaEm,
        String leaseOwner,
        Instant leaseExpiraEm,
        String ultimoErro,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static final String ESTADO_PENDENTE = "PENDENTE";
    public static final String ESTADO_PROCESSANDO = "PROCESSANDO";
    public static final String ESTADO_ENVIADO = "ENVIADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";
    public static final String ESTADO_EXPIRADO_FORA_DA_JANELA = "EXPIRADO_FORA_DA_JANELA";
    public static final String ESTADO_FALHA_FINAL = "FALHA_FINAL";
}
