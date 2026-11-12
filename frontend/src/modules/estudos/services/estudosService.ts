import { request } from './api';
import {
  CronogramaDTO,
  CriarCronogramaPayload,
  AtualizarCronogramaPayload,
  BlocoCronogramaItemDTO,
  PaginaBlocosDTO,
  ConcluirAtrasadosResponse,
  DisponibilidadeSemanalDTO,
  BlocoHojeDTO
} from '@shared/contracts';
import {
  Trilha,
  AtualizacaoMetadadosTrilha,
  Sessao,
  DisciplinaProgramada,
  PlanejamentoCiclo,
  PaginaEditais,
  TrilhaResumoDTO,
  TrilhaDetalheDTO,
  DisciplinaDTO,
  ProgressoTopicoDTO,
  CriarDisciplinaRequest,
  AtualizarDisciplinaRequest,
  RegistrarSessaoPayload,
  SessaoRegistradaDTO,
  PaginaSessoesDTO,
  EstatisticasTrilhaDTO,
  RemocaoSessoesDisciplinaResponse,
  ItemProgresso,
  Ciclo
} from '../types/trilhara';

export function adaptarTrilhaDetalhe(dto: TrilhaDetalheDTO): Trilha {
  const progresso: Record<string, ItemProgresso> = {};
  if (Array.isArray(dto.topicosConcluidosIds)) {
    for (const topicoId of dto.topicosConcluidosIds) {
      progresso[topicoId] = { done: true };
    }
  }
  return {
    id: dto.id,
    usuarioId: dto.usuarioId,
    donoId: dto.donoId,
    origemEditalId: dto.origemEditalId,
    origemVersao: dto.origemVersao,
    nome: dto.nome,
    cargo: dto.cargo,
    areaCargo: dto.areaCargo || dto.cargo || '',
    banca: dto.banca,
    dataProva: dto.dataProva,
    informacoesComplementares: dto.informacoesComplementares,
    cor: dto.cor,
    disciplinas: (dto.disciplinas || []).map((d) => ({
      id: d.id,
      nome: d.nome,
      cor: d.cor,
      peso: d.peso ?? 1,
      cadernoLink: d.cadernoLink,
      ordem: d.ordem,
      topicos: (d.topicos || []).map((t) => ({
        id: t.id,
        nome: t.nome,
        ordem: t.ordem
      }))
    })),
    topicosConcluidosIds: dto.topicosConcluidosIds || [],
    progresso,
    ciclos: ((dto as { ciclos?: Ciclo[] }).ciclos || []) as Ciclo[],
    cicloAtivoId: dto.cicloAtivoId,
    criadoEm: dto.criadoEm,
    atualizadoEm: dto.atualizadoEm
  };
}

export function adaptarTrilhaResumo(dto: TrilhaResumoDTO): Trilha {
  return {
    id: dto.id,
    usuarioId: dto.usuarioId,
    donoId: dto.donoId,
    origemEditalId: dto.origemEditalId,
    nome: dto.nome,
    cargo: dto.cargo,
    areaCargo: dto.areaCargo || dto.cargo || '',
    banca: dto.banca,
    dataProva: dto.dataProva,
    cor: dto.cor,
    totalDisciplinas: dto.totalDisciplinas,
    totalTopicos: dto.totalTopicos,
    topicosConcluidos: dto.topicosConcluidos,
    percentualProgresso: dto.percentualProgresso,
    cronogramaAtivoResumo: dto.cronogramaAtivoResumo,
    disciplinas: [],
    progresso: {},
    ciclos: [],
    criadoEm: dto.criadoEm,
    atualizadoEm: dto.atualizadoEm
  };
}

export interface ResumoEstatisticas {
  totalMinutos: number;
  totalQuestoes: number;
  taxaAcerto: number;
  streakDiasAtual: number;
  melhorStreakDias: number;
  totalSessoes: number;
  diasComEstudo: number;
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
  async listarTrilhas(): Promise<Trilha[]> {
    const resumos = await request<TrilhaResumoDTO[]>('/trilhas');
    return resumos.map(adaptarTrilhaResumo);
  },

  async buscarTrilha(id: string): Promise<Trilha> {
    const detalhe = await request<TrilhaDetalheDTO>(`/trilhas/${encodeURIComponent(id)}`);
    return adaptarTrilhaDetalhe(detalhe);
  },

