import React, { useState, useMemo, useRef, useCallback } from 'react';
import { FileText, Plus, Search, Trash2, X } from 'lucide-react';
import { Badge, Button, Input } from '@shared/design-system';
import { Nota } from '../../types/nota';
import styles from './ListaPaginas.module.css';

export interface ListaPaginasProps {
  secaoNome?: string;
  secaoCor?: string;
  notas: Nota[];
  notaAtivaId?: string;
  onSelecionarNota: (id: string) => void;
  onNovaPagina: () => void;
  onExcluirNota: (nota: Nota) => void;
}

function formatarDataOneNote(dataIso?: string): string {
  if (!dataIso) return '';
  const d = new Date(dataIso);
  if (isNaN(d.getTime())) return '';

  const agora = new Date();
  const hoje = new Date(agora.getFullYear(), agora.getMonth(), agora.getDate());
  const ontem = new Date(hoje);
  ontem.setDate(ontem.getDate() - 1);

  const dataNota = new Date(d.getFullYear(), d.getMonth(), d.getDate());

  const horas = String(d.getHours()).padStart(2, '0');
  const minutos = String(d.getMinutes()).padStart(2, '0');
  const horaFormatada = `${horas}:${minutos}`;

  if (dataNota.getTime() === hoje.getTime()) {
    return `Hoje, ${horaFormatada}`;
  }
  if (dataNota.getTime() === ontem.getTime()) {
    return `Ontem, ${horaFormatada}`;
  }

  const dia = String(d.getDate()).padStart(2, '0');
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const ano = String(d.getFullYear()).slice(-2);
  return `${dia}/${mes}/${ano}, ${horaFormatada}`;
}

