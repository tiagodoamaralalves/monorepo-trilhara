import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Table from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableCell from '@tiptap/extension-table-cell';
import TableHeader from '@tiptap/extension-table-header';
import Placeholder from '@tiptap/extension-placeholder';
import { Markdown } from 'tiptap-markdown';
import {
  Bold,
  Italic,
  List,
  ListOrdered,
  Table as TableIcon,
  Heading1,
  Heading2,
  Code,
  Quote,
  Trash2,
  AlertTriangle
} from 'lucide-react';
import { SyncStatusBadge, SyncStatus } from '@shared/design-system';
import { NotaService } from '../../services/notaService';
import { RascunhoNotaLocal } from '../../types/nota';
import styles from './TiptapEditor.module.css';

export interface TiptapEditorProps {
  usuarioId: string;
  notaId: string;
  titulo: string;
  conteudoMarkdown: string;
  atualizadoEmServidor?: string;
  statusSalvamento?: SyncStatus;
  onSalvar: (notaId: string, novoTitulo: string, novoMarkdown: string) => Promise<void> | void;
  onUpdateTitulo?: (novoTitulo: string) => void;
  onExcluirNota?: () => void;
}

export const TiptapEditor: React.FC<TiptapEditorProps> = ({
  usuarioId,
  notaId,
  titulo,
  conteudoMarkdown,
  atualizadoEmServidor,
  statusSalvamento: statusSalvamentoProp,
  onSalvar,
  onUpdateTitulo,
  onExcluirNota
}) => {
  const [tituloLocal, setTituloLocal] = useState(titulo);
  const [statusInterno, setStatusInterno] = useState<SyncStatus>(statusSalvamentoProp || 'salvo');
  const [rascunhoPendente, setRascunhoPendente] = useState<RascunhoNotaLocal | null>(null);

  const statusAtual = statusSalvamentoProp || statusInterno;

  const notaIdAtualRef = useRef(notaId);
  const tituloRef = useRef(titulo);
  const onSalvarRef = useRef(onSalvar);
  const ultimoMarkdownPendenteRef = useRef(conteudoMarkdown || '');
  const temAlteracaoPendenteRef = useRef(false);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    onSalvarRef.current = onSalvar;
  }, [onSalvar]);

  useEffect(() => {
    notaIdAtualRef.current = notaId;
  }, [notaId]);

  // Executa salvamento assíncrono com gestão de status e contingência em localStorage
  const executarSalvamento = useCallback(async (idAlvo: string, tit: string, md: string) => {
    if (!idAlvo) return;
    setStatusInterno('salvando');

    // Antes de confirmar, salva rascunho de contingência em localStorage
    const rascunho: RascunhoNotaLocal = {
      titulo: tit,
      conteudoMarkdown: md,
      atualizadoEmLocal: new Date().toISOString(),
      baseAtualizadoEmServidor: atualizadoEmServidor
    };
    NotaService.salvarRascunhoLocal(usuarioId, idAlvo, rascunho);

    try {
      await onSalvarRef.current(idAlvo, tit, md);
      // Se salvamento teve sucesso e ainda estamos na mesma nota, limpa o rascunho local
      if (notaIdAtualRef.current === idAlvo) {
        NotaService.removerRascunhoLocal(usuarioId, idAlvo);
        setStatusInterno('salvo');
        setRascunhoPendente(null);
      }
    } catch {
      if (notaIdAtualRef.current === idAlvo) {
        setStatusInterno('erro');
      }
    }
  }, [usuarioId, atualizadoEmServidor]);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: {
          levels: [1, 2, 3]
        }
      }),
      Table.configure({
        resizable: true
      }),
      TableRow,
      TableHeader,
      TableCell,
      Placeholder.configure({
        placeholder: 'Comece a escrever suas anotações em Markdown puro...'
      }),
      Markdown.configure({
        html: false,
        transformPastedText: true,
        transformCopiedText: true
      })
    ],
    content: conteudoMarkdown || '',
    onUpdate: ({ editor }) => {
      const markdown = (editor.storage as { markdown?: { getMarkdown: () => string } }).markdown?.getMarkdown() ?? '';
      ultimoMarkdownPendenteRef.current = markdown;
      temAlteracaoPendenteRef.current = true;
      setStatusInterno('alteracoes-locais');

      // Grava rascunho imediatamente em localStorage
      NotaService.salvarRascunhoLocal(usuarioId, notaIdAtualRef.current, {
        titulo: tituloRef.current,
        conteudoMarkdown: markdown,
        atualizadoEmLocal: new Date().toISOString(),
        baseAtualizadoEmServidor: atualizadoEmServidor
      });

      // Debounce do salvamento automático (800ms)
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      debounceTimerRef.current = setTimeout(() => {
        temAlteracaoPendenteRef.current = false;
        executarSalvamento(notaIdAtualRef.current, tituloRef.current, markdown);
      }, 800);
    }
  });

  // Verifica existência de rascunho local não salvo mais recente ao abrir a página
  useEffect(() => {
    if (!usuarioId || !notaId) return;

    const rascunho = NotaService.obterRascunhoLocal(usuarioId, notaId);
    if (rascunho && rascunho.atualizadoEmLocal) {
      const tempoLocal = new Date(rascunho.atualizadoEmLocal).getTime();
      const tempoServidor = atualizadoEmServidor ? new Date(atualizadoEmServidor).getTime() : 0;

      // Se rascunho local for mais recente que o servidor, exibe barra de recuperação
      if (tempoLocal > tempoServidor && (rascunho.conteudoMarkdown !== conteudoMarkdown || rascunho.titulo !== titulo)) {
        setRascunhoPendente(rascunho);
      } else {
        setRascunhoPendente(null);
      }
    } else {
      setRascunhoPendente(null);
    }
  }, [usuarioId, notaId, atualizadoEmServidor, conteudoMarkdown, titulo]);

  // Atualiza conteúdo e título quando muda a nota selecionada
  useEffect(() => {
    setTituloLocal(titulo);
    tituloRef.current = titulo;

    if (editor && notaIdAtualRef.current !== notaId) {
      if (temAlteracaoPendenteRef.current) {
        if (debounceTimerRef.current) {
          clearTimeout(debounceTimerRef.current);
        }
        temAlteracaoPendenteRef.current = false;
        executarSalvamento(notaIdAtualRef.current, tituloRef.current, ultimoMarkdownPendenteRef.current);
      }
      notaIdAtualRef.current = notaId;
      ultimoMarkdownPendenteRef.current = conteudoMarkdown || '';
      editor.commands.setContent(conteudoMarkdown || '');
      setStatusInterno('salvo');
    }
  }, [notaId, titulo, conteudoMarkdown, editor, executarSalvamento]);

  // Flush no cleanup de desmontagem
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
      if (temAlteracaoPendenteRef.current) {
        temAlteracaoPendenteRef.current = false;
        executarSalvamento(notaIdAtualRef.current, tituloRef.current, ultimoMarkdownPendenteRef.current);
      }
    };
  }, [executarSalvamento]);

  const handleTituloChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const novo = e.target.value;
    setTituloLocal(novo);
    tituloRef.current = novo;
    if (onUpdateTitulo) {
      onUpdateTitulo(novo);
    }

    temAlteracaoPendenteRef.current = true;
    setStatusInterno('alteracoes-locais');

    const markdown = (editor?.storage as { markdown?: { getMarkdown: () => string } })?.markdown?.getMarkdown() ?? ultimoMarkdownPendenteRef.current;
    NotaService.salvarRascunhoLocal(usuarioId, notaIdAtualRef.current, {
      titulo: novo,
      conteudoMarkdown: markdown,
      atualizadoEmLocal: new Date().toISOString(),
      baseAtualizadoEmServidor: atualizadoEmServidor
    });

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      temAlteracaoPendenteRef.current = false;
      executarSalvamento(notaIdAtualRef.current, novo, markdown);
    }, 800);
  };

  const handleRestaurarRascunho = () => {
    if (!rascunhoPendente) return;
    setTituloLocal(rascunhoPendente.titulo);
    tituloRef.current = rascunhoPendente.titulo;
    if (onUpdateTitulo) {
      onUpdateTitulo(rascunhoPendente.titulo);
    }
    if (editor) {
      editor.commands.setContent(rascunhoPendente.conteudoMarkdown);
    }
    ultimoMarkdownPendenteRef.current = rascunhoPendente.conteudoMarkdown;
    temAlteracaoPendenteRef.current = true;
    setStatusInterno('alteracoes-locais');
    setRascunhoPendente(null);

    // Salva imediatamente no servidor
    executarSalvamento(notaId, rascunhoPendente.titulo, rascunhoPendente.conteudoMarkdown);
  };

  const handleDescartarRascunho = () => {
    NotaService.removerRascunhoLocal(usuarioId, notaId);
    setRascunhoPendente(null);
  };

  const handleRetry = () => {
    const markdown = (editor?.storage as { markdown?: { getMarkdown: () => string } })?.markdown?.getMarkdown() ?? ultimoMarkdownPendenteRef.current;
    executarSalvamento(notaId, tituloRef.current, markdown);
  };

  const handleInserirTabela = useCallback(() => {
    if (!editor) return;
    editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
  }, [editor]);

  const contagemPalavras = editor?.storage.characterCount?.words?.() ?? (editor?.getText().trim().split(/\s+/).filter(Boolean).length || 0);
  const contagemCaracteres = editor?.getText().length || 0;

  return (
    <div className={styles.editorWrapper}>
      {/* ── BARRA DE RECUPERAÇÃO DE RASCUNHO NÃO SALVO ── */}
      {rascunhoPendente && (
        <div className={styles.recoveryBanner} role="alert">
          <div className={styles.recoveryBannerContent}>
            <AlertTriangle size={16} aria-hidden="true" />
            <span>Existe um rascunho local não salvo mais recente do que a versão do servidor.</span>
          </div>
          <div className={styles.recoveryActions}>
            <button
              type="button"
              className={styles.restoreBtn}
              onClick={handleRestaurarRascunho}
            >
              Restaurar rascunho não salvo
            </button>
            <button
              type="button"
              className={styles.discardBtn}
              onClick={handleDescartarRascunho}
            >
              Descartar rascunho
            </button>
          </div>
        </div>
      )}

      {/* Topo: Título da Nota, Toolbar Minimalista e Indicador SyncStatusBadge */}
      <header className={styles.editorHeader}>
        <div className={styles.headerLeft}>
          <input
            type="text"
            className={styles.titleInput}
            value={tituloLocal}
            onChange={handleTituloChange}
            placeholder="Título da nota..."
          />
        </div>

        <div className={styles.headerRight}>
          {/* Toolbar Minimalista e Discreta */}
          <div className={styles.minimalToolbar}>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('bold') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleBold().run()}
              title="Negrito (**)"
              aria-label="Negrito"
            >
              <Bold size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('italic') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleItalic().run()}
              title="Itálico (*)"
              aria-label="Itálico"
            >
              <Italic size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('heading', { level: 1 }) ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleHeading({ level: 1 }).run()}
              title="Título 1 (#)"
              aria-label="Título 1"
            >
              <Heading1 size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('heading', { level: 2 }) ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}
              title="Título 2 (##)"
              aria-label="Título 2"
            >
              <Heading2 size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('bulletList') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleBulletList().run()}
              title="Lista com marcadores (-)"
              aria-label="Lista com marcadores"
            >
              <List size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('orderedList') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleOrderedList().run()}
              title="Lista numerada (1.)"
              aria-label="Lista numerada"
            >
              <ListOrdered size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('blockquote') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleBlockquote().run()}
              title="Citação (>)"
              aria-label="Citação"
            >
              <Quote size={15} />
            </button>
            <button
              type="button"
              className={`${styles.toolBtn} ${editor?.isActive('codeBlock') ? styles.toolBtnActive : ''}`}
              onClick={() => editor?.chain().focus().toggleCodeBlock().run()}
              title="Bloco de código (```)"
              aria-label="Bloco de código"
            >
              <Code size={15} />
            </button>
            <button
              type="button"
              className={styles.toolBtn}
              onClick={handleInserirTabela}
              title="Inserir Tabela"
              aria-label="Inserir Tabela"
            >
              <TableIcon size={15} />
            </button>
          </div>

          {/* Componente SyncStatusBadge do Design System */}
          <SyncStatusBadge
            status={statusAtual}
            onRetry={handleRetry}
          />

          {onExcluirNota && (
            <button
              type="button"
              className={styles.toolBtn}
              onClick={onExcluirNota}
              title="Excluir esta nota"
              aria-label="Excluir nota"
            >
              <Trash2 size={15} />
            </button>
          )}
        </div>
      </header>

      {/* Área Central: Folha em Branco Minimalista */}
      <div className={styles.editorScrollArea}>
        <div className={styles.editorCanvas}>
          <EditorContent editor={editor} />
        </div>
      </div>

      {/* Rodapé: Contagem e atalhos */}
      <footer className={styles.editorFooter}>
        <span>{contagemPalavras} {contagemPalavras === 1 ? 'palavra' : 'palavras'}</span>
        <span>{contagemCaracteres} caracteres</span>
      </footer>
    </div>
  );
};
