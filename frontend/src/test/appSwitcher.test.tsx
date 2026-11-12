import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppSwitcher, APPS_TRILHARA } from '../shared/design-system/AppSwitcher/AppSwitcher';

describe('AppSwitcher - Seletor de Aplicativos Trilhara (FASE 1)', () => {
  it('deve listar os 4 módulos do ecossistema Trilhara', () => {
    expect(APPS_TRILHARA).toHaveLength(4);
    const ids = APPS_TRILHARA.map((a) => a.id);
    expect(ids).toContain('estudos');
    expect(ids).toContain('tarefas');
    expect(ids).toContain('notas');
    expect(ids).toContain('pomodoro');
  });

  it('deve exibir o módulo atual selecionado no botão gatilho', () => {
    render(<AppSwitcher moduloAtual="tarefas" />);
    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    expect(trigger).toBeInTheDocument();
    expect(trigger).toHaveTextContent('Tarefas');
  });

  it('deve abrir o menu com os 4 aplicativos ao clicar no gatilho', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="estudos" />);

    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    await user.click(trigger);

    const menu = screen.getByRole('menu', { name: /Aplicativos do Ecossistema Trilhara/i });
    expect(menu).toBeInTheDocument();

    const items = screen.getAllByRole('menuitem');
    expect(items).toHaveLength(4);

    expect(within(menu).getByText('Estudos')).toBeInTheDocument();
    expect(within(menu).getByText('Tarefas')).toBeInTheDocument();
    expect(within(menu).getByText('Notas')).toBeInTheDocument();
    expect(within(menu).getByText('Pomodoro')).toBeInTheDocument();
  });

  it('deve destacar o módulo atual com aria-current="page" e badge "Atual"', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="notas" />);

    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    await user.click(trigger);

    const notasItem = screen.getByRole('menuitem', { name: /Notas/i });
    expect(notasItem).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('Atual')).toBeInTheDocument();
  });

  it('deve disparar onTrocarApp ao selecionar um módulo diferente', async () => {
    const user = userEvent.setup();
    const handleTrocar = vi.fn();
    render(<AppSwitcher moduloAtual="estudos" onTrocarApp={handleTrocar} />);

    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    await user.click(trigger);

    const tarefasItem = screen.getByRole('menuitem', { name: /Tarefas/i });
    await user.click(tarefasItem);

    expect(handleTrocar).toHaveBeenCalledTimes(1);
    expect(handleTrocar).toHaveBeenCalledWith('tarefas', expect.stringContaining('/tarefas/'));
  });

  it('deve fechar o dropdown ao pressionar Escape', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="pomodoro" />);

    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    await user.click(trigger);
    expect(screen.getByRole('menu')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
