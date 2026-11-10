import React from 'react';
import { Plus, Folder, Edit2, Trash2 } from 'lucide-react';
import { Secao } from '../../types/nota';
import styles from './SecaoTabs.module.css';

export interface SecaoTabsProps {
  secoes: Secao[];
  secaoAtivaId?: string;
  onSelecionarSecao: (id: string) => void;
  onAbrirNovaSecao: () => void;
  onAbrirEditarSecao?: (secao: Secao) => void;
  onExcluirSecao?: (secao: Secao) => void;
}

export const SecaoTabs: React.FC<SecaoTabsProps> = ({
  secoes,
  secaoAtivaId,
  onSelecionarSecao,
  onAbrirNovaSecao,
  onAbrirEditarSecao,
  onExcluirSecao
}) => {
  return (
    <div className={styles.secaoContainer}>
      <div className={styles.secaoHeader}>
        <span className={styles.secaoLabel}>
          <Folder size={12} />
          <span>Seções</span>
        </span>
        <button
          type="button"
          className={styles.addSecaoBtn}
          onClick={onAbrirNovaSecao}
          title="Nova Seção"
          aria-label="Nova Seção"
        >
          <Plus size={13} />
          <span>Nova</span>
        </button>
      </div>

      <div className={styles.secaoList} role="tablist">
        {secoes.map((secao) => {
          const isAtiva = secao.id === secaoAtivaId;
          const cor = secao.cor || 'var(--primary)';

          return (
            <div
              key={secao.id}
              className={`${styles.secaoItem} ${isAtiva ? styles.secaoItemAtiva : ''}`}
              style={{
                borderLeftColor: isAtiva ? cor : 'transparent'
              }}
            >
              <button
                type="button"
                className={styles.secaoBtn}
                onClick={() => onSelecionarSecao(secao.id)}
                role="tab"
                aria-selected={isAtiva}
                title={secao.titulo}
              >
                <span
                  className={styles.secaoDot}
                  style={{ backgroundColor: cor }}
                />
                <span className={styles.secaoTitulo}>{secao.titulo}</span>
              </button>

              {isAtiva && (
                <div className={styles.itemActions}>
                  {onAbrirEditarSecao && (
                    <button
                      type="button"
                      className={styles.miniBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        onAbrirEditarSecao(secao);
                      }}
                      title="Renomear seção"
                      aria-label="Editar seção"
                    >
                      <Edit2 size={11} />
                    </button>
                  )}
                  {onExcluirSecao && secoes.length > 1 && (
                    <button
                      type="button"
                      className={styles.miniBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        onExcluirSecao(secao);
                      }}
                      title="Excluir seção e suas notas"
                      aria-label="Excluir seção"
                    >
                      <Trash2 size={11} />
                    </button>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
