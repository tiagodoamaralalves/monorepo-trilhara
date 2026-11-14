import React from 'react';
import { Edit2, Plus, Trash2 } from 'lucide-react';
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

/**
 * Abas de seção do caderno ativo.
 *
 * A exclusão da última seção é permitida: o caderno pode ficar temporariamente com zero seções
 * ativas e exibe o estado vazio orientando criar uma seção ou abrir a lixeira.
 * A trava legada que escondia o botão de excluir quando havia uma única seção foi removida.
 */
export const SecaoTabs: React.FC<SecaoTabsProps> = ({
  secoes,
  secaoAtivaId,
  onSelecionarSecao,
  onAbrirNovaSecao,
  onAbrirEditarSecao,
  onExcluirSecao
}) => (
  <div className={styles.secaoContainer}>
    <div className={styles.secaoHeader}>
      <span className={styles.secaoLabel}>Seções</span>
      <button
        type="button"
        className={styles.addSecaoBtn}
        onClick={onAbrirNovaSecao}
        title="Nova seção"
        aria-label="Nova seção"
      >
        <Plus size={11} /> Nova
      </button>
    </div>

    <div className={styles.secaoList} role="tablist" aria-label="Seções do caderno">
      {secoes.map((secao) => {
        const isAtiva = secao.id === secaoAtivaId;
        return (
          <div
            key={secao.id}
            className={`${styles.secaoItem} ${isAtiva ? styles.secaoItemAtiva : ''}`}
          >
            <button
              type="button"
              role="tab"
              aria-selected={isAtiva}
              className={styles.secaoBtn}
              onClick={() => onSelecionarSecao(secao.id)}
              title={secao.titulo}
            >
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
                {onExcluirSecao && (
                  <button
                    type="button"
                    className={styles.miniBtn}
                    onClick={(e) => {
                      e.stopPropagation();
                      onExcluirSecao(secao);
                    }}
                    title="Mover seção para a lixeira"
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
