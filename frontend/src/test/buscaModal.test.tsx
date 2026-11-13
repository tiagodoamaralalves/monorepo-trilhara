import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { BuscaModal, DEBOUNCE_BUSCA_MS } from '../modules/notas/components/BuscaModal/BuscaModal';
import {
  SnippetDestacado,
  calcularTrechos
} from '../modules/notas/components/BuscaModal/SnippetDestacado';
import { NotaService } from '../modules/notas/services/notaService';
import { BuscaNotasResponse, ResultadoBuscaNota } from '../modules/notas/types/nota';

vi.mock('../modules/notas/services/notaService', () => ({
  NotaService: {
    buscarGlobal: vi.fn()
  }
}));

const buscarGlobal = vi.mocked(NotaService.buscarGlobal);

function resposta(itens: ResultadoBuscaNota[] = []): BuscaNotasResponse {
  return {
    itens,
    pagina: 1,
    tamanho: 20,
    totalItens: itens.length,
    totalPaginas: 1
  };
}

function digitar(texto: string) {
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: texto } });
}

describe('BuscaModal — D-30', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('não dispara request com menos de 2 caracteres alfanuméricos', async () => {
    render(<BuscaModal isOpen onClose={() => undefined} onSelecionarNota={() => undefined} />);

    digitar('a');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS * 2);
    });
    expect(buscarGlobal).not.toHaveBeenCalled();

    digitar('- -');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS * 2);
    });
    expect(buscarGlobal).not.toHaveBeenCalled();
    expect(screen.getByText(/Digite ao menos 2 letras ou números/i)).toBeInTheDocument();
  });

  it('usa debounce de 300 ms e dispara uma única busca', async () => {
    buscarGlobal.mockResolvedValue(resposta());
    render(<BuscaModal isOpen onClose={() => undefined} onSelecionarNota={() => undefined} />);

    digitar('constituicao');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS - 1);
    });
    expect(buscarGlobal).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(buscarGlobal).toHaveBeenCalledTimes(1);
    expect(buscarGlobal.mock.calls[0][0]).toBe('constituicao');
  });

  it('estado vazio usa o texto normativo com a consulta entre aspas', async () => {
    buscarGlobal.mockResolvedValue(resposta());
    render(<BuscaModal isOpen onClose={() => undefined} onSelecionarNota={() => undefined} />);

    digitar('inexistente');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByText(/Nenhuma página encontrada para “inexistente”/)).toBeInTheDocument();
    expect(screen.getByText(/A busca encontra palavras inteiras/i)).toBeInTheDocument();
  });

  it('429 mostra a mensagem de espera em vez do card de erro', async () => {
    const { ApiError } = await import('../shared/auth/api');
    buscarGlobal.mockRejectedValue(
      new ApiError({ status: 429, code: 'RATE_LIMITED', detail: 'limite' })
    );
    render(<BuscaModal isOpen onClose={() => undefined} onSelecionarNota={() => undefined} />);

    digitar('constituicao');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByText('Muitas buscas seguidas. Aguarde alguns segundos.')).toBeInTheDocument();
    expect(screen.queryByTestId('section-error-card')).not.toBeInTheDocument();
  });

  it('renderiza o resultado com <mark> por offsets em texto escapado', async () => {
    buscarGlobal.mockResolvedValue(
      resposta([
        {
          notaId: 'n1',
          titulo: 'Princípios',
          cadernoId: 'c1',
          cadernoTitulo: 'Constitucional',
          secaoId: 's1',
          secaoTitulo: 'Geral',
          atualizadoEm: '2026-01-01T00:00:00Z',
          snippet: 'a Constituição de 1988',
          destaques: [{ inicio: 2, fim: 14 }]
        }
      ])
    );
    render(<BuscaModal isOpen onClose={() => undefined} onSelecionarNota={() => undefined} />);

    digitar('constituicao');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_BUSCA_MS);
      await Promise.resolve();
      await Promise.resolve();
    });

    const marcas = screen.getAllByTestId('snippet-mark');
    expect(marcas).toHaveLength(1);
    expect(marcas[0].textContent).toBe('Constituição');
    expect(screen.getByText('Princípios')).toBeInTheDocument();
  });
});

describe('SnippetDestacado — renderização segura', () => {
  it('HTML no snippet é renderizado como TEXTO, nunca interpretado', () => {
    const payload = '<img src=x onerror=alert(1)>';
    render(<SnippetDestacado snippet={payload} destaques={[]} />);

    const container = screen.getByTestId('snippet-destacado');
    expect(container.textContent).toBe(payload);
    expect(container.querySelector('img')).toBeNull();
    expect(document.querySelector('img')).toBeNull();
  });

  it('marca apenas o intervalo indicado e descarta offsets inválidos', () => {
    render(
      <SnippetDestacado
        snippet="texto com destaque aqui"
        destaques={[
          { inicio: 10, fim: 18 },
          { inicio: -5, fim: 3 },
          { inicio: 100, fim: 200 }
        ]}
      />
    );

    const marcas = screen.getAllByTestId('snippet-mark');
    expect(marcas).toHaveLength(1);
    expect(marcas[0].textContent).toBe('destaque');
    expect(screen.getByTestId('snippet-destacado').textContent).toBe('texto com destaque aqui');
  });

  it('calculaTrechos funde sobreposições e preserva o texto completo', () => {
    const trechos = calcularTrechos('abcdef', [
      { inicio: 1, fim: 3 },
      { inicio: 2, fim: 5 }
    ]);
    expect(trechos).toEqual([
      { texto: 'a', destacado: false },
      { texto: 'bcde', destacado: true },
      { texto: 'f', destacado: false }
    ]);
  });
});
