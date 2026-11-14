import { request } from '../auth/api';
import {
  BlocoCronogramaItemDTO,
  RegistrarSessaoPayload,
  SessaoRegistradaDTO
} from '../contracts/domainContracts';

/** Resultado da etapa 1 já concluída em uma abertura anterior do modal. */
interface SessaoJaSalva {
  id: string;
  trilhaId?: string;
  disciplinaId?: string;
  duracaoSegundos?: number;
  dataSessao?: string;
}

export interface ExecutarFluxoParams {
  trilhaId: string;
  blocoId: string;
  payload: RegistrarSessaoPayload;
  chave: string;
  /** Sessão já criada por uma tentativa anterior: a etapa 1 não é reenviada. */
  sessaoSalva?: SessaoJaSalva | null;
}

export interface ResultadoFluxo {
  sessaoSalva: boolean;
  sessaoId?: string;
  blocoConcluido: boolean;
  /** Preenchido quando a etapa 2 falha: "sessão salva, bloco ainda pendente" (§6.4). */
  erro?: Error;
}

/**
 * Serviço único de registro de sessão e conclusão de bloco.
 *
 * `registrarSessao` é o **único** caminho para `POST /api/trilhas/{trilhaId}/sessoes`, com a
 * chave de idempotência no cabeçalho `Idempotency-Key` **e** no corpo (contrato do backend);
 * `executarFluxo` orquestra as duas etapas e **não reverte** a sessão quando a conclusão do
 * bloco falha: devolve `sessaoSalva: true` + `erro` para retry exclusivo da etapa 2;
 * a chave é sempre responsabilidade do chamador (`GlobalSessionModal` a gera na abertura).
 */
export const sessaoGlobalService = {
  async registrarSessao(
    trilhaId: string,
    payload: RegistrarSessaoPayload,
    chave: string
  ): Promise<SessaoRegistradaDTO> {
    return request<SessaoRegistradaDTO>(`/trilhas/${encodeURIComponent(trilhaId)}/sessoes`, {
      method: 'POST',
      headers: { 'Idempotency-Key': chave },
      body: JSON.stringify({ ...payload, chaveIdempotencia: chave })
    });
  },

  /** Conclui o bloco (`PATCH { concluido: true }`). */
  async concluirBloco(blocoId: string): Promise<void> {
    await request<BlocoCronogramaItemDTO>(`/blocos/${encodeURIComponent(blocoId)}/conclusao`, {
      method: 'PATCH',
      body: JSON.stringify({ concluido: true })
    });
  },

  /** Reabre o bloco (`PATCH { concluido: false }`); não exclui sessões (§6.4). */
  async reabrirBloco(blocoId: string): Promise<void> {
    await request<BlocoCronogramaItemDTO>(`/blocos/${encodeURIComponent(blocoId)}/conclusao`, {
      method: 'PATCH',
      body: JSON.stringify({ concluido: false })
    });
  },

  /**
 * Executa o fluxo de duas etapas com retry isolado da conclusão.
 *
 * A etapa 2 nunca rejeita: a falha é reportada em `erro` com `sessaoSalva: true`, para que o
 * modal ofereça **"Tentar concluir bloco novamente"** sem reenviar a sessão.
 */
  async executarFluxo(params: ExecutarFluxoParams): Promise<ResultadoFluxo> {
    let sessaoId = params.sessaoSalva?.id;

    if (!sessaoId) {
      const sessao = await this.registrarSessao(params.trilhaId, params.payload, params.chave);
      sessaoId = sessao.id;
    }

    try {
      await this.concluirBloco(params.blocoId);
      return { sessaoSalva: true, sessaoId, blocoConcluido: true };
    } catch (erro) {
      return {
        sessaoSalva: true,
        sessaoId,
        blocoConcluido: false,
        erro: erro instanceof Error ? erro : new Error('Falha ao concluir o bloco.')
      };
    }
  }
};


