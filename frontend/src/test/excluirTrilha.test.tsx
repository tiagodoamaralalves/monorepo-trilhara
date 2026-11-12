import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ConfirmProvider, ToastProvider } from '@shared/design-system';
import { TrilhaModal } from '../modules/estudos/components/TrilhaModal/TrilhaModal';
import { EstudosService } from '../modules/estudos/services/estudosService';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

const trilha: TrilhaDetalheDTO = {
  id: 't-1',
  nome: 'Receita Federal 2026',
  cargo: 'Auditor Fiscal',
  banca: 'FGV',
  dataProva: '2026-12-01',
  disciplinas: [],
  topicosConcluidosIds: []
};

function renderModal(onExcluirTrilha = vi.fn().mockResolvedValue(true)) {
  render(
    <ToastProvider>
      <ConfirmProvider>
        <TrilhaModal
          isOpen
          trilha={trilha}
          onClose={() => undefined}
          onTrilhaSalva={() => undefined}
          onExcluirTrilha={onExcluirTrilha}
        />
      </ConfirmProvider>
    </ToastProvider>
  );
  return onExcluirTrilha;
}

/**
 * D-38: excluir trilha é irreversível e exige o nome exato (padrão GitHub), com o impacto
 * descrito em números. O botão destrutivo só habilita com o texto correto.
 */
describe('Excluir trilha — confirmação por nome exato (D-38)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(EstudosService, 'obterImpactoExclusaoTrilha').mockResolvedValue({
      disciplinas: 4,
      cronogramas: 2,
      sessoes: 7,
      cadernosVinculados: 1
    });
  });

  it('mantém o botão destrutivo desabilitado até digitar o nome exato', async () => {
    renderModal();

    fireEvent.click(screen.getByRole('button', { name: /^Excluir$/i }));

    const confirmar = await screen.findByRole('button', { name: /Excluir trilha/i });
    expect(confirmar).toBeDisabled();

    const campo = screen.getByLabelText('Confirme o nome da trilha');
    fireEvent.change(campo, { target: { value: 'receita federal 2026' } });
    expect(confirmar).toBeDisabled();

    fireEvent.change(campo, { target: { value: 'Receita Federal 2026' } });
    await waitFor(() => expect(confirmar).toBeEnabled());
  });

  it('exibe o impacto em números antes de excluir', async () => {
    renderModal();
    fireEvent.click(screen.getByRole('button', { name: /^Excluir$/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/4 disciplina\(s\), 2 cronograma\(s\), 7 sessão\(ões\) e 1 caderno\(s\) vinculado\(s\)/)
      ).toBeInTheDocument();
    });
  });

  it('chama a exclusão apenas quando o nome confere', async () => {
    const onExcluirTrilha = renderModal();
    fireEvent.click(screen.getByRole('button', { name: /^Excluir$/i }));

    const campo = await screen.findByLabelText('Confirme o nome da trilha');
    fireEvent.change(campo, { target: { value: 'Receita Federal 2026' } });

    const confirmar = screen.getByRole('button', { name: /Excluir trilha/i });
    await waitFor(() => expect(confirmar).toBeEnabled());
    fireEvent.click(confirmar);

    await waitFor(() => expect(onExcluirTrilha).toHaveBeenCalledWith('t-1'));
  });
});
