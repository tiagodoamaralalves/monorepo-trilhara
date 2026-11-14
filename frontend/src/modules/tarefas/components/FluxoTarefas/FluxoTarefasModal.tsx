import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  Columns2,
  Link2,
  Plus,
  Route,
  Unlink
} from 'lucide-react';
import {
  Badge,
  Button,
  Checkbox,
  Input,
  Modal,
  ToggleGroup
} from '@shared/design-system';
import type { ListaTarefas, Tarefa, TipoConexaoFluxo } from '../../types/tarefa';
import { tarefaPertenceAoFluxo } from './fluxoLayout';
import styles from './FluxoTarefasModal.module.css';

interface ConexaoExistente {
  alvo: Tarefa;
  tipo: TipoConexaoFluxo;
  rotulo: string;
  variant: 'default' | 'primary' | 'purple';
}

type ModoFluxoModal = 'trazer' | 'conectar';

export interface FluxoTarefasModalProps {
  isOpen: boolean;
  modo: ModoFluxoModal;
  tarefa?: Tarefa | null;
  todasTarefas: Tarefa[];
  listas?: ListaTarefas[];
  onClose: () => void;
  onAdicionarMultiplas?: (tarefaIds: string[]) => Promise<void>;
  onConectarMultiplas?: (
    origemId: string,
    alvosIds: string[],
    tipo: TipoConexaoFluxo
  ) => Promise<void>;
  onDesconectar?: (origemId: string, alvoId: string) => Promise<void>;
  onAbrirTelaFluxo?: (tarefaId: string) => void;
}

const OPCOES_TIPO = [
  { value: 'ANTECESSORA', label: 'Antecessora', icon: <ArrowLeft size={14} aria-hidden="true" /> },
  { value: 'PAR', label: 'Par (Paralelo)', icon: <Columns2 size={14} aria-hidden="true" /> },
  { value: 'SUCESSORA', label: 'Sucessora', icon: <ArrowRight size={14} aria-hidden="true" /> }
] as const;

