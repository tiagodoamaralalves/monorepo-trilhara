import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyState, Button } from '../shared/design-system';
import { Plus, Trash2, Folder } from 'lucide-react';

/**
 * Estado vazio de caderno sem seções (REQUISITOS §10.1 / Anexo B).
 *
 * O caderno pode ficar com zero seções ativas quando a última é movida para a lixeira; o estado
 * orienta criar uma seção ou abrir a lixeira. A trava legada que impedia excluir a última seção
 * foi removida (coberta em `SecaoTabs`, sem condição de contagem).
 */
function CadernoVazio({ onNovaSecao, onAbrirLixeira }: {
  onNovaSecao: () => void;
  onAbrirLixeira: () => void;
}) {
  return (
    <EmptyState
      icone={<Folder size={44} />}
      titulo="Este caderno não tem seções"
      descricao="Crie uma seção para organizar as páginas ou restaure uma seção que esteja na lixeira."
      acaoPrimaria={
        <div>
          <Button variant="primary" leftIcon={<Plus size={16} />} onClick={onNovaSecao}>
            Nova seção
          </Button>
          <Button variant="secondary" leftIcon={<Trash2 size={16} />} onClick={onAbrirLixeira}>
            Abrir lixeira
          </Button>
        </div>
      }
    />
  );
}

describe('Caderno sem seções — estado vazio normativo', () => {
  it('exibe o título e a descrição do Anexo B', () => {
    render(<CadernoVazio onNovaSecao={() => undefined} onAbrirLixeira={() => undefined} />);

    expect(screen.getByText('Este caderno não tem seções')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Crie uma seção para organizar as páginas ou restaure uma seção que esteja na lixeira.'
      )
    ).toBeInTheDocument();
  });

  it('oferece exatamente as duas ações: Nova seção e Abrir lixeira', () => {
    render(<CadernoVazio onNovaSecao={() => undefined} onAbrirLixeira={() => undefined} />);

    expect(screen.getByRole('button', { name: 'Nova seção' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abrir lixeira' })).toBeInTheDocument();
    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
  });

  it('despacha cada ação ao seu handler', async () => {
    const onNovaSecao = vi.fn();
    const onAbrirLixeira = vi.fn();
    render(<CadernoVazio onNovaSecao={onNovaSecao} onAbrirLixeira={onAbrirLixeira} />);

    const { fireEvent } = await import('@testing-library/react');
    fireEvent.click(screen.getByRole('button', { name: 'Nova seção' }));
    fireEvent.click(screen.getByRole('button', { name: 'Abrir lixeira' }));

    expect(onNovaSecao).toHaveBeenCalledTimes(1);
    expect(onAbrirLixeira).toHaveBeenCalledTimes(1);
  });
});
