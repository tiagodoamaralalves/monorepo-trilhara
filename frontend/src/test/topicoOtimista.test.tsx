import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { DisciplinasTopicosModal } from '../modules/estudos/components/DisciplinasTopicosModal/DisciplinasTopicosModal';
import { EstudosService } from '../modules/estudos/services/estudosService';
import { ToastProvider } from '@shared/design-system';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import type { Disciplina, TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

const disciplina: Disciplina = {
  id: 'd-1',
  trilhaId: 't-1',
  nome: 'Direito Constitucional',
  cor: '#336699',
  peso: 3,
  ordem: 0,
  topicos: [
    { id: 'top-1', nome: 'Direitos Fundamentais', ordem: 1, concluido: false },
    { id: 'top-2', nome: 'Poder Judiciário', ordem: 2, concluido: false }
  ]
};

function trilhaCom(idsConcluidos: string[]): TrilhaDetalheDTO {
  return {
    id: 't-1',
    nome: 'Trilha',
    disciplinas: [disciplina],
    topicosConcluidosIds: idsConcluidos
  };
}

/**
 * marcar/desmarcar tópico é otimista com rollback + toast de erro. O estado do `Set`
 * muda antes da resposta e volta ao valor anterior quando a API falha.
 */
describe('Tópico — atualização otimista com rollback', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  function Harness({ falhar }: { falhar: boolean }) {
    const [trilha, setTrilha] = useState<TrilhaDetalheDTO>(trilhaCom([]));

    const alternar = async (topicoId: string, concluido: boolean) => {
      const anterior = new Set<string>(trilha.topicosConcluidosIds);
      const otimista = new Set<string>(anterior);
      if (concluido) {
        otimista.add(topicoId);
      } else {
        otimista.delete(topicoId);
      }
      setTrilha((atual) => ({ ...atual, topicosConcluidosIds: [...otimista] }));

      try {
        if (falhar) throw new Error('Falha de rede simulada');
        if (concluido) {
          await EstudosService.marcarTopicoConcluido('t-1', topicoId);
        } else {
          await EstudosService.desmarcarTopicoConcluido('t-1', topicoId);
        }
      } catch {
        setTrilha((atual) => ({ ...atual, topicosConcluidosIds: [...anterior] }));
      }
    };

    return (
      <DisciplinasTopicosModal
        isOpen
        title="Assuntos"
        disciplinas={trilha.disciplinas}
        progresso={new Set(trilha.topicosConcluidosIds)}
        onToggleTopico={(topicoId, concluido) => void alternar(topicoId, concluido)}
        onClose={() => undefined}
        editable={false}
      />
    );
  }

  it('marca o tópico imediatamente, antes da resposta da API', async () => {
    let resolver: (() => void) | undefined;
    vi.spyOn(EstudosService, 'marcarTopicoConcluido').mockImplementation(
      () => new Promise((resolve) => {
        resolver = () => resolve({ topicoId: 'top-1', concluido: true });
      })
    );

    render(
      <ToastProvider>
        <ConfirmProvider>
          <Harness falhar={false} />
        </ConfirmProvider>
      </ToastProvider>
    );

    // Abre a disciplina na lista e marca o primeiro assunto.
    fireEvent.click(screen.getByText('Direito Constitucional'));
    const caixa = screen.getAllByRole('checkbox')[0];
    fireEvent.click(caixa);

    // Reflete na hora, sem esperar a API.
    await waitFor(() => {
      expect((screen.getAllByRole('checkbox')[0] as HTMLInputElement).checked).toBe(true);
    });

    resolver?.();
  });

  it('faz rollback quando a API falha', async () => {
    render(
      <ToastProvider>
        <ConfirmProvider>
          <Harness falhar />
        </ConfirmProvider>
      </ToastProvider>
    );

    fireEvent.click(screen.getByText('Direito Constitucional'));
    const caixa = screen.getAllByRole('checkbox')[0];
    expect((caixa as HTMLInputElement).checked).toBe(false);

    fireEvent.click(caixa);

    // Volta ao estado anterior por causa da falha.
    await waitFor(() => {
      expect((screen.getAllByRole('checkbox')[0] as HTMLInputElement).checked).toBe(false);
    });
  });
});