function extrairSnippetMarkdown(markdown?: string): string {
  if (!markdown || !markdown.trim()) {
    return '';
  }

  return markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[.*?\]\(.*?\)/g, '')
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^[\s*>-]+\s+/gm, '')
    .replace(/[*_~]/g, '')
    .replace(/\|/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export const ListaPaginas: React.FC<ListaPaginasProps> = ({
  secaoNome = 'Seção',
  secaoCor,
  notas,
  notaAtivaId,
  onSelecionarNota,
  onNovaPagina,
  onExcluirNota
}) => {
  const [termoBusca, setTermoBusca] = useState('');
  const itemRefs = useRef<Map<string, HTMLButtonElement>>(new Map());

  const setItemRef = useCallback((id: string, el: HTMLButtonElement | null) => {
    if (el) {
      itemRefs.current.set(id, el);
    } else {
      itemRefs.current.delete(id);
    }
  }, []);

  const notasFiltradas = useMemo(() => {
    if (!termoBusca.trim()) return notas;
    const termo = termoBusca.toLowerCase().trim();
    return notas.filter(
      (n) =>
        n.titulo.toLowerCase().includes(termo) ||
        (n.conteudoMarkdown && n.conteudoMarkdown.toLowerCase().includes(termo))
    );
  }, [notas, termoBusca]);

  const handleKeyDown = (e: React.KeyboardEvent, indexAtual: number) => {
    if (notasFiltradas.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const proximoIndex = Math.min(indexAtual + 1, notasFiltradas.length - 1);
      const proximaNota = notasFiltradas[proximoIndex];
      onSelecionarNota(proximaNota.id);
      itemRefs.current.get(proximaNota.id)?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const anteriorIndex = Math.max(indexAtual - 1, 0);
      const anteriorNota = notasFiltradas[anteriorIndex];
      onSelecionarNota(anteriorNota.id);
      itemRefs.current.get(anteriorNota.id)?.focus();
    } else if (e.key === 'Home') {
      e.preventDefault();
      const primeiraNota = notasFiltradas[0];
      onSelecionarNota(primeiraNota.id);
      itemRefs.current.get(primeiraNota.id)?.focus();
    } else if (e.key === 'End') {
      e.preventDefault();
      const ultimaNota = notasFiltradas[notasFiltradas.length - 1];
      onSelecionarNota(ultimaNota.id);
      itemRefs.current.get(ultimaNota.id)?.focus();
    }
  };

  return (
    <aside className={styles.container} aria-label="Painel de páginas da seção">
      {/* ── HEADER DO PAINEL DE PÁGINAS ── */}
      <div className={styles.header}>
        <div className={styles.topRow}>
          <div className={styles.sectionInfo}>
            {secaoCor && (
              <span
                className={styles.sectionDot}
                style={{ backgroundColor: secaoCor }}
                aria-hidden="true"
              />
            )}
            <h2 className={styles.sectionTitle} title={secaoNome}>
              {secaoNome}
            </h2>
          </div>
          <Badge variant="neutral" size="sm">
            {notas.length}
          </Badge>
        </div>

        <div className={styles.newButtonRow}>
          <Button
            variant="primary"
            size="sm"
            leftIcon={<Plus size={14} />}
            fullWidth
            onClick={onNovaPagina}
          >
            Nova Página
          </Button>
        </div>

        <div className={styles.searchRow}>
          <Input
            placeholder="Filtrar páginas..."
            leftIcon={<Search size={14} />}
            rightIcon={
              termoBusca ? (
                <button
                  type="button"
                  onClick={() => setTermoBusca('')}
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: 'var(--text-soft)',
                    padding: 0,
                    display: 'flex'
                  }}
                  title="Limpar busca"
                  aria-label="Limpar busca"
                >
                  <X size={13} />
                </button>
              ) : undefined
            }
            value={termoBusca}
            onChange={(e) => setTermoBusca(e.target.value)}
          />
        </div>
      </div>

      {/* ── LISTA DE CARDS DE PÁGINA (ESTILO ONENOTE) ── */}
      {notasFiltradas.length === 0 ? (
        <div className={styles.emptyState}>
          {termoBusca ? (
            <>
              <Search size={28} className={styles.emptyIcon} />
              <div className={styles.emptyTitle}>Nenhuma página encontrada</div>
              <div className={styles.emptyDesc}>
                Não encontramos páginas com o termo &quot;{termoBusca}&quot;.
              </div>
              <div className={styles.emptyAction}>
                <Button variant="ghost" size="sm" onClick={() => setTermoBusca('')}>
                  Limpar busca
                </Button>
              </div>
            </>
          ) : (
            <>
              <FileText size={28} className={styles.emptyIcon} />
              <div className={styles.emptyTitle}>Nenhuma página nesta seção</div>
              <div className={styles.emptyDesc}>
                Crie a primeira página para começar suas anotações nesta seção.
              </div>
              <div className={styles.emptyAction}>
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<Plus size={14} />}
                  onClick={onNovaPagina}
                >
                  Criar Página
                </Button>
              </div>
            </>
          )}
        </div>
      ) : (
        <div
          role="listbox"
          aria-label={`Páginas de ${secaoNome}`}
          className={styles.list}
        >
          {notasFiltradas.map((nota, idx) => {
            const isAtiva = nota.id === notaAtivaId;
            const snippet = extrairSnippetMarkdown(nota.conteudoMarkdown);
            const dataFormatada = formatarDataOneNote(nota.atualizadoEm || nota.criadoEm);

            return (
              <button
                key={nota.id}
                ref={(el) => setItemRef(nota.id, el)}
                type="button"
                role="option"
                aria-selected={isAtiva}
                tabIndex={0}
                className={`${styles.item} ${isAtiva ? styles.itemActive : ''}`}
                style={isAtiva && secaoCor ? { borderLeftColor: secaoCor } : undefined}
                onClick={() => onSelecionarNota(nota.id)}
                onKeyDown={(e) => handleKeyDown(e, idx)}
              >
                <div className={styles.itemHeader}>
                  <span className={styles.itemTitle} title={nota.titulo}>
                    {nota.titulo || 'Sem título'}
                  </span>
                  <button
                    type="button"
                    className={styles.deleteBtn}
                    title="Excluir página"
                    aria-label={`Excluir página ${nota.titulo}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      onExcluirNota(nota);
                    }}
                  >
                    <Trash2 size={13} />
                  </button>
                </div>

                {dataFormatada && (
                  <span className={styles.itemDate}>{dataFormatada}</span>
                )}

                {snippet ? (
                  <span className={styles.itemSnippet}>{snippet}</span>
                ) : (
                  <span className={styles.itemSnippetEmpty}>Página em branco</span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </aside>
  );
};
