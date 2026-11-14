import React, { useCallback, useEffect, useState } from 'react';
import { Trash2, RotateCcw } from 'lucide-react';
import { Modal, Button, EmptyState, Pagination, useConfirm, useToast } from '@shared/design-system';
import { ItemLixeira } from '../../types/nota';
import { NotaService } from '../../services/notaService';

const TAMANHO_PAGINA = 20;

export interface LixeiraModalProps {
  isOpen: boolean;
  onClose: () => void;
  onItemRestaurado?: () => void;
}

const ROTULO_TIPO: Record<ItemLixeira['tipo'], string> = {
  CADERNO: 'Caderno',
  SECAO: 'Seção',
  NOTA: 'Página'
};

const COR_TIPO: Record<ItemLixeira['tipo'], string> = {
  CADERNO: '#0284c7',
  SECAO: '#d97706',
  NOTA: '#059669'
};

function calcularDiasRestantes(expiresAt: string): string {
  if (!expiresAt) return '30 dias restantes';
  const dias = Math.max(
    0,
    Math.ceil((new Date(expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
  );
  return `${dias} ${dias === 1 ? 'dia restante' : 'dias restantes'}`;
}

/**
 * Lixeira hierárquica de Notas.
 *
 * Lista paginada com `breadcrumb` por item, restauração com feedback do destino real
 * (`realocadaPara`) e esvaziamento antecipado com confirmação `danger`.
 */
export const LixeiraModal: React.FC<LixeiraModalProps> = ({
  isOpen,
  onClose,
  onItemRestaurado
}) => {
  const toast = useToast();
  const { confirm } = useConfirm();
  const [itens, setItens] = useState<ItemLixeira[]>([]);
  const [pagina, setPagina] = useState(1);
  const [totalItens, setTotalItens] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [carregando, setCarregando] = useState(false);
  const [acaoId, setAcaoId] = useState<string | null>(null);

  const carregarItens = useCallback(
    async (paginaAlvo: number) => {
      try {
        setCarregando(true);
        const resposta = await NotaService.listarLixeira(paginaAlvo, TAMANHO_PAGINA);
        setItens(resposta.itens ?? []);
        setPagina(resposta.pagina ?? paginaAlvo);
        setTotalItens(resposta.totalItens ?? 0);
        setTotalPaginas(resposta.totalPaginas ?? 0);
      } catch (err: unknown) {
        toast.error(err instanceof Error ? err.message : 'Falha ao carregar itens da lixeira.');
      } finally {
        setCarregando(false);
      }
    },
    [toast]
  );

  useEffect(() => {
    if (isOpen) {
      void carregarItens(1);
    }
  }, [isOpen, carregarItens]);

  const handleRestaurar = async (item: ItemLixeira) => {
    try {
      setAcaoId(item.id);
      const resposta = await NotaService.restaurarItemLixeira(item.tipo, item.id);
      toast.success(resposta.mensagem || `"${item.titulo}" foi restaurado com sucesso.`);
      await carregarItens(pagina);
      onItemRestaurado?.();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Não foi possível restaurar o item.');
    } finally {
      setAcaoId(null);
    }
  };

  const handleEsvaziar = async () => {
    const confirmado = await confirm({
      title: 'Esvaziar a lixeira de Notas',
      description:
        'Todos os cadernos, seções e páginas da lixeira serão excluídos definitivamente. Esta ação não pode ser desfeita.',
      confirmText: 'Esvaziar lixeira',
      variant: 'danger'
    });
    if (!confirmado) return;

    try {
      setCarregando(true);
      const resultado = await NotaService.esvaziarLixeira();
      toast.success(
        `Lixeira esvaziada. Removidos: ${resultado.cadernosRemovidos} caderno(s), ` +
          `${resultado.secoesRemovidas} seção(ões), ${resultado.notasRemovidas} página(s).`
      );
      await carregarItens(1);
      onItemRestaurado?.();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Falha ao esvaziar a lixeira.');
    } finally {
      setCarregando(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Lixeira de Notas"
      subtitle="Itens excluídos ficam disponíveis para restauração por 30 dias."
      icon={<Trash2 size={20} />}
      maxWidth="lg"
      footer={
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            width: '100%'
          }}
        >
          {totalItens > 0 ? (
            <Button
              type="button"
              variant="danger"
              size="md"
              leftIcon={<Trash2 size={15} />}
              onClick={handleEsvaziar}
              disabled={carregando}
            >
              Esvaziar lixeira
            </Button>
          ) : (
            <div />
          )}
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        </div>
      }
    >
      {carregando && itens.length === 0 ? (
        <p style={{ textAlign: 'center', padding: '32px 0', color: 'var(--text-muted)' }}>
          Carregando…
        </p>
      ) : itens.length === 0 ? (
        <EmptyState
          icone={<Trash2 size={40} />}
          titulo="A lixeira está vazia"
          descricao="Itens excluídos ficam aqui por 30 dias."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
              maxHeight: '420px',
              overflowY: 'auto'
            }}
          >
            {itens.map((item) => (
              <div
                key={`${item.tipo}-${item.id}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '12px',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-subtle)',
                  background: 'var(--surface-1)'
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span
                      style={{
                        fontSize: '0.7rem',
                        fontWeight: 700,
                        padding: '2px 6px',
                        borderRadius: '4px',
                        color: COR_TIPO[item.tipo],
                        backgroundColor: 'rgba(2, 132, 199, 0.12)'
                      }}
                    >
                      {ROTULO_TIPO[item.tipo]}
                    </span>
                    <strong style={{ fontSize: '0.9rem' }}>{item.titulo}</strong>
                  </div>

                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    {item.breadcrumb && <span>{item.breadcrumb} • </span>}
                    <span>{calcularDiasRestantes(item.expiresAt)}</span>
                  </div>
                </div>

                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<RotateCcw size={13} />}
                  loading={acaoId === item.id}
                  onClick={() => handleRestaurar(item)}
                >
                  Restaurar
                </Button>
              </div>
            ))}
          </div>

          {totalPaginas > 1 && (
            <Pagination
              ariaLabel="Paginação da lixeira de Notas"
              currentPage={pagina}
              totalPages={totalPaginas}
              onPageChange={(proxima) => void carregarItens(proxima)}
              disabled={carregando}
              summary={`${totalItens} itens`}
            />
          )}
        </div>
      )}
    </Modal>
  );
};
