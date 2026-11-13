import React, { useId, useState } from 'react';
import { AlertTriangle, BookOpen, Check, ChevronDown, ChevronRight, ListTodo } from 'lucide-react';
import { Badge, DisciplineLabel, EmptyState, SectionErrorCard, Skeleton } from '../design-system';
import { BlocoHojeDTO, TarefaDTO } from '../contracts/domainContracts';
import { AgendaHojeDerivado } from './agendaHojeService';
import { TarefaAgendaItem } from './TarefaAgendaItem';
import styles from './AgendaHoje.module.css';

type ModoAgendaHoje = 'tarefas' | 'pomodoro';

interface FocoAgenda {
  tipo: 'TAREFA' | 'BLOCO';
  id: string;
}

export interface AgendaHojeProps {
  /** `tarefas` (densidade confortável, ações de edição) ou `pomodoro` (compacta, foco). */
  modo: ModoAgendaHoje;
  /** Saída de `useAgendaHoje().dados`; `null` para visitante (nunca renderizado). */
  dados: AgendaHojeDerivado | null;
  onAlternarTarefa: (id: string, concluida: boolean) => void;
  onConcluirBloco: (bloco: BlocoHojeDTO) => void;
  /** Retry isolado da fonte de Tarefas (Atrasadas, tarefas de Hoje e Sem data). */
  onRetryTarefas: () => void;
  /** Retry isolado da fonte de Blocos (subgrupo "Blocos de estudo"). */
  onRetryBlocos: () => void;
  recarregando?: boolean;
  onEditarTarefa?: (tarefa: TarefaDTO) => void;
  onExcluirTarefa?: (tarefa: TarefaDTO) => void;
  onPularTarefa?: (id: string) => void;
  onAlternarSubtarefa?: (tarefaId: string, subtarefaId: string, concluida: boolean) => void;
  /** Somente em `modo="pomodoro"`: contexto visual de foco atual. */
  foco?: FocoAgenda | null;
  onDefinirFoco?: (foco: FocoAgenda) => void;
}

const TEXTO_ERRO_REDE = 'Verifique sua conexão e tente novamente.';

/** Mensagem de falha da fonte, sem expor detalhes internos do servidor. */
function mensagemErro(erro: Error | null): string {
  const detalhe = erro?.message?.trim();
  if (!detalhe || detalhe.length > 160) return TEXTO_ERRO_REDE;
  return detalhe;
}

/**
 * Visão Hoje compartilhada por Tarefas e Pomodoro (§9 / D-23 / D-37).
 *
 * - Seções: Atrasadas (agrupadas por série), Hoje (subgrupos "Blocos de estudo" e
 *   "Tarefas") e Sem data, cada uma com contador;
 * - carregamento inicial: `Skeleton row` ×3 por seção;
 * - falha de uma fonte: `SectionErrorCard variant="inline"` apenas no subgrupo afetado,
 *   mantendo os dados da outra fonte;
 * - tudo vazio com as duas fontes OK: `EmptyState` "Tudo em dia por hoje";
 * - `modo` controla densidade e a ação "Definir como foco" (exclusiva do Pomodoro).
 */
