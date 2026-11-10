import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  EstudosService,
  adaptarTrilhaResumo,
  adaptarTrilhaDetalhe
} from '../modules/estudos/services/estudosService';
import type {
  TrilhaResumoDTO,
  TrilhaDetalheDTO
} from '../modules/estudos/types/trilhara';

describe('EstudosService e Contratos Normalizados (FASE 2)', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('adaptarTrilhaResumo deve converter TrilhaResumoDTO preservando contadores e metadados leves', () => {
    const dto: TrilhaResumoDTO = {
      id: 't-123',
      usuarioId: 'u-456',
      donoId: 'u-456',
      nome: 'Receita Federal',
      cargo: 'Auditor Fiscal',
      areaCargo: 'Auditor Fiscal',
      banca: 'FGV',
      dataProva: '2026-12-01',
      totalDisciplinas: 5,
      totalTopicos: 40,
      topicosConcluidos: 10,
      percentualProgresso: 25.0,
      cronogramaAtivoResumo: {
        cronogramaAtivoId: 'c-1',
        modalidade: 'CICLO',
        dataInicio: '2026-01-01',
        dataFim: '2026-12-01'
      }
    };

    const trilha = adaptarTrilhaResumo(dto);
    expect(trilha.id).toBe('t-123');
    expect(trilha.nome).toBe('Receita Federal');
    expect(trilha.areaCargo).toBe('Auditor Fiscal');
    expect(trilha.totalDisciplinas).toBe(5);
    expect(trilha.totalTopicos).toBe(40);
    expect(trilha.topicosConcluidos).toBe(10);
    expect(trilha.percentualProgresso).toBe(25.0);
    expect(trilha.cronogramaAtivoResumo?.cronogramaAtivoId).toBe('c-1');
    expect(trilha.disciplinas).toEqual([]);
    expect(trilha.progresso).toEqual({});
  });

  it('adaptarTrilhaDetalhe deve construir mapa de progresso a partir de topicosConcluidosIds', () => {
    const dto: TrilhaDetalheDTO = {
      id: 't-1',
      usuarioId: 'u-1',
      donoId: 'u-1',
      nome: 'Analista Judiciário',
      cargo: 'Analista',
      areaCargo: 'Analista',
      disciplinas: [
        {
          id: 'd-1',
          trilhaId: 't-1',
          nome: 'Direito Constitucional',
          cor: '#ff0000',
          peso: 2,
          ordem: 1,
          totalTopicos: 2,
          topicosConcluidos: 1,
          percentualProgresso: 50.0,
          topicos: [
            { id: 'top-1', nome: 'Direitos Fundamentais', ordem: 1 },
            { id: 'top-2', nome: 'Poder Judiciário', ordem: 2 }
          ]
        }
      ],
      topicosConcluidosIds: ['top-1'],
      cronogramas: []
    };

    const trilha = adaptarTrilhaDetalhe(dto);
    expect(trilha.id).toBe('t-1');
    expect(trilha.disciplinas.length).toBe(1);
    expect(trilha.disciplinas[0].topicos.length).toBe(2);
    expect(trilha.progresso['top-1']?.done).toBe(true);
    expect(trilha.progresso['top-2']).toBeUndefined();
    expect(trilha.topicosConcluidosIds).toEqual(['top-1']);
  });

  it('listarTrilhas deve chamar GET /api/trilhas e retornar lista adaptada', async () => {
    const mockResumos: TrilhaResumoDTO[] = [
      {
        id: 't-1',
        nome: 'Trilha 1',
        totalDisciplinas: 2,
        totalTopicos: 10,
        topicosConcluidos: 4,
        percentualProgresso: 40.0
      }
    ];

    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      expect(url).toContain('/api/trilhas');
      return new Response(JSON.stringify(mockResumos), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const lista = await EstudosService.listarTrilhas();
    expect(lista.length).toBe(1);
    expect(lista[0].id).toBe('t-1');
    expect(lista[0].totalTopicos).toBe(10);
    expect(lista[0].percentualProgresso).toBe(40.0);
  });

  it('buscarTrilha deve chamar GET /api/trilhas/{id} e retornar detalhe completo', async () => {
    const mockDetalhe: TrilhaDetalheDTO = {
      id: 't-abc',
      nome: 'Trilha ABC',
      disciplinas: [
        {
          id: 'd-10',
          trilhaId: 't-abc',
          nome: 'Português',
          peso: 1,
          ordem: 1,
          totalTopicos: 1,
          topicosConcluidos: 1,
          percentualProgresso: 100.0,
          topicos: [{ id: 't-1', nome: 'Sintaxe', ordem: 1 }]
        }
      ],
      topicosConcluidosIds: ['t-1'],
      cronogramas: []
    };

    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      expect(url).toContain('/api/trilhas/t-abc');
      return new Response(JSON.stringify(mockDetalhe), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const detalhe = await EstudosService.buscarTrilha('t-abc');
    expect(detalhe.id).toBe('t-abc');
    expect(detalhe.disciplinas[0].nome).toBe('Português');
    expect(detalhe.progresso['t-1']?.done).toBe(true);
  });

  it('criarDisciplina deve chamar POST /api/trilhas/{id}/disciplinas', async () => {
    let chamadaMetodo = '';
    let bodyEnviado: unknown = null;

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      chamadaMetodo = init?.method || '';
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(
        JSON.stringify({
          id: 'nova-d',
          trilhaId: 't-1',
          nome: 'Direito Administrativo',
          peso: 1,
          ordem: 1,
          topicos: []
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const res = await EstudosService.criarDisciplina('t-1', {
      nome: 'Direito Administrativo',
      cor: '#336699',
      peso: 1,
      topicos: [{ nome: 'Atos Administrativos', ordem: 1 }]
    });

    expect(chamadaMetodo).toBe('POST');
    expect(res.id).toBe('nova-d');
    expect((bodyEnviado as { nome: string }).nome).toBe('Direito Administrativo');
  });

  it('atualizarDisciplina deve chamar PUT /api/trilhas/{id}/disciplinas/{discId}', async () => {
    let urlChamada = '';
    let chamadaMetodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      chamadaMetodo = init?.method || '';
      return new Response(
        JSON.stringify({
          id: 'd-1',
          trilhaId: 't-1',
          nome: 'Português Atualizado',
          peso: 2,
          ordem: 1,
          topicos: []
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const res = await EstudosService.atualizarDisciplina('t-1', 'd-1', {
      nome: 'Português Atualizado',
      cor: '#00ff00',
      peso: 2,
      topicos: [{ id: 'top-1', nome: 'Crase', ordem: 1 }]
    });

    expect(chamadaMetodo).toBe('PUT');
    expect(urlChamada).toContain('/api/trilhas/t-1/disciplinas/d-1');
    expect(res.nome).toBe('Português Atualizado');
  });

  it('excluirDisciplina deve chamar DELETE /api/trilhas/{id}/disciplinas/{discId}', async () => {
    let urlChamada = '';
    let chamadaMetodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      chamadaMetodo = init?.method || '';
      return new Response(null, { status: 204 });
    });

    await EstudosService.excluirDisciplina('t-1', 'd-del');
    expect(chamadaMetodo).toBe('DELETE');
    expect(urlChamada).toContain('/api/trilhas/t-1/disciplinas/d-del');
  });

  it('reordenarDisciplinas deve chamar PUT /api/trilhas/{id}/disciplinas/reordenar com ordemIds', async () => {
    let chamadaMetodo = '';
    let bodyEnviado: unknown = null;

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      chamadaMetodo = init?.method || '';
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(null, { status: 204 });
    });

    await EstudosService.reordenarDisciplinas('t-1', ['d-2', 'd-1', 'd-3']);
    expect(chamadaMetodo).toBe('PUT');
    expect((bodyEnviado as { ordemIds: string[] }).ordemIds).toEqual(['d-2', 'd-1', 'd-3']);
  });

  it('marcarTopicoConcluido deve chamar PUT /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso', async () => {
    let urlChamada = '';
    let chamadaMetodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      chamadaMetodo = init?.method || '';
      return new Response(
        JSON.stringify({
          id: 'prog-1',
          usuarioId: 'u-1',
          trilhaId: 't-1',
          topicoId: 'top-1',
          concluido: true,
          concluidoEm: '2026-11-09T12:00:00Z'
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const res = await EstudosService.marcarTopicoConcluido('t-1', 'top-1');
    expect(chamadaMetodo).toBe('PUT');
    expect(urlChamada).toContain('/api/trilhas/t-1/topicos/top-1/progresso');
    expect(res.concluido).toBe(true);
  });

  it('desmarcarTopicoConcluido deve chamar DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso', async () => {
    let urlChamada = '';
    let chamadaMetodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      chamadaMetodo = init?.method || '';
      return new Response(null, { status: 204 });
    });

    await EstudosService.desmarcarTopicoConcluido('t-1', 'top-1');
    expect(chamadaMetodo).toBe('DELETE');
    expect(urlChamada).toContain('/api/trilhas/t-1/topicos/top-1/progresso');
  });
});
