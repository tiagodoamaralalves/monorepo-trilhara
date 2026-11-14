import { request } from './api';
import {
  CronogramaDTO,
  CriarCronogramaPayload,
  AtualizarCronogramaPayload,
  BlocoCronogramaItemDTO,
  Pagina,
  ConcluirAtrasadosResponse,
  DisponibilidadeSemanalDTO,
  BlocoHojeDTO
} from '@shared/contracts';
import {
  AtualizacaoMetadadosTrilha,
  Sessao,
  PaginaEditais,
  TrilhaResumoDTO,
  TrilhaDetalheDTO,
  DisciplinaDTO,
  ProgressoTopicoDTO,
  CriarDisciplinaRequest,
  AtualizarDisciplinaRequest,
  CriarTrilhaRequest,
  TrilhaImpactoExclusaoDTO,
  SessaoRegistradaDTO,
  EstatisticasTrilhaDTO,
  RemocaoSessoesDisciplinaResponse
} from '../types/trilhara';

/** Deriva o conjunto de tópicos concluídos usado pela UI a partir do detalhe da trilha. */
export function topicosConcluidosDe(detalhe: TrilhaDetalheDTO): ReadonlySet<string> {
  return new Set(detalhe.topicosConcluidosIds ?? []);
}

export interface PaginaSessoes {
  itens: Sessao[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

export const EstudosService = {
  // ── TRILHAS ──
  async listarTrilhas(): Promise<TrilhaResumoDTO[]> {
    return await request<TrilhaResumoDTO[]>('/trilhas');
  },

  async buscarTrilha(id: string): Promise<TrilhaDetalheDTO> {
    return await request<TrilhaDetalheDTO>(`/trilhas/${encodeURIComponent(id)}`);
  },

  async criarTrilha(payload: CriarTrilhaRequest): Promise<TrilhaDetalheDTO> {
    return await request<TrilhaDetalheDTO>('/trilhas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async atualizarMetadadosTrilha(
    id: string,
    payload: AtualizacaoMetadadosTrilha
  ): Promise<TrilhaDetalheDTO> {
    return await request<TrilhaDetalheDTO>(`/trilhas/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  async obterImpactoExclusaoTrilha(id: string): Promise<TrilhaImpactoExclusaoDTO> {
    return await request<TrilhaImpactoExclusaoDTO>(
      `/trilhas/${encodeURIComponent(id)}/impacto-exclusao`
    );
  },

  async excluirTrilha(id: string): Promise<void> {
    await request(`/trilhas/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  // ── DISCIPLINAS (GRANULAR) ──
  async criarDisciplina(trilhaId: string, payload: CriarDisciplinaRequest): Promise<DisciplinaDTO> {
    return await request<DisciplinaDTO>(`/trilhas/${encodeURIComponent(trilhaId)}/disciplinas`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async atualizarDisciplina(
    trilhaId: string,
    disciplinaId: string,
    payload: AtualizarDisciplinaRequest
  ): Promise<DisciplinaDTO> {
    return await request<DisciplinaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/disciplinas/${encodeURIComponent(disciplinaId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload)
      }
    );
  },

  async excluirDisciplina(trilhaId: string, disciplinaId: string): Promise<void> {
    await request(
      `/trilhas/${encodeURIComponent(trilhaId)}/disciplinas/${encodeURIComponent(disciplinaId)}`,
      {
        method: 'DELETE'
      }
    );
  },

  async reordenarDisciplinas(trilhaId: string, disciplinaIds: string[]): Promise<void> {
    await request(`/trilhas/${encodeURIComponent(trilhaId)}/disciplinas/reordenar`, {
      method: 'PUT',
      body: JSON.stringify({ disciplinaIds })
    });
  },

  // ── PROGRESSO DE TÓPICOS (IDEMPOTENTE) ──
  async marcarTopicoConcluido(trilhaId: string, topicoId: string): Promise<ProgressoTopicoDTO> {
    return await request<ProgressoTopicoDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/topicos/${encodeURIComponent(topicoId)}/progresso`,
      {
        method: 'PUT'
      }
    );
  },

  async desmarcarTopicoConcluido(trilhaId: string, topicoId: string): Promise<void> {
    await request(
      `/trilhas/${encodeURIComponent(trilhaId)}/topicos/${encodeURIComponent(topicoId)}/progresso`,
      {
        method: 'DELETE'
      }
    );
  },

  // ── CATÁLOGO DE EDITAIS ──
  async listarEditais(filtro?: {
    q?: string;
    banca?: string;
    esfera?: string;
    uf?: string;
    emAndamento?: boolean;
    page?: number;
    size?: number;
  }): Promise<PaginaEditais> {
    const params = new URLSearchParams();
    if (filtro?.q) params.set('q', filtro.q);
    if (filtro?.banca) params.set('banca', filtro.banca);
    if (filtro?.esfera) params.set('esfera', filtro.esfera);
    if (filtro?.uf) params.set('uf', filtro.uf);
    if (filtro?.emAndamento) params.set('emAndamento', 'true');
    if (filtro?.page !== undefined) params.set('page', String(filtro.page));
    if (filtro?.size !== undefined) params.set('size', String(filtro.size));
    const query = params.toString() ? `?${params.toString()}` : '';
    return await request<PaginaEditais>(`/editais${query}`);
  },

  async importarEdital(editalId: string): Promise<TrilhaDetalheDTO> {
    return await request<TrilhaDetalheDTO>('/trilhas', {
      method: 'POST',
      body: JSON.stringify({ nome: '', editalId })
    });
  },

  // ── DISCIPLINAS DO DIA ──
  // ── SESSÕES & HISTÓRICO ──
  async atualizarSessao(
    trilhaId: string,
    sessaoId: string,
    payload: { duracaoSegundos: number; questoes?: number; acertos?: number }
  ): Promise<SessaoRegistradaDTO> {
    return await request<SessaoRegistradaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/sessoes/${encodeURIComponent(sessaoId)}`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload)
      }
    );
  },

  async excluirSessao(
    trilhaId: string,
    sessaoId: string
  ): Promise<void> {
    await request<void>(
      `/trilhas/${encodeURIComponent(trilhaId)}/sessoes/${encodeURIComponent(sessaoId)}`,
      {
        method: 'DELETE'
      }
    );
  },

  async excluirSessoesDisciplina(
    trilhaId: string,
    disciplinaId: string
  ): Promise<RemocaoSessoesDisciplinaResponse> {
    return await request<RemocaoSessoesDisciplinaResponse>(
      `/trilhas/${encodeURIComponent(trilhaId)}/disciplinas/${encodeURIComponent(disciplinaId)}/sessoes`,
      {
        method: 'DELETE'
      }
    );
  },

  async listarSessoesPaginado(
    trilhaId: string,
    filtro?: {
      de?: string;
      ate?: string;
      dataInicio?: string;
      dataFim?: string;
      disciplinaId?: string;
      pagina?: number;
      tamanho?: number;
      excluirAjustes?: boolean;
    }
  ): Promise<PaginaSessoes> {
    const params = new URLSearchParams();
    const dataInicio = filtro?.dataInicio || filtro?.de;
    const dataFim = filtro?.dataFim || filtro?.ate;
    if (dataInicio) params.set('dataInicio', dataInicio);
    if (dataFim) params.set('dataFim', dataFim);
    if (filtro?.disciplinaId) params.set('disciplinaId', filtro.disciplinaId);
    if (filtro?.pagina !== undefined) params.set('pagina', String(filtro.pagina));
    if (filtro?.tamanho !== undefined) params.set('tamanho', String(filtro.tamanho));
    const query = params.toString() ? `?${params.toString()}` : '';
    const res = await request<Pagina<SessaoRegistradaDTO>>(
      `/trilhas/${encodeURIComponent(trilhaId)}/sessoes${query}`
    );
    return {
      itens: (res.itens || []).map((dto) => ({
        id: dto.id,
        trilhaId: dto.trilhaId,
        disciplinaId: dto.disciplinaId,
        disciplinaNome: dto.disciplinaNome,
        disciplinaCor: dto.disciplinaCor,
        disciplinaRemovida: dto.disciplinaRemovida,
        dataSessao: dto.dataSessao,
        duracaoSegundos: dto.duracaoSegundos,
        questoes: dto.questoes ?? 0,
        acertos: dto.acertos ?? 0,
        erros: dto.erros ?? Math.max(0, (dto.questoes ?? 0) - (dto.acertos ?? 0)),
        percentualAcerto: dto.percentualAcerto,
        editavel: dto.editavel,
        chaveIdempotencia: dto.chaveIdempotencia,
        criadoEm: dto.criadoEm,
        atualizadoEm: dto.atualizadoEm
      })),
      pagina: res.pagina,
      tamanho: res.tamanho,
      total: res.totalItens,
      totalPaginas: res.totalPaginas
    };
  },

  async obterEstatisticas(
    trilhaId: string,
    filtro?: { periodo?: string; de?: string; ate?: string; dataInicio?: string; dataFim?: string }
  ): Promise<EstatisticasTrilhaDTO> {
    const params = new URLSearchParams();
    if (filtro?.periodo) params.set('periodo', filtro.periodo);
    const dataInicio = filtro?.dataInicio || filtro?.de;
    const dataFim = filtro?.dataFim || filtro?.ate;
    if (dataInicio) params.set('dataInicio', dataInicio);
    if (dataFim) params.set('dataFim', dataFim);
    const query = params.toString() ? `?${params.toString()}` : '';
    return await request<EstatisticasTrilhaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/estatisticas${query}`
    );
  },

  // ── CRONOGRAMAS, BLOCOS & DISPONIBILIDADE SEMANAL ──
  async listarCronogramas(trilhaId: string): Promise<CronogramaDTO[]> {
    return await request<CronogramaDTO[]>(`/trilhas/${encodeURIComponent(trilhaId)}/cronogramas`);
  },

  async criarCronograma(trilhaId: string, payload: CriarCronogramaPayload): Promise<CronogramaDTO> {
    return await request<CronogramaDTO>(`/trilhas/${encodeURIComponent(trilhaId)}/cronogramas`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async atualizarCronograma(
    trilhaId: string,
    cronogramaId: string,
    payload: AtualizarCronogramaPayload
  ): Promise<CronogramaDTO> {
    return await request<CronogramaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload)
      }
    );
  },

  async ativarCronograma(trilhaId: string, cronogramaId: string): Promise<CronogramaDTO> {
    return await request<CronogramaDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}/ativar`,
      {
        method: 'PATCH'
      }
    );
  },

  async excluirCronograma(trilhaId: string, cronogramaId: string): Promise<void> {
    await request<void>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}`,
      {
        method: 'DELETE'
      }
    );
  },

  async listarBlocosCronograma(
    trilhaId: string,
    cronogramaId: string,
    params?: {
      concluido?: boolean;
      statusData?: 'ATRASADO' | 'HOJE' | 'FUTURO';
      dataInicio?: string;
      dataFim?: string;
      pagina?: number;
      tamanho?: number;
    }
  ): Promise<Pagina<BlocoCronogramaItemDTO>> {
    const search = new URLSearchParams();
    if (params?.concluido !== undefined) search.set('concluido', String(params.concluido));
    if (params?.statusData) search.set('statusData', params.statusData);
    if (params?.dataInicio) search.set('dataInicio', params.dataInicio);
    if (params?.dataFim) search.set('dataFim', params.dataFim);
    if (params?.pagina) search.set('pagina', String(params.pagina));
    if (params?.tamanho) search.set('tamanho', String(params.tamanho));
    const query = search.toString() ? `?${search.toString()}` : '';

    return await request<Pagina<BlocoCronogramaItemDTO>>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}/blocos${query}`
    );
  },

