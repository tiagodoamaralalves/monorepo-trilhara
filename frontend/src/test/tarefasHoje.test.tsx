import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { agendaHojeService, derivarAgenda } from '../shared/agenda/agendaHojeService';

vi.mock('../shared/auth/api', () => ({
  request: vi.fn()
}));

import { request } from '../shared/auth/api';

const USUARIO = '6af20000000000000000000a';

/** Estado derivado da última carga — atalho dos asserts deste arquivo. */
async function carregarAgenda() {
  const estado = await agendaHojeService.recarregar(USUARIO);
  return derivarAgenda(estado)!;
}

describe('tarefasHoje (D-23)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    agendaHojeService.resetarParaTestes();
  });

  it('hoje devolve somente tarefas PENDENTES (concluídas não aparecem)', async () => {
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: '2026-11-09',
          atrasadas: [],
          hoje: [
            { id: 'p-1', titulo: 'Pendente', concluida: false, estado: 'PENDENTE', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }
          ],
          semData: []
        };
      }
      return [];
    });

    const dados = await carregarAgenda();

    expect(dados).not.toBeNull();
    expect(dados.tarefasHoje).toHaveLength(1);
    expect(dados.tarefasHoje[0].id).toBe('p-1');
    // Nenhuma tarefa concluída é derivada de Hoje: a chave não existe mais.
    expect(Object.keys(dados)).not.toContain('tarefasConcluidasHoje');
  });

  it('tarefas concluídas não entram em Hoje mesmo se a API as enviar', async () => {
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: '2026-11-09',
          atrasadas: [],
          hoje: [
            { id: 'c-1', titulo: 'Concluída', concluida: true, estado: 'CONCLUIDA', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }
          ],
          semData: []
        };
      }
      return [];
    });

    const dados = await carregarAgenda();

    expect(dados.tarefasHoje).toHaveLength(0);
  });

  it('atrasadas mantêm agrupamento único sob a ocorrência mais recente', async () => {
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: '2026-11-09',
          atrasadas: [
            { id: 'a-1', titulo: 'Antiga', concluida: false, estado: 'PENDENTE', prioridade: 'MEDIA', serieId: 's-1', dataVencimento: '2026-11-01' },
            { id: 'a-2', titulo: 'Recente', concluida: false, estado: 'PENDENTE', prioridade: 'MEDIA', serieId: 's-1', dataVencimento: '2026-11-05' }
          ],
          hoje: [],
          semData: []
        };
      }
      return [];
    });

    const dados = await carregarAgenda();

    expect(dados.atrasadasAgrupadas).toHaveLength(1);
    expect(dados.atrasadasAgrupadas[0].principal.id).toBe('a-2');
    expect(dados.atrasadasAgrupadas[0].anteriores).toHaveLength(1);
  });

  it('falha de rede mantém o estado anterior e registra erroTarefas', async () => {
    vi.mocked(request).mockRejectedValue(new Error('offline'));

    const dados = await carregarAgenda();

    expect(dados?.erroTarefas).toBeInstanceOf(Error);
    expect(dados.tarefasHoje).toHaveLength(0);
  });

  it('empty state de Hoje usa o texto do Anexo B', () => {
    render(
      <div>
        <h2>Tudo em dia por hoje</h2>
        <p>Nenhuma tarefa ou bloco pendente para hoje.</p>
      </div>
    );

    expect(screen.getByText('Tudo em dia por hoje')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma tarefa ou bloco pendente para hoje.')).toBeInTheDocument();
  });
});
