import React, { useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  Plus,
  CheckCircle2,
  Award,
  Copy
} from 'lucide-react';
import {
  Button,
  Badge,
  DisciplineLabel,
  PageBanner,
  Modal,
  FormModal,
  Textarea,
  ToggleGroup,
  useToast
} from '@shared/design-system';
import { CriarDisciplinaRequest, Disciplina, TrilhaDetalheDTO } from '../types/trilhara';
import { EstudosService } from '../services/estudosService';
import { iniciarHandoffParaApp, obterUrlAppNotas } from '@shared/auth';
import { DisciplinaPesoButton } from '../components/DisciplinaPesoButton/DisciplinaPesoButton';
import { EmptyJourneyState } from '../components/EmptyJourneyState/EmptyJourneyState';
import { DisciplinasTopicosModal } from '../components/DisciplinasTopicosModal/DisciplinasTopicosModal';
import { obterCorDisponivel } from '../utils/coresDisciplina';
import {
  calcularProgressoTrilha,
  formatarPercentualEdital,
  isDisciplinaConcluida
} from '../utils/progressoTrilha';
import styles from './DisciplinasView.module.css';

export interface DisciplinasViewProps {
  trilha: TrilhaDetalheDTO;
  onTrilhaAtualizada: (trilha: TrilhaDetalheDTO) => void;
}