export const AgendaHoje: React.FC<AgendaHojeProps> = ({
  modo,
  dados,
  onAlternarTarefa,
  onConcluirBloco,
  onRetryTarefas,
  onRetryBlocos,
  recarregando = false,
  onEditarTarefa,
  onExcluirTarefa,
  onPularTarefa,
  onAlternarSubtarefa,
  foco = null,
  onDefinirFoco
}) => {
  const [expansores, setExpansores] = useState<Record<string, boolean>>({});
  const idBase = useId();

  const compacta = modo === 'pomodoro';
  const densidade = compacta ? 'compacta' : 'confortavel';
  const permiteFoco = compacta && Boolean(onDefinirFoco);

  if (!dados) return null;

  const carregandoTarefas = dados.statusTarefas === 'carregando';
  const carregandoBlocos = dados.statusBlocos === 'carregando';
  const erroTarefas = dados.statusTarefas === 'erro' ? dados.erroTarefas : null;
  const erroBlocos = dados.statusBlocos === 'erro' ? dados.erroBlocos : null;

  const chaveGrupo = (principal: TarefaDTO): string => principal.serieId || principal.id;

  const renderTarefa = (tarefa: TarefaDTO) => (
    <TarefaAgendaItem
      key={tarefa.id}
      tarefa={tarefa}
      densidade={densidade}
      onAlternar={onAlternarTarefa}
      onEditar={compacta ? undefined : onEditarTarefa}
      onExcluir={compacta ? undefined : onExcluirTarefa}
      onPular={compacta ? undefined : onPularTarefa}
      onAlternarSubtarefa={onAlternarSubtarefa}
      onSelecionar={
        permiteFoco
          ? (selecionada) => onDefinirFoco?.({ tipo: 'TAREFA', id: selecionada.id })
          : undefined
      }
      selecionado={foco?.tipo === 'TAREFA' && foco.id === tarefa.id}
    />
  );

  const renderBloco = (bloco: BlocoHojeDTO) => {
    const blocoId = bloco.blocoId || bloco.id || '';
    const selecionado = foco?.tipo === 'BLOCO' && foco.id === blocoId;

    return (
      <div
        key={blocoId}
        className={`${styles.blocoItem} ${selecionado ? styles.blocoSelecionado : ''}`}
      >
        <div className={styles.blocoConteudo}>
          <button
            type="button"
            className={styles.blocoConcluir}
            onClick={() => onConcluirBloco(bloco)}
            title="Concluir bloco e registrar sessão"
            aria-label={`Concluir bloco de ${bloco.disciplinaNome}`}
          >
            <Check size={compacta ? 12 : 14} />
          </button>

          <div className={styles.blocoInfo}>
            {permiteFoco ? (
              <button
                type="button"
                className={styles.blocoFoco}
                onClick={() => onDefinirFoco?.({ tipo: 'BLOCO', id: blocoId })}
                title="Definir como foco"
                aria-label="Definir como foco"
              >
                <DisciplineLabel name={bloco.disciplinaNome} color={bloco.disciplinaCor} />
              </button>
            ) : (
              <DisciplineLabel name={bloco.disciplinaNome} color={bloco.disciplinaCor} />
            )}

            {bloco.trilhaNome && (
              <span className={styles.blocoTrilhaPill}>
                <BookOpen size={11} />
                <span>{bloco.trilhaNome}</span>
              </span>
            )}

            <span className={styles.blocoMeta}>
              <span>{bloco.duracaoMinutos} min</span>
            </span>
          </div>
        </div>

        <Badge variant="warning">Hoje</Badge>
      </div>
    );
  };

  /** Carregamento inicial da fonte: skeleton `row` ×3 (D-37, 200 ms). */
  const renderSkeleton = () => <Skeleton variant="row" linhas={3} className={styles.carregando} />;

  /** Erro recuperável da fonte, sempre dentro do subgrupo afetado. */
  const renderErro = (origem: 'tarefas' | 'blocos', erro: Error | null) => (
    <SectionErrorCard
      variant="inline"
      titulo="Não foi possível carregar"
      descricao={mensagemErro(erro)}
      onRetry={origem === 'tarefas' ? onRetryTarefas : onRetryBlocos}
      recarregando={recarregando}
    />
  );

  const listaVazia = (texto: string) => <p className={styles.vazioSecao}>{texto}</p>;

  return (
    <div className={`${styles.container} ${compacta ? styles.compacta : ''}`}>
      {/* ── Seção 1: Atrasadas ─────────────────────────────────────────── */}
      <section className={styles.secao} aria-labelledby={`${idBase}-atrasadas`}>
        <h2
          id={`${idBase}-atrasadas`}
          className={`${styles.secaoTitulo} ${styles.secaoTituloAtrasadas}`}
        >
          <AlertTriangle size={compacta ? 15 : 18} aria-hidden="true" />
          <span>Atrasadas</span>
          <span className={styles.contador}>({dados.atrasadas.length})</span>
        </h2>

        {carregandoTarefas ? (
          renderSkeleton()
        ) : erroTarefas ? (
          renderErro('tarefas', erroTarefas)
        ) : dados.atrasadasAgrupadas.length === 0 ? (
          listaVazia('Nenhuma tarefa atrasada.')
        ) : (
          <div className={styles.lista}>
            {dados.atrasadasAgrupadas.map(({ principal, anteriores }) => {
              const chave = chaveGrupo(principal);
              const expandido = Boolean(expansores[chave]);

              return (
                <div key={chave}>
                  {renderTarefa(principal)}

                  {anteriores.length > 0 && (
                    <>
                      <button
                        type="button"
                        className={styles.expansor}
                        aria-expanded={expandido}
                        onClick={() =>
                          setExpansores((prev) => ({ ...prev, [chave]: !prev[chave] }))
                        }
                      >
                        {expandido ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                        <span>
                          {expandido
                            ? 'Ocultar ocorrências anteriores'
                            : `+ ${anteriores.length} ocorrência(s) anterior(es) atrasada(s)`}
                        </span>
                      </button>

                      {expandido && (
                        <div className={styles.anteriores}>{anteriores.map(renderTarefa)}</div>
                      )}
                    </>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* ── Seção 2: Hoje ──────────────────────────────────────────────── */}
      <section className={styles.secao} aria-labelledby={`${idBase}-hoje`}>
        <h2 id={`${idBase}-hoje`} className={styles.secaoTitulo}>
          <ListTodo size={compacta ? 15 : 18} aria-hidden="true" />
          <span>Hoje</span>
        </h2>

        <div className={styles.subgrupo}>
          <h3 className={styles.subgrupoTitulo}>
            <BookOpen size={compacta ? 14 : 15} aria-hidden="true" />
            <span>Blocos de estudo</span>
            <span className={styles.contador}>({dados.blocosHoje.length})</span>
          </h3>

          {carregandoBlocos ? (
            renderSkeleton()
          ) : erroBlocos ? (
            renderErro('blocos', erroBlocos)
          ) : dados.blocosHoje.length === 0 ? (
            listaVazia('Nenhum bloco de estudo para hoje.')
          ) : (
            <div className={styles.lista}>{dados.blocosHoje.map(renderBloco)}</div>
          )}
        </div>

        <div className={styles.subgrupo}>
          <h3 className={styles.subgrupoTitulo}>
            <ListTodo size={compacta ? 14 : 15} aria-hidden="true" />
            <span>Tarefas</span>
            <span className={styles.contador}>({dados.tarefasHoje.length})</span>
          </h3>

          {carregandoTarefas ? (
            renderSkeleton()
          ) : erroTarefas ? (
            renderErro('tarefas', erroTarefas)
          ) : dados.tarefasHoje.length === 0 ? (
            listaVazia('Nenhuma tarefa com vencimento para hoje.')
          ) : (
            <div className={styles.lista}>{dados.tarefasHoje.map(renderTarefa)}</div>
          )}
        </div>
      </section>

      {/* ── Seção 3: Sem data ──────────────────────────────────────────── */}
      <section className={styles.secao} aria-labelledby={`${idBase}-sem-data`}>
        <h2 id={`${idBase}-sem-data`} className={styles.secaoTitulo}>
          <Check size={compacta ? 15 : 18} aria-hidden="true" />
          <span>Sem data</span>
          <span className={styles.contador}>({dados.semData.length})</span>
        </h2>

        {carregandoTarefas ? (
          renderSkeleton()
        ) : erroTarefas ? (
          renderErro('tarefas', erroTarefas)
        ) : dados.semData.length === 0 ? (
          listaVazia('Nenhuma tarefa sem data.')
        ) : (
          <div className={styles.lista}>{dados.semData.map(renderTarefa)}</div>
        )}
      </section>

      {dados.vazioTotal && (
        <EmptyState
          icone={<Check size={compacta ? 20 : 24} />}
          titulo="Tudo em dia por hoje"
          descricao="Nenhuma tarefa ou bloco pendente para hoje."
        />
      )}
    </div>
  );
};

