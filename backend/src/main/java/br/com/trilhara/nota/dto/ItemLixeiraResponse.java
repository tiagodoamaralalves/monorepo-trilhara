package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Item da lixeira hierárquica de Notas (D-34).
 *
 * <p>O {@code breadcrumb} já vem pronto do backend porque o item isolado não tem contexto
 * suficiente na UI: um caderno mostra só o título, uma seção mostra "Caderno › Seção" e uma
 * página mostra "Caderno › Seção". A hierarquia é sempre a mesma ordem de nível.</p>
 *
 * @param tipo     {@code CADERNO}, {@code SECAO} ou {@code NOTA}
 * @param titulo   título do próprio item
 * @param breadcrumb caminho legível do nível superior (vazio para caderno)
 */
public record ItemLixeiraResponse(
        String tipo,
        String id,
        String titulo,
        String breadcrumb,
        String cadernoId,
        String secaoId,
        String loteExclusaoId,
        Instant deletedAt,
        Instant expiresAt) {
}
