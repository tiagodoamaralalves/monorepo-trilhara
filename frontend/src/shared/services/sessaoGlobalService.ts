import { request } from '../auth/api';
import {
  BlocoCronogramaItemDTO,
  RegistrarSessaoPayload,
  SessaoRegistradaDTO
} from '../contracts/domainContracts';

export interface ExecutarDuasEtapasParams {
  trilhaId: string;
  blocoId: string;
  payload: RegistrarSessaoPayload;
  chaveIdempotencia?: string;
  sessaoJaSalva?: SessaoRegistradaDTO | null;
}

export interface ExecutarDuasEtapasResultado {
  sessao: SessaoRegistradaDTO;
  bloco: BlocoCronogramaItemDTO;
  sessaoEstavaPreviamenteSalva: boolean;
}

/**
 * Serviço centralizado para criação de sessões de estudo e conclusão de blocos de cronograma.
 * Atende aos requisitos da FASE 5 (GlobalSessionModal, retry isolado e idempotência).
 */
export const sessaoGlobalService = {
  /**
   * Registra uma sessão de estudo associada a uma disciplina e trilha.
   * Envia a chaveIdempotencia tanto no cabeçalho `Idempotency-Key` quanto no corpo da requisição.
   */
  async registrarSessao(
    trilhaId: string,
    payload: RegistrarSessaoPayload,
    chaveIdempotencia?: string
  ): Promise<SessaoRegistradaDTO> {
    const chave = chaveIdempotencia || payload.chaveIdempotencia;
    const headers: Record<string, string> = {};
    if (chave) {
      headers['Idempotency-Key'] = chave;
    }

    return await request<SessaoRegistradaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/sessoes`,
      {
        method: 'POST',
        headers,
        body: JSON.stringify({
          ...payload,
          chaveIdempotencia: chave
        })
      }
    );
  },

  /**
   * Conclui ou desmarca um bloco de cronograma individualmente.
   */
  async concluirBloco(
    blocoId: string,
    concluido = true
  ): Promise<BlocoCronogramaItemDTO> {
    return await request<BlocoCronogramaItemDTO>(
      `/blocos/${encodeURIComponent(blocoId)}/conclusao`,
      {
        method: 'PATCH',
        body: JSON.stringify({ concluido })
      }
    );
  },

  /**
   * Executa o fluxo em 2 etapas com suporte a recuperação e isolamento de retry:
   * - Etapa 1: Registra a sessão de estudo (se ainda não salva nesta abertura).
   * - Etapa 2: Atualiza o status do bloco para concluído.
   * Se a Etapa 1 suceder e a Etapa 2 falhar, o chamador retém `sessaoJaSalva` e pode
   * retentar exclusivamente a Etapa 2 sem reenviar a Etapa 1.
   */
  async executarFluxoDuasEtapas(
    params: ExecutarDuasEtapasParams
  ): Promise<ExecutarDuasEtapasResultado> {
    let sessao: SessaoRegistradaDTO;
    let sessaoEstavaPreviamenteSalva = false;

    if (params.sessaoJaSalva) {
      sessao = params.sessaoJaSalva;
      sessaoEstavaPreviamenteSalva = true;
    } else {
      sessao = await this.registrarSessao(
        params.trilhaId,
        params.payload,
        params.chaveIdempotencia
      );
    }

    const bloco = await this.concluirBloco(params.blocoId, true);

    return {
      sessao,
      bloco,
      sessaoEstavaPreviamenteSalva
    };
  }
};