export const FluxoTarefasModal: React.FC<FluxoTarefasModalProps> = ({
  isOpen,
  modo,
  tarefa = null,
  todasTarefas,
  listas = [],
  onClose,
  onAdicionarMultiplas,
  onConectarMultiplas,
  onDesconectar,
  onAbrirTelaFluxo
}) => {
  const [tipo, setTipo] = useState<TipoConexaoFluxo>('SUCESSORA');
  const [busca, setBusca] = useState('');
  const [selecionadasIds, setSelecionadasIds] = useState<string[]>([]);
  const [enviando, setEnviando] = useState(false);

  const isConectar = modo === 'conectar';

  const mapaTarefas = useMemo(
    () => new Map(todasTarefas.map((item) => [item.id, item])),
    [todasTarefas]
  );

  const mapasListas = useMemo(
    () => new Map(listas.map((l) => [l.id, l])),
    [listas]
  );

  const conexoesAtuais = useMemo<ConexaoExistente[]>(() => {
    if (!isConectar || !tarefa) return [];
    const lista: ConexaoExistente[] = [];

    for (const id of tarefa.antecessorasIds ?? []) {
      const alvo = mapaTarefas.get(id);
      if (alvo) {
        lista.push({ alvo, tipo: 'ANTECESSORA', rotulo: 'Antecessora', variant: 'default' });
      }
    }
    for (const id of tarefa.paresIds ?? []) {
      const alvo = mapaTarefas.get(id);
      if (alvo) {
        lista.push({ alvo, tipo: 'PAR', rotulo: 'Par', variant: 'purple' });
      }
    }
    for (const id of tarefa.sucessorasIds ?? []) {
      const alvo = mapaTarefas.get(id);
      if (alvo) {
        lista.push({ alvo, tipo: 'SUCESSORA', rotulo: 'Sucessora', variant: 'primary' });
      }
    }

    return lista;
  }, [isConectar, tarefa, mapaTarefas]);

  const candidatas = useMemo(() => {
    const termo = busca.trim().toLowerCase();

    if (isConectar) {
      if (!tarefa) return [];
      const jaConectadas = new Set([
        ...(tarefa.antecessorasIds ?? []),
        ...(tarefa.sucessorasIds ?? []),
        ...(tarefa.paresIds ?? [])
      ]);
      return todasTarefas
        .filter((item) => item.id !== tarefa.id && !jaConectadas.has(item.id))
        .filter((item) => {
          if (!termo) return true;
          const nomeLista = item.listaId ? mapasListas.get(item.listaId)?.nome ?? '' : '';
          return (
            item.titulo.toLowerCase().includes(termo) ||
            nomeLista.toLowerCase().includes(termo)
          );
        });
    }

    return todasTarefas
      .filter((item) => !tarefaPertenceAoFluxo(item))
      .filter((item) => {
        if (!termo) return true;
        const nomeLista = item.listaId ? mapasListas.get(item.listaId)?.nome ?? '' : '';
        return (
          item.titulo.toLowerCase().includes(termo) ||
          nomeLista.toLowerCase().includes(termo)
        );
      });
  }, [isConectar, tarefa, todasTarefas, busca, mapasListas]);

  useEffect(() => {
    if (!isOpen) {
      setBusca('');
      setSelecionadasIds([]);
      setTipo('SUCESSORA');
      return;
    }
    setSelecionadasIds((atual) =>
      atual.filter((id) => candidatas.some((c) => c.id === id))
    );
  }, [isOpen, candidatas]);

  if (isConectar && !tarefa) return null;

  const selecionadasSet = new Set(selecionadasIds);
  const todasVisiveisSelecionadas =
    candidatas.length > 0 && candidatas.every((item) => selecionadasSet.has(item.id));

  const alternarSelecao = (id: string) => {
    setSelecionadasIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const alternarTodasVisiveis = () => {
    if (todasVisiveisSelecionadas) {
      const idsVisiveis = new Set(candidatas.map((c) => c.id));
      setSelecionadasIds((prev) => prev.filter((id) => !idsVisiveis.has(id)));
      return;
    }
    const merged = new Set(selecionadasIds);
    for (const c of candidatas) merged.add(c.id);
    setSelecionadasIds([...merged]);
  };

  const handleConfirmar = async () => {
    if (selecionadasIds.length === 0 || enviando) return;
    setEnviando(true);
    try {
      if (isConectar && tarefa && onConectarMultiplas) {
        await onConectarMultiplas(tarefa.id, selecionadasIds, tipo);
        setSelecionadasIds([]);
        setBusca('');
      } else if (!isConectar && onAdicionarMultiplas) {
        await onAdicionarMultiplas(selecionadasIds);
        onClose();
      }
    } finally {
      setEnviando(false);
    }
  };

  const handleDesconectar = async (alvoId: string) => {
    if (!tarefa || !onDesconectar || enviando) return;
    setEnviando(true);
    try {
      await onDesconectar(tarefa.id, alvoId);
    } finally {
      setEnviando(false);
    }
  };

  const qtdSelecionadas = selecionadasIds.length;
  const textoAcaoPrincipal = isConectar
    ? qtdSelecionadas > 0
      ? `Conectar (${qtdSelecionadas})`
      : 'Conectar selecionadas'
    : qtdSelecionadas > 0
      ? `Trazer para o fluxo (${qtdSelecionadas})`
      : 'Trazer selecionadas';

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isConectar ? 'Conectar no Fluxo' : 'Trazer tarefas para o fluxo'}
      subtitle={
        isConectar
          ? tarefa?.titulo
          : 'Selecione uma ou mais tarefas para posicionar na tela infinita'
      }
      icon={<Route size={18} />}
      maxWidth="lg"
      isSaving={enviando}
      footer={
        <div className={styles.footerBar}>
          <div>
            {isConectar && tarefa && onAbrirTelaFluxo && (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                leftIcon={<Route size={14} />}
                disabled={enviando}
                onClick={() => {
                  onAbrirTelaFluxo(tarefa.id);
                  onClose();
                }}
              >
                Abrir na Tela de Fluxo
              </Button>
            )}
          </div>
          <div className={styles.footerRight}>
            <Button type="button" variant="ghost" size="sm" disabled={enviando} onClick={onClose}>
              {isConectar ? 'Fechar' : 'Cancelar'}
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              leftIcon={isConectar ? <Link2 size={14} /> : <Plus size={14} />}
              disabled={enviando || qtdSelecionadas === 0}
              loading={enviando}
              onClick={() => void handleConfirmar()}
            >
              {textoAcaoPrincipal}
            </Button>
          </div>
        </div>
      }
    >
      <div className={styles.container}>
        {isConectar && (
          <>
            <div className={styles.section}>
              <div className={styles.sectionHeader}>
                <h3 className={styles.sectionTitle}>
                  Conexões atuais ({conexoesAtuais.length})
                </h3>
              </div>

              {conexoesAtuais.length === 0 ? (
                <p className={styles.emptyConnections}>
                  Nenhuma conexão ainda. Escolha a relação e marque uma ou mais tarefas abaixo.
                </p>
              ) : (
                <div className={styles.connectionsList}>
                  {conexoesAtuais.map(({ alvo, tipo: tipoConexao, rotulo, variant }) => (
                    <div key={`${tipoConexao}-${alvo.id}`} className={styles.connectionRow}>
                      <div className={styles.connectionInfo}>
                        <Badge variant={variant} size="sm">
                          {rotulo}
                        </Badge>
                        <span className={styles.connectionTitle} title={alvo.titulo}>
                          {alvo.titulo}
                        </span>
                      </div>

                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        iconOnly
                        disabled={enviando}
                        onClick={() => void handleDesconectar(alvo.id)}
                        title={`Desconectar ${alvo.titulo}`}
                        aria-label={`Desconectar ${alvo.titulo}`}
                      >
                        <Unlink size={14} />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className={styles.divider} />

            <div className={styles.section}>
              <h3 className={styles.sectionTitle}>Tipo da nova conexão</h3>
              <ToggleGroup
                options={OPCOES_TIPO}
                value={tipo}
                onChange={setTipo}
                ariaLabel="Tipo de conexão da tarefa"
              />
            </div>
          </>
        )}

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>
              {isConectar ? 'Selecionar tarefas para conectar' : 'Tarefas disponíveis'}
            </h3>
            <div className={styles.selectionToolbar}>
              {qtdSelecionadas > 0 && (
                <span className={styles.selectionCount}>
                  {qtdSelecionadas} {qtdSelecionadas === 1 ? 'selecionada' : 'selecionadas'}
                </span>
              )}
              {candidatas.length > 0 && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={enviando}
                  onClick={alternarTodasVisiveis}
                >
                  {todasVisiveisSelecionadas ? 'Desmarcar visíveis' : 'Selecionar visíveis'}
                </Button>
              )}
            </div>
          </div>

          <Input
            label="Filtrar tarefas"
            placeholder="Buscar por título ou lista..."
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            disabled={enviando}
          />

          {candidatas.length === 0 ? (
            <p className={styles.emptyConnections}>
              {isConectar
                ? 'Nenhuma outra tarefa disponível para conectar com o filtro atual.'
                : 'Todas as tarefas disponíveis já estão no fluxo ou nenhuma corresponde à busca.'}
            </p>
          ) : (
            <div className={styles.pickerList} role="group" aria-label="Lista de tarefas selecionáveis">
              {candidatas.map((item) => {
                const selecionada = selecionadasSet.has(item.id);
                const lista = item.listaId ? mapasListas.get(item.listaId) : undefined;
                const p = String(item.prioridade ?? '').toUpperCase();

                return (
                  <div
                    key={item.id}
                    className={`${styles.pickerRow} ${selecionada ? styles.pickerRowSelected : ''}`}
                  >
                    <div className={styles.pickerCheckboxWrap}>
                      <Checkbox
                        checked={selecionada}
                        disabled={enviando}
                        onChange={() => alternarSelecao(item.id)}
                        label={item.titulo}
                        strikeThroughOnChecked={false}
                      />
                    </div>

                    <div className={styles.pickerMeta}>
                      {lista && (
                        <span className={styles.listaTag} title={lista.nome}>
                          <span
                            className={styles.listaDot}
                            style={{ background: lista.cor || 'var(--primary)' }}
                          />
                          <span className={styles.listaNome}>{lista.nome}</span>
                        </span>
                      )}
                      {item.concluida && (
                        <Badge variant="success" size="sm">
                          Concluída
                        </Badge>
                      )}
                      {!item.concluida && (p === 'ALTA' || p === 'URGENTE') && (
                        <Badge variant="danger" size="sm">
                          Alta
                        </Badge>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
};