  async criarTrilha(payload: Partial<Trilha>): Promise<Trilha> {
    const res = await request<TrilhaDetalheDTO>('/trilhas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    return adaptarTrilhaDetalhe(res);
  },

  async atualizarTrilha(id: string, payload: Partial<Trilha>): Promise<Trilha> {
    const res = await request<TrilhaDetalheDTO>(`/trilhas/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
    return adaptarTrilhaDetalhe(res);
  },

  async atualizarMetadadosTrilha(id: string, payload: AtualizacaoMetadadosTrilha): Promise<Trilha> {
    const res = await request<TrilhaDetalheDTO>(`/trilhas/${encodeURIComponent(id)}/metadados`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    });
    return adaptarTrilhaDetalhe(res);
  },

  async excluirTrilha(id: string): Promise<void> {
    await request(`/trilhas/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  // ── DISCIPLINAS (GRANULAR FASE 2) ──
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

  async reordenarDisciplinas(trilhaId: string, ordemIds: string[]): Promise<void> {
    await request(`/trilhas/${encodeURIComponent(trilhaId)}/disciplinas/reordenar`, {
      method: 'PUT',
      body: JSON.stringify({ ordemIds })
    });
  },

  // ── PROGRESSO DE TÓPICOS (IDEMPOTENTE FASE 2) ──
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

  async atualizarProgressoTopico(
    trilhaId: string,
    topicoId: string,
    done: boolean
  ): Promise<Trilha> {
    if (done) {
      await this.marcarTopicoConcluido(trilhaId, topicoId);
    } else {
      await this.desmarcarTopicoConcluido(trilhaId, topicoId);
    }
    return await this.buscarTrilha(trilhaId);
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

  async importarEdital(editalId: string): Promise<Trilha> {
    const res = await request<TrilhaDetalheDTO>(`/trilhas/importar/${encodeURIComponent(editalId)}`, {
      method: 'POST'
    });
    return adaptarTrilhaDetalhe(res);
  },

  // ── CICLOS & DISCIPLINAS DO DIA ──
  async obterDisciplinasDoDia(
    trilhaId: string,
    cicloId: string,
    dataISO: string
  ): Promise<DisciplinaProgramada[]> {
    return await request<DisciplinaProgramada[]>(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/disciplinas-do-dia?data=${encodeURIComponent(dataISO)}`
    );
  },

  async obterPlanejamentoCiclo(trilhaId: string, cicloId: string): Promise<PlanejamentoCiclo> {
    return await request<PlanejamentoCiclo>(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/planejamento`
    );
  },

  async salvarPlanejamentoCiclo(
    trilhaId: string,
    cicloId: string,
    planejamento: Omit<PlanejamentoCiclo, 'revisao' | 'atualizadoEm'> & { revisaoEsperada: number }
  ): Promise<PlanejamentoCiclo> {
    return await request<PlanejamentoCiclo>(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/planejamento`,
      {
        method: 'PUT',
        body: JSON.stringify(planejamento)
      }
    );
  },

  async atualizarConclusaoBloco(
    trilhaId: string,
    cicloId: string,
    ocorrenciaId: string,
    data: string,
    concluida: boolean
  ): Promise<PlanejamentoCiclo> {
    return await request<PlanejamentoCiclo>(
      `/trilhas/${encodeURIComponent(trilhaId)}/ciclos/${encodeURIComponent(cicloId)}/planejamento/blocos/${encodeURIComponent(ocorrenciaId)}?data=${encodeURIComponent(data)}`,
      {
        method: 'PATCH',
        body: JSON.stringify({ concluida })
      }
    );
  },

  // ── SESSÕES & HISTÓRICO ──
  async registrarSessao(
    trilhaId: string,
    payload: {
      disciplinaId?: string;
      dataSessao?: string;
      duracaoSegundos?: number;
      inicio?: string;
      minutos?: number;
      questoes?: number;
      acertos?: number;
      erros?: number;
      tipo?: 'ESTUDO' | 'QUESTOES' | 'ESTUDO_E_QUESTOES' | 'AJUSTE_MANUAL';
      chaveIdempotencia?: string;
    },
    idempotencyKey?: string
  ): Promise<SessaoRegistradaDTO> {
    const key = idempotencyKey || payload.chaveIdempotencia || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `sessao-${Date.now()}`);
    const headers: Record<string, string> = {
      'Idempotency-Key': key
    };

    let duracaoSegundos = payload.duracaoSegundos;
    if (duracaoSegundos === undefined || duracaoSegundos === null) {
      duracaoSegundos = (payload.minutos != null && payload.minutos > 0) ? payload.minutos * 60 : 60;
    }
    duracaoSegundos = Math.max(60, Math.min(86400, duracaoSegundos));

    let dataSessao = payload.dataSessao;
    if (!dataSessao) {
      if (payload.inicio) {
        dataSessao = payload.inicio.split('T')[0];
      } else {
        const d = new Date();
        dataSessao = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      }
    }

    const body: RegistrarSessaoPayload = {
      disciplinaId: payload.disciplinaId || '',
      dataSessao,
      duracaoSegundos,
      questoes: payload.questoes ?? 0,
      acertos: payload.acertos ?? 0,
      chaveIdempotencia: key
    };

    return await request<SessaoRegistradaDTO>(`/trilhas/${encodeURIComponent(trilhaId)}/sessoes`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    });
  },

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

  async listarSessoes(
    trilhaId: string,
    filtro?: { de?: string; ate?: string; disciplinaId?: string; dataInicio?: string; dataFim?: string }
  ): Promise<Sessao[]> {
    const pag = await this.listarSessoesPaginado(trilhaId, {
      ...filtro,
      pagina: 1,
      tamanho: 100
    });
    return pag.itens;
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
    const res = await request<PaginaSessoesDTO>(
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
        minutos: Math.round(dto.duracaoSegundos / 60),
        inicio: dto.dataSessao ? `${dto.dataSessao}T12:00:00Z` : dto.criadoEm,
        questoes: dto.questoes ?? 0,
        acertos: dto.acertos ?? 0,
        erros: dto.erros ?? Math.max(0, (dto.questoes ?? 0) - (dto.acertos ?? 0)),
        percentualAcerto: dto.percentualAcerto,
        editavel: dto.editavel,
        chaveIdempotencia: dto.chaveIdempotencia,
        tipo: 'ESTUDO',
        criadoEm: dto.criadoEm,
        atualizadoEm: dto.atualizadoEm
      })),
      pagina: res.pagina,
      tamanho: res.tamanho,
      total: res.totalItens,
      totalPaginas: res.totalPaginas
    };
  },

  async obterResumoEstatisticas(
    trilhaId: string,
    filtro?: { de?: string; ate?: string; dataInicio?: string; dataFim?: string }
  ): Promise<ResumoEstatisticas> {
    const stats = await this.obterEstatisticas(trilhaId, filtro);
    return {
      totalMinutos: Math.round(stats.totalSegundos / 60),
      totalQuestoes: stats.totalQuestoes,
      taxaAcerto: stats.percentualAcertoGlobal,
      streakDiasAtual: stats.streakDias,
      melhorStreakDias: stats.streakDias,
      totalSessoes: Number(stats.totalSessoes),
      diasComEstudo: stats.serieTemporal.length
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

  // ── FASE 4: CRONOGRAMAS, BLOCOS & DISPONIBILIDADE SEMANAL ──
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
      dataInicio?: string;
      dataFim?: string;
      pagina?: number;
      tamanho?: number;
    }
  ): Promise<PaginaBlocosDTO> {
    const search = new URLSearchParams();
    if (params?.concluido !== undefined) search.set('concluido', String(params.concluido));
    if (params?.dataInicio) search.set('dataInicio', params.dataInicio);
    if (params?.dataFim) search.set('dataFim', params.dataFim);
    if (params?.pagina) search.set('pagina', String(params.pagina));
    if (params?.tamanho) search.set('tamanho', String(params.tamanho));
    const query = search.toString() ? `?${search.toString()}` : '';

    return await request<PaginaBlocosDTO>(
      `/trilhas/${encodeURIComponent(trilhaId)}/cronogramas/${encodeURIComponent(cronogramaId)}/blocos${query}`
    );
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

export const estudosService = EstudosService;
export type {
  CronogramaDTO,
  CriarCronogramaPayload,
  AtualizarCronogramaPayload,
  BlocoCronogramaItemDTO,
  PaginaBlocosDTO,
  ConcluirAtrasadosResponse,
  DisponibilidadeSemanalDTO,
  BlocoHojeDTO
};

