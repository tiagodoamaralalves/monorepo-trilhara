import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TarefaService } from '../modules/tarefas/services/tarefaService';
import { Tarefa, TarefasHojeDTO, LixeiraTarefasDTO } from '../modules/tarefas/types/tarefa';

// Mock do módulo de API request
vi.mock('../modules/tarefas/services/api', () => ({
  request: vi.fn()
}));

import { request } from '../modules/tarefas/services/api';

describe('FASE 6 - Tarefas Manuais, Recorrência e Lixeira (Frontend)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Checklist e Subtarefas independentes', () => {
    it('alternar conclusão de subtarefa não altera automaticamente o estado da tarefa pai', async () => {
      const mockTarefaAtualizada: Tarefa = {
        id: 'tarefa-123',
        titulo: 'Revisar Direito Constitucional',
        concluida: false,
        prioridade: 'MEDIA',
        subtarefas: [
          { id: 'sub-1', titulo: 'Ler Art. 5º', concluida: true, ordem: 0 },
          { id: 'sub-2', titulo: 'Fazer 10 questões', concluida: false, ordem: 1 }
        ]
      };

      vi.mocked(request).mockResolvedValueOnce(mockTarefaAtualizada);

      const res = await TarefaService.alternarSubtarefa('tarefa-123', 'sub-1', true);

      expect(request).toHaveBeenCalledWith(
        '/tarefas/tarefa-123/subtarefas/sub-1',
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ concluida: true })
        })
      );
      expect(res.id).toBe('tarefa-123');
      expect(res.subtarefas?.[0].concluida).toBe(true);
      expect(res.concluida).toBe(false); // Tarefa pai continua não-concluída
    });
  });

  describe('Edição e Exclusão com 3 Escopos de Recorrência', () => {
    it('atualiza tarefa recorrente no escopo ESTA', async () => {
      const mockRetorno: Tarefa = {
        id: 'tarefa-rec-1',
        titulo: 'Ocorrência Editada Isoladamente',
        concluida: false,
        prioridade: 'ALTA',
        desvinculadaDaSerie: true
      };

      vi.mocked(request).mockResolvedValueOnce(mockRetorno);

      const res = await TarefaService.atualizarTarefa(
        'tarefa-rec-1',
        { titulo: 'Ocorrência Editada Isoladamente' },
        'ESTA'
      );

      expect(request).toHaveBeenCalledWith(
        '/tarefas/tarefa-rec-1?escopo=ESTA',
        expect.objectContaining({
          method: 'PUT'
        })
      );
      expect(res.desvinculadaDaSerie).toBe(true);
    });

    it('exclui tarefa recorrente no escopo ESTA_E_FUTURAS', async () => {
      vi.mocked(request).mockResolvedValueOnce(undefined);

      await TarefaService.excluirTarefa('tarefa-rec-2', 'ESTA_E_FUTURAS');

      expect(request).toHaveBeenCalledWith(
        '/tarefas/tarefa-rec-2?escopo=ESTA_E_FUTURAS',
        expect.objectContaining({
          method: 'DELETE'
        })
      );
    });

    it('exclui tarefa recorrente no escopo TODA_A_SERIE', async () => {
      vi.mocked(request).mockResolvedValueOnce(undefined);

      await TarefaService.excluirTarefa('tarefa-rec-3', 'TODA_A_SERIE');

      expect(request).toHaveBeenCalledWith(
        '/tarefas/tarefa-rec-3?escopo=TODA_A_SERIE',
        expect.objectContaining({
          method: 'DELETE'
        })
      );
    });
  });

  describe('Visão Hoje (GET /api/tarefas/hoje)', () => {
    it('obterTarefasHoje carrega e normaliza tarefas em atrasadas, hoje e semData', async () => {
      const mockHojeResponse: TarefasHojeDTO = {
        hojeLocal: '2026-11-09',
        atrasadas: [
          {
            id: 't-atrasada-1',
            titulo: 'Tarefa Atrasada de Ontem',
            serieId: 'serie-abc',
            dataOriginalOcorrencia: '2026-11-08',
            concluida: false,
            prioridade: 'ALTA'
          }
        ],
        hoje: [
          {
            id: 't-hoje-1',
            titulo: 'Tarefa de Hoje',
            concluida: false,
            prioridade: 'MEDIA'
          }
        ],
        semData: [
          {
            id: 't-semdata-1',
            titulo: 'Tarefa no Backlog',
            concluida: false,
            prioridade: 'BAIXA'
          }
        ]
      };

      vi.mocked(request).mockResolvedValueOnce(mockHojeResponse);

      const res = await TarefaService.obterTarefasHoje();

      expect(request).toHaveBeenCalledWith('/tarefas/hoje');
      expect(res.hojeLocal).toBe('2026-11-09');
      expect(res.atrasadas).toHaveLength(1);
      expect(res.hoje).toHaveLength(1);
      expect(res.semData).toHaveLength(1);
      expect(res.atrasadas[0].prioridade).toBe('ALTA');
    });
  });

  describe('Lixeira e Restauração', () => {
    it('obterLixeira consulta GET /api/tarefas/lixeira', async () => {
      const mockLixeira: LixeiraTarefasDTO = {
        tarefas: [
          {
            id: 't-del-1',
            titulo: 'Tarefa Excluída',
            concluida: false,
            prioridade: 'BAIXA',
            deletedAt: '2026-11-09T10:00:00Z',
            expiresAt: '2026-12-09T10:00:00Z'
          }
        ],
        listas: [
          {
            id: 'lista-del-1',
            nome: 'Lista Deletada',
            deletedAt: '2026-11-09T10:00:00Z',
            expiresAt: '2026-12-09T10:00:00Z'
          }
        ],
        series: []
      };

      vi.mocked(request).mockResolvedValueOnce(mockLixeira);

      const res = await TarefaService.obterLixeira();

      expect(request).toHaveBeenCalledWith('/tarefas/lixeira');
      expect(res.tarefas).toHaveLength(1);
      expect(res.listas).toHaveLength(1);
      expect(res.tarefas[0].titulo).toBe('Tarefa Excluída');
    });

    it('restaurarTarefa chama POST /api/tarefas/lixeira/{id}/restaurar', async () => {
      const mockRestaurada: Tarefa = {
        id: 't-del-1',
        titulo: 'Tarefa Excluída',
        concluida: false,
        prioridade: 'BAIXA'
      };

      vi.mocked(request).mockResolvedValueOnce(mockRestaurada);

      const res = await TarefaService.restaurarTarefa('t-del-1');

      expect(request).toHaveBeenCalledWith(
        '/tarefas/lixeira/t-del-1/restaurar',
        expect.objectContaining({ method: 'POST' })
      );
      expect(res.id).toBe('t-del-1');
    });

    it('esvaziarLixeira chama DELETE /api/tarefas/lixeira/esvaziar', async () => {
      vi.mocked(request).mockResolvedValueOnce(undefined);

      await TarefaService.esvaziarLixeira();

      expect(request).toHaveBeenCalledWith(
        '/tarefas/lixeira/esvaziar',
        expect.objectContaining({ method: 'DELETE' })
      );
    });
  });
});
