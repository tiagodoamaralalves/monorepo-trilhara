import { describe, it, expect, vi } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { useRecurso } from '../shared/hooks';

function Harness({ carregar }: { carregar: (signal: AbortSignal) => Promise<string[]> }) {
  const { status, dados, erro, recarregando, recarregar } = useRecurso(carregar);
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="itens">{dados ? dados.length : 'undefined'}</span>
      <span data-testid="erro">{erro ? 'sim' : 'nao'}</span>
      <span data-testid="recarregando">{recarregando ? 'sim' : 'nao'}</span>
      <button onClick={recarregar}>recarregar</button>
    </div>
  );
}

describe('R1 — useRecurso (D-37)', () => {
  it('começa em carregando e nunca assume lista vazia antes da resposta', async () => {
    const carregar = vi.fn().mockResolvedValue(['a', 'b']);
    render(<Harness carregar={carregar} />);

    expect(screen.getByTestId('status')).toHaveTextContent('carregando');
    expect(screen.getByTestId('itens')).toHaveTextContent('undefined');

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByTestId('status')).toHaveTextContent('sucesso');
    expect(screen.getByTestId('itens')).toHaveTextContent('2');
  });

  it('expõe erro sem renderizar lista vazia como sucesso', async () => {
    const carregar = vi.fn().mockRejectedValue(new Error('falha de rede'));
    render(<Harness carregar={carregar} />);

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByTestId('status')).toHaveTextContent('erro');
    expect(screen.getByTestId('erro')).toHaveTextContent('sim');
    expect(screen.getByTestId('itens')).toHaveTextContent('undefined');
  });

  it('recarregar mantém status sucesso e sinaliza recarregando', async () => {
    let resolverSegunda: ((valor: string[]) => void) | undefined;
    const carregar = vi
      .fn()
      .mockResolvedValueOnce(['a'])
      .mockImplementationOnce(() => new Promise<string[]>((resolve) => {
        resolverSegunda = resolve;
      }));

    render(<Harness carregar={carregar} />);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(screen.getByTestId('itens')).toHaveTextContent('1');

    fireEvent.click(screen.getByText('recarregar'));
    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByTestId('status')).toHaveTextContent('sucesso');
    expect(screen.getByTestId('recarregando')).toHaveTextContent('sim');
    expect(screen.getByTestId('itens')).toHaveTextContent('1');

    await act(async () => {
      resolverSegunda?.(['a', 'b', 'c']);
      await Promise.resolve();
    });
    expect(screen.getByTestId('itens')).toHaveTextContent('3');
    expect(screen.getByTestId('recarregando')).toHaveTextContent('nao');
  });
});
