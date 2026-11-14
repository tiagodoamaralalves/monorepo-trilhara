import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaskModal } from '../modules/tarefas/components/TaskModal/TaskModal';
import { pushService } from '../shared/services/pushService';
import type { Tarefa } from '../modules/tarefas/types/tarefa';

const TAREFA_BASE: Tarefa = {
  id: 'tarefa-1',
  titulo: 'Revisar Direito Administrativo',
  observacoes: 'Anotações iniciais',
  prioridade: 'MEDIA',
  concluida: false,
  estado: 'PENDENTE',
  dataVencimento: '2026-11-14',
  horario: '10:00',
  ordem: 0,
  subtarefas: [
    { id: 'sub-1', titulo: 'Ler resumo', concluida: false },
    { id: 'sub-2', titulo: 'Fazer 15 questões', concluida: true }
  ],
  criadoEm: '2026-11-10T00:00:00Z',
  atualizadoEm: '2026-11-10T00:00:00Z'
};

describe('TaskModal — padrão minimalista e checklist padronizado', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(pushService, 'obterConfig').mockResolvedValue({
      habilitado: false,
      padraoMinutosAntes: 15
    });
  });

  it('cria tarefa com prioridade via pílula, subtarefa via QuickAddInput e recorrência', async () => {
    const user = userEvent.setup();
    const onSalvar = vi.fn().mockResolvedValue(true);

    render(
      <TaskModal
        isOpen
        onClose={vi.fn()}
        onSalvar={onSalvar}
      />
    );

    await user.type(screen.getByLabelText(/Título da tarefa/i), 'Simulado Geral');
    await user.click(screen.getByRole('button', { name: 'Alta' }));

    const inputSubtarefa = screen.getByLabelText('Adicionar uma subtarefa');
    await user.type(inputSubtarefa, 'Corrigir gabarito{Enter}');
    expect(screen.getByText('Corrigir gabarito')).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox', { name: /Repetir tarefa/i }));
    await user.click(screen.getByRole('button', { name: 'Criar tarefa' }));

    await waitFor(() => expect(onSalvar).toHaveBeenCalledTimes(1));
    expect(onSalvar).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: 'Simulado Geral',
        prioridade: 'ALTA',
        subtarefas: [expect.objectContaining({ titulo: 'Corrigir gabarito', concluida: false })],
        recorrencia: expect.objectContaining({ frequencia: 'DIARIO' })
      })
    );
  });

  it('permite editar, alternar conclusão, renomear subtarefa inline e acionar Excluir tarefa', async () => {
    const user = userEvent.setup();
    const onSalvar = vi.fn().mockResolvedValue(true);
    const onToggleConclusao = vi.fn();
    const onExcluir = vi.fn();
    const onClose = vi.fn();

    render(
      <TaskModal
        isOpen
        onClose={onClose}
        onSalvar={onSalvar}
        tarefaParaEditar={TAREFA_BASE}
        onToggleConclusao={onToggleConclusao}
        onExcluir={onExcluir}
      />
    );

    await user.click(screen.getByRole('button', { name: 'Concluir tarefa' }));
    expect(onToggleConclusao).toHaveBeenCalledWith('tarefa-1', true);

    await user.click(screen.getByRole('button', { name: 'Editar subtarefa Ler resumo' }));
    const editInput = screen.getByLabelText('Nome da subtarefa');
    await user.clear(editInput);
    await user.type(editInput, 'Ler capítulo 2{Enter}');
    expect(screen.getByText('Ler capítulo 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(onSalvar).toHaveBeenCalledTimes(1));
    expect(onSalvar.mock.calls[0][0].subtarefas).toEqual([
      { id: 'sub-1', titulo: 'Ler capítulo 2', concluida: false },
      { id: 'sub-2', titulo: 'Fazer 15 questões', concluida: true }
    ]);

    await user.click(screen.getByRole('button', { name: 'Excluir tarefa' }));
    expect(onExcluir).toHaveBeenCalledWith(TAREFA_BASE);
  });
});