export const DisciplinasView: React.FC<DisciplinasViewProps> = ({
  trilha,
  onTrilhaAtualizada
}) => {
  const [filtroStatus, setFiltroStatus] = useState<'todos' | 'pendentes' | 'concluidos'>('todos');
  const [renomeacaoPendente, setRenomeacaoPendente] = useState<{
    original: Disciplina;
    editada: Disciplina;
  } | null>(null);

  const [modalDisciplinasLoteAberto, setModalDisciplinasLoteAberto] = useState(false);
  const [formatoDisciplinasLote, setFormatoDisciplinasLote] = useState<'txt' | 'json'>('txt');
  const [disciplinasLoteRaw, setDisciplinasLoteRaw] = useState('');
  const [erroDisciplinasLote, setErroDisciplinasLote] = useState('');
  const [salvandoDisciplinasLote, setSalvandoDisciplinasLote] = useState(false);
  const [resumoQuestoes, setResumoQuestoes] = useState<
    Record<string, { questoes: number; acertos: number }>
  >({});

  const [modalNovaDisciplinaAberto, setModalNovaDisciplinaAberto] = useState(false);
  const [salvandoNovaDisciplina, setSalvandoNovaDisciplina] = useState(false);
  const [disciplinaEmEdicaoId, setDisciplinaEmEdicaoId] = useState<string | null>(null);
  const [salvandoDisciplina, setSalvandoDisciplina] = useState(false);
  const [excluindoDisciplina, setExcluindoDisciplina] = useState(false);
  const toast = useToast();

  useEffect(() => {
    let active = true;
    EstudosService.obterEstatisticas(trilha.id)
      .then((estatisticas) => {
        if (!active) return;
        setResumoQuestoes(
          (estatisticas.porDisciplina || []).reduce<Record<string, { questoes: number; acertos: number }>>(
            (resumo, estatistica) => {
              resumo[estatistica.disciplinaId] = {
                questoes: estatistica.totalQuestoes,
                acertos: estatistica.totalAcertos
              };
              return resumo;
            },
            {}
          )
        );
      })
      .catch(() => {
        if (active) setResumoQuestoes({});
      });

    return () => {
      active = false;
    };
  }, [trilha.id]);

  /** marcar/desmarcar tópico é otimista com rollback + toast em falha. */
  const aplicarProgressoOtimista = (
    ids: ReadonlySet<string>,
    disciplinaAlteradaId?: string,
    concluidaExplicita?: boolean
  ) => {
    const topicosConcluidosIds = [...ids];
    const disciplinasAtualizadas = trilha.disciplinas.map((disciplina) => {
      const concluidos = disciplina.topicos.filter((t) => ids.has(t.id)).length;
      const total = disciplina.topicos.length;
      let concluida = Boolean(disciplina.concluida);
      if (disciplina.id === disciplinaAlteradaId && concluidaExplicita !== undefined) {
        concluida = concluidaExplicita;
      } else if (disciplina.id === disciplinaAlteradaId && total > 0) {
        concluida = concluidos === total;
      }
      return {
        ...disciplina,
        concluida,
        topicosConcluidos: concluidos,
        percentualProgresso: total > 0 ? (concluidos / total) * 100 : 0,
        topicos: disciplina.topicos.map((t) => ({ ...t, concluido: ids.has(t.id) }))
      };
    });
    const resumo = calcularProgressoTrilha(disciplinasAtualizadas, ids);

    onTrilhaAtualizada({
      ...trilha,
      topicosConcluidosIds,
      totalDisciplinas: resumo.totalDisciplinas,
      disciplinasConcluidas: resumo.disciplinasConcluidas,
      temDisciplinaSemTopico: resumo.temDisciplinaSemTopico,
      totalTopicos: resumo.totalTopicos,
      topicosConcluidos: resumo.topicosConcluidos,
      percentualProgresso: resumo.percentualProgresso,
      disciplinas: disciplinasAtualizadas
    });
  };

  const handleToggleTopicoCheck = async (topicoId: string, concluido: boolean) => {
    const trilhaAnterior = trilha;
    const anterior = new Set<string>(trilha.topicosConcluidosIds ?? []);
    const otimista = new Set<string>(anterior);
    if (concluido) {
      otimista.add(topicoId);
    } else {
      otimista.delete(topicoId);
    }
    const disciplinaDona = trilha.disciplinas.find((d) =>
      d.topicos.some((t) => t.id === topicoId)
    );
    // Aplica imediatamente (otimista) e reverte se a API falhar.
    aplicarProgressoOtimista(otimista, disciplinaDona?.id);

    try {
      if (concluido) {
        await EstudosService.marcarTopicoConcluido(trilha.id, topicoId);
      } else {
        await EstudosService.desmarcarTopicoConcluido(trilha.id, topicoId);
      }
    } catch (err) {
      onTrilhaAtualizada(trilhaAnterior);
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar o assunto.');
    }
  };

  const handleToggleConclusaoDisciplina = async (disciplinaId: string, concluida: boolean) => {
    const disciplina = trilha.disciplinas.find((item) => item.id === disciplinaId);
    if (!disciplina) return;

    const trilhaAnterior = trilha;
    const otimista = new Set<string>(trilha.topicosConcluidosIds ?? []);

    for (const topico of disciplina.topicos) {
      if (concluida) {
        otimista.add(topico.id);
      } else {
        otimista.delete(topico.id);
      }
    }

    aplicarProgressoOtimista(otimista, disciplinaId, concluida);

    try {
      await EstudosService.atualizarConclusaoDisciplina(trilha.id, disciplinaId, concluida);
    } catch (err) {
      onTrilhaAtualizada(trilhaAnterior);
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar a conclusão da disciplina.');
    }
  };

  const criarNovaDisciplina = async (payload: CriarDisciplinaRequest) => {
    if (salvandoNovaDisciplina) return;
    setSalvandoNovaDisciplina(true);
    try {
      await EstudosService.criarDisciplina(trilha.id, payload);
      const detalhe = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(detalhe);
      setModalNovaDisciplinaAberto(false);
      toast.success('Disciplina adicionada com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar a disciplina.');
    } finally {
      setSalvandoNovaDisciplina(false);
    }
  };

  const executarSalvarDisciplinaEditada = async (
    disciplinaParaSalvar: Disciplina,
    zerarHistorico: boolean
  ) => {
    if (salvandoDisciplina) return;
    setSalvandoDisciplina(true);

    try {
      const original = trilha.disciplinas.find((d) => d.id === disciplinaParaSalvar.id);
      const idsPersistidos = new Set((original?.topicos ?? []).map((t) => t.id));

      if (zerarHistorico) {
        await EstudosService.excluirDisciplina(trilha.id, disciplinaParaSalvar.id);
        await EstudosService.criarDisciplina(trilha.id, {
          nome: disciplinaParaSalvar.nome,
          cor: disciplinaParaSalvar.cor,
          peso: disciplinaParaSalvar.peso,
          topicos: disciplinaParaSalvar.topicos.map((t, idx) => ({
            nome: t.nome,
            ordem: idx + 1
          }))
        });
      } else {
        await EstudosService.atualizarDisciplina(trilha.id, disciplinaParaSalvar.id, {
          nome: disciplinaParaSalvar.nome,
          cor: disciplinaParaSalvar.cor,
          peso: disciplinaParaSalvar.peso,
          topicos: disciplinaParaSalvar.topicos.map((t, idx) => ({
            ...(t.id && idsPersistidos.has(t.id) ? { id: t.id } : {}),
            nome: t.nome,
            ordem: idx + 1
          }))
        });
      }
      const detalhe = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(detalhe);
      setDisciplinaEmEdicaoId(null);
      setRenomeacaoPendente(null);
      toast.success(
        zerarHistorico
          ? 'Disciplina renomeada e histórico reiniciado com sucesso.'
          : 'Disciplina atualizada com sucesso.'
      );
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar as alterações.');
    } finally {
      setSalvandoDisciplina(false);
    }
  };

  const salvarDisciplinaEditada = async (disciplina: Disciplina) => {
    const original = trilha.disciplinas.find((item) => item.id === disciplina.id);
    if (original && original.nome.trim() !== disciplina.nome.trim()) {
      setRenomeacaoPendente({ original, editada: disciplina });
      return;
    }
    await executarSalvarDisciplinaEditada(disciplina, false);
  };

  const excluirDisciplina = async (disciplina: Disciplina) => {
    if (excluindoDisciplina) return;
    setExcluindoDisciplina(true);

    try {
      await EstudosService.excluirDisciplina(trilha.id, disciplina.id);
      const detalhe = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(detalhe);
      setDisciplinaEmEdicaoId(null);
      toast.success('Disciplina excluída com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a disciplina.');
    } finally {
      setExcluindoDisciplina(false);
    }
  };

  const obterResumoQuestoes = (disciplinaId: string) => {
    const resumo = resumoQuestoes[disciplinaId];
    const percentual = resumo && resumo.questoes > 0
      ? Math.round((resumo.acertos / resumo.questoes) * 100)
      : 0;
    return {
      percentual,
      variant: percentual === 0
        ? 'secondary' as const
        : percentual >= 80
          ? 'success' as const
          : percentual >= 60
            ? 'warning' as const
            : 'danger' as const
    };
  };

  const abrirNovaDisciplina = () => {
    setDisciplinaEmEdicaoId(null);
    setModalNovaDisciplinaAberto(true);
  };

  const abrirEdicaoDisciplina = (disciplina: Disciplina) => {
    setModalNovaDisciplinaAberto(false);
    setDisciplinaEmEdicaoId(disciplina.id);
  };

  /**
   * "Abrir no Notas": SSO para o app de Notas já filtrado pela trilha.
   *
   * O handoff leva o access token para o outro subdomínio; o parâmetro `origemTrilhaId` é o
   * que `NotasApp` lê na carga inicial para listar só o caderno vinculado a esta trilha.
   * Sem trilha carregada, o botão é inerte — não inventamos um ID.
   */
  const abrirNotasDaTrilha = async () => {
    if (!trilha.id) return;
    try {
      const separador = obterUrlAppNotas().includes('?') ? '&' : '?';
      const destino = `${obterUrlAppNotas()}${separador}origemTrilhaId=${encodeURIComponent(trilha.id)}`;
      const urlComSessao = await iniciarHandoffParaApp(destino);
      window.location.assign(urlComSessao);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível abrir o app de Notas.');
    }
  };

  const salvarDisciplinasLote = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    let entradas: Array<{ nome: string; assuntos: string[] }>;
    try {
      if (formatoDisciplinasLote === 'json') {
        const parsed: unknown = JSON.parse(disciplinasLoteRaw);
        if (!Array.isArray(parsed)) {
          throw new Error('O JSON deve ser uma lista de objetos com disciplina e assuntos.');
        }
        entradas = parsed.map((item, index) => {
          if (typeof item !== 'object' || item === null || Array.isArray(item)) {
            throw new Error(`O item ${index + 1} deve ser um objeto.`);
          }
          const registro = item as { disciplina?: unknown; assuntos?: unknown };
          if (typeof registro.disciplina !== 'string' || !registro.disciplina.trim()) {
            throw new Error(`O item ${index + 1} precisa conter uma disciplina.`);
          }
          if (!Array.isArray(registro.assuntos) ||
            registro.assuntos.some((assunto) => typeof assunto !== 'string' || !assunto.trim())) {
            throw new Error(`A disciplina ${index + 1} deve conter uma lista de assuntos.`);
          }
          return {
            nome: registro.disciplina.trim(),
            assuntos: registro.assuntos.map((assunto) => assunto.trim())
          };
        });
      } else {
        entradas = disciplinasLoteRaw
          .split(/\r?\n/)
          .map((nome) => nome.trim())
          .filter(Boolean)
          .map((nome) => ({ nome, assuntos: [] }));
      }
    } catch (error) {
      setErroDisciplinasLote(error instanceof Error ? error.message : 'Formato inválido.');
      return;
    }

    if (entradas.length === 0) {
      setErroDisciplinasLote('Informe pelo menos uma disciplina.');
      return;
    }
    if (entradas.some(({ nome }) => nome.length > 100)) {
      setErroDisciplinasLote('Cada disciplina deve ter no máximo 100 caracteres.');
      return;
    }
    if (entradas.some(({ assuntos }) => assuntos.some((assunto) => assunto.length > 500))) {
      setErroDisciplinasLote('Cada assunto deve ter no máximo 500 caracteres.');
      return;
    }

    setSalvandoDisciplinasLote(true);
    try {
      const criadas: Disciplina[] = [];
      const nomesAdicionados = new Set<string>(
        trilha.disciplinas.map((d) => d.nome.trim().toLocaleLowerCase())
      );
      const novasDisciplinas = entradas
        .filter(({ nome }) => {
          const chave = nome.toLocaleLowerCase();
          if (nomesAdicionados.has(chave)) return false;
          nomesAdicionados.add(chave);
          return true;
        })
        .map(({ nome, assuntos }) => {
          const disciplina: Disciplina = {
            id: '',
            nome,
            cor: obterCorDisponivel([...trilha.disciplinas, ...criadas]),
            peso: 1,
            ordem: trilha.disciplinas.length + criadas.length,
            topicos: assuntos.map((assunto) => ({ id: '', nome: assunto }))
          };
          criadas.push(disciplina);
          return disciplina;
        });
      for (const disc of novasDisciplinas) {
        await EstudosService.criarDisciplina(trilha.id, {
          nome: disc.nome,
          cor: disc.cor,
          peso: disc.peso,
          topicos: disc.topicos.map((t, idx) => ({
            nome: t.nome,
            ordem: idx + 1
          }))
        });
      }
      const atualizada = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(atualizada);
      setModalDisciplinasLoteAberto(false);
      toast.success(`${novasDisciplinas.length} disciplinas adicionadas com sucesso.`);
    } catch (error) {
      toast.error(error instanceof Error ? error : 'Não foi possível salvar as disciplinas.');
    } finally {
      setSalvandoDisciplinasLote(false);
    }
  };

  const disciplinaEmEdicao = useMemo(
    () => trilha.disciplinas.find((item) => item.id === disciplinaEmEdicaoId) ?? null,
    [trilha.disciplinas, disciplinaEmEdicaoId]
  );

  // Contagens gerais
  const concluidosSet = useMemo(
    () => new Set(trilha.topicosConcluidosIds ?? []),
    [trilha.topicosConcluidosIds]
  );
  const resumoProgresso = useMemo(
    () => calcularProgressoTrilha(trilha.disciplinas, concluidosSet),
    [trilha.disciplinas, concluidosSet]
  );

  const disciplinasFiltradas = useMemo(() => {
    if (filtroStatus === 'todos') {
      return trilha.disciplinas;
    }
    return trilha.disciplinas.filter((disc) => {
      const concluida = isDisciplinaConcluida(disc, concluidosSet);
      return filtroStatus === 'concluidos' ? concluida : !concluida;
    });
  }, [trilha.disciplinas, concluidosSet, filtroStatus]);

  const pctGeralTexto = formatarPercentualEdital(resumoProgresso.percentualProgresso);
  const totalQuestoesConsolidadas = Object.values(resumoQuestoes)
    .reduce((total, resumo) => total + resumo.questoes, 0);
  const totalAcertosConsolidados = Object.values(resumoQuestoes)
    .reduce((total, resumo) => total + resumo.acertos, 0);
  const pctAcertosConsolidado = totalQuestoesConsolidadas > 0
    ? Math.round((totalAcertosConsolidados / totalQuestoesConsolidadas) * 100)
    : null;
  const modeloDisciplinasJson = `[
  {
    "disciplina": "Direito Administrativo",
    "assuntos": ["Princípios fundamentais", "Direitos e garantias"]
  },
  {
    "disciplina": "Língua Portuguesa",
    "assuntos": ["Interpretação de texto"]
  }
]`;

  const semDisciplinas = trilha.disciplinas.length === 0;

  return (
    <div className={styles.container}>
      <PageBanner
        title="Disciplinas & Assuntos do Edital"
        description={
          semDisciplinas
            ? 'Acompanhe a cobertura do edital e organize as matérias da sua trilha.'
            : 'Acompanhe a cobertura do edital e marque os assuntos estudados de cada matéria.'
        }
        icon={<BookOpen size={26} />}
        actions={
          <div className={styles.pageActions}>
            <Button
              variant="primary"
              size="md"
              leftIcon={<Plus size={16} />}
              onClick={abrirNovaDisciplina}
            >
              Adicionar Disciplina
            </Button>
            <Button
              variant="secondary"
              size="md"
              onClick={() => {
                setDisciplinasLoteRaw('');
                setFormatoDisciplinasLote('txt');
                setErroDisciplinasLote('');
                setModalDisciplinasLoteAberto(true);
              }}
            >
              Adicionar em lote
            </Button>
            <Button variant="secondary" size="md" onClick={abrirNotasDaTrilha}>
              Abrir no Notas
            </Button>
          </div>
        }
      />

      {semDisciplinas ? (
        <EmptyJourneyState hasTrilha onPrimaryAction={abrirNovaDisciplina} />
      ) : (
        <>
          <div className={styles.toolbarRow}>
            <ToggleGroup<'todos' | 'pendentes' | 'concluidos'>
              className={styles.filterGroup}
              ariaLabel="Filtrar disciplinas por status"
              value={filtroStatus}
              onChange={setFiltroStatus}
              options={[
                { value: 'todos', label: 'Todos' },
                { value: 'pendentes', label: 'Pendentes' },
                { value: 'concluidos', label: 'Concluídos' }
              ] as const}
            />
            <div className={styles.toolbarRight}>
              <div className={styles.progressoText}>
                <CheckCircle2 size={15} className={styles.successIcon} />
                <span>
                  {resumoProgresso.totalTopicos === 0 ? (
                    <>
                      <strong>{resumoProgresso.disciplinasConcluidas}</strong> de{' '}
                      <strong>{resumoProgresso.totalDisciplinas}</strong> disciplinas ({pctGeralTexto})
                    </>
                  ) : resumoProgresso.temDisciplinaSemTopico ? (
                    <>
                      <strong>
                        {resumoProgresso.disciplinasConcluidas}/{resumoProgresso.totalDisciplinas}
                      </strong>{' '}
                      disciplinas ·{' '}
                      <strong>
                        {resumoProgresso.topicosConcluidos}/{resumoProgresso.totalTopicos}
                      </strong>{' '}
                      assuntos ({pctGeralTexto})
                    </>
                  ) : (
                    <>
                      <strong>{resumoProgresso.topicosConcluidos}</strong> de{' '}
                      <strong>{resumoProgresso.totalTopicos}</strong> assuntos ({pctGeralTexto})
                    </>
                  )}
                </span>
              </div>
              <div className={styles.progressoText}>
                <Award size={15} aria-hidden="true" />
                <span>
                  <strong>{totalAcertosConsolidados}</strong> de <strong>{totalQuestoesConsolidadas}</strong> questões
                  {' '}({pctAcertosConsolidado === null ? '—' : `${pctAcertosConsolidado}%`})
                </span>
              </div>
            </div>
          </div>

          {/* Lista de disciplinas */}
          <div className={styles.disciplinasList}>
            {disciplinasFiltradas.length === 0 ? (
              <p className={styles.calculationText} role="status">
                Nenhuma disciplina encontrada para o filtro selecionado.
              </p>
            ) : (
              disciplinasFiltradas.map((disc) => {
                const topicos = disc.topicos;
                const concluidosDisc = topicos.filter((t) => concluidosSet.has(t.id)).length;
                const pctDisc = topicos.length > 0 ? Math.round((concluidosDisc / topicos.length) * 100) : 0;
                const concluidaDisc = isDisciplinaConcluida(disc, concluidosSet);
                return (
                  <article
                    key={disc.id}
                    className={styles.disciplinaCard}
                  >
                    <div className={styles.discHeader}>
                      <div className={styles.discTitleBlock}>
                        <h3 className={styles.discNome}>
                          <button
                            type="button"
                            className={styles.discCardTrigger}
                            onClick={() => abrirEdicaoDisciplina(disc)}
                            aria-label={`Editar disciplina ${disc.nome}`}
                          >
                            <DisciplineLabel name={disc.nome} color={disc.cor} />
                          </button>
                        </h3>
                        <div className={styles.discMeta}>
                          <DisciplinaPesoButton
                            peso={disc.peso}
                            onClick={() => abrirEdicaoDisciplina(disc)}
                          />
                          <Badge
                            variant={concluidaDisc || pctDisc >= 60 ? 'success' : 'secondary'}
                            size="md"
                            className={styles.discCount}
                          >
                            {topicos.length === 0 && concluidaDisc
                              ? 'Concluída'
                              : `${concluidosDisc} / ${topicos.length} assuntos (${pctDisc}%)`}
                          </Badge>
                          <Badge
                            variant={obterResumoQuestoes(disc.id).variant}
                            size="md"
                            aria-label={`${obterResumoQuestoes(disc.id).percentual}% de acerto`}
                          >
                            {obterResumoQuestoes(disc.id).percentual}% de acerto
                          </Badge>
                        </div>
                      </div>
                    </div>
                  </article>
                );
              })
            )}
          </div>
        </>
      )}

      <FormModal
        isOpen={modalDisciplinasLoteAberto}
        onClose={() => {
          if (!salvandoDisciplinasLote) setModalDisciplinasLoteAberto(false);
        }}
        title="Cadastrar várias disciplinas"
        subtitle="No TXT, digite ou cole uma disciplina por linha. No JSON, digite ou cole a disciplina com a lista de  assuntos se tiver."
        icon={<BookOpen size={20} />}
        onSubmit={salvarDisciplinasLote}
        submitText="Salvar"
        loading={salvandoDisciplinasLote}
        error={erroDisciplinasLote}
      >
        <>
          <div className={styles.loteFormatAction}>
            <ToggleGroup<'txt' | 'json'>
              ariaLabel="Formato das disciplinas"
              value={formatoDisciplinasLote}
              onChange={(value) => {
                setFormatoDisciplinasLote(value);
                setErroDisciplinasLote('');
              }}
              options={[
                { value: 'txt', label: 'TXT' },
                { value: 'json', label: 'JSON' }
              ] as const}
            />
            <Button
              type="button"
              variant="ghost"
              size="sm"
              leftIcon={<Copy size={14} />}
              iconOnly
              title="Copiar modelo JSON"
              aria-label="Copiar modelo JSON"
              onClick={() => {
                void navigator.clipboard.writeText(modeloDisciplinasJson)
                  .then(() => {
                    setFormatoDisciplinasLote('json');
                    toast.success('Modelo JSON copiado.');
                  })
                  .catch(() => toast.error('Não foi possível copiar o modelo JSON.'));
              }}
            />
          </div>
          <Textarea
            label="Disciplinas"
            rows={16}
            value={disciplinasLoteRaw}
            onChange={(event) => {
              setDisciplinasLoteRaw(event.target.value);
              setErroDisciplinasLote('');
            }}
            placeholder={formatoDisciplinasLote === 'txt'
              ? 'Uma disciplina por linha\nDireito Administrativo\nLíngua Portuguesa'
              : modeloDisciplinasJson}
            className={styles.loteTextarea}
          />
        </>
      </FormModal>

      <DisciplinasTopicosModal
        isOpen={modalNovaDisciplinaAberto || Boolean(disciplinaEmEdicao)}
        mode={modalNovaDisciplinaAberto ? 'create' : 'edit'}
        initialDisciplinaId={modalNovaDisciplinaAberto ? null : (disciplinaEmEdicao?.id ?? null)}
        onClose={() => {
          setModalNovaDisciplinaAberto(false);
          setDisciplinaEmEdicaoId(null);
        }}
        title={modalNovaDisciplinaAberto ? 'Nova disciplina' : (disciplinaEmEdicao?.nome ?? 'Editar disciplina')}
        disciplinas={
          modalNovaDisciplinaAberto
            ? trilha.disciplinas
            : (disciplinaEmEdicao ? [disciplinaEmEdicao] : [])
        }
        progresso={concluidosSet}
        onToggleTopico={(topicoId, concluido) => void handleToggleTopicoCheck(topicoId, concluido)}
        onToggleConclusaoDisciplina={(disciplinaId, concluida) =>
          void handleToggleConclusaoDisciplina(disciplinaId, concluida)
        }
        editable
        savingDiscipline={modalNovaDisciplinaAberto ? salvandoNovaDisciplina : salvandoDisciplina}
        onCreateDiscipline={(payload) => void criarNovaDisciplina(payload)}
        onSaveDiscipline={(disciplina) => void salvarDisciplinaEditada(disciplina)}
        deletingDiscipline={excluindoDisciplina}
        onDeleteDiscipline={(disciplina) => void excluirDisciplina(disciplina)}
        mostrarPeso
      />

      {renomeacaoPendente && (
        <Modal
          isOpen={true}
          onClose={() => setRenomeacaoPendente(null)}
          title="Alteração de nome da disciplina"
          subtitle="Escolha como deseja tratar o histórico de estudos e horas desta matéria."
          footer={
            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', width: '100%' }}>
              <Button
                type="button"
                variant="secondary"
                onClick={() => setRenomeacaoPendente(null)}
                disabled={salvandoDisciplina}
              >
                Cancelar
              </Button>
              <Button
                type="button"
                variant="danger"
                onClick={() => void executarSalvarDisciplinaEditada(renomeacaoPendente.editada, true)}
                loading={salvandoDisciplina}
              >
                Zerar histórico
              </Button>
              <Button
                type="button"
                variant="primary"
                onClick={() => void executarSalvarDisciplinaEditada(renomeacaoPendente.editada, false)}
                loading={salvandoDisciplina}
              >
                Manter histórico
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '14px', lineHeight: '1.5' }}>
            <p>
              Você alterou o nome de <strong>«{renomeacaoPendente.original.nome}»</strong> para <strong>«{renomeacaoPendente.editada.nome}»</strong>.
            </p>
            <div style={{ padding: '12px', background: 'var(--surface-2)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
              <p style={{ margin: '0 0 8px 0' }}>
                <strong>• Manter histórico:</strong> Preserva todas as sessões, horas estudadas e questões acumuladas. O nome é atualizado nos cronogramas ativos e no planejamento.
              </p>
              <p style={{ margin: 0, color: 'var(--danger-text, #ef4444)' }}>
                <strong>• Zerar histórico:</strong> Trata como uma nova disciplina iniciada do zero. Todo o histórico anterior de sessões, horas e questões será excluído permanentemente.
              </p>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
