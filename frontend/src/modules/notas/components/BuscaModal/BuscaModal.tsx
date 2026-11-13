import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Search, FileText } from 'lucide-react';
import { Modal, Input, EmptyState, SectionErrorCard, Pagination } from '@shared/design-system';
import { ApiError } from '@shared/auth';
import { ResultadoBuscaNota } from '../../types/nota';
import { NotaService } from '../../services/notaService';
import { SnippetDestacado } from './SnippetDestacado';
import styles from './BuscaModal.module.css';

/** Debounce da busca (D-30). */
export const DEBOUNCE_BUSCA_MS = 300;

/** Mínimo de caracteres alfanuméricos para disparar a busca, validado no cliente. */
const MINIMO_ALFANUMERICOS = 2;

const TAMANHO_PAGINA = 20;

export interface BuscaModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelecionarNota: (cadernoId: string, secaoId: string, notaId: string) => void;
}

/** Conta só letras/dígitos: "a b" e "--" não atingem o mínimo e não geram request. */
function contarAlfanumericos(texto: string): number {
  return (texto.match(/[\p{L}\p{N}]/gu) ?? []).length;
}

/**
 * Busca global de páginas (D-30).
 *
 * O backend devolve snippet em texto puro + offsets; a renderização é sempre por nós de texto
 * React com `<mark>`. O mínimo de caracteres é validado **no cliente** (mensagem inline, sem
 * request) e o `429` tem mensagem própria.
 */
export const BuscaModal: React.FC<BuscaModalProps> = ({ isOpen, onClose, onSelecionarNota }) => {
  const [termo, setTermo] = useState('');
  const [resultados, setResultados] = useState<ResultadoBuscaNota[]>([]);
  const [pagina, setPagina] = useState(1);
  const [totalItens, setTotalItens] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [buscando, setBuscando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [mensagemInline, setMensagemInline] = useState<string | null>(null);
  const [termoBuscado, setTermoBuscado] = useState('');

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

  const limparTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const resetar = useCallback(() => {
    setTermo('');
    setResultados([]);
    setPagina(1);
    setTotalItens(0);
    setTotalPaginas(0);
    setErro(null);
    setMensagemInline(null);
    setTermoBuscado('');
    controllerRef.current?.abort();
    limparTimer();
  }, [limparTimer]);

  useEffect(() => {
    if (!isOpen) {
      resetar();
    }
  }, [isOpen, resetar]);

  useEffect(() => {
    return () => {
      controllerRef.current?.abort();
      limparTimer();
    };
  }, [limparTimer]);

  const executarBusca = useCallback(async (texto: string, paginaAlvo: number) => {
    const q = texto.trim();
    if (contarAlfanumericos(q) < MINIMO_ALFANUMERICOS) {
      setResultados([]);
      setTotalItens(0);
      setTotalPaginas(0);
      setErro(null);
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    try {
      setBuscando(true);
      setErro(null);
      setMensagemInline(null);
      const resposta = await NotaService.buscarGlobal(q, paginaAlvo, TAMANHO_PAGINA, {
        signal: controller.signal
      });
      if (controller.signal.aborted) return;
      setResultados(resposta.itens ?? []);
      setTotalItens(resposta.totalItens ?? 0);
      setTotalPaginas(resposta.totalPaginas ?? 0);
      setPagina(resposta.pagina ?? paginaAlvo);
      setTermoBuscado(q);
    } catch (falha) {
      if (controller.signal.aborted) return;
      setResultados([]);
      setTotalItens(0);
      setTotalPaginas(0);
      setErro(falha);
    } finally {
      if (!controller.signal.aborted) {
        setBuscando(false);
      }
    }
  }, []);

  const handleTermoChange = (evento: React.ChangeEvent<HTMLInputElement>) => {
    const valor = evento.target.value;
    setTermo(valor);

    if (contarAlfanumericos(valor) < MINIMO_ALFANUMERICOS) {
      limparTimer();
      controllerRef.current?.abort();
      setResultados([]);
      setTotalItens(0);
      setTotalPaginas(0);
      setErro(null);
      setMensagemInline(
        valor.trim().length > 0 ? 'Digite ao menos 2 letras ou números para buscar.' : null
      );
      return;
    }

    setMensagemInline(null);
    limparTimer();
    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      void executarBusca(valor, 1);
    }, DEBOUNCE_BUSCA_MS);
  };

  const mensagemErro =
    erro instanceof ApiError && erro.status === 429
      ? 'Muitas buscas seguidas. Aguarde alguns segundos.'
      : null;

  const erroRecuperavel = erro !== null && mensagemErro === null;
  const mostraVazio = !buscando && !erro && totalItens === 0 && termoBuscado.length > 0;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Busca Global de Notas"
      subtitle="Pesquise por títulos ou conteúdos em todos os seus cadernos ativos."
      icon={<Search size={20} />}
      maxWidth="lg"
    >
      <div className={styles.conteudo}>
        <Input
          type="search"
          placeholder="Digite ao menos 2 caracteres para pesquisar..."
          value={termo}
          onChange={handleTermoChange}
          leftIcon={<Search size={16} />}
          aria-describedby={mensagemInline ? 'busca-aviso' : undefined}
        />

        {mensagemInline && (
          <p id="busca-aviso" className={styles.aviso} role="status">
            {mensagemInline}
          </p>
        )}

        {buscando && <p className={styles.carregando}>Pesquisando…</p>}

        {mensagemErro && (
          <p className={styles.avisoErro} role="alert">
            {mensagemErro}
          </p>
        )}

        {erroRecuperavel && (
          <SectionErrorCard
            variant="inline"
            titulo="Não foi possível carregar"
            descricao="Verifique sua conexão e tente novamente."
            onRetry={() => void executarBusca(termoBuscado || termo, pagina)}
            recarregando={buscando}
          />
        )}

        {mostraVazio && (
          <EmptyState
            icone={<Search size={36} />}
            titulo={`Nenhuma página encontrada para “${termoBuscado}”`}
            descricao="A busca encontra palavras inteiras."
          />
        )}

        {resultados.length > 0 && (
          <div className={styles.resultados}>
            <span className={styles.resumo}>
              {totalItens} {totalItens === 1 ? 'resultado encontrado' : 'resultados encontrados'}
            </span>

            {resultados.map((item) => (
              <button
                key={item.notaId}
                type="button"
                className={styles.item}
                onClick={() => {
                  onSelecionarNota(item.cadernoId, item.secaoId, item.notaId);
                  onClose();
                }}
              >
                <span className={styles.itemTitulo}>
                  <FileText size={15} aria-hidden="true" />
                  <strong>{item.titulo}</strong>
                </span>

                <span className={styles.itemBreadcrumb}>
                  {item.cadernoTitulo} &gt; {item.secaoTitulo}
                </span>

                {item.snippet && (
                  <span className={styles.itemSnippet}>
                    <SnippetDestacado snippet={item.snippet} destaques={item.destaques} />
                  </span>
                )}
              </button>
            ))}

            {totalPaginas > 1 && (
              <Pagination
                ariaLabel="Paginação dos resultados de busca"
                currentPage={pagina}
                totalPages={totalPaginas}
                onPageChange={(proxima) => void executarBusca(termoBuscado, proxima)}
                disabled={buscando}
                summary={`${totalItens} resultados`}
              />
            )}
          </div>
        )}
      </div>
    </Modal>
  );
};
