import { request } from './api';
import { TrilhaResumo, DisciplinaProgramada, PlanejamentoCiclo } from '../types/ciclo';

export const TrilhaService = {
  async listarTrilhas(): Promise<TrilhaResumo[]> {
    return request<TrilhaResumo[]>('/trilhas');
  },

  async buscarTrilha(id: string): Promise<TrilhaResumo | null> {
    try {
      return await request<TrilhaResumo>(`/trilhas/${encodeURIComponent(id)}`);
    } catch {
      return null;
    }
  },

  async obterPlanejamento(
    trilhaId: string,
    cicloId: string
  ): Promise<PlanejamentoCiclo | null> {
    try {
      return await request<PlanejamentoCiclo>(
        `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/planejamento`
      );
    } catch {
      return null;
    }
  },

  async obterDisciplinasDoDia(
    trilhaId: string,
    cicloId: string,
    dataISO: string
  ): Promise<DisciplinaProgramada[]> {
    return request<DisciplinaProgramada[]>(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/disciplinas-do-dia?data=${encodeURIComponent(dataISO)}`
    );
  },

  async atualizarConclusaoBloco(
    trilhaId: string,
    cicloId: string,
    ocorrenciaId: string,
    data: string,
    concluida: boolean
  ): Promise<void> {
    await request(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/planejamento/blocos/${encodeURIComponent(ocorrenciaId)}?data=${encodeURIComponent(data)}`,
      {
        method: 'PATCH',
        body: JSON.stringify({ concluida })
      }
    );
  },

  async registrarSessaoEstudo(
    trilhaId: string,
    payload: {
      disciplinaId: string;
      inicio: string;
      minutos: number;
      questoes?: number;
      acertos?: number;
      erros?: number;
      tipo: 'ESTUDO' | 'QUESTOES' | 'AJUSTE_MANUAL';
    },
    idempotencyKey?: string
  ): Promise<boolean> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }

    await request(`/trilhas/${encodeURIComponent(trilhaId)}/sessoes`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        disciplinaId: payload.disciplinaId,
        inicio: payload.inicio,
        minutos: payload.minutos || 0,
        questoes: payload.questoes || 0,
        acertos: payload.acertos || 0,
        erros: payload.erros || 0,
        tipo: payload.tipo
      })
    });
    return true;
  }
};
