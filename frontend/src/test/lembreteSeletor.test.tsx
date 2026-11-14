import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TarefaService } from '../modules/tarefas/services/tarefaService';
import type { Tarefa } from '../modules/tarefas/types/tarefa';

vi.mock('../modules/tarefas/services/api', () => ({
  request: vi.fn()
}));

import { request } from '../modules/tarefas/services/api';

const tarefa = (over: Partial<Tarefa>): Tarefa => ({
  id: 't-1',
  titulo: 'Tarefa',
  concluida: false,
  prioridade: 'MEDIA',
  ...over
});

describe('lembreteSeletor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('"Sem alerta" envia modoLembrete SEM_ALERTA e nunca minutos', async () => {
    vi.mocked(request).mockResolvedValueOnce(
      tarefa({ modoLembrete: 'SEM_ALERTA', lembreteMinutosAntes: undefined })
    );

    const criada = await TarefaService.criarTarefa({
      titulo: 'Sem alerta',
      modoLembrete: 'SEM_ALERTA'
    });

    const [, opcoes] = vi.mocked(request).mock.calls[0];
    const corpo = JSON.parse(String(opcoes?.body));
    expect(corpo.modoLembrete).toBe('SEM_ALERTA');
    expect(corpo.lembreteMinutosAntes).toBeUndefined();
    expect(criada.modoLembrete).toBe('SEM_ALERTA');
  });

  it('"Usar padrão" envia HERDAR sem minutos e é distinto de SEM_ALERTA no payload', async () => {
    vi.mocked(request).mockResolvedValueOnce(tarefa({ modoLembrete: 'HERDAR' }));

    await TarefaService.criarTarefa({ titulo: 'Herdar', modoLembrete: 'HERDAR' });

    const [, opcoes] = vi.mocked(request).mock.calls[0];
    const corpo = JSON.parse(String(opcoes?.body));
    expect(corpo.modoLembrete).toBe('HERDAR');
    expect(corpo.lembreteMinutosAntes).toBeUndefined();
    expect(corpo.modoLembrete).not.toBe('SEM_ALERTA');
  });

  it('"No horário" envia PERSONALIZADO com 0 minuto', async () => {
    vi.mocked(request).mockResolvedValueOnce(
      tarefa({ modoLembrete: 'PERSONALIZADO', lembreteMinutosAntes: 0 })
    );

    await TarefaService.criarTarefa({
      titulo: 'No horário',
      modoLembrete: 'PERSONALIZADO',
      lembreteMinutosAntes: 0
    });

    const [, opcoes] = vi.mocked(request).mock.calls[0];
    const corpo = JSON.parse(String(opcoes?.body));
    expect(corpo.modoLembrete).toBe('PERSONALIZADO');
    expect(corpo.lembreteMinutosAntes).toBe(0);
  });

  it('PERSONALIZADO propaga o deslocamento escolhido (5/15/30/60)', async () => {
    for (const minutos of [5, 15, 30, 60]) {
      vi.mocked(request).mockResolvedValueOnce(
        tarefa({ modoLembrete: 'PERSONALIZADO', lembreteMinutosAntes: minutos })
      );
      await TarefaService.criarTarefa({
        titulo: 'Personalizado',
        modoLembrete: 'PERSONALIZADO',
        lembreteMinutosAntes: minutos
      });
      const [, opcoes] = vi.mocked(request).mock.calls.at(-1)!;
      expect(JSON.parse(String(opcoes?.body)).lembreteMinutosAntes).toBe(minutos);
    }
  });
});
