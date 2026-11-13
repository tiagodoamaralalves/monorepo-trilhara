import { request } from './api';
import type { Pagina } from '@shared/contracts';
import {
  Tarefa,
  ListaTarefas,
  SerieRecorrenteDTO,
  EscopoRecorrencia,
  TarefasHojeDTO,
  LixeiraTarefasDTO,
  RestauracaoTarefaDTO,
  ListaImpactoExclusaoDTO,
  ExclusaoListaDTO,
  ListaTarefasDTO,
  TarefaDTO,
  PrioridadeTarefa
} from '../types/tarefa';

function normalizarPrioridade(p?: unknown): PrioridadeTarefa {
  if (!p) return 'MEDIA';
  const upper = String(p).toUpperCase();
  if (upper === 'BAIXA' || upper === 'MEDIA' || upper === 'ALTA') return upper;
  if (upper === 'URGENTE') return 'ALTA';
  return 'MEDIA';
}

function normalizarTarefa(t: TarefaDTO | Tarefa): Tarefa {
  const data = t.dataVencimento || '';
  const desc = t.descricao || '';
  return {
    ...t,
    data,
    dataVencimento: data,
    descricao: desc,
    observacoes: desc,
    subtarefas: t.subtarefas || [],
    prioridade: normalizarPrioridade(t.prioridade),
    desvinculadaDaSerie: Boolean(t.desvinculadaDaSerie)
  };
}

function normalizarLista(l: ListaTarefasDTO | ListaTarefas): ListaTarefas {
  const nome = l.nome || 'Sem título';
  return {
    ...l,
    nome,
    titulo: nome
  };
}

