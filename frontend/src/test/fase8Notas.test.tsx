import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SyncStatusBadge } from '../shared/design-system';
import { NotaService } from '../modules/notas/services/notaService';
import { TiptapEditor } from '../modules/notas/components/Editor/TiptapEditor';
import * as apiModule from '../shared/auth/api';

// Mock do módulo de API request central
vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return {
    ...actual,
    request: vi.fn()
  };
});

describe('FASE 8 — Reestruturação de Cadernos, Seções e Notas', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe('1. SyncStatusBadge (Design System)', () => {
    it('renderiza corretamente os estados com role="status" e aria-live="polite"', () => {
      const { rerender } = render(<SyncStatusBadge status="salvo" />);
      const badge = screen.getByRole('status');
      expect(badge).toBeInTheDocument();
      expect(badge).toHaveAttribute('aria-live', 'polite');
      expect(screen.getByText('Salvo')).toBeInTheDocument();

      rerender(<SyncStatusBadge status="salvando" />);
      expect(screen.getByText('Salvando...')).toBeInTheDocument();

      rerender(<SyncStatusBadge status="alteracoes-locais" />);
      expect(screen.getByText(/Alterações locais pendentes/i)).toBeInTheDocument();

      rerender(<SyncStatusBadge status="erro" />);
      expect(screen.getByText('Erro ao salvar')).toBeInTheDocument();
    });

    it('renderiza botão de retry quando em estado de erro e dispara onRetry', () => {
      const handleRetry = vi.fn();
      render(<SyncStatusBadge status="erro" onRetry={handleRetry} />);

      const retryBtn = screen.getByRole('button', { name: /tentar novamente/i });
      expect(retryBtn).toBeInTheDocument();

      fireEvent.click(retryBtn);
      expect(handleRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe('2. NotaService — Rascunho Local em Contingência (localStorage)', () => {
    const usuarioId = 'usr-123';
    const notaId = 'nota-456';
    const chaveEsperada = `trilhara:u:${usuarioId}:nota_draft:${notaId}`;

    it('salva rascunho local sob chave canônica com timestamp ISO', () => {
      const isoAgora = new Date().toISOString();
      NotaService.salvarRascunhoLocal(usuarioId, notaId, {
        titulo: 'Meu Título Rascunho',
        conteudoMarkdown: '# Conteúdo Markdown Rascunho',
        atualizadoEmLocal: isoAgora
      });

      const raw = localStorage.getItem(chaveEsperada);
      expect(raw).not.toBeNull();
      const parsed = JSON.parse(raw!);
      expect(parsed.titulo).toBe('Meu Título Rascunho');
      expect(parsed.conteudoMarkdown).toBe('# Conteúdo Markdown Rascunho');
      expect(parsed.atualizadoEmLocal).toBe(isoAgora);
    });

    it('obtém rascunho local salvo', () => {
      NotaService.salvarRascunhoLocal(usuarioId, notaId, {
        titulo: 'Título Salvo',
        conteudoMarkdown: 'Conteúdo',
        atualizadoEmLocal: new Date().toISOString()
      });
      const rascunho = NotaService.obterRascunhoLocal(usuarioId, notaId);
      expect(rascunho).not.toBeNull();
      expect(rascunho?.titulo).toBe('Título Salvo');
      expect(rascunho?.conteudoMarkdown).toBe('Conteúdo');
    });

    it('remove rascunho local especificado', () => {
      NotaService.salvarRascunhoLocal(usuarioId, notaId, {
        titulo: 'Título Para Remover',
        conteudoMarkdown: 'Conteúdo',
        atualizadoEmLocal: new Date().toISOString()
      });
      expect(NotaService.obterRascunhoLocal(usuarioId, notaId)).not.toBeNull();

      NotaService.removerRascunhoLocal(usuarioId, notaId);
      expect(NotaService.obterRascunhoLocal(usuarioId, notaId)).toBeNull();
    });

    it('lista todos os rascunhos locais de um usuário', () => {
      NotaService.salvarRascunhoLocal(usuarioId, 'nota-1', {
        titulo: 'N1',
        conteudoMarkdown: 'C1',
        atualizadoEmLocal: new Date().toISOString()
      });
      NotaService.salvarRascunhoLocal(usuarioId, 'nota-2', {
        titulo: 'N2',
        conteudoMarkdown: 'C2',
        atualizadoEmLocal: new Date().toISOString()
      });
      NotaService.salvarRascunhoLocal('outro-user', 'nota-3', {
        titulo: 'N3',
        conteudoMarkdown: 'C3',
        atualizadoEmLocal: new Date().toISOString()
      });

      const rascunhos = NotaService.listarRascunhosLocais(usuarioId);
      expect(rascunhos).toHaveLength(2);
      expect(rascunhos.map((r) => r.notaId).sort()).toEqual(['nota-1', 'nota-2']);
    });
  });

  describe('3. NotaService — Chamadas de API e Contratos BSON', () => {
    const cadernoId = 'cad-1';
    const secaoId = 'sec-1';
    const notaId = 'not-1';

    it('listarNotasPorSecao solicita apenas metadados (sem markdown no payload de listagem)', async () => {
      const mockMetadados = [
        { id: 'not-1', cadernoId, secaoId, titulo: 'Nota 1', ordem: 0, criadoEm: '2026-11-09T00:00:00Z', atualizadoEm: '2026-11-09T00:00:00Z' },
        { id: 'not-2', cadernoId, secaoId, titulo: 'Nota 2', ordem: 1, criadoEm: '2026-11-09T00:00:00Z', atualizadoEm: '2026-11-09T00:00:00Z' }
      ];
      vi.mocked(apiModule.request).mockResolvedValueOnce(mockMetadados);

      const resultado = await NotaService.listarNotasPorSecao(cadernoId, secaoId);
      expect(apiModule.request).toHaveBeenCalledWith(
        `/cadernos/${cadernoId}/secoes/${secaoId}/notas?pagina=1&tamanho=50`
      );
      expect(resultado).toHaveLength(2);
      // Confirma que o DTO de listagem projeta metadados
      expect((resultado[0] as any).conteudoMarkdown).toBeUndefined();
    });

    it('buscarNota busca conteúdo completo sob demanda', async () => {
      const mockNotaCompleta = {
        id: notaId,
        cadernoId,
        secaoId,
        titulo: 'Nota 1',
        conteudoMarkdown: '# Conteúdo Integral da Nota',
        criadoEm: '2026-11-09T00:00:00Z',
        atualizadoEm: '2026-11-09T00:00:00Z'
      };
      vi.mocked(apiModule.request).mockResolvedValueOnce(mockNotaCompleta);

      const nota = await NotaService.buscarNota(notaId);
      expect(apiModule.request).toHaveBeenCalledWith(`/notas/${notaId}`);
      expect(nota.conteudoMarkdown).toBe('# Conteúdo Integral da Nota');
    });

    it('buscarGlobal codifica busca textual em português com paginação', async () => {
      vi.mocked(apiModule.request).mockResolvedValueOnce({
        itens: [
          {
            notaId: 'not-1',
            titulo: 'Direito Constitucional',
            cadernoId: 'cad-1',
            cadernoTitulo: 'Caderno 1',
            secaoId: 'sec-1',
            secaoTitulo: 'Seção 1',
            atualizadoEm: '2026-11-09T00:00:00Z',
            snippet: '...estudo sobre <b>Constitucional</b>...'
          }
        ],
        total: 1,
        pagina: 1,
        tamanho: 20
      });

      const res = await NotaService.buscarGlobal('constitucional');
      expect(apiModule.request).toHaveBeenCalledWith(
        expect.stringContaining('/notas/busca?q=constitucional&pagina=1&tamanho=20')
      );
      expect(res.itens).toHaveLength(1);
      expect(res.itens[0].snippet).toContain('<b>Constitucional</b>');
    });

    it('importarTrilha e sincronizarTrilha acionam endpoints correspondentes', async () => {
      vi.mocked(apiModule.request).mockResolvedValueOnce({ id: 'cad-novo', titulo: 'Trilha OAB' });
      const impRes = await NotaService.importarTrilha('trilha-oab');
      expect(apiModule.request).toHaveBeenCalledWith(
        '/cadernos/importar-trilha/trilha-oab',
        expect.objectContaining({ method: 'POST' })
      );
      expect(impRes.id).toBe('cad-novo');

      vi.mocked(apiModule.request).mockResolvedValueOnce({
        cadernoId: 'cad-novo',
        secoesCriadas: 2,
        notasCriadas: 4,
        itensExistentesPreservados: 10
      });
      const sincRes = await NotaService.sincronizarTrilha('cad-novo');
      expect(apiModule.request).toHaveBeenCalledWith(
        '/cadernos/cad-novo/sincronizar-trilha',
        expect.objectContaining({ method: 'POST' })
      );
      expect(sincRes.secoesCriadas).toBe(2);
    });

    it('gerencia lixeira: listar, restaurar e esvaziar', async () => {
      vi.mocked(apiModule.request).mockResolvedValueOnce([
        { id: 'not-trash', tipo: 'NOTA', titulo: 'Nota Antiga', excluidoEm: '2026-11-08T00:00:00Z', expiraEm: '2026-12-08T00:00:00Z', loteExclusaoId: 'lote-1' }
      ]);
      const lixeira = await NotaService.listarLixeira();
      expect(lixeira).toHaveLength(1);

      vi.mocked(apiModule.request).mockResolvedValueOnce({
        mensagem: 'Item restaurado com sucesso'
      });
      const rest = await NotaService.restaurarItemLixeira('NOTA', 'not-trash');
      expect(rest.mensagem).toContain('restaurado');

      vi.mocked(apiModule.request).mockResolvedValueOnce({
        cadernosRemovidos: 1,
        secoesRemovidas: 2,
        notasRemovidas: 5
      });
      const purga = await NotaService.esvaziarLixeira();
      expect(purga.notasRemovidas).toBe(5);
    });
  });

  describe('4. TiptapEditor — Banner de Recuperação de Rascunho', () => {
    it('exibe o banner de recuperação quando existe rascunho local mais recente que o servidor', async () => {
      const usuarioId = 'usr-test';
      const notaId = 'nota-draft-test';
      const serverDate = '2026-11-09T10:00:00.000Z';
      const localDate = '2026-11-09T12:00:00.000Z';

      // Salva rascunho local com data mais recente
      localStorage.setItem(
        `trilhara:u:${usuarioId}:nota_draft:${notaId}`,
        JSON.stringify({
          usuarioId,
          notaId,
          titulo: 'Título Local Não Salvo',
          conteudoMarkdown: 'Texto escrito offline ou antes do crash',
          atualizadoEmLocal: localDate
        })
      );

      const mockSalvar = vi.fn();
      render(
        <TiptapEditor
          usuarioId={usuarioId}
          notaId={notaId}
          titulo="Título Original Servidor"
          conteudoMarkdown="Texto Servidor"
          atualizadoEmServidor={serverDate}
          onSalvar={mockSalvar}
        />
      );

      // Deve exibir o aviso de recuperação
      expect(screen.getByText(/Existe um rascunho local não salvo mais recente do que a versão do servidor/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Restaurar rascunho não salvo/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Descartar rascunho/i })).toBeInTheDocument();
    });

    it('ao clicar em Descartar rascunho, o banner fecha e o item é removido do localStorage', async () => {
      const usuarioId = 'usr-test';
      const notaId = 'nota-discard-test';
      const serverDate = '2026-11-09T10:00:00.000Z';
      const localDate = '2026-11-09T12:00:00.000Z';

      localStorage.setItem(
        `trilhara:u:${usuarioId}:nota_draft:${notaId}`,
        JSON.stringify({
          usuarioId,
          notaId,
          titulo: 'Título Desc',
          conteudoMarkdown: 'Conteúdo Desc',
          atualizadoEmLocal: localDate
        })
      );

      render(
        <TiptapEditor
          usuarioId={usuarioId}
          notaId={notaId}
          titulo="Título Servidor"
          conteudoMarkdown="Conteúdo Servidor"
          atualizadoEmServidor={serverDate}
          onSalvar={vi.fn()}
        />
      );

      const descartarBtn = screen.getByRole('button', { name: /Descartar rascunho/i });
      fireEvent.click(descartarBtn);

      await waitFor(() => {
        expect(screen.queryByText(/Existe um rascunho local não salvo mais recente/i)).not.toBeInTheDocument();
      });

      expect(localStorage.getItem(`trilhara:u:${usuarioId}:nota_draft:${notaId}`)).toBeNull();
    });
  });
});
