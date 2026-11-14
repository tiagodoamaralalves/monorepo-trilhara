import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Table } from '@tiptap/extension-table';
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
import { SyncStatusBadge } from '@shared/design-system';
import { NotaService } from '../../services/notaService';
import { RascunhoNotaLocal } from '../../types/nota';
import { useAutosaveNota } from '../../hooks/useAutosaveNota';
import { LinkSeguro } from './linkSeguro';
import styles from './TiptapEditor.module.css';

export interface TiptapEditorProps {
  usuarioId: string;
  notaId: string;
  titulo: string;
  conteudoMarkdown: string;
  atualizadoEmServidor?: string;
  /** Deve devolver o `atualizadoEm` novo para virar a base do rascunho. */
  onSalvar: (
    notaId: string,
    novoTitulo: string,
    novoMarkdown: string,
    signal?: AbortSignal
  ) => Promise<{ atualizadoEm: string } | void>;
  onUpdateTitulo?: (novoTitulo: string) => void;
  onExcluirNota?: () => void;
}

function lerMarkdown(editor: { storage: unknown } | null): string {
  const storage = editor?.storage as { markdown?: { getMarkdown: () => string } } | undefined;
  return storage?.markdown?.getMarkdown() ?? '';
}

/**
 * Editor de página com autosave e recuperação de rascunho.
 *
 * Markdown com `html: false` (HTML bruto desabilitado) e colagem pelo schema do ProseMirror;
 * links passam pelo mark `LinkSeguro` (allowlist `http:`/`https:`/`mailto:`, `rel` e `target`);
 * autosave com debounce de 800 ms, `seq` por página e `AbortController` no hook
 * `useAutosaveNota`;
 * barra de recuperação aparece quando existe rascunho local com **conteúdo diferente** do
 * servidor (nunca por comparação de relógios), avisando quando a versão do servidor mudou.
 */
