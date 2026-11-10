package br.com.trilhara.nota.dto;

public record RestauracaoLixeiraResponse(
    String mensagem,
    String tipo,
    String id,
    String cadernoId,
    String secaoId,
    boolean ancestralRestaurado,
    boolean realocadoParaRestauradas
) {}
