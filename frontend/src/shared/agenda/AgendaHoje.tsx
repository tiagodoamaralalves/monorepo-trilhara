import React, { useState } from 'react';
import { Check, ChevronDown, ChevronRight } from 'lucide-react';
import { EmptyState, SectionErrorCard, Skeleton, ToggleGroup } from '../design-system';
import { BlocoHojeDTO, TarefaDTO } from '../contracts/domainContracts';
import { AgendaHojeDerivado } from './agendaHojeService';
import { BlocoAgendaItem } from './BlocoAgendaItem';
import { StatusAgendaTarefa, TarefaAgendaItem } from './TarefaAgendaItem';
import styles from './AgendaHoje.module.css';

type ModoAgendaHoje = 'tarefas' | 'pomodoro';
type FiltroAgendaHoje = 'todos' | 'atrasadas' | 'hoje' | 'blocos' | 'sem-data';

interface FocoAgenda {
  tipo: 'TAREFA' | 'BLOCO';
  id: string;
}

export interface AgendaHojeProps {
  /** `tarefas` (clique abre edição) ou `pomodoro` (clique define foco, sem edição). */
  modo: ModoAgendaHoje;
  /** Saída de `useAgendaHoje.dados`; `null` para visitante (nunca renderizado). */
  dados: AgendaHojeDerivado | null;
  onAlternarTarefa: (id: string, concluida: boolean) => void;
  onConcluirBloco: (bloco: BlocoHojeDTO) => void;
  /** Retry isolado da fonte de Tarefas (Atrasadas, tarefas de Hoje e Sem data). */
  onRetryTarefas: () => void;
  /** Retry isolado da fonte de Blocos. */
  onRetryBlocos: () => void;
  recarregando?: boolean;
  onEditarTarefa?: (tarefa: TarefaDTO) => void;
  onExcluirTarefa?: (tarefa: TarefaDTO) => void;
  onAlternarSubtarefa?: (tarefaId: string, subtarefaId: string, concluida: boolean) => void;
  /** Somente em `modo="pomodoro"`: contexto visual de foco atual. */
  foco?: FocoAgenda | null;
  onDefinirFoco?: (foco: FocoAgenda) => void;
}

const TEXTO_ERRO_REDE = 'Verifique sua conexão e tente novamente.';

const OPCOES_FILTRO = [
  { value: 'todos', label: 'Todos' },
  { value: 'atrasadas', label: 'Atrasadas' },
  { value: 'hoje', label: 'Hoje' },
  { value: 'blocos', label: 'Blocos' },
  { value: 'sem-data', label: 'Sem data' }
] as const;

/** Mensagem de falha da fonte, sem expor detalhes internos do servidor. */
function mensagemErro(erro: Error | null): string {
  const detalhe = erro?.message?.trim();
  if (!detalhe || detalhe.length > 160) return TEXTO_ERRO_REDE;
  return detalhe;
}

