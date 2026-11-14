import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ContaModal } from '../shared/auth/ContaModal';
import { ApiError } from '../shared/auth/api';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import { ToastProvider } from '../shared/design-system/Toast/Toast';

const USUARIO = {
  id: '6af21234567890abcdef1234',
  nome: 'Carlos Drummond',
  email: 'carlos@trilhara.com.br',
  papeis: ['USER'] as ('USER' | 'ADMIN')[],
  preferencias: {
    tema: 'dark' as const,
    disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
    padraoLembreteMinutosAntes: 15
  },
  emailVerificado: true,
  ativo: true,
  criadoEm: '2026-11-09T00:00:00Z'
};

function montar(onExcluirConta = vi.fn().mockResolvedValue(undefined), onClose = vi.fn()) {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <ContaModal
          isOpen
          onClose={onClose}
          usuario={USUARIO}
          onExcluirConta={onExcluirConta}
        />
      </ConfirmProvider>
    </ToastProvider>
  );
}

/** Percorre ConfirmDialog (danger) até o formulário de senha + frase. */
async function abrirFormulario(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /Excluir minha conta/i }));
  await user.click(await screen.findByRole('button', { name: /Prosseguir para exclusão/i }));
  await screen.findByLabelText('Senha atual');
}

describe('ContaModal: confirmação dupla, erros inline e envio único', () => {
  it('mostra os dados da conta e a Zona de Perigo', () => {
    montar();
    expect(screen.getByText('Carlos Drummond')).toBeInTheDocument();
    expect(screen.getByText('carlos@trilhara.com.br')).toBeInTheDocument();
    expect(screen.getByText('Zona de Perigo')).toBeInTheDocument();
  });

  it('exige o ConfirmDialog destrutivo antes de pedir a senha', async () => {
    const user = userEvent.setup();
    montar();

    expect(screen.queryByLabelText('Senha atual')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Excluir minha conta/i }));

    expect(await screen.findByText(/irreversível/i)).toBeInTheDocument();
    expect(screen.queryByLabelText('Senha atual')).not.toBeInTheDocument();
  });

  it('mantém o botão desabilitado sem a senha', async () => {
    const user = userEvent.setup();
    montar();
    await abrirFormulario(user);

    const confirmar = screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i });
    expect(confirmar).toBeDisabled();
  });

  it('mantém o botão desabilitado com a frase divergente, mesmo com senha', async () => {
    const user = userEvent.setup();
    montar();
    await abrirFormulario(user);

    await user.type(screen.getByLabelText('Senha atual'), 'Senh@Forte2026!');
    await user.type(screen.getByLabelText('Confirmação textual'), 'excluir minha conta');

    expect(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i })).toBeDisabled();
  });

  it('habilita o botão apenas com senha e a frase exata', async () => {
    const user = userEvent.setup();
    montar();
    await abrirFormulario(user);

    await user.type(screen.getByLabelText('Senha atual'), 'Senh@Forte2026!');
    await user.type(screen.getByLabelText('Confirmação textual'), 'EXCLUIR MINHA CONTA');

    expect(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i })).toBeEnabled();
  });

  it('chama onExcluirConta uma única vez com a senha informada', async () => {
    const user = userEvent.setup();
    const onExcluirConta = vi.fn().mockResolvedValue(undefined);
    montar(onExcluirConta);
    await abrirFormulario(user);

    await user.type(screen.getByLabelText('Senha atual'), 'Senh@Forte2026!');
    await user.type(screen.getByLabelText('Confirmação textual'), 'EXCLUIR MINHA CONTA');
    await user.click(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i }));

    await waitFor(() => expect(onExcluirConta).toHaveBeenCalledTimes(1));
    expect(onExcluirConta).toHaveBeenCalledWith('Senh@Forte2026!');
  });

  it('exibe "Senha atual incorreta." inline quando o servidor responde 401', async () => {
    const user = userEvent.setup();
    const onExcluirConta = vi.fn().mockRejectedValue(
      new ApiError({ status: 401, code: 'UNAUTHORIZED', detail: 'Senha atual incorreta.' })
    );
    montar(onExcluirConta);
    await abrirFormulario(user);

    await user.type(screen.getByLabelText('Senha atual'), 'senha-errada');
    await user.type(screen.getByLabelText('Confirmação textual'), 'EXCLUIR MINHA CONTA');
    await user.click(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i }));

    expect(await screen.findByText('Senha atual incorreta.')).toBeInTheDocument();
    // O modal permanece aberto para o usuário tentar de novo.
    expect(screen.getByLabelText('Senha atual')).toBeInTheDocument();
  });

  it('exibe o tempo de espera quando o servidor responde 429 com Retry-After', async () => {
    const user = userEvent.setup();
    const onExcluirConta = vi.fn().mockRejectedValue(
      new ApiError({
        status: 429,
        code: 'RATE_LIMITED',
        detail: 'Muitas tentativas.',
        retryAfterSeconds: 42
      })
    );
    montar(onExcluirConta);
    await abrirFormulario(user);

    await user.type(screen.getByLabelText('Senha atual'), 'Senh@Forte2026!');
    await user.type(screen.getByLabelText('Confirmação textual'), 'EXCLUIR MINHA CONTA');
    await user.click(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i }));

    expect(
      await screen.findByText(/Muitas tentativas\. Tente novamente em 42 segundos\./i)
    ).toBeInTheDocument();
  });
});
