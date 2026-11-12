import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { Skeleton } from '../shared/design-system/Skeleton/Skeleton';
import { Spinner } from '../shared/design-system/Spinner/Spinner';
import { EmptyState } from '../shared/design-system/EmptyState/EmptyState';
import { FieldError } from '../shared/design-system/FieldError/FieldError';
import { SectionErrorCard } from '../shared/design-system/SectionErrorCard/SectionErrorCard';
import { useDelayedFlag } from '../shared/hooks/useDelayedFlag';

describe('R1 — Skeleton (D-37)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('não aparece antes de 200 ms', () => {
    render(<Skeleton variant="row" linhas={3} />);
    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(150);
    });
    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });

  it('aparece depois de 200 ms com aria-busy e texto oculto "Carregando…"', () => {
    render(<Skeleton variant="row" linhas={3} />);

    act(() => {
      vi.advanceTimersByTime(210);
    });

    const skeleton = screen.getByTestId('skeleton');
    expect(skeleton).toBeInTheDocument();
    expect(skeleton).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByText('Carregando…')).toBeInTheDocument();
  });

  it('permanece visível no mínimo 300 ms mesmo quando o carregamento termina', () => {
    const { rerender } = render(<Skeleton variant="line" carregando />);
    act(() => {
      vi.advanceTimersByTime(210);
    });
    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    rerender(<Skeleton variant="line" carregando={false} />);
    act(() => {
      vi.advanceTimersByTime(150);
    });
    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(200);
    });
    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });
});

describe('R1 — useDelayedFlag', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function Harness({ ativo }: { ativo: boolean }) {
    const visivel = useDelayedFlag(ativo);
    return <span data-testid="flag">{visivel ? 'visivel' : 'oculto'}</span>;
  }

  it('respeita o atraso configurado', () => {
    render(<Harness ativo />);
    expect(screen.getByTestId('flag')).toHaveTextContent('oculto');
    act(() => {
      vi.advanceTimersByTime(210);
    });
    expect(screen.getByTestId('flag')).toHaveTextContent('visivel');
  });
});

describe('R1 — Spinner e EmptyState', () => {
  it('Spinner expõe role=status e rótulo acessível', () => {
    render(<Spinner />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('Carregando…')).toBeInTheDocument();
  });

  it('EmptyState usa os textos do contexto', () => {
    render(<EmptyState icone={<svg />} titulo="Tudo em dia por hoje" descricao="Nenhuma tarefa pendente." />);
    expect(screen.getByRole('heading', { name: 'Tudo em dia por hoje' })).toBeInTheDocument();
    expect(screen.getByText('Nenhuma tarefa pendente.')).toBeInTheDocument();
  });

  it('FieldError liga a mensagem ao input por aria-describedby', () => {
    render(
      <div>
        <input id="campo" aria-describedby="campo-erro" aria-invalid="true" />
        <FieldError id="campo-erro" mensagem="Campo obrigatório." />
      </div>
    );
    expect(screen.getByText('Campo obrigatório.')).toHaveAttribute('id', 'campo-erro');
    expect(screen.getByRole('alert')).toHaveTextContent('Campo obrigatório.');
  });

  it('FieldError não renderiza nada sem mensagem', () => {
    const { container } = render(<FieldError mensagem={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('R1 — SectionErrorCard', () => {
  it('chama onRetry e liga o botão à descrição', () => {
    const onRetry = vi.fn();
    render(
      <SectionErrorCard
        titulo="Não foi possível carregar"
        descricao="Verifique sua conexão e tente novamente."
        onRetry={onRetry}
      />
    );

    const botao = screen.getByRole('button', { name: 'Tentar novamente' });
    const descricao = screen.getByText('Verifique sua conexão e tente novamente.');
    expect(botao.getAttribute('aria-describedby')).toBe(descricao.id);

    botao.click();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('suporta variant inline', () => {
    render(<SectionErrorCard titulo="Falha" descricao="Blocos indisponíveis." variant="inline" />);
    expect(screen.getByTestId('section-error-card')).toHaveAttribute('data-variant', 'inline');
  });

  it('não renderiza botão sem onRetry', () => {
    render(<SectionErrorCard titulo="Falha" descricao="Sem retry." />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
