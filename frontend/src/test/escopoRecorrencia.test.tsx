import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TarefaService } from '../modules/tarefas/services/tarefaService';

vi.mock('../modules/tarefas/services/api', () => ({
  request: vi.fn()
}));

import { request } from '../modules/tarefas/services/api';

describe('escopoRecorrencia', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ESTA_E_FUTURAS envia Idempotency-Key no cabeçalho e escopo na query', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      id: 't-1',
      titulo: 'Nova regra',
      concluida: false,
      prioridade: 'MEDIA'
    });

    await TarefaService.atualizarTarefa(
      't-1',
      { titulo: 'Nova regra' },
      'ESTA_E_FUTURAS',
      'chave-uuid'
    );

    const [url, opcoes] = vi.mocked(request).mock.calls[0];
    expect(url).toContain('escopo=ESTA_E_FUTURAS');
    expect(opcoes?.headers).toMatchObject({ 'Idempotency-Key': 'chave-uuid' });
  });

  it('ESTA não envia Idempotency-Key nem exige chave', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      id: 't-1',
      titulo: 'Apenas esta',
      concluida: false,
      prioridade: 'MEDIA',
      desvinculadaDaSerie: true
    });

    const res = await TarefaService.atualizarTarefa('t-1', { titulo: 'Apenas esta' }, 'ESTA');

    const [url, opcoes] = vi.mocked(request).mock.calls[0];
    expect(url).toContain('escopo=ESTA');
    expect((opcoes?.headers as Record<string, string>)?.['Idempotency-Key']).toBeUndefined();
    expect(res.desvinculadaDaSerie).toBe(true);
  });

  it('TODA_A_SERIE envia o escopo informado sem chave de idempotência', async () => {
    vi.mocked(request).mockResolvedValueOnce({
      id: 't-1',
      titulo: 'Série',
      concluida: false,
      prioridade: 'MEDIA'
    });

    await TarefaService.atualizarTarefa('t-1', { titulo: 'Série' }, 'TODA_A_SERIE');

    const [url] = vi.mocked(request).mock.calls[0];
    expect(url).toContain('escopo=TODA_A_SERIE');
  });

  it('excluirTarefa propaga o escopo escolhido na query', async () => {
    vi.mocked(request).mockResolvedValueOnce(undefined);

    await TarefaService.excluirTarefa('t-1', 'ESTA_E_FUTURAS');

    expect(request).toHaveBeenCalledWith(
      '/tarefas/t-1?escopo=ESTA_E_FUTURAS',
      expect.objectContaining({ method: 'DELETE' })
    );
  });

  it('excluirTarefa sem escopo não envia parâmetro (tarefa avulsa)', async () => {
    vi.mocked(request).mockResolvedValueOnce(undefined);

    await TarefaService.excluirTarefa('t-1');

    expect(request).toHaveBeenCalledWith('/tarefas/t-1', expect.objectContaining({ method: 'DELETE' }));
  });
});
