import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import {
  CHAVE_OCULTAR_MODAL_IMPORTACAO,
  ImportarTrilhaModal,
  TrilhaItem
} from '../modules/notas/components/ImportarTrilhaModal/ImportarTrilhaModal';
import { Caderno } from '../modules/notas/types/nota';

const USUARIO_ID = 'usuario-1';

function caderno(overrides: Partial<Caderno> = {}): Caderno {
  return {
    id: 'c1',
    titulo: 'Caderno',
    cor: '#0284c7',
    icone: 'book-open',
    origemTrilhaId: null,
    trilhaNome: null,
    totalSecoes: 0,
    totalPaginas: 0,
    criadoEm: '2026-01-01T00:00:00Z',
    atualizadoEm: '2026-01-01T00:00:00Z',
    ...overrides
  };
}

const trilhas: TrilhaItem[] = [
  { id: 't1', nome: 'Trilha A' },
  { id: 't2', nome: 'Trilha B' }
];

describe('Sugestão automática de importação — preferência local', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('a chave de preferência segue o namespace por usuário exigido', () => {
    expect(CHAVE_OCULTAR_MODAL_IMPORTACAO(USUARIO_ID)).toBe(
      'trilhara:u:usuario-1:notas_ocultar_modal_importacao'
    );
  });

  it('sem a chave gravada a sugestão pode abrir (chave ≠ "true")', () => {
    expect(localStorage.getItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(USUARIO_ID))).toBeNull();
    expect(localStorage.getItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(USUARIO_ID)) !== 'true').toBe(true);
  });

  it('marcar "Não mostrar" grava "true" na chave do usuário', async () => {
    const { fireEvent } = await import('@testing-library/react');
    render(
      <ImportarTrilhaModal
        isOpen
        onClose={() => undefined}
        trilhas={trilhas}
        cadernos={[]}
        usuarioId={USUARIO_ID}
        onImportar={vi.fn()}
      />
    );

    fireEvent.click(
      screen.getByLabelText(/Não mostrar esta sugestão automaticamente/i)
    );

    expect(localStorage.getItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(USUARIO_ID))).toBe('true');
  });
});

describe('ImportarTrilhaModal — reimportação confirmada', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('trilha não vinculada importa direto com reimportar=false', async () => {
    const onImportar = vi.fn().mockResolvedValue(undefined);
    const { fireEvent } = await import('@testing-library/react');

    render(
      <ImportarTrilhaModal
        isOpen
        onClose={() => undefined}
        trilhas={trilhas}
        cadernos={[]}
        usuarioId={USUARIO_ID}
        onImportar={onImportar}
      />
    );

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Importar trilha' }));
      await Promise.resolve();
    });

    expect(onImportar).toHaveBeenCalledWith('t1', false);
  });

  it('trilha já vinculada pede confirmação e só então envia reimportar=true', async () => {
    const onImportar = vi.fn().mockResolvedValue(undefined);
    const { fireEvent } = await import('@testing-library/react');

    render(
      <ImportarTrilhaModal
        isOpen
        onClose={() => undefined}
        trilhas={trilhas}
        cadernos={[caderno({ origemTrilhaId: 't1', trilhaNome: 'Trilha A' })]}
        usuarioId={USUARIO_ID}
        onImportar={onImportar}
      />
    );

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Importar trilha' }));
      await Promise.resolve();
    });

    // Ainda não importou: primeiro mostra a confirmação com o texto normativo.
    expect(onImportar).not.toHaveBeenCalled();
    expect(screen.getByText(/O caderno atual será mantido e desvinculado/i)).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Importar e desvincular o atual' }));
      await Promise.resolve();
    });

    expect(onImportar).toHaveBeenCalledWith('t1', true);
  });

  it('rótulo da trilha já vinculada avisa no seletor', async () => {
    const { fireEvent } = await import('@testing-library/react');
    render(
      <ImportarTrilhaModal
        isOpen
        onClose={() => undefined}
        trilhas={trilhas}
        cadernos={[caderno({ origemTrilhaId: 't2', trilhaNome: 'Trilha B' })]}
        usuarioId={USUARIO_ID}
        onImportar={vi.fn()}
      />
    );

    // O Select do design system é um listbox: as opções só existem com a lista aberta.
    fireEvent.click(screen.getByRole('combobox'));
    expect(screen.getByRole('option', { name: /Trilha B \(já vinculada\)/ })).toBeInTheDocument();
  });
});
