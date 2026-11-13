import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TarefaService } from '../modules/tarefas/services/tarefaService';

vi.mock('../modules/tarefas/services/api', () => ({
  request: vi.fn()
}));

import { request } from '../modules/tarefas/services/api';

describe('lixeiraTarefas (D-25 / D-28)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('listarLixeira envia pagina e tamanho e normaliza os metadados', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      tarefas: [
        { id: 't-1', titulo: 'Excluída', concluida: false, prioridade: 'MEDIA', deletedAt: '2026-11-01T10:00:00Z' }
      ],
      listas: [],
      series: [],
      pagina: 2,
      tamanho: 50,
      totalItens: 51,
      totalPaginas: 2
    });

    const res = await TarefaService.listarLixeira({ pagina: 2, tamanho: 50 });

    expect(request).toHaveBeenCalledWith('/tarefas/lixeira?pagina=2&tamanho=50');
    expect(res.pagina).toBe(2);
    expect(res.totalItens).toBe(51);
    expect(res.totalPaginas).toBe(2);
    expect(res.tarefas).toHaveLength(1);
  });

  it('listarLixeira sem parâmetros mantém o default do servidor', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      tarefas: [], listas: [], series: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1
    });

    await TarefaService.listarLixeira();

    expect(request).toHaveBeenCalledWith('/tarefas/lixeira');
  });

  it('restaurarTarefaLixeira devolve realocadaParaListaGeral=false quando a lista foi restaurada', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      tarefa: { id: 't-1', titulo: 'Voltou', concluida: false, prioridade: 'MEDIA', listaId: 'lista-1' },
      realocadaParaListaGeral: false
    });

    const res = await TarefaService.restaurarTarefaLixeira('t-1');

    expect(res.realocadaParaListaGeral).toBe(false);
    expect(res.tarefa.listaId).toBe('lista-1');
  });

  it('restaurarTarefaLixeira sinaliza a realocação na Lista Geral quando a lista foi purgada', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      tarefa: { id: 't-1', titulo: 'Realocada', concluida: false, prioridade: 'MEDIA' },
      realocadaParaListaGeral: true
    });

    const res = await TarefaService.restaurarTarefaLixeira('t-1');

    expect(res.realocadaParaListaGeral).toBe(true);
    expect(res.tarefa.listaId).toBeUndefined();
  });

  it('obterImpactoExclusaoLista consulta o endpoint de D-33', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      id: 'lista-1',
      nome: 'Faculdade',
      tarefasAtivas: 3,
      tarefasConcluidas: 2,
      seriesVinculadas: 1
    });

    const impacto = await TarefaService.obterImpactoExclusaoLista('lista-1');

    expect(request).toHaveBeenCalledWith('/listas-tarefas/lista-1/impacto-exclusao');
    expect(impacto.tarefasAtivas).toBe(3);
    expect(impacto.seriesVinculadas).toBe(1);
  });
});
