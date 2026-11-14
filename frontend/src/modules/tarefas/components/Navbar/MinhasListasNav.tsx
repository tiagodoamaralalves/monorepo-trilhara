import React from 'react';
import { Plus, Folder, BookOpen, MoreVertical, Check } from 'lucide-react';
import { useSidebar, DisciplineLabel } from '@shared/design-system';
import { ListaTarefas } from '../../types/tarefa';
import styles from './MinhasListasNav.module.css';

export interface TrilhaComCronogramaInfo {
  id: string;
  nome: string;
  cor?: string;
  cronogramaAtivoId: string;
}

export interface MinhasListasNavProps {
  listas: ListaTarefas[];
  listaSelecionadaId?: string;
  onSelecionarLista: (id?: string) => void;
  onAbrirNovaLista: () => void;
  onAbrirEditarLista?: (lista: ListaTarefas) => void;
  onExcluirLista?: (id: string) => void;
  contadoresPorLista?: Record<string, number>;
  trilhasComCronograma?: TrilhaComCronogramaInfo[];
  trilhaSelecionadaId?: string;
  onSelecionarTrilha?: (id?: string) => void;
}

export const MinhasListasNav: React.FC<MinhasListasNavProps> = ({
  listas,
  listaSelecionadaId,
  onSelecionarLista,
  onAbrirNovaLista,
  onAbrirEditarLista,
  contadoresPorLista = {},
  trilhasComCronograma = [],
  trilhaSelecionadaId,
  onSelecionarTrilha
}) => {
  const { collapsed } = useSidebar();

  // listas virtuais de cronograma não são listas persistidas de tarefas.
  const listasManuais = listas.filter((l) => l.origem !== 'cronograma');

  if (collapsed) {
    return (
      <div className={styles.listasNavCollapsed}>
        <div className={styles.headerCollapsed}>
          <button
            type="button"
            className={styles.collapsedAddBtn}
            onClick={onAbrirNovaLista}
            title="Nova lista"
            aria-label="Nova lista"
          >
            <Plus size={16} />
          </button>
        </div>

        {/* Todas as listas */}
        <button
          type="button"
          className={`${styles.collapsedItemBtn} ${!listaSelecionadaId ? styles.collapsedItemAtivo : ''}`}
          onClick={() => onSelecionarLista(undefined)}
          title="Todas as listas"
          aria-label="Todas as listas"
        >
          <span className={styles.listaDot} style={{ backgroundColor: 'var(--text-soft)' }} />
        </button>

        {/* Listas Manuais */}
        {listasManuais.map((l) => (
          <button
            key={l.id}
            type="button"
            className={`${styles.collapsedItemBtn} ${listaSelecionadaId === l.id && !trilhaSelecionadaId ? styles.collapsedItemAtivo : ''}`}
            onClick={() => {
              if (onSelecionarTrilha) onSelecionarTrilha(undefined);
              onSelecionarLista(l.id);
            }}
            title={`${l.titulo} (${contadoresPorLista[l.id] ?? 0})`}
            aria-label={l.titulo}
          >
            <span
              className={styles.listaDot}
              style={{ backgroundColor: l.cor || 'var(--primary)' }}
            />
          </button>
        ))}

        {/* Trilhas de Estudo com Cronograma Ativo */}
        {trilhasComCronograma.map((t) => (
          <button
            key={`trilha_${t.id}`}
            type="button"
            className={`${styles.collapsedItemBtn} ${trilhaSelecionadaId === t.id ? styles.collapsedItemAtivo : ''}`}
            onClick={() => {
              onSelecionarLista(undefined);
              if (onSelecionarTrilha) onSelecionarTrilha(t.id);
            }}
            title={`Trilha: ${t.nome}`}
            aria-label={`Trilha: ${t.nome}`}
          >
            <span
              className={styles.listaDot}
              style={{ backgroundColor: t.cor || 'var(--purple, #a855f7)' }}
            />
          </button>
        ))}
      </div>
    );
  }

  return (
    <div className={styles.listasNav}>
      {/* ── CABEÇALHO DE MINHAS LISTAS COM O BOTÃO [+] ── */}
      <div className={styles.listasHeader}>
        <span className={styles.listasLabel}>
          <Folder size={12} />
          <span>Minhas Listas</span>
        </span>
        <button
          type="button"
          className={styles.novaListaBtn}
          onClick={onAbrirNovaLista}
          title="Criar nova lista"
          aria-label="Criar nova lista"
        >
          <Plus size={14} />
        </button>
      </div>

      <div className={styles.listasItems}>
        {/* Item "Todas as listas" */}
        <div className={`${styles.listaItemRow} ${!listaSelecionadaId && !trilhaSelecionadaId ? styles.listaItemAtiva : ''}`}>
          <button
            type="button"
            className={styles.listaItemContent}
            onClick={() => {
              if (onSelecionarTrilha) onSelecionarTrilha(undefined);
              onSelecionarLista(undefined);
            }}
          >
            <DisciplineLabel
              name="Todas as listas"
              color="var(--text-soft)"
              className={styles.listaDisciplineLabel}
            />
            {!listaSelecionadaId && !trilhaSelecionadaId && <Check size={13} className={styles.listaCheck} />}
          </button>
        </div>

        {/* Listas Manuais Criadas pelo Usuário */}
        {listasManuais.map((lista) => {
          const isAtiva = listaSelecionadaId === lista.id && !trilhaSelecionadaId;
          const count = contadoresPorLista[lista.id];

          return (
            <div
              key={lista.id}
              className={`${styles.listaItemRow} ${isAtiva ? styles.listaItemAtiva : ''}`}
            >
              <button
                type="button"
                className={styles.listaItemContent}
                onClick={() => {
                  if (onSelecionarTrilha) onSelecionarTrilha(undefined);
                  onSelecionarLista(lista.id);
                }}
              >
                <DisciplineLabel
                  name={lista.titulo || lista.nome}
                  color={lista.cor}
                  className={styles.listaDisciplineLabel}
                />
                {count !== undefined && count > 0 && (
                  <span className={styles.listaBadge}>{count}</span>
                )}
              </button>

              {onAbrirEditarLista && (
                <button
                  type="button"
                  className={styles.listaOpcoesBtn}
                  onClick={(e) => {
                    e.stopPropagation();
                    onAbrirEditarLista(lista);
                  }}
                  title="Opções da lista"
                  aria-label="Opções da lista"
                >
                  <MoreVertical size={13} />
                </button>
              )}
            </div>
          );
        })}

        {/* ── SEÇÃO TRILHAS DE ESTUDO ── */}
        {trilhasComCronograma.length > 0 && (
          <div style={{ marginTop: '16px' }}>
            <div className={styles.listasHeader} style={{ padding: '4px 10px 4px' }}>
              <span className={styles.listasLabel}>
                <BookOpen size={12} />
                <span>Trilhas de Estudo</span>
              </span>
            </div>

            {trilhasComCronograma.map((trilha) => {
              const isAtiva = trilhaSelecionadaId === trilha.id;
              return (
                <div
                  key={`trilha_${trilha.id}`}
                  className={`${styles.listaItemRow} ${isAtiva ? styles.listaItemAtiva : ''}`}
                >
                  <button
                    type="button"
                    className={styles.listaItemContent}
                    onClick={() => {
                      onSelecionarLista(undefined);
                      if (onSelecionarTrilha) onSelecionarTrilha(trilha.id);
                    }}
                    title={`Trilha: ${trilha.nome}`}
                  >
                    <DisciplineLabel
                      name={trilha.nome}
                      color={trilha.cor || 'var(--purple, #a855f7)'}
                      className={styles.listaDisciplineLabel}
                    />
                    <span className={styles.listaBadgeCiclo}>Estudos</span>
                    {isAtiva && <Check size={13} className={styles.listaCheck} />}
                  </button>
                </div>
              );
            })}
          </div>
        )}

        {/* ── FIM DA SEÇÃO TRILHAS DE ESTUDO ── */}
      </div>
    </div>
  );
};
