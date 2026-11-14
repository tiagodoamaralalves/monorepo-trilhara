import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { EstudosService, topicosConcluidosDe } from '../modules/estudos/services/estudosService';
import type { TrilhaResumoDTO, TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

/**
 * Contratos de Estudos após a o serviço devolve os DTOs do backend sem adaptadores
 * e o progresso é derivado de `topicosConcluidosIds`.
 */
describe('EstudosService e Contratos Normalizados', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('listarTrilhas devolve TrilhaResumoDTO sem adaptação', async () => {
    const resumos: TrilhaResumoDTO[] = [
      {
        id: 't-123',
        nome: 'Receita Federal',
        cargo: 'Auditor Fiscal',
        banca: 'FGV',
        dataProva: '2026-12-01',
        totalDisciplinas: 5,
        totalTopicos: 40,
        topicosConcluidos: 10,
        percentualProgresso: 25.0,
        cronogramaAtivoResumo: {
          cronogramaAtivoId: 'c-1',
          modalidade: 'SEQUENCIAL',
          dataInicio: '2026-01-01',
          dataFim: '2026-12-01'
        }
      }
    ];

    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      expect(url).toContain('/api/trilhas');
      return new Response(JSON.stringify(resumos), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const lista = await EstudosService.listarTrilhas();
    expect(lista).toHaveLength(1);
    expect(lista[0].id).toBe('t-123');
    expect(lista[0].totalDisciplinas).toBe(5);
    expect(lista[0].totalTopicos).toBe(40);
    expect(lista[0].topicosConcluidos).toBe(10);
    expect(lista[0].percentualProgresso).toBe(25.0);
    expect(lista[0].cronogramaAtivoResumo?.cronogramaAtivoId).toBe('c-1');
    // O resumo não carrega árvore de tópicos (requisito §4.1).
    expect(lista[0]).not.toHaveProperty('disciplinas');
    expect(lista[0]).not.toHaveProperty('progresso');
  });

  it('buscarTrilha chama GET /api/trilhas/{id} e o progresso é derivado de topicosConcluidosIds', async () => {
    const detalhe: TrilhaDetalheDTO = {
      id: 't-abc',
      nome: 'Trilha ABC',
      disciplinas: [
        {
          id: 'd-10',
          trilhaId: 't-abc',
          nome: 'Português',
          peso: 1,
          ordem: 1,
          totalTopicos: 2,
          topicosConcluidos: 1,
          percentualProgresso: 50.0,
          topicos: [
            { id: 'top-1', nome: 'Sintaxe', ordem: 1, concluido: true },
            { id: 'top-2', nome: 'Crase', ordem: 2, concluido: false }
          ]
        }
      ],
      topicosConcluidosIds: ['top-1']
    };

    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      expect(url).toContain('/api/trilhas/t-abc');
      return new Response(JSON.stringify(detalhe), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const trilha = await EstudosService.buscarTrilha('t-abc');
    const progresso = topicosConcluidosDe(trilha);

    expect(trilha.id).toBe('t-abc');
    expect(trilha.disciplinas[0].topicos).toHaveLength(2);
    expect(progresso.has('top-1')).toBe(true);
    expect(progresso.has('top-2')).toBe(false);
    expect(trilha).not.toHaveProperty('progresso');
  });

  it('criarTrilha envia disciplinasIniciais em POST /api/trilhas', async () => {
    let bodyEnviado: unknown = null;
    let urlChamada = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(
        JSON.stringify({ id: 't-nova', nome: 'Nova', disciplinas: [], topicosConcluidosIds: [] }),
        { status: 201, headers: { 'Content-Type': 'application/json' } }
      );
    });

    await EstudosService.criarTrilha({
      nome: 'Nova',
      disciplinasIniciais: [{ nome: 'D1', topicos: [{ nome: 'T1' }] }]
    });

    expect(urlChamada).toContain('/api/trilhas');
    expect(urlChamada).not.toContain('/importar/');
    expect((bodyEnviado as { disciplinasIniciais: unknown[] }).disciplinasIniciais).toHaveLength(1);
    expect(bodyEnviado).not.toHaveProperty('disciplinas');
  });

  it('importarEdital usa POST /api/trilhas com editalId, sem rota dedicada de importação', async () => {
    let urlChamada = '';
    let bodyEnviado: unknown = null;

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(
        JSON.stringify({ id: 't-imp', nome: 'Importada', disciplinas: [], topicosConcluidosIds: [] }),
        { status: 201, headers: { 'Content-Type': 'application/json' } }
      );
    });

    await EstudosService.importarEdital('60c72b2f9b1d8b2bad000001');

    expect(urlChamada).toContain('/api/trilhas');
    expect(urlChamada).not.toContain('/importar/');
    expect((bodyEnviado as { editalId: string }).editalId).toBe('60c72b2f9b1d8b2bad000001');
  });

  it('atualizarMetadadosTrilha usa PUT /api/trilhas/{id} (sem rota /metadados)', async () => {
    let urlChamada = '';
    let metodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      metodo = init?.method || '';
      return new Response(
        JSON.stringify({ id: 't-1', nome: 'Atualizada', disciplinas: [], topicosConcluidosIds: [] }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    await EstudosService.atualizarMetadadosTrilha('t-1', { nome: 'Atualizada', cargo: 'Auditor' });

    expect(metodo).toBe('PUT');
    expect(urlChamada).toContain('/api/trilhas/t-1');
    expect(urlChamada).not.toContain('/metadados');
  });

  it('obterImpactoExclusaoTrilha chama GET /api/trilhas/{id}/impacto-exclusao', async () => {
    let urlChamada = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      urlChamada = url;
      return new Response(
        JSON.stringify({ disciplinas: 2, cronogramas: 1, sessoes: 3, cadernosVinculados: 1 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const impacto = await EstudosService.obterImpactoExclusaoTrilha('t-1');
    expect(urlChamada).toContain('/api/trilhas/t-1/impacto-exclusao');
    expect(impacto).toEqual({ disciplinas: 2, cronogramas: 1, sessoes: 3, cadernosVinculados: 1 });
  });

  it('criarDisciplina chama POST /api/trilhas/{id}/disciplinas sem cadernoLink', async () => {
    let metodo = '';
    let bodyEnviado: unknown = null;

    globalThis.fetch = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) => {
      metodo = init?.method || '';
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(
        JSON.stringify({ id: 'nova-d', trilhaId: 't-1', nome: 'Direito Administrativo', peso: 1, ordem: 1, topicos: [] }),
        { status: 201, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const res = await EstudosService.criarDisciplina('t-1', {
      nome: 'Direito Administrativo',
      cor: '#336699',
      peso: 1,
      topicos: [{ nome: 'Atos Administrativos', ordem: 1 }]
    });

    expect(metodo).toBe('POST');
    expect(res.id).toBe('nova-d');
    expect(bodyEnviado).not.toHaveProperty('cadernoLink');
  });

  it('reordenarDisciplinas envia disciplinaIds (nome do contrato, não ordemIds)', async () => {
    let metodo = '';
    let bodyEnviado: unknown = null;

    globalThis.fetch = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) => {
      metodo = init?.method || '';
      bodyEnviado = JSON.parse(init?.body as string);
      return new Response(null, { status: 204 });
    });

    await EstudosService.reordenarDisciplinas('t-1', ['d-2', 'd-1', 'd-3']);

    expect(metodo).toBe('PUT');
    expect((bodyEnviado as { disciplinaIds: string[] }).disciplinaIds).toEqual(['d-2', 'd-1', 'd-3']);
    expect(bodyEnviado).not.toHaveProperty('ordemIds');
  });

  it('marcarTopicoConcluido chama PUT /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso', async () => {
    let urlChamada = '';
    let metodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      metodo = init?.method || '';
      return new Response(
        JSON.stringify({ topicoId: 'top-1', concluido: true, concluidoEm: '2026-11-09T12:00:00Z' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
    });

    const res = await EstudosService.marcarTopicoConcluido('t-1', 'top-1');

    expect(metodo).toBe('PUT');
    expect(urlChamada).toContain('/api/trilhas/t-1/topicos/top-1/progresso');
    expect(res.concluido).toBe(true);
  });

  it('desmarcarTopicoConcluido chama DELETE /api/trilhas/{trilhaId}/topicos/{topicoId}/progresso', async () => {
    let urlChamada = '';
    let metodo = '';

    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      urlChamada = url;
      metodo = init?.method || '';
      return new Response(null, { status: 204 });
    });

    await EstudosService.desmarcarTopicoConcluido('t-1', 'top-1');

    expect(metodo).toBe('DELETE');
    expect(urlChamada).toContain('/api/trilhas/t-1/topicos/top-1/progresso');
  });
});
