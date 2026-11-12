import React, { useState, useEffect, useCallback } from 'react';
import { Trash2, RotateCcw } from 'lucide-react';
import { Modal, Button, EmptyState, useConfirm, useToast } from '@shared/design-system';
import { ItemLixeira } from '../../types/nota';
import { NotaService } from '../../services/notaService';

export interface LixeiraModalProps {
  isOpen: boolean;
  onClose: () => void;
  onItemRestaurado?: () => void;
}

export const LixeiraModal: React.FC<LixeiraModalProps> = ({
  isOpen,
  onClose,
  onItemRestaurado
}) => {
  const toast = useToast();
  const { confirm } = useConfirm();
  const [itens, setItens] = useState<ItemLixeira[]>([]);
  const [carregando, setCarregando] = useState(false);
  const [acaoId, setAcaoId] = useState<string | null>(null);

  const carregarItens = useCallback(async () => {
    try {
      setCarregando(true);
      const lista = await NotaService.listarLixeira(1, 100);
      setItens(lista);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao carregar itens da lixeira.');
    } finally {
      setCarregando(false);
    }
  }, [toast]);

  useEffect(() => {
    if (isOpen) {
      carregarItens();
    }
  }, [isOpen, carregarItens]);

  const handleRestaurar = async (item: ItemLixeira) => {
    try {
      setAcaoId(item.id);
      const resp = await NotaService.restaurarItemLixeira(item.tipo, item.id);
      toast.success(resp.mensagem || `"${item.titulo}" foi restaurado com sucesso.`);
      await carregarItens();
      if (onItemRestaurado) {
        onItemRestaurado();
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível restaurar o item.');
    } finally {
      setAcaoId(null);
    }
  };

  const handleEsvaziar = async () => {
    const confirmado = await confirm({
      title: 'Esvaziar Lixeira de Notas',
      description: 'Todos os cadernos, seções e páginas atualmente na lixeira serão permanentemente excluídos. Esta ação não pode ser desfeita.',
      confirmText: 'Esvaziar Lixeira',
      variant: 'danger'
    });

    if (!confirmado) return;

    try {
      setCarregando(true);
      const res = await NotaService.esvaziarLixeira();
      toast.success(`Lixeira esvaziada. Removidos: ${res.cadernosRemovidos} cadernos, ${res.secoesRemovidas} seções, ${res.notasRemovidas} notas.`);
      await carregarItens();
      if (onItemRestaurado) {
        onItemRestaurado();
      }
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao esvaziar lixeira.');
    } finally {
      setCarregando(false);
    }
  };

  const calcularDiasRestantes = (expiresAtStr: string) => {
    if (!expiresAtStr) return '30 dias restantes';
    const expires = new Date(expiresAtStr).getTime();
    const agora = Date.now();
    const dias = Math.max(0, Math.ceil((expires - agora) / (1000 * 60 * 60 * 24)));
    return `${dias} ${dias === 1 ? 'dia restante' : 'dias restantes'}`;
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Lixeira de Notas"
      subtitle="Itens excluídos permanecem disponíveis para recuperação por até 30 dias."
      icon={<Trash2 size={20} />}
      maxWidth="lg"
      footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
          {itens.length > 0 ? (
            <Button
              variant="danger"
              size="sm"
              onClick={handleEsvaziar}
              disabled={carregando}
            >
              Esvaziar lixeira agora
            </Button>
          ) : <div />}
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        </div>
      }
    >
      {carregando && itens.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--text-muted)' }}>
          Carregando lixeira...
        </div>
      ) : itens.length === 0 ? (
        <EmptyState
          icon={<Trash2 size={40} />}
          title="Lixeira vazia"
          description="Nenhum caderno, seção ou página foi excluído nos últimos 30 dias."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '420px', overflowY: 'auto' }}>
          {itens.map((item) => (
            <div
              key={`${item.tipo}-${item.id}`}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)',
                background: 'var(--surface-1)'
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span
                    style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      padding: '2px 6px',
                      borderRadius: '4px',
                      backgroundColor:
                        item.tipo === 'CADERNO'
                          ? 'rgba(2, 132, 199, 0.15)'
                          : item.tipo === 'SECAO'
                          ? 'rgba(217, 119, 6, 0.15)'
                          : 'rgba(16, 185, 129, 0.15)',
                      color:
                        item.tipo === 'CADERNO'
                          ? '#0284c7'
                          : item.tipo === 'SECAO'
                          ? '#d97706'
                          : '#059669'
                    }}
                  >
                    {item.tipo}
                  </span>
                  <strong style={{ fontSize: '0.9rem' }}>{item.titulo}</strong>
                </div>

                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  {item.cadernoTitulo && <span>Caderno: {item.cadernoTitulo}</span>}
                  {item.secaoTitulo && <span> • Seção: {item.secaoTitulo}</span>}
                  <span> • {calcularDiasRestantes(item.expiresAt)}</span>
                </div>
              </div>

              <Button
                variant="secondary"
                size="sm"
                leftIcon={<RotateCcw size={13} />}
                isLoading={acaoId === item.id}
                onClick={() => handleRestaurar(item)}
              >
                Restaurar
              </Button>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
};
