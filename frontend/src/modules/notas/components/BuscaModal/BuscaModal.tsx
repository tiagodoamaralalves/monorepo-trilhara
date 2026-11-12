import React, { useState, useEffect, useRef } from 'react';
import { Search, FileText } from 'lucide-react';
import { Modal, Input, EmptyState } from '@shared/design-system';
import { ResultadoBuscaNota } from '../../types/nota';
import { NotaService } from '../../services/notaService';

export interface BuscaModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelecionarNota: (cadernoId: string, secaoId: string, notaId: string) => void;
}

export const BuscaModal: React.FC<BuscaModalProps> = ({
  isOpen,
  onClose,
  onSelecionarNota
}) => {
  const [termo, setTermo] = useState('');
  const [resultados, setResultados] = useState<ResultadoBuscaNota[]>([]);
  const [total, setTotal] = useState(0);
  const [buscando, setBuscando] = useState(false);
  const [mensagemErro, setMensagemErro] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setTermo('');
      setResultados([]);
      setTotal(0);
      setMensagemErro(null);
    }
  }, [isOpen]);

  const executarBusca = async (texto: string) => {
    const q = texto.trim();
    if (q.length < 2) {
      setResultados([]);
      setTotal(0);
      setMensagemErro(null);
      return;
    }

    try {
      setBuscando(true);
      setMensagemErro(null);
      const res = await NotaService.buscarGlobal(q, 1, 20);
      setResultados(res.itens || []);
      setTotal(res.total || 0);
    } catch (err: unknown) {
      setResultados([]);
      setTotal(0);
      setMensagemErro(err instanceof Error ? err.message : 'Falha ao buscar páginas.');
    } finally {
      setBuscando(false);
    }
  };

  const handleTermoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setTermo(val);

    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }

    timerRef.current = setTimeout(() => {
      void executarBusca(val);
    }, 350);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Busca Global de Notas"
      subtitle="Pesquise por títulos ou conteúdos em todos os seus cadernos ativos."
      icon={<Search size={20} />}
      maxWidth="lg"
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <Input
          type="search"
          placeholder="Digite ao menos 2 caracteres para pesquisar..."
          value={termo}
          onChange={handleTermoChange}
          leftIcon={<Search size={16} />}
        />

        {buscando && (
          <div style={{ textAlign: 'center', padding: '16px', color: 'var(--text-muted)' }}>
            Pesquisando no banco de dados...
          </div>
        )}

        {mensagemErro && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: '6px',
              background: 'rgba(239, 68, 68, 0.1)',
              color: '#dc2626',
              fontSize: '0.85rem'
            }}
          >
            {mensagemErro}
          </div>
        )}

        {!buscando && termo.trim().length >= 2 && resultados.length === 0 && !mensagemErro && (
          <EmptyState
            icone={<Search size={36} />}
            titulo="Nenhum resultado encontrado"
            descricao={`Não foram encontradas páginas contendo os termos "${termo}".`}
          />
        )}

        {resultados.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '400px', overflowY: 'auto' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              {total} {total === 1 ? 'resultado encontrado' : 'resultados encontrados'}:
            </span>

            {resultados.map((item) => (
              <button
                key={item.notaId}
                type="button"
                onClick={() => {
                  onSelecionarNota(item.cadernoId, item.secaoId, item.notaId);
                  onClose();
                }}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '4px',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-subtle)',
                  background: 'var(--surface-1)',
                  cursor: 'pointer',
                  textAlign: 'left',
                  transition: 'border-color 0.15s ease, background-color 0.15s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'var(--primary)';
                  e.currentTarget.style.backgroundColor = 'var(--surface-2)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'var(--border-subtle)';
                  e.currentTarget.style.backgroundColor = 'var(--surface-1)';
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <FileText size={15} color="var(--primary)" />
                  <strong style={{ fontSize: '0.9rem' }}>{item.titulo}</strong>
                </div>

                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  <span>{item.cadernoTitulo}</span>
                  <span> &gt; </span>
                  <span>{item.secaoTitulo}</span>
                </div>

                {item.snippet && (
                  <p
                    style={{
                      fontSize: '0.8rem',
                      color: 'var(--text)',
                      margin: '4px 0 0 0',
                      lineHeight: '1.3'
                    }}
                  >
                    {item.snippet}
                  </p>
                )}
              </button>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
};