export const TiptapEditor: React.FC<TiptapEditorProps> = ({
  usuarioId,
  notaId,
  titulo,
  conteudoMarkdown,
  atualizadoEmServidor,
  onSalvar,
  onUpdateTitulo,
  onExcluirNota
}) => {
  const [rascunhoPendente, setRascunhoPendente] = useState<RascunhoNotaLocal | null>(null);
  const [servidorMudouDesdeRascunho, setServidorMudouDesdeRascunho] = useState(false);
  const editorRef = useRef<ReturnType<typeof useEditor> | null>(null);

  const salvarNoServidor = useCallback(
    async (
      id: string,
      novoTitulo: string,
      novoMarkdown: string,
      signal: AbortSignal
    ): Promise<{ atualizadoEm: string } | void> => onSalvar(id, novoTitulo, novoMarkdown, signal),
    [onSalvar]
  );

  const autosave = useAutosaveNota({
    usuarioId,
    notaId,
    tituloInicial: titulo,
    conteudoInicial: conteudoMarkdown,
    atualizadoEmServidor: atualizadoEmServidor ?? null,
    salvar: salvarNoServidor
  });

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] }
      }),
      Table.configure({ resizable: true }),
      TableRow,
      TableHeader,
      TableCell,
      Placeholder.configure({
        placeholder: 'Comece a escrever suas anotações em Markdown puro...'
      }),
      LinkSeguro,
      Markdown.configure({
        html: false,
        transformPastedText: true,
        transformCopiedText: true
      })
    ],
    content: conteudoMarkdown || '',
    onUpdate: ({ editor: instancia }) => {
      autosave.alterarConteudo(lerMarkdown(instancia as unknown as { storage: unknown }));
    }
  });

  useEffect(() => {
    editorRef.current = editor;
  }, [editor]);

  // Ao trocar de página, recarrega o conteúdo do servidor no editor.
  const notaIdAnteriorRef = useRef(notaId);
  useEffect(() => {
    if (notaIdAnteriorRef.current === notaId) return;
    notaIdAnteriorRef.current = notaId;
    if (editor) {
      editor.commands.setContent(conteudoMarkdown || '');
    }
  }, [conteudoMarkdown, editor, notaId]);

  // Recuperação de rascunho: compara CONTEÚDO, não relógios.
  useEffect(() => {
    if (!usuarioId || !notaId) {
      setRascunhoPendente(null);
      setServidorMudouDesdeRascunho(false);
      return;
    }

    const rascunho = NotaService.obterRascunhoLocal(usuarioId, notaId);
    if (!rascunho) {
      setRascunhoPendente(null);
      setServidorMudouDesdeRascunho(false);
      return;
    }

    const conteudoDiferente =
      rascunho.conteudoMarkdown !== conteudoMarkdown || rascunho.titulo !== titulo;

    if (!conteudoDiferente) {
      // Rascunho idêntico ao servidor: já está salvo, não há o que recuperar.
      NotaService.removerRascunhoLocal(usuarioId, notaId);
      setRascunhoPendente(null);
      setServidorMudouDesdeRascunho(false);
      return;
    }

    const base = rascunho.baseAtualizadoEmServidor ?? null;
    setServidorMudouDesdeRascunho(
      Boolean(base) && Boolean(atualizadoEmServidor) && base !== atualizadoEmServidor
    );
    setRascunhoPendente(rascunho);
  }, [atualizadoEmServidor, conteudoMarkdown, notaId, titulo, usuarioId]);

  const handleTituloChange = (evento: React.ChangeEvent<HTMLInputElement>) => {
    const novo = evento.target.value;
    autosave.alterarTitulo(novo);
    onUpdateTitulo?.(novo);
  };

  const handleRestaurarRascunho = () => {
    if (!rascunhoPendente) return;
    if (editor) {
      editor.commands.setContent(rascunhoPendente.conteudoMarkdown);
    }
    // `aplicarRascunho` recebe título e conteúdo prontos e envia sem debounce: evita o estado
    // defasado entre duas chamadas de `alterar*`.
    autosave.aplicarRascunho(rascunhoPendente.titulo, rascunhoPendente.conteudoMarkdown);
    onUpdateTitulo?.(rascunhoPendente.titulo);
    setRascunhoPendente(null);
    setServidorMudouDesdeRascunho(false);
  };

  const handleDescartarRascunho = () => {
    NotaService.removerRascunhoLocal(usuarioId, notaId);
    setRascunhoPendente(null);
    setServidorMudouDesdeRascunho(false);
  };

  const handleInserirTabela = useCallback(() => {
    if (!editor) return;
    editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
  }, [editor]);

  const contagemPalavras =
    (editor?.storage as { characterCount?: { words?: () => number } })?.characterCount?.words?.() ??
    editor?.getText().trim().split(/\s+/).filter(Boolean).length ??
    0;
  const contagemCaracteres = editor?.getText().length ?? 0;

  return (
    <div className={styles.editorWrapper}>
      {rascunhoPendente && (
        <div className={styles.recoveryBanner} role="region" aria-label="Rascunho local encontrado">
          <div className={styles.recoveryBannerContent}>
            <AlertTriangle size={16} aria-hidden="true" />
            <span>
              Existe um rascunho local com alterações diferentes da versão salva.
              {servidorMudouDesdeRascunho && (
                <>
                  {' '}
                  <strong>A versão do servidor mudou desde este rascunho.</strong>
                </>
              )}
            </span>
          </div>
          <div className={styles.recoveryActions}>
            <button type="button" className={styles.restoreBtn} onClick={handleRestaurarRascunho}>
              Restaurar rascunho não salvo
            </button>
            <button type="button" className={styles.discardBtn} onClick={handleDescartarRascunho}>
              Descartar rascunho
            </button>
          </div>
        </div>
      )}

      <header className={styles.editorHeader}>
        <div className={styles.headerLeft}>
          <input
            className={styles.titleInput}
            value={autosave.titulo}
            onChange={handleTituloChange}
            placeholder="Título da página"
            aria-label="Título da página"
          />
        </div>

        <div className={styles.toolbar} role="toolbar" aria-label="Formatação do texto">
          <button
            type="button"
            className={`${styles.toolBtn} ${editor?.isActive('bold') ? styles.toolBtnActive : ''}`}
            onClick={() => editor?.chain().focus().toggleBold().run()}
            title="Negrito"
            aria-label="Negrito"
          >
            <Bold size={15} />
          </button>
          <button
            type="button"
            className={`${styles.toolBtn} ${editor?.isActive('italic') ? styles.toolBtnActive : ''}`}
            onClick={() => editor?.chain().focus().toggleItalic().run()}
            title="Itálico"
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

        <SyncStatusBadge status={autosave.status} onRetry={autosave.tentarNovamente} />

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
      </header>

      <div className={styles.editorScrollArea}>
        <div className={styles.editorCanvas}>
          <EditorContent editor={editor} />
        </div>
      </div>

      <footer className={styles.editorFooter}>
        <span>
          {contagemPalavras} {contagemPalavras === 1 ? 'palavra' : 'palavras'}
        </span>
        <span>{contagemCaracteres} caracteres</span>
      </footer>
    </div>
  );
};