  /**
 * Contagem exata de blocos atrasados: usa `statusData=ATRASADO` com `tamanho=1` e lê
 * `totalItens`, evitando depender da página carregada na tela.
 */
  async contarBlocosAtrasados(trilhaId: string, cronogramaId: string): Promise<number> {
    const pagina = await this.listarBlocosCronograma(trilhaId, cronogramaId, {
      statusData: 'ATRASADO',
      concluido: false,
      pagina: 1,
      tamanho: 1
    });
    return pagina.totalItens;
  },

  async obterBlocosHoje(): Promise<BlocoHojeDTO[]> {
    return await request<BlocoHojeDTO[]>('/blocos/hoje');
  },

  async atualizarConclusaoBlocoIndividual(
    blocoId: string,
    concluido: boolean
  ): Promise<BlocoCronogramaItemDTO> {
    return await request<BlocoCronogramaItemDTO>(`/blocos/${encodeURIComponent(blocoId)}/conclusao`, {
      method: 'PATCH',
      body: JSON.stringify({ concluido })
    });
  },

  async concluirBlocosAtrasados(
    trilhaId: string,
    cronogramaId: string,
    operationId?: string
  ): Promise<ConcluirAtrasadosResponse> {
    const opId = operationId || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `op-${Date.now()}`);
    return await request<ConcluirAtrasadosResponse>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}/blocos/concluir-atrasados`,
      {
        method: 'POST',
        body: JSON.stringify({ operationId: opId })
      }
    );
  },

  async obterDisponibilidadeSemanal(): Promise<DisponibilidadeSemanalDTO> {
    return await request<DisponibilidadeSemanalDTO>('/usuarios/me/disponibilidade-semanal');
  },

  async atualizarDisponibilidadeSemanal(
    disponibilidadeSemanalMinutos: number[]
  ): Promise<DisponibilidadeSemanalDTO> {
    return await request<DisponibilidadeSemanalDTO>('/usuarios/me/disponibilidade-semanal', {
      method: 'PUT',
      body: JSON.stringify({ disponibilidadeSemanalMinutos })
    });
  }
};

export type {
  CronogramaDTO,
  CriarCronogramaPayload,
  AtualizarCronogramaPayload,
  BlocoCronogramaItemDTO,
  Pagina,
  ConcluirAtrasadosResponse,
  DisponibilidadeSemanalDTO,
  BlocoHojeDTO
};

