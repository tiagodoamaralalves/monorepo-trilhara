import React, { useState } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppSwitcher, APPS_TRILHARA } from '../shared/design-system/AppSwitcher/AppSwitcher';
import { unsavedChangesRegistry } from '../shared/state/unsavedChangesRegistry';
import { useUnsavedChanges } from '../shared/hooks/useUnsavedChanges';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';

describe('AppSwitcher (R2/D-12) — APG Menu Button', () => {
  it('deve listar os 4 módulos do ecossistema Trilhara', () => {
    expect(APPS_TRILHARA).toHaveLength(4);
    expect(APPS_TRILHARA.map((a) => a.id)).toEqual(
      expect.arrayContaining(['estudos', 'tarefas', 'notas', 'pomodoro'])
    );
  });

  it('deve exibir o módulo atual no gatilho com aria-haspopup e aria-expanded', () => {
    render(<AppSwitcher moduloAtual="tarefas" />);
    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    expect(trigger).toHaveTextContent('Tarefas');
    expect(trigger).toHaveAttribute('aria-haspopup', 'menu');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('deve abrir com Enter e mover o foco para o item do módulo atual', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="notas" />);

    await user.tab();
    await user.keyboard('{Enter}');

    const menu = screen.getByRole('menu', { name: /Aplicativos do Ecossistema Trilhara/i });
    expect(menu).toBeInTheDocument();
    expect(within(menu).getAllByRole('menuitem')).toHaveLength(4);

    const notasItem = screen.getByRole('menuitem', { name: /Notas/i });
    expect(notasItem).toHaveFocus();
    expect(notasItem).toHaveAttribute('aria-current', 'page');
  });

  it('deve navegar com setas circulando, Home/End e Tab', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="estudos" />);

    await user.tab();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: /Estudos/i })).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: /Tarefas/i })).toHaveFocus();

    await user.keyboard('{End}');
    expect(screen.getByRole('menuitem', { name: /Pomodoro/i })).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: /Estudos/i })).toHaveFocus();

    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menuitem', { name: /Pomodoro/i })).toHaveFocus();

    await user.keyboard('{Home}');
    expect(screen.getByRole('menuitem', { name: /Estudos/i })).toHaveFocus();

    await user.keyboard('{Tab}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('deve fechar com Escape devolvendo o foco ao gatilho', async () => {
    const user = userEvent.setup();
    render(<AppSwitcher moduloAtual="pomodoro" />);

    const trigger = screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i });
    await user.click(trigger);
    expect(screen.getByRole('menu')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('deve disparar onTrocarApp ao selecionar um módulo diferente', async () => {
    const user = userEvent.setup();
    const handleTrocar = vi.fn();
    render(<AppSwitcher moduloAtual="estudos" onTrocarApp={handleTrocar} />);

    await user.click(screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i }));
    await user.click(screen.getByRole('menuitem', { name: /Tarefas/i }));

    expect(handleTrocar).toHaveBeenCalledTimes(1);
    expect(handleTrocar).toHaveBeenCalledWith('tarefas', expect.stringContaining('/tarefas/'));
  });
});

const FormularioSujo: React.FC = () => {
  const [sujo] = useState(true);
  useUnsavedChanges('formulario-teste', sujo, () => undefined);
  return <div>formulário com edição pendente</div>;
};

const FormularioComFlush: React.FC<{ flush: () => void }> = ({ flush }) => {
  useUnsavedChanges('formulario-flush', true, flush);
  return <div>formulário com flush</div>;
};

describe('AppSwitcher (R2/D-41) — guarda de alterações não salvas', () => {
  beforeEach(() => {
    unsavedChangesRegistry.__resetarParaTeste();
  });

  afterEach(() => {
    unsavedChangesRegistry.__resetarParaTeste();
  });

  it('com alterações registradas, pede confirmação e "Ficar" não navega', async () => {
    const user = userEvent.setup();
    const handleTrocar = vi.fn();

    render(
      <ConfirmProvider>
        <FormularioSujo />
        <AppSwitcher moduloAtual="estudos" onTrocarApp={handleTrocar} />
      </ConfirmProvider>
    );

    expect(unsavedChangesRegistry.temAlteracoes()).toBe(true);

    await user.click(screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i }));
    await user.click(screen.getByRole('menuitem', { name: /Tarefas/i }));

    expect(await screen.findByText(/Você tem alterações não salvas/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ficar' }));

    expect(handleTrocar).not.toHaveBeenCalled();
    expect(unsavedChangesRegistry.temAlteracoes()).toBe(true);
  });

  it('com alterações registradas, "Sair mesmo assim" executa o flush e navega', async () => {
    const user = userEvent.setup();
    const handleTrocar = vi.fn();
    const flush = vi.fn();

    render(
      <ConfirmProvider>
        <FormularioComFlush flush={flush} />
        <AppSwitcher moduloAtual="estudos" onTrocarApp={handleTrocar} />
      </ConfirmProvider>
    );

    await user.click(screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i }));
    await user.click(screen.getByRole('menuitem', { name: /Notas/i }));

    await user.click(await screen.findByRole('button', { name: 'Sair mesmo assim' }));

    expect(flush).toHaveBeenCalledTimes(1);
    expect(handleTrocar).toHaveBeenCalledWith('notas', expect.stringContaining('/notas/'));
    expect(unsavedChangesRegistry.temAlteracoes()).toBe(false);
  });

  it('sem alterações registradas navega diretamente, sem diálogo', async () => {
    const user = userEvent.setup();
    const handleTrocar = vi.fn();

    render(
      <ConfirmProvider>
        <AppSwitcher moduloAtual="estudos" onTrocarApp={handleTrocar} />
      </ConfirmProvider>
    );

    await user.click(screen.getByRole('button', { name: /Alternar aplicativo Trilhara/i }));
    await user.click(screen.getByRole('menuitem', { name: /Pomodoro/i }));

    expect(screen.queryByText(/Você tem alterações não salvas/i)).not.toBeInTheDocument();
    expect(handleTrocar).toHaveBeenCalledWith('pomodoro', expect.stringContaining('/pomodoro/'));
  });

  it('registro ativa beforeunload apenas enquanto há item sujo', () => {
    unsavedChangesRegistry.registrar({ id: 'x' });
    const evento = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(evento);
    expect(evento.defaultPrevented).toBe(true);

    unsavedChangesRegistry.remover('x');
    const eventoLimpo = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(eventoLimpo);
    expect(eventoLimpo.defaultPrevented).toBe(false);
  });
});
