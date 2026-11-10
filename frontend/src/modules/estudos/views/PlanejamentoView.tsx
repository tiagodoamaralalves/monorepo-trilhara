import { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '@shared/auth';
import { useGlobalSessionModal } from '@shared/design-system';
import { sessaoGlobalService } from '@shared/services/sessaoGlobalService';
import {
  EstudosService,
  CronogramaDTO,
  BlocoCronogramaItemDTO,
  PaginaBlocosDTO,
  DisponibilidadeSemanalDTO,
} from '../services/estudosService';
import { Trilha, RegistroPlanejamentoPendente } from '../types/trilhara';
import { ModalidadeCronograma, CriarCronogramaPayload } from '@shared/contracts';
import styles from './PlanejamentoView.module.css';

const DIAS_SEMANA = [
  { id: 1, label: 'Segunda-feira' },
  { id: 2, label: 'Terça-feira' },
  { id: 3, label: 'Quarta-feira' },
  { id: 4, label: 'Quinta-feira' },
  { id: 5, label: 'Sexta-feira' },
  { id: 6, label: 'Sábado' },
  { id: 7, label: 'Domingo' },
];

export interface PlanejamentoProps {
  trilha?: Trilha;
  onTrilhaAtualizada?: (atualizada: Trilha) => void;
  onRegistrarBloco?: (registro: RegistroPlanejamentoPendente) => void;
  registroPlanejamentoRevision?: number;
  onIniciarEstudo?: (disciplinaId: string, registro: RegistroPlanejamentoPendente) => void;
  onIrParaDisciplinas?: () => void;
}

export function PlanejamentoView({
  trilha: trilhaProp,
  onTrilhaAtualizada,
  onRegistrarBloco,
  registroPlanejamentoRevision,
  onIniciarEstudo,
  onIrParaDisciplinas,
}: PlanejamentoProps = {}) {
  const { usuario } = useAuth();
  const { abrirModal } = useGlobalSessionModal();
  const [trilhas, setTrilhas] = useState<Trilha[]>(trilhaProp ? [trilhaProp] : []);
  const [selectedTrilhaId, setSelectedTrilhaId] = useState<string>(trilhaProp ? trilhaProp.id : '');
  const [selectedTrilhaDetalhe, setSelectedTrilhaDetalhe] = useState<Trilha | null>(trilhaProp || null);
  const [loadingTrilhas, setLoadingTrilhas] = useState(!trilhaProp);

  // Cronogramas
  const [cronogramas, setCronogramas] = useState<CronogramaDTO[]>([]);
  const [selectedCronogramaId, setSelectedCronogramaId] = useState<string>('');
  const [loadingCronogramas, setLoadingCronogramas] = useState(false);

  // Blocos paginados
  const [paginaBlocos, setPaginaBlocos] = useState<PaginaBlocosDTO | null>(null);
  const [page, setPage] = useState(1);
  const [concluidoFiltro, setConcluidoFiltro] = useState<string>('');
  const [loadingBlocos, setLoadingBlocos] = useState(false);

  // Form novo cronograma
  const [showNovoForm, setShowNovoForm] = useState(false);
  const [modalidade, setModalidade] = useState<ModalidadeCronograma>('SEQUENCIAL');
  const [tipoPeriodo, setTipoPeriodo] = useState<string>('UM_MES');
  const [diasAtivosSemana, setDiasAtivosSemana] = useState<number[]>([1, 2, 3, 4, 5]);
  const [blocosPorDia, setBlocosPorDia] = useState<number>(4);
  const [duracaoBlocoMinutos, setDuracaoBlocoMinutos] = useState<number>(50);
  const [dataInicio, setDataInicio] = useState<string>(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });
  const [dataFim, setDataFim] = useState<string>(() => {
    const future = new Date();
    future.setDate(future.getDate() + 30);
    return future.toISOString().split('T')[0];
  });
  const [savingCronograma, setSavingCronograma] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Disponibilidade semanal & Sobrecarga
  const [disponibilidade, setDisponibilidade] = useState<DisponibilidadeSemanalDTO | null>(null);

  // Atualizar quando trilhaProp mudar
  useEffect(() => {
    if (trilhaProp) {
      setSelectedTrilhaId(trilhaProp.id);
      setSelectedTrilhaDetalhe(trilhaProp);
    }
  }, [trilhaProp]);

  // Carregar lista de trilhas se não fornecida por prop
  useEffect(() => {
    async function carregarTrilhas() {
      try {
        setLoadingTrilhas(true);
        const lista = await EstudosService.listarTrilhas();
        setTrilhas(lista);
        if (!selectedTrilhaId && lista.length > 0) {
          setSelectedTrilhaId(lista[0].id);
        }
      } catch (err) {
        console.error('Erro ao carregar trilhas:', err);
        setErrorMessage('Não foi possível carregar suas trilhas.');
      } finally {
        setLoadingTrilhas(false);
      }
    }
    if (!trilhaProp) {
      carregarTrilhas();
    }
  }, [trilhaProp, selectedTrilhaId]);

  // Carregar detalhe da trilha selecionada (para ter disciplinas atualizadas)
  useEffect(() => {
    async function carregarDetalhe() {
      if (!selectedTrilhaId) {
        setSelectedTrilhaDetalhe(null);
        return;
      }
      if (trilhaProp && trilhaProp.id === selectedTrilhaId && trilhaProp.disciplinas?.length) {
        setSelectedTrilhaDetalhe(trilhaProp);
        return;
      }
      try {
        const detalhe = await EstudosService.buscarTrilha(selectedTrilhaId);
        setSelectedTrilhaDetalhe(detalhe);
      } catch (err) {
        console.error('Erro ao buscar detalhe da trilha:', err);
      }
    }
    carregarDetalhe();
  }, [selectedTrilhaId, trilhaProp]);

  // Carregar disponibilidade semanal do usuário
  useEffect(() => {
    async function carregarDisponibilidade() {
      try {
        const disp = await EstudosService.obterDisponibilidadeSemanal();
        setDisponibilidade(disp);
      } catch (err) {
        console.error('Erro ao carregar disponibilidade semanal:', err);
      }
    }
    carregarDisponibilidade();
  }, []);

  // Carregar cronogramas quando a trilha selecionada mudar
  const carregarCronogramas = useCallback(async (trilhaId: string) => {
    if (!trilhaId) {
      setCronogramas([]);
      setSelectedCronogramaId('');
      return;
    }
    try {
      setLoadingCronogramas(true);
      const lista = await EstudosService.listarCronogramas(trilhaId);
      setCronogramas(lista);
      if (lista.length > 0) {
        const ativo = lista.find(c => c.ativo);
        setSelectedCronogramaId(ativo ? ativo.id : lista[0].id);
      } else {
        setSelectedCronogramaId('');
      }
    } catch (err) {
      console.error('Erro ao carregar cronogramas:', err);
      setErrorMessage('Erro ao buscar cronogramas da trilha.');
    } finally {
      setLoadingCronogramas(false);
    }
  }, []);

  useEffect(() => {
    if (selectedTrilhaId) {
      carregarCronogramas(selectedTrilhaId);
    }
  }, [selectedTrilhaId, carregarCronogramas]);

  // Carregar blocos quando o cronograma selecionado ou página/filtro mudar
  const carregarBlocos = useCallback(
    async (trilhaId: string, cronogramaId: string, p: number, filtroConcluido?: string) => {
      if (!trilhaId || !cronogramaId) {
        setPaginaBlocos(null);
        return;
      }
      try {
        setLoadingBlocos(true);
        const params: {
          pagina: number;
          tamanho: number;
          concluido?: boolean;
        } = {
          pagina: p,
          tamanho: 20,
        };
        if (filtroConcluido === 'true') params.concluido = true;
        if (filtroConcluido === 'false') params.concluido = false;

        const res = await EstudosService.listarBlocosCronograma(trilhaId, cronogramaId, params);
        setPaginaBlocos(res);
      } catch (err) {
        console.error('Erro ao carregar blocos do cronograma:', err);
        setErrorMessage('Erro ao listar blocos do cronograma.');
      } finally {
        setLoadingBlocos(false);
      }
    },
    []
  );

  useEffect(() => {
    if (selectedTrilhaId && selectedCronogramaId) {
      carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, concluidoFiltro);
    } else {
      setPaginaBlocos(null);
    }
  }, [selectedTrilhaId, selectedCronogramaId, page, concluidoFiltro, carregarBlocos]);

  // Modalidades já utilizadas na trilha
  const modalidadesUsadas = useMemo(() => {
    return new Set(cronogramas.map(c => c.modalidade));
  }, [cronogramas]);

  // Verificação de sobrecarga não-bloqueante
  const alertaSobrecarga = useMemo(() => {
    if (!disponibilidade || !showNovoForm) return null;
    const blocosSemanaisNovos = diasAtivosSemana.length * blocosPorDia;
    const minutosSemanaisNovos = blocosSemanaisNovos * duracaoBlocoMinutos;

    const totalDisponivel = disponibilidade.disponibilidadeSemanalMinutos.reduce((a, b) => a + b, 0);
    const totalCargaAtiva = disponibilidade.cargaAtivaMinutosPorDia.reduce((a, b) => a + b, 0);

    if (totalCargaAtiva + minutosSemanaisNovos > totalDisponivel) {
      const horasNovas = (minutosSemanaisNovos / 60).toFixed(1);
      const horasAtivas = (totalCargaAtiva / 60).toFixed(1);
      const horasDisp = (totalDisponivel / 60).toFixed(1);
      return `Aviso de Capacidade: Este planejamento demandará ~${horasNovas}h/semana. Somado aos cronogramas ativos (~${horasAtivas}h), o total excede sua disponibilidade semanal configurada (${horasDisp}h). Você ainda pode prosseguir.`;
    }
    return null;
  }, [disponibilidade, showNovoForm, diasAtivosSemana, blocosPorDia, duracaoBlocoMinutos]);

  const handleToggleDia = (diaId: number) => {
    setDiasAtivosSemana(prev =>
      prev.includes(diaId) ? prev.filter(d => d !== diaId) : [...prev, diaId]
    );
  };

  const handleCriarCronograma = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (cronogramas.length >= 3) {
      setErrorMessage('Limite de 3 cronogramas por trilha atingido. Exclua um para criar outro.');
      return;
    }

    if (modalidadesUsadas.has(modalidade)) {
      setErrorMessage(`A modalidade ${modalidade} já está em uso nesta trilha.`);
      return;
    }

    if (diasAtivosSemana.length === 0) {
      setErrorMessage('Selecione ao menos um dia da semana.');
      return;
    }

    if (blocosPorDia <= 0 || blocosPorDia > 24) {
      setErrorMessage('A quantidade de blocos por dia deve ser entre 1 e 24.');
      return;
    }

    if (!dataInicio || !dataFim || dataFim < dataInicio) {
      setErrorMessage('Intervalo de datas inválido.');
      return;
    }

    const disciplinasIds = selectedTrilhaDetalhe?.disciplinas?.map(d => d.id) || [];
    if (disciplinasIds.length === 0) {
      setErrorMessage('Esta trilha não possui disciplinas cadastradas para compor o cronograma.');
      return;
    }

    const payload: CriarCronogramaPayload = {
      modalidade,
      duracaoBlocoMinutos,
      tipoPeriodo,
      dataInicio,
      dataFim,
      diasAtivosSemana,
      blocosPorDia,
      disciplinasParticipantesIds: disciplinasIds,
    };

    try {
      setSavingCronograma(true);
      const novo = await EstudosService.criarCronograma(selectedTrilhaId, payload);
      setSuccessMessage('Cronograma gerado com sucesso e ativado!');
      setShowNovoForm(false);
      await carregarCronogramas(selectedTrilhaId);
      setSelectedCronogramaId(novo.id);
      setPage(1);

      if (onTrilhaAtualizada && selectedTrilhaDetalhe) {
        const atualizada = await EstudosService.buscarTrilha(selectedTrilhaId);
        onTrilhaAtualizada(atualizada);
      }
    } catch (err: any) {
      console.error('Erro ao gerar cronograma:', err);
      if (err.message && err.message.includes('DUPLICATE_MODALITY')) {
        setErrorMessage('Esta trilha já possui um cronograma com a modalidade selecionada.');
      } else if (err.message && err.message.includes('SCHEDULE_GENERATION_IN_PROGRESS')) {
        setErrorMessage('Existe outra geração de cronograma em andamento para esta trilha. Tente novamente em alguns segundos.');
      } else {
        setErrorMessage(err.message || 'Erro ao gerar cronograma.');
      }
    } finally {
      setSavingCronograma(false);
    }
  };

  const handleAtivarCronograma = async (cronogramaId: string) => {
    try {
      await EstudosService.ativarCronograma(selectedTrilhaId, cronogramaId);
      setSuccessMessage('Cronograma definido como ativo!');
      await carregarCronogramas(selectedTrilhaId);
      if (onTrilhaAtualizada && selectedTrilhaDetalhe) {
        const atualizada = await EstudosService.buscarTrilha(selectedTrilhaId);
        onTrilhaAtualizada(atualizada);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Erro ao ativar cronograma.');
    }
  };

  const handleExcluirCronograma = async (cronogramaId: string) => {
    if (!window.confirm('Tem certeza que deseja excluir este cronograma e todos os seus blocos?')) {
      return;
    }
    try {
      await EstudosService.excluirCronograma(selectedTrilhaId, cronogramaId);
      setSuccessMessage('Cronograma excluído.');
      await carregarCronogramas(selectedTrilhaId);
      if (onTrilhaAtualizada && selectedTrilhaDetalhe) {
        const atualizada = await EstudosService.buscarTrilha(selectedTrilhaId);
        onTrilhaAtualizada(atualizada);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Erro ao excluir cronograma.');
    }
  };

  const handleToggleConclusaoBloco = async (bloco: BlocoCronogramaItemDTO) => {
    if (bloco.concluido) {
      try {
        await sessaoGlobalService.concluirBloco(bloco.id, false);
        if (selectedTrilhaId && selectedCronogramaId) {
          carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, concluidoFiltro);
        }
      } catch (err: any) {
        setErrorMessage(err.message || 'Erro ao atualizar status do bloco.');
      }
    } else {
      abrirModal({
        modo: 'BLOCO_DIRETO',
        trilhaId: bloco.trilhaId,
        disciplinaId: bloco.disciplinaId,
        blocoId: bloco.id,
        duracaoInicialSegundos: bloco.duracaoMinutos * 60,
        disciplinaNome: bloco.disciplinaNome,
        disciplinaCor: bloco.disciplinaCor,
        trilhaNome: selectedTrilhaDetalhe?.nome,
        onSuccess: () => {
          if (selectedTrilhaId && selectedCronogramaId) {
            carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, concluidoFiltro);
          }
        }
      });
    }
  };

  const handleConcluirAtrasados = async () => {
    if (!selectedCronogramaId) return;
    if (!window.confirm('Deseja marcar como concluídos todos os blocos pendentes com data até hoje?')) {
      return;
    }
    try {
      const res = await EstudosService.concluirBlocosAtrasados(selectedTrilhaId, selectedCronogramaId);
      setSuccessMessage(`${res.totalConcluidos} blocos atrasados foram concluídos com sucesso!`);
      if (selectedTrilhaId && selectedCronogramaId) {
        carregarBlocos(selectedTrilhaId, selectedCronogramaId, page, concluidoFiltro);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Erro ao concluir blocos atrasados.');
    }
  };

  const handleIniciarEstudoBloco = (bloco: BlocoCronogramaItemDTO) => {
    if (onIniciarEstudo) {
      const reg: RegistroPlanejamentoPendente = {
        requestId: `bloco-${bloco.id}-${Date.now()}`,
        trilhaId: bloco.trilhaId,
        cicloId: bloco.cronogramaId,
        ocorrenciaId: bloco.id,
        disciplinaId: bloco.disciplinaId,
        duracaoMinutos: bloco.duracaoMinutos,
      };
      onIniciarEstudo(bloco.disciplinaId, reg);
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Planejamento de Cronogramas</h1>
          <p className={styles.subtitle}>
            Gere e gerencie cronogramas de estudo estruturados, com controle de modalidade e blocos diários.
          </p>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={handleConcluirAtrasados}
            disabled={!selectedCronogramaId}
            title="Conclui em lote todos os blocos com data até hoje que estejam pendentes"
          >
            Concluir Blocos Atrasados
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => {
              setShowNovoForm(prev => !prev);
              setErrorMessage(null);
              setSuccessMessage(null);
            }}
            disabled={cronogramas.length >= 3}
          >
            {showNovoForm ? 'Cancelar' : '+ Novo Cronograma'}
          </button>
        </div>
      </header>

      {errorMessage && <div className={styles.errorMessage}>{errorMessage}</div>}
      {successMessage && <div className={styles.successMessage}>{successMessage}</div>}

      {/* Seletor de Trilha (exibido apenas se houver mais de uma trilha cadastrada) */}
      <section className={styles.sectionCard}>
        <div className={styles.filterRow}>
          <label htmlFor="trilhaSelect" className={styles.label}>
            Trilha:
          </label>
          <select
            id="trilhaSelect"
            className={styles.select}
            value={selectedTrilhaId}
            onChange={e => setSelectedTrilhaId(e.target.value)}
            disabled={loadingTrilhas}
          >
            {trilhas.map(t => (
              <option key={t.id} value={t.id}>
                {t.nome} ({t.totalDisciplinas || t.disciplinas?.length || 0} disciplinas)
              </option>
            ))}
          </select>
          {selectedTrilhaDetalhe && (
            <span className={styles.badgeMuted}>
              {cronogramas.length} de 3 cronogramas cadastrados
            </span>
          )}
          {onIrParaDisciplinas && (
            <button
              type="button"
              className={styles.secondaryButton}
              style={{ marginLeft: 'auto', fontSize: '13px', padding: '6px 12px' }}
              onClick={onIrParaDisciplinas}
            >
              Gerenciar Disciplinas &rarr;
            </button>
          )}
        </div>
      </section>

      {/* Formulário de Novo Cronograma */}
      {showNovoForm && (
        <section className={styles.formCard}>
          <h2 className={styles.formTitle}>Novo Cronograma</h2>
          <form onSubmit={handleCriarCronograma}>
            {alertaSobrecarga && (
              <div className={styles.overloadAlert}>{alertaSobrecarga}</div>
            )}

            <div className={styles.formGrid}>
              <div className={styles.formGroup}>
                <label className={styles.label}>Modalidade</label>
                <select
                  className={styles.select}
                  value={modalidade}
                  onChange={e => setModalidade(e.target.value as ModalidadeCronograma)}
                >
                  <option value="SEQUENCIAL" disabled={modalidadesUsadas.has('SEQUENCIAL')}>
                    Sequencial {modalidadesUsadas.has('SEQUENCIAL') ? '(Já utilizado)' : ''}
                  </option>
                  <option value="PONDERADO" disabled={modalidadesUsadas.has('PONDERADO')}>
                    Ponderado por Peso {modalidadesUsadas.has('PONDERADO') ? '(Já utilizado)' : ''}
                  </option>
                  <option value="MANUAL" disabled={modalidadesUsadas.has('MANUAL')}>
                    Manual {modalidadesUsadas.has('MANUAL') ? '(Já utilizado)' : ''}
                  </option>
                </select>
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Período</label>
                <select
                  className={styles.select}
                  value={tipoPeriodo}
                  onChange={e => setTipoPeriodo(e.target.value)}
                >
                  <option value="UMA_SEMANA">Uma Semana</option>
                  <option value="UM_MES">Um Mês</option>
                  <option value="TRES_MESES">Três Meses</option>
                  <option value="ATE_PROVA">Até a Prova</option>
                </select>
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Blocos por Dia (máx 24)</label>
                <input
                  type="number"
                  className={styles.input}
                  min={1}
                  max={24}
                  value={blocosPorDia}
                  onChange={e => setBlocosPorDia(parseInt(e.target.value) || 1)}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Duração do Bloco (minutos)</label>
                <input
                  type="number"
                  className={styles.input}
                  min={10}
                  max={240}
                  step={5}
                  value={duracaoBlocoMinutos}
                  onChange={e => setDuracaoBlocoMinutos(parseInt(e.target.value) || 50)}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Data Início</label>
                <input
                  type="date"
                  className={styles.input}
                  value={dataInicio}
                  onChange={e => setDataInicio(e.target.value)}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Data Término (máx 366 dias)</label>
                <input
                  type="date"
                  className={styles.input}
                  value={dataFim}
                  onChange={e => setDataFim(e.target.value)}
                />
              </div>
            </div>

            <div className={styles.formGroup} style={{ marginTop: '1rem' }}>
              <label className={styles.label}>Dias da Semana para Estudo:</label>
              <div className={styles.diasCheckboxGroup}>
                {DIAS_SEMANA.map(dia => (
                  <label key={dia.id} className={styles.diaCheckboxLabel}>
                    <input
                      type="checkbox"
                      checked={diasAtivosSemana.includes(dia.id)}
                      onChange={() => handleToggleDia(dia.id)}
                    />
                    <span>{dia.label}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.secondaryButton}
                onClick={() => setShowNovoForm(false)}
                disabled={savingCronograma}
              >
                Cancelar
              </button>
              <button
                type="submit"
                className={styles.primaryButton}
                disabled={savingCronograma}
              >
                {savingCronograma ? 'Gerando Blocos...' : 'Gerar Cronograma'}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Lista de Cronogramas */}
      <section className={styles.sectionCard}>
        <h2 className={styles.sectionTitle}>Cronogramas da Trilha</h2>
        {loadingCronogramas ? (
          <p className={styles.loadingText}>Carregando cronogramas...</p>
        ) : cronogramas.length === 0 ? (
          <p className={styles.emptyText}>
            Nenhum cronograma cadastrado nesta trilha. Clique em "+ Novo Cronograma" para gerar um.
          </p>
        ) : (
          <div className={styles.cronogramasGrid}>
            {cronogramas.map(c => {
              const isSelected = c.id === selectedCronogramaId;
              return (
                <div
                  key={c.id}
                  className={`${styles.cronogramaCard} ${isSelected ? styles.selectedCard : ''}`}
                  onClick={() => {
                    setSelectedCronogramaId(c.id);
                    setPage(1);
                  }}
                >
                  <div className={styles.cronogramaHeader}>
                    <div className={styles.cronogramaTitleRow}>
                      <span className={styles.cronogramaNome}>{c.modalidade}</span>
                      {c.ativo && <span className={styles.badgeActive}>Ativo</span>}
                    </div>
                    <span className={styles.badgeModalidade}>{c.tipoPeriodo}</span>
                  </div>

                  <div className={styles.cronogramaMeta}>
                    <div>
                      <strong>Período:</strong> {c.dataInicio} até {c.dataFim}
                    </div>
                    <div>
                      <strong>Blocos/dia:</strong> {c.blocosPorDia || '—'} ({c.duracaoBlocoMinutos} min cada)
                    </div>
                    <div>
                      <strong>Versão:</strong> v{c.versaoAtiva} | {c.quantidadeBlocos} blocos gerados
                    </div>
                  </div>

                  <div className={styles.cronogramaActions} onClick={e => e.stopPropagation()}>
                    {!c.ativo && (
                      <button
                        type="button"
                        className={styles.actionButtonSmall}
                        onClick={() => handleAtivarCronograma(c.id)}
                      >
                        Tornar Ativo
                      </button>
                    )}
                    <button
                      type="button"
                      className={styles.actionDangerSmall}
                      onClick={() => handleExcluirCronograma(c.id)}
                    >
                      Excluir
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Visualização de Blocos do Cronograma Selecionado */}
      {selectedCronogramaId && (
        <section className={styles.sectionCard}>
          <div className={styles.blocosHeader}>
            <div>
              <h2 className={styles.sectionTitle}>Blocos do Cronograma Selecionado</h2>
              <p className={styles.subtitle}>
                {paginaBlocos?.totalItens ?? 0} blocos no total ({paginaBlocos?.totalPaginas ?? 0} páginas)
              </p>
            </div>
            <div className={styles.blocosFiltros}>
              <select
                className={styles.select}
                value={concluidoFiltro}
                onChange={e => {
                  setConcluidoFiltro(e.target.value);
                  setPage(1);
                }}
              >
                <option value="">Todos os status</option>
                <option value="false">Pendentes</option>
                <option value="true">Concluídos</option>
              </select>
            </div>
          </div>

          {loadingBlocos ? (
            <p className={styles.loadingText}>Carregando blocos...</p>
          ) : !paginaBlocos || paginaBlocos.itens.length === 0 ? (
            <p className={styles.emptyText}>Nenhum bloco encontrado para os filtros selecionados.</p>
          ) : (
            <div className={styles.tableResponsive}>
              <table className={styles.blocosTable}>
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Ordem</th>
                    <th>Disciplina</th>
                    <th>Duração</th>
                    <th>Status</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {paginaBlocos.itens.map(bloco => {
                    return (
                      <tr key={bloco.id} className={bloco.concluido ? styles.blocoConcluidoRow : ''}>
                        <td>{bloco.data}</td>
                        <td>#{bloco.ordem}</td>
                        <td>
                          <span
                            style={{
                              display: 'inline-block',
                              width: 10,
                              height: 10,
                              borderRadius: '50%',
                              backgroundColor: bloco.disciplinaCor || '#6366f1',
                              marginRight: 6,
                            }}
                          />
                          <strong>{bloco.disciplinaNome}</strong>
                        </td>
                        <td>{bloco.duracaoMinutos} min</td>
                        <td>
                          <span
                            className={
                              bloco.concluido
                                ? styles.statusBadgeConcluido
                                : styles.statusBadgePendente
                            }
                          >
                            {bloco.concluido ? 'CONCLUÍDO' : bloco.statusData}
                          </span>
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: '6px' }}>
                            <button
                              type="button"
                              className={
                                bloco.concluido ? styles.actionUndoButton : styles.actionCheckButton
                              }
                              onClick={() => handleToggleConclusaoBloco(bloco)}
                            >
                              {bloco.concluido ? 'Desmarcar' : 'Concluir'}
                            </button>
                            {onIniciarEstudo && !bloco.concluido && (
                              <button
                                type="button"
                                className={styles.actionButtonSmall}
                                onClick={() => handleIniciarEstudoBloco(bloco)}
                              >
                                Estudar
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Paginação */}
              <div className={styles.paginationBar}>
                <button
                  type="button"
                  className={styles.paginationButton}
                  disabled={page <= 1}
                  onClick={() => setPage(prev => Math.max(1, prev - 1))}
                >
                  &larr; Anterior
                </button>
                <span className={styles.paginationLabel}>
                  Página {page} de {paginaBlocos.totalPaginas || 1}
                </span>
                <button
                  type="button"
                  className={styles.paginationButton}
                  disabled={page >= (paginaBlocos.totalPaginas || 1)}
                  onClick={() => setPage(prev => prev + 1)}
                >
                  Próxima &rarr;
                </button>
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
