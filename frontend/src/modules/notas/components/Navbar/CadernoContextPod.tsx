import React, { useMemo } from 'react';
import { BookMarked, Edit2, Plus, Trash2, Search, RefreshCw, BookOpen } from 'lucide-react';
import { Button, Select, useSidebar } from '@shared/design-system';
import { Caderno } from '../../types/nota';
import styles from './CadernoContextPod.module.css';

export interface CadernoContextPodProps {
  cadernos: Caderno[];
  cadernoAtivoId?: string;
  onSelecionarCaderno: (id: string) => void;
  onAbrirNovoCaderno: () => void;
  onAbrirEditarCaderno: () => void;
  onExcluirCaderno: () => void;
  onAbrirNovaNota?: () => void;
  onAbrirImportarTrilha?: () => void;
  onSincronizarTrilha?: () => void;
  onAbrirBusca?: () => void;
  onAbrirLixeira?: () => void;
}

export const CadernoContextPod: React.FC<CadernoContextPodProps> = ({
  cadernos,
  cadernoAtivoId,
  onSelecionarCaderno,
  onAbrirNovoCaderno,
  onAbrirEditarCaderno,
  onExcluirCaderno,
  onAbrirNovaNota,
  onAbrirImportarTrilha,
  onSincronizarTrilha,
  onAbrirBusca,
  onAbrirLixeira
}) => {
  const { collapsed } = useSidebar();

  const cadernoOptions = useMemo(
    () =>
      cadernos.map((c) => ({
        value: c.id,
        label: c.origemTrilhaId ? `📚 ${c.titulo}` : c.titulo,
        dotColor: c.cor || undefined
      })),
    [cadernos]
  );

  const cadernoAtivo = cadernos.find((c) => c.id === cadernoAtivoId);

  if (collapsed) {
    return (
      <div className={styles.podCollapsed}>
        <button
          type="button"
          className={styles.collapsedBtn}
          onClick={onAbrirNovoCaderno}
          title={cadernoAtivo ? `Caderno: ${cadernoAtivo.titulo} - Clique para Novo Caderno` : 'Novo Caderno'}
          aria-label="Novo Caderno"
        >
          {cadernoAtivo?.cor ? (
            <span
              style={{
                width: 14,
                height: 14,
                borderRadius: '50%',
                backgroundColor: cadernoAtivo.cor,
                boxShadow: '0 0 0 1px var(--border-strong)'
              }}
            />
          ) : (
            <BookMarked size={18} />
          )}
        </button>

        {onAbrirNovaNota && (
          <button
            type="button"
            className={styles.collapsedBtn}
            onClick={onAbrirNovaNota}
            title="Nova Nota"
            aria-label="Nova Nota"
          >
            <Plus size={18} />
          </button>
        )}

        {onAbrirBusca && (
          <button
            type="button"
            className={styles.collapsedBtn}
            onClick={onAbrirBusca}
            title="Busca Global"
            aria-label="Busca Global"
          >
            <Search size={16} />
          </button>
        )}

        {onAbrirImportarTrilha && (
          <button
            type="button"
            className={styles.collapsedBtn}
            onClick={onAbrirImportarTrilha}
            title="Importar Trilha de Estudos"
            aria-label="Importar Trilha"
          >
            <BookOpen size={16} />
          </button>
        )}

        {onAbrirLixeira && (
          <button
            type="button"
            className={styles.collapsedBtn}
            onClick={onAbrirLixeira}
            title="Lixeira"
            aria-label="Lixeira"
          >
            <Trash2 size={16} />
          </button>
        )}
      </div>
    );
  }

  return (
    <div className={styles.pod}>
      <div className={styles.podHeader}>
        <span className={styles.podLabel}>
          <span
            style={{
              width: 9,
              height: 9,
              borderRadius: '50%',
              backgroundColor: cadernoAtivo?.cor || 'var(--primary)'
            }}
            aria-hidden="true"
          />
          <span>Caderno</span>
        </span>

        {cadernoAtivo && (
          <div className={styles.headerActions}>
            <button
              type="button"
              className={styles.iconBtn}
              onClick={onAbrirEditarCaderno}
              title="Editar título e cor do caderno"
              aria-label="Editar caderno"
            >
              <Edit2 size={13} />
            </button>
            <button
              type="button"
              className={styles.iconBtn}
              onClick={onExcluirCaderno}
              title="Excluir caderno e todas as suas notas"
              aria-label="Excluir caderno"
            >
              <Trash2 size={13} />
            </button>
          </div>
        )}
      </div>

      {/* Pílula identificadora se o caderno está vinculado a uma trilha */}
      {cadernoAtivo?.origemTrilhaId && (
        <span
          className={styles.trilhaBadge}
          title="Vinculado à trilha; use Sincronizar para atualizar nomes e novas disciplinas"
        >
          <BookOpen size={11} aria-hidden="true" />
          <span>
            Trilha: {cadernoAtivo.trilhaNome?.trim() || 'vinculada'}
          </span>
        </span>
      )}

      {cadernos.length > 0 ? (
        <div className={styles.selectWrapper}>
          <Select
            value={cadernoAtivoId || ''}
            onChange={onSelecionarCaderno}
            options={cadernoOptions}
            title="Selecionar caderno"
            aria-label="Selecionar caderno"
          />
        </div>
      ) : (
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<BookMarked size={14} />}
          onClick={onAbrirNovoCaderno}
          fullWidth
        >
          Criar Primeiro Caderno
        </Button>
      )}

      <div className={styles.actionButtons}>
        {/* Sincronização explícita com trilha */}
        {cadernoAtivo?.origemTrilhaId && onSincronizarTrilha && (
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<RefreshCw size={13} />}
            fullWidth
            onClick={onSincronizarTrilha}
          >
            Sincronizar Trilha
          </Button>
        )}

        <Button
          variant="ghost"
          size="sm"
          leftIcon={<BookMarked size={14} />}
          fullWidth
          onClick={onAbrirNovoCaderno}
        >
          Novo Caderno
        </Button>

        {onAbrirImportarTrilha && (
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<BookOpen size={14} />}
            fullWidth
            onClick={onAbrirImportarTrilha}
          >
            Importar Trilha
          </Button>
        )}

        {onAbrirBusca && (
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<Search size={14} />}
            fullWidth
            onClick={onAbrirBusca}
          >
            Busca Global
          </Button>
        )}

        {onAbrirLixeira && (
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<Trash2 size={14} />}
            fullWidth
            onClick={onAbrirLixeira}
          >
            Lixeira
          </Button>
        )}
      </div>
    </div>
  );
};