/**
 * Visão Hoje compartilhada por Tarefas e Pomodoro.
 *
 * Lista única de cartões no padrão de Disciplinas com filtros no topo
 * (`Todos | Atrasadas | Hoje | Blocos | Sem data`) e pílulas de status horizontais
 * dentro de cada cartão.
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
  onAlternarSubtarefa,
  foco = null,
  onDefinirFoco
}) => {
  const [filtro, setFiltro] = useState<FiltroAgendaHoje>('todos');
  const [expansores, setExpansores] = useState<Record<string, boolean>>({});

  if (!dados) return null;

  const isPomodoro = modo === 'pomodoro';
  const permiteFoco = isPomodoro && Boolean(onDefinirFoco);

  const carregandoTarefas = dados.statusTarefas === 'carregando';
  const carregandoBlocos = dados.statusBlocos === 'carregando';
  const erroTarefas = dados.statusTarefas === 'erro' ? dados.erroTarefas : null;
  const erroBlocos = dados.statusBlocos === 'erro' ? dados.erroBlocos : null;

  const incluiTarefas = filtro !== 'blocos';
  const incluiBlocos = filtro === 'todos' || filtro === 'blocos';

  const chaveGrupo = (principal: TarefaDTO): string => principal.serieId || principal.id;

  const renderTarefa = (tarefa: TarefaDTO, statusAgenda: StatusAgendaTarefa) => (
    <TarefaAgendaItem
      key={tarefa.id}
      tarefa={tarefa}
      statusAgenda={statusAgenda}
      onAlternar={onAlternarTarefa}
      onEditar={isPomodoro ? undefined : onEditarTarefa}
      onExcluir={onExcluirTarefa}
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
      <BlocoAgendaItem
        key={blocoId}
        disciplinaNome={bloco.disciplinaNome}
        disciplinaCor={bloco.disciplinaCor}
        duracaoMinutos={bloco.duracaoMinutos}
        trilhaNome={bloco.trilhaNome}
        statusData="HOJE"
        selecionado={selecionado}
        onConcluir={() => onConcluirBloco(bloco)}
        onDefinirFoco={
          permiteFoco ? () => onDefinirFoco?.({ tipo: 'BLOCO', id: blocoId }) : undefined
        }
      />
    );
  };

  const renderErro = (origem: 'tarefas' | 'blocos', erro: Error | null) => (
    <SectionErrorCard
      variant="inline"
      titulo="Não foi possível carregar"
      descricao={mensagemErro(erro)}
      onRetry={origem === 'tarefas' ? onRetryTarefas : onRetryBlocos}
      recarregando={recarregando}
    />
  );

  const carregandoFiltroAtual =
    (filtro === 'todos' && carregandoTarefas && carregandoBlocos) ||
    (filtro === 'blocos' && carregandoBlocos) ||
    (filtro !== 'todos' && filtro !== 'blocos' && carregandoTarefas);

  const exibirAtrasadas =
    (filtro === 'todos' || filtro === 'atrasadas') && !erroTarefas && dados.atrasadasAgrupadas.length > 0;
  const exibirBlocos =
    (filtro === 'todos' || filtro === 'blocos') && !erroBlocos && dados.blocosHoje.length > 0;
  const exibirHoje =
    (filtro === 'todos' || filtro === 'hoje') && !erroTarefas && dados.tarefasHoje.length > 0;
  const exibirSemData =
    (filtro === 'todos' || filtro === 'sem-data') && !erroTarefas && dados.semData.length > 0;

  const temItensVisiveis = exibirAtrasadas || exibirBlocos || exibirHoje || exibirSemData;

  const renderVazioFiltro = () => {
    if (filtro === 'todos' && dados.vazioTotal) {
      return (
        <EmptyState
          icone={<Check size={isPomodoro ? 20 : 24} />}
          titulo="Tudo em dia por hoje"
          descricao="Nenhuma tarefa ou bloco pendente para hoje."
        />
      );
    }

    if (
      !carregandoFiltroAtual &&
      !temItensVisiveis &&
      !(incluiTarefas && erroTarefas) &&
      !(incluiBlocos && erroBlocos)
    ) {
      const mensagens: Record<Exclude<FiltroAgendaHoje, 'todos'>, string> = {
        atrasadas: 'Nenhuma tarefa atrasada.',
        hoje: 'Nenhuma tarefa para hoje.',
        blocos: 'Nenhum bloco de estudo para hoje.',
        'sem-data': 'Nenhuma tarefa sem data.'
      };
      if (filtro !== 'todos') {
        return (
          <p className={styles.vazioFiltro} role="status">
            {mensagens[filtro]}
          </p>
        );
      }
    }

    return null;
  };

  return (
    <div className={styles.container}>
      <div className={styles.toolbarRow}>
        <ToggleGroup<FiltroAgendaHoje>
          className={styles.filterGroup}
          ariaLabel="Filtrar itens da agenda de hoje"
          value={filtro}
          onChange={setFiltro}
          options={OPCOES_FILTRO}
        />
      </div>

      {carregandoFiltroAtual ? (
        <Skeleton variant="row" linhas={3} className={styles.carregando} />
      ) : (
        <>
          {incluiTarefas && erroTarefas && renderErro('tarefas', erroTarefas)}
          {incluiBlocos && erroBlocos && renderErro('blocos', erroBlocos)}

          {temItensVisiveis && (
            <div className={styles.lista}>
              {exibirAtrasadas &&
                dados.atrasadasAgrupadas.map(({ principal, anteriores }) => {
                  const chave = chaveGrupo(principal);
                  const expandido = Boolean(expansores[chave]);

                  return (
                    <div key={chave} className={styles.grupoAtrasado}>
                      {renderTarefa(principal, 'atrasada')}

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
                            <div className={styles.anteriores}>
                              {anteriores.map((ant) => renderTarefa(ant, 'atrasada'))}
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  );
                })}

              {exibirBlocos && dados.blocosHoje.map(renderBloco)}

              {exibirHoje && dados.tarefasHoje.map((t) => renderTarefa(t, 'hoje'))}

              {exibirSemData && dados.semData.map((t) => renderTarefa(t, 'sem-data'))}
            </div>
          )}

          {renderVazioFiltro()}
        </>
      )}
    </div>
  );
};
