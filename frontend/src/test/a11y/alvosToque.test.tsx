import { readFileSync } from 'fs';
import { resolve } from 'path';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Button } from '../../shared/design-system/Button/Button';
import { IconButton } from '../../shared/design-system/IconButton/IconButton';
import { Checkbox } from '../../shared/design-system/Checkbox/Checkbox';

/**
 * Alvos mínimos de toque/clique (WCAG 2.2 SC 2.5.8) — R12.
 *
 * A regra global sobre `input`/`a` genéricos foi substituída por **tokens**
 * (`--target-min`, 24px → 40px em `pointer: coarse`) aplicados nos componentes
 * do Design System. Um teste que apenas procura `min-width: 24px` no CSS não
 * prova isso — por isso verificamos o token declarado, a ausência do seletor
 * global e a presença do alvo nos componentes reais.
 */

const css = readFileSync(resolve(__dirname, '../../shared/styles/index.css'), 'utf-8');

describe('Design System — alvos mínimos de toque (SC 2.5.8)', () => {
  it('declara os tokens de alvo e a troca para pointer: coarse', () => {
    expect(css).toContain('--target-min: 24px');
    expect(css).toContain('--target-min-touch: 40px');
    expect(css).toContain('@media (pointer: coarse)');
    expect(css).toMatch(/@media \(pointer: coarse\) \{[\s\S]*--target-min: var\(--target-min-touch\)/);
  });

  it('não aplica mais min-width/min-height globais a button/a/input/select/textarea genéricos', () => {
    // O bloco removido era um seletor múltiplo contendo `button` e `a` com min-width: 24px.
    expect(css).not.toMatch(/button,\s*\n\s*a,\s*\n\s*input,\s*\n\s*select,\s*\n\s*textarea/);
    expect(css).not.toContain('min-width: 24px');
    expect(css).not.toContain('min-height: 24px');
  });

  it('Button e IconButton usam o token de alvo mínimo', () => {
    const botaoCss = readFileSync(
      resolve(__dirname, '../../shared/design-system/Button/Button.module.css'),
      'utf-8'
    );
    const iconeCss = readFileSync(
      resolve(__dirname, '../../shared/design-system/IconButton/IconButton.module.css'),
      'utf-8'
    );
    expect(botaoCss).toContain('var(--target-min)');
    expect(iconeCss).toContain('var(--target-min)');
  });

  it('IconButton expõe nome acessível obrigatório via aria-label', () => {
    render(<IconButton aria-label="Excluir tarefa" icon={<svg />} />);
    const botao = screen.getByRole('button', { name: 'Excluir tarefa' });
    expect(botao).toBeInTheDocument();
    expect(botao).toHaveAttribute('type', 'button');
  });

  it('IconButton em estado de carregamento fica desabilitado com aria-busy', () => {
    render(<IconButton aria-label="Salvando" icon={<svg />} loading />);
    const botao = screen.getByRole('button', { name: 'Salvando' });
    expect(botao).toBeDisabled();
    expect(botao).toHaveAttribute('aria-busy', 'true');
  });

  it('Checkbox mantém a caixa visual em 20px e aplica o alvo no wrapper', () => {
    const checkboxCss = readFileSync(
      resolve(__dirname, '../../shared/design-system/Checkbox/Checkbox.module.css'),
      'utf-8'
    );
    // A caixa visual continua 20px (não distorce o layout).
    expect(checkboxCss).toMatch(/\.box \{[^}]*width: 20px;[^}]*height: 20px;/);
    // O alvo mínimo vive no wrapper.
    expect(checkboxCss).toMatch(/\.checkboxWrapper \{[^}]*var\(--target-min\)/);
  });

  it('renderiza o Checkbox com label associado (nome acessível)', () => {
    render(<Checkbox label="Concluir tarefa" readOnly />);
    expect(screen.getByRole('checkbox', { name: 'Concluir tarefa' })).toBeInTheDocument();
  });

  it('Button continua clicável e nomeado', () => {
    render(<Button>Salvar</Button>);
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeEnabled();
  });
});