export const TarefaService = {
  // ── LISTAS DE TAREFAS ──

  async listarListas(): Promise<ListaTarefas[]> {
    const res = await request<ListaTarefasDTO[]>('/listas-tarefas');
    return res.map(normalizarLista);
  },

  async buscarLista(id: string): Promise<ListaTarefas> {
    const res = await request<ListaTarefasDTO>(`/listas-tarefas/${encodeURIComponent(id)}`);
    return normalizarLista(res);
  },

  async criarLista(dados: { nome?: string; titulo?: string; cor?: string; ordem?: number }): Promise<ListaTarefas> {
    const nome = dados.nome || dados.titulo || 'Nova Lista';
    const res = await request<ListaTarefasDTO>('/listas-tarefas', {
      method: 'POST',
      body: JSON.stringify({
        nome,
        cor: dados.cor,
        ordem: dados.ordem
      })
    });
    return normalizarLista(res);
  },

  async atualizarLista(id: string, dados: { nome?: string; titulo?: string; cor?: string; ordem?: number }): Promise<ListaTarefas> {
    const nome = dados.nome || dados.titulo;
    const res = await request<ListaTarefasDTO>(`/listas-tarefas/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({
        nome,
        cor: dados.cor,
        ordem: dados.ordem
      })
    });
    return normalizarLista(res);
  },

  async excluirLista(id: string): Promise<ExclusaoListaDTO> {
    return request<ExclusaoListaDTO>(`/listas-tarefas/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  async restaurarLista(id: string): Promise<ListaTarefas> {
    const res = await request<ListaTarefasDTO>(`/listas-tarefas/${encodeURIComponent(id)}/restaurar`, {
      method: 'POST'
    });
    return normalizarLista(res);
  },

  // ── TAREFAS ──

  async listarTarefas(params?: {
    listaId?: string;
    filtro?: string;
    data?: string;
    dataInicio?: string;
    dataFim?: string;
    concluida?: boolean;
    estado?: string;
    pagina?: number;
    tamanho?: number;
  }): Promise<Pagina<Tarefa>> {
    const query = new URLSearchParams();
    if (params?.listaId) query.set('listaId', params.listaId);
    if (params?.filtro) query.set('filtro', params.filtro);
    if (params?.data) query.set('data', params.data);
    if (params?.dataInicio) query.set('dataInicio', params.dataInicio);
    if (params?.dataFim) query.set('dataFim', params.dataFim);
    if (params?.concluida !== undefined) query.set('concluida', String(params.concluida));
    if (params?.estado) query.set('estado', params.estado);
    if (params?.pagina !== undefined) query.set('pagina', String(params.pagina));
    if (params?.tamanho !== undefined) query.set('tamanho', String(params.tamanho));

    const qs = query.toString() ? `?${query.toString()}` : '';
    const pagina = await request<Pagina<TarefaDTO>>(`/tarefas${qs}`);
    return { ...pagina, itens: (pagina.itens || []).map(normalizarTarefa) };
  },

  async buscarTarefa(id: string): Promise<Tarefa> {
    const t = await request<TarefaDTO>(`/tarefas/${encodeURIComponent(id)}`);
    return normalizarTarefa(t);
  },

  async criarTarefa(dados: Partial<Tarefa>): Promise<Tarefa> {
    const prioridade = String(dados.prioridade || 'MEDIA').toUpperCase();
    const payload = {
      listaId: dados.listaId || undefined,
      titulo: dados.titulo?.trim(),
      descricao: (dados.descricao || dados.observacoes || '').trim() || undefined,
      dataVencimento: dados.dataVencimento || dados.data || undefined,
      horario: dados.horario || undefined,
      prioridade,
      subtarefas: dados.subtarefas || [],
      modoLembrete: dados.modoLembrete,
      lembreteMinutosAntes: dados.lembreteMinutosAntes,
      fusoAgendamento: dados.fusoAgendamento,
      ordem: dados.ordem
    };

    const criada = await request<TarefaDTO>('/tarefas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    return normalizarTarefa(criada);
  },

  async atualizarTarefa(
    id: string,
    dados: Partial<Tarefa>,
    escopo?: EscopoRecorrencia,
    idempotencyKey?: string
  ): Promise<Tarefa> {
    const prioridade = dados.prioridade ? String(dados.prioridade).toUpperCase() : undefined;
    const payload = {
      listaId: dados.listaId || undefined,
      titulo: dados.titulo?.trim(),
      descricao: (dados.descricao || dados.observacoes || '').trim() || undefined,
      dataVencimento: dados.dataVencimento || dados.data || undefined,
      horario: dados.horario || undefined,
      prioridade,
      subtarefas: dados.subtarefas || [],
      modoLembrete: dados.modoLembrete,
      lembreteMinutosAntes: dados.lembreteMinutosAntes,
      fusoAgendamento: dados.fusoAgendamento,
      ordem: dados.ordem,
      escopo
    };

    const qs = escopo ? `?escopo=${encodeURIComponent(escopo)}` : '';
    const headers: Record<string, string> = {};
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

    const atualizada = await request<TarefaDTO>(`/tarefas/${encodeURIComponent(id)}${qs}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(payload)
    });
    return normalizarTarefa(atualizada);
  },

  async alternarConclusao(id: string, concluida: boolean): Promise<Tarefa> {
    const atualizada = await request<TarefaDTO>(`/tarefas/${encodeURIComponent(id)}/conclusao`, {
      method: 'PATCH',
      body: JSON.stringify({ concluida })
    });
    return normalizarTarefa(atualizada);
  },

  async alternarSubtarefa(tarefaId: string, subtarefaId: string, concluida: boolean): Promise<Tarefa> {
    const atualizada = await request<TarefaDTO>(
      `/tarefas/${encodeURIComponent(tarefaId)}/subtarefas/${encodeURIComponent(subtarefaId)}`,
      {
        method: 'PATCH',
        body: JSON.stringify({ concluida })
      }
    );
    return normalizarTarefa(atualizada);
  },

  async pularTarefa(id: string): Promise<Tarefa> {
    const atualizada = await request<TarefaDTO>(`/tarefas/${encodeURIComponent(id)}/pular`, {
      method: 'POST'
    });
    return normalizarTarefa(atualizada);
  },

  async excluirTarefa(id: string, escopo?: EscopoRecorrencia): Promise<void> {
    const qs = escopo ? `?escopo=${encodeURIComponent(escopo)}` : '';
    await request(`/tarefas/${encodeURIComponent(id)}${qs}`, {
      method: 'DELETE'
    });
  },

  // ── VISÃO HOJE ──

  async obterTarefasHoje(): Promise<TarefasHojeDTO> {
    const res = await request<TarefasHojeDTO>('/tarefas/hoje');
    return {
      hojeLocal: res.hojeLocal,
      atrasadas: (res.atrasadas || []).map(normalizarTarefa),
      hoje: (res.hoje || []).map(normalizarTarefa),
      semData: (res.semData || []).map(normalizarTarefa)
    };
  },

  // ── SÉRIES RECORRENTES ──

  async listarSeries(): Promise<SerieRecorrenteDTO[]> {
    return request<SerieRecorrenteDTO[]>('/series-recorrentes');
  },

  async buscarSerie(id: string): Promise<SerieRecorrenteDTO> {
    return request<SerieRecorrenteDTO>(`/series-recorrentes/${encodeURIComponent(id)}`);
  },

  async criarSerie(dados: Partial<SerieRecorrenteDTO>): Promise<SerieRecorrenteDTO> {
    return request<SerieRecorrenteDTO>('/series-recorrentes', {
      method: 'POST',
      body: JSON.stringify(dados)
    });
  },

  async atualizarSerie(id: string, dados: Partial<SerieRecorrenteDTO>): Promise<SerieRecorrenteDTO> {
    return request<SerieRecorrenteDTO>(`/series-recorrentes/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(dados)
    });
  },

  async excluirSerie(id: string): Promise<void> {
    await request(`/series-recorrentes/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  // ── LIXEIRA (30 DIAS) ──

  async listarLixeira(params?: { pagina?: number; tamanho?: number }): Promise<LixeiraTarefasDTO> {
    const query = new URLSearchParams();
    if (params?.pagina !== undefined) query.set('pagina', String(params.pagina));
    if (params?.tamanho !== undefined) query.set('tamanho', String(params.tamanho));
    const qs = query.toString() ? `?${query.toString()}` : '';

    const res = await request<LixeiraTarefasDTO>(`/tarefas/lixeira${qs}`);
    return {
      tarefas: (res.tarefas || []).map(normalizarTarefa),
      listas: (res.listas || []).map(normalizarLista),
      series: res.series || [],
      pagina: res.pagina ?? 1,
      tamanho: res.tamanho ?? 50,
      totalItens: res.totalItens ?? 0,
      totalPaginas: res.totalPaginas ?? 1
    };
  },

  async obterLixeira(params?: { pagina?: number; tamanho?: number }): Promise<LixeiraTarefasDTO> {
    return this.listarLixeira(params);
  },

  async restaurarTarefaLixeira(id: string): Promise<RestauracaoTarefaDTO> {
    const res = await request<RestauracaoTarefaDTO>(`/tarefas/lixeira/${encodeURIComponent(id)}/restaurar`, {
      method: 'POST'
    });
    return { ...res, tarefa: normalizarTarefa(res.tarefa) };
  },

  async restaurarTarefa(id: string): Promise<RestauracaoTarefaDTO> {
    return this.restaurarTarefaLixeira(id);
  },

  /** D-33: contagens exibidas no ConfirmDialog antes de excluir a lista. */
  async obterImpactoExclusaoLista(id: string): Promise<ListaImpactoExclusaoDTO> {
    return request<ListaImpactoExclusaoDTO>(`/listas-tarefas/${encodeURIComponent(id)}/impacto-exclusao`);
  },

  async esvaziarLixeira(): Promise<void> {
    await request('/tarefas/lixeira/esvaziar', {
      method: 'DELETE'
    });
  },

  // Os blocos de cronograma não são tarefas: a lista virtual usa
  // `GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos` (Estudos).
};
