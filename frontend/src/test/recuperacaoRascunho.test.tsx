import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { TiptapEditor } from '../modules/notas/components/Editor/TiptapEditor';
import { NotaService } from '../modules/notas/services/notaService';

const USUARIO_ID = 'usuario-1';
const NOTA_ID = 'nota-1';
const SERVIDOR_ATUALIZADO_EM = '2026-01-01T00:00:10Z';

function gravarRascunho(rascunho: {
  titulo: string;
  conteudoMarkdown: string;
  baseAtualizadoEmServidor?: string | null;
}) {
  NotaService.salvarRascunhoLocal(USUARIO_ID, NOTA_ID, {
    ...rascunho,
    atualizadoEmLocal: new Date().toISOString()
  });
}

function renderizarEditor(onSalvar = vi.fn().mockResolvedValue({ atualizadoEm: SERVIDOR_ATUALIZADO_EM })) {
  return render(
    <TiptapEditor
      usuarioId={USUARIO_ID}
      notaId={NOTA_ID}
      titulo="Título do servidor"
      conteudoMarkdown="conteúdo do servidor"
      atualizadoEmServidor={SERVIDOR_ATUALIZADO_EM}
      onSalvar={onSalvar}
    />
  );
}

/** A barra só aparece quando o rascunho difere do servidor. */
describe('Recuperação de rascunho', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('rascunho com conteúdo diferente mostra a barra de recuperação', async () => {
    gravarRascunho({ titulo: 'Título do servidor', conteudoMarkdown: 'conteúdo NÃO salvo' });

    renderizarEditor();

    const barra = await screen.findByRole('region', { name: 'Rascunho local encontrado' });
    expect(barra).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Restaurar rascunho não salvo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Descartar rascunho' })).toBeInTheDocument();
  });

  it('rascunho idêntico ao servidor não mostra a barra e é limpo', async () => {
    gravarRascunho({ titulo: 'Título do servidor', conteudoMarkdown: 'conteúdo do servidor' });

    renderizarEditor();
    await act(async () => {
      await Promise.resolve();
    });

    expect(
      screen.queryByRole('region', { name: 'Rascunho local encontrado' })
    ).not.toBeInTheDocument();
    expect(NotaService.obterRascunhoLocal(USUARIO_ID, NOTA_ID)).toBeNull();
  });

  it('avisa quando a versão do servidor mudou desde o rascunho', async () => {
    gravarRascunho({
      titulo: 'Título do servidor',
      conteudoMarkdown: 'conteúdo NÃO salvo',
      baseAtualizadoEmServidor: '2025-12-31T00:00:00Z'
    });

    renderizarEditor();

    await screen.findByRole('region', { name: 'Rascunho local encontrado' });
    expect(screen.getByText(/A versão do servidor mudou desde este rascunho/i)).toBeInTheDocument();
  });

  it('não avisa mudança quando a base coincide com o servidor', async () => {
    gravarRascunho({
      titulo: 'Título do servidor',
      conteudoMarkdown: 'conteúdo NÃO salvo',
      baseAtualizadoEmServidor: SERVIDOR_ATUALIZADO_EM
    });

    renderizarEditor();

    await screen.findByRole('region', { name: 'Rascunho local encontrado' });
    expect(
      screen.queryByText(/A versão do servidor mudou desde este rascunho/i)
    ).not.toBeInTheDocument();
  });

  it('Descartar rascunho remove a chave local e fecha a barra', async () => {
    gravarRascunho({ titulo: 'Título do servidor', conteudoMarkdown: 'conteúdo NÃO salvo' });

    renderizarEditor();
    await screen.findByRole('region', { name: 'Rascunho local encontrado' });

    fireEvent.click(screen.getByRole('button', { name: 'Descartar rascunho' }));

    expect(
      screen.queryByRole('region', { name: 'Rascunho local encontrado' })
    ).not.toBeInTheDocument();
    expect(NotaService.obterRascunhoLocal(USUARIO_ID, NOTA_ID)).toBeNull();
  });

  it('Restaurar rascunho salva o conteúdo local no servidor', async () => {
    const onSalvar = vi.fn().mockResolvedValue({ atualizadoEm: '2026-01-01T00:00:20Z' });
    gravarRascunho({ titulo: 'Título recuperado', conteudoMarkdown: 'conteúdo recuperado' });

    renderizarEditor(onSalvar);
    await screen.findByRole('region', { name: 'Rascunho local encontrado' });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Restaurar rascunho não salvo' }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(onSalvar).toHaveBeenCalledWith(
      NOTA_ID,
      'Título recuperado',
      expect.stringContaining('conteúdo recuperado'),
      expect.any(AbortSignal)
    );
  });
});
