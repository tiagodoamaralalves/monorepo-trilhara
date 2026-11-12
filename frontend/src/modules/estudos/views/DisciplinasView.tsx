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
  Input,
  DisciplineLabel,
  PageBanner,
  Modal,
  FormModal,
  Textarea,
  ToggleGroup,
  useToast
} from '@shared/design-system';
import { TrilhaDetalheDTO, Disciplina } from '../types/trilhara';
import { EstudosService } from '../services/estudosService';
import { DisciplinaPesoButton } from '../components/DisciplinaPesoButton/DisciplinaPesoButton';
import { EmptyJourneyState } from '../components/EmptyJourneyState/EmptyJourneyState';
import { DisciplinasTopicosModal } from '../components/DisciplinasTopicosModal/DisciplinasTopicosModal';
import { obterCorDisponivel } from '../utils/coresDisciplina';
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

  const [disciplinaQuestoes, setDisciplinaQuestoes] = useState<Disciplina | null>(null);
  const [modalDisciplinasLoteAberto, setModalDisciplinasLoteAberto] = useState(false);
  const [formatoDisciplinasLote, setFormatoDisciplinasLote] = useState<'txt' | 'json'>('txt');
  const [disciplinasLoteRaw, setDisciplinasLoteRaw] = useState('');
  const [erroDisciplinasLote, setErroDisciplinasLote] = useState('');
  const [salvandoDisciplinasLote, setSalvandoDisciplinasLote] = useState(false);
  const [totalQ, setTotalQ] = useState('');
  const [acertosQ, setAcertosQ] = useState('');
  const [salvandoQuestoes, setSalvandoQuestoes] = useState(false);
  const [carregandoQuestoes, setCarregandoQuestoes] = useState(false);
  const [erroCarregamentoQuestoes, setErroCarregamentoQuestoes] = useState('');
  const [erroQuestoes, setErroQuestoes] = useState('');
  const [tentativaQuestoes, setTentativaQuestoes] = useState(0);
  const [resumoQuestoes, setResumoQuestoes] = useState<
    Record<string, { questoes: number; acertos: number }>
  >({});

  const [modalTopicosAberto, setModalTopicosAberto] = useState(false);
  const [disciplinaTopicosInicial, setDisciplinaTopicosInicial] = useState<string | null>(null);
  const [disciplinaRascunho, setDisciplinaRascunho] = useState<Disciplina | null>(null);
  const [salvandoRascunho, setSalvandoRascunho] = useState(false);
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
  }, [trilha.id, tentativaQuestoes]);

  useEffect(() => {
    if (!disciplinaQuestoes) return;

    let active = true;
    setCarregandoQuestoes(true);
    setErroCarregamentoQuestoes('');
    setErroQuestoes('');
    setTotalQ('');
    setAcertosQ('');

    EstudosService.obterEstatisticas(trilha.id, { periodo: 'tudo' })
      .then((estatisticas) => {
        if (!active) return;
        const estatistica = (estatisticas.porDisciplina || []).find((item) => item.disciplinaId === disciplinaQuestoes.id);
        setTotalQ(String(estatistica?.totalQuestoes ?? 0));
        setAcertosQ(String(estatistica?.totalAcertos ?? 0));
      })
      .catch((err: unknown) => {
        if (!active) return;
        const mensagem = err instanceof Error ? err.message : 'Não foi possível carregar as questões atuais.';
        setErroCarregamentoQuestoes(mensagem);
        toast.error(mensagem);
      })
      .finally(() => {
        if (active) setCarregandoQuestoes(false);
      });

    return () => {
      active = false;
    };
  }, [disciplinaQuestoes, trilha.id, tentativaQuestoes, toast]);

  /** D-39: marcar/desmarcar tópico é otimista com rollback + toast em falha. */
  const aplicarTopicosConcluidos = (ids: ReadonlySet<string>) => {
    const topicosConcluidosIds = [...ids];
    onTrilhaAtualizada({
      ...trilha,
      topicosConcluidosIds,
      topicosConcluidos: topicosConcluidosIds.length,
      disciplinas: trilha.disciplinas.map((disciplina) => {
        const concluidos = disciplina.topicos.filter((t) => ids.has(t.id)).length;
        const total = disciplina.topicos.length;
        return {
          ...disciplina,
          topicosConcluidos: concluidos,
          percentualProgresso: total > 0 ? (concluidos / total) * 100 : 0,
          topicos: disciplina.topicos.map((t) => ({ ...t, concluido: ids.has(t.id) }))
        };
      })
    });
  };

  const handleToggleTopicoCheck = async (topicoId: string, concluido: boolean) => {
    const anterior = new Set<string>(trilha.topicosConcluidosIds ?? []);
    const otimista = new Set<string>(anterior);
    if (concluido) {
      otimista.add(topicoId);
    } else {
      otimista.delete(topicoId);
    }
    // Aplica imediatamente (otimista) e reverte se a API falhar.
    aplicarTopicosConcluidos(otimista);

    try {
      if (concluido) {
        await EstudosService.marcarTopicoConcluido(trilha.id, topicoId);
      } else {
        await EstudosService.desmarcarTopicoConcluido(trilha.id, topicoId);
      }
    } catch (err) {
      aplicarTopicosConcluidos(anterior);
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar o assunto.');
    }
  };

  const handleToggleTopicosDisciplina = async (disciplinaId: string, concluido: boolean) => {
    const disciplina = trilha.disciplinas.find((item) => item.id === disciplinaId);
    if (!disciplina || disciplina.topicos.length === 0) return;

    const anterior = new Set<string>(trilha.topicosConcluidosIds ?? []);
    const otimista = new Set<string>(anterior);
    const alterados: string[] = [];

    for (const topico of disciplina.topicos) {
      if (otimista.has(topico.id) === concluido) continue;
      alterados.push(topico.id);
      if (concluido) {
        otimista.add(topico.id);
      } else {
        otimista.delete(topico.id);
      }
    }

    if (alterados.length === 0) return;

    aplicarTopicosConcluidos(otimista);

    try {
      if (concluido) {
        await Promise.all(alterados.map((tId) => EstudosService.marcarTopicoConcluido(trilha.id, tId)));
      } else {
        await Promise.all(alterados.map((tId) => EstudosService.desmarcarTopicoConcluido(trilha.id, tId)));
      }
    } catch (err) {
      aplicarTopicosConcluidos(anterior);
      toast.error(err instanceof Error ? err : 'Não foi possível atualizar os assuntos.');
    }
  };

  const handleAlterarDisciplinasNoModal = async (disciplinas: Disciplina[]) => {
    if (disciplinaRascunho) {
      const atualizada = disciplinas.find((item) => item.id === disciplinaRascunho.id);
      if (atualizada) {
        setDisciplinaRascunho(atualizada);
      }
      return;
    }

    const ordemIds = disciplinas.map((item) => item.id);
    const ordemOriginal = trilha.disciplinas.map((item) => item.id);
    const mudouOrdem =
      ordemIds.length === ordemOriginal.length &&
      ordemIds.some((id, idx) => id !== ordemOriginal[idx]) &&
      new Set(ordemIds).size === ordemOriginal.length;

    if (mudouOrdem) {
      try {
        await EstudosService.reordenarDisciplinas(trilha.id, ordemIds);
        const detalhe = await EstudosService.buscarTrilha(trilha.id);
        onTrilhaAtualizada(detalhe);
      } catch (err) {
        toast.error(err instanceof Error ? err : 'Não foi possível reordenar as disciplinas.');
      }
    }
  };

  const salvarDisciplinaRascunho = async (disciplina: Disciplina) => {
    if (salvandoRascunho) return;
    setSalvandoRascunho(true);
    try {
      await EstudosService.criarDisciplina(trilha.id, {
        nome: disciplina.nome,
        cor: disciplina.cor,
        peso: disciplina.peso,
        topicos: disciplina.topicos.map((t, idx) => ({
          nome: t.nome,
          ordem: idx + 1
        }))
      });
      const detalhe = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(detalhe);
      setModalTopicosAberto(false);
      setDisciplinaTopicosInicial(null);
      setDisciplinaRascunho(null);
      toast.success('Disciplina adicionada com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível salvar a disciplina.');
    } finally {
      setSalvandoRascunho(false);
    }
  };

  const executarSalvarDisciplinaEditada = async (
    disciplinaParaSalvar: Disciplina,
    zerarHistorico: boolean
  ) => {
    if (salvandoDisciplina) return;
    setSalvandoDisciplina(true);

    try {
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
            id: t.id,
            nome: t.nome,
            ordem: idx + 1
          }))
        });
      }
      const detalhe = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(detalhe);
      setModalTopicosAberto(false);
      setDisciplinaTopicosInicial(null);
      setDisciplinaRascunho(null);
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
      setModalTopicosAberto(false);
      setDisciplinaTopicosInicial(null);
      setDisciplinaRascunho(null);
      toast.success('Disciplina excluída com sucesso.');
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a disciplina.');
    } finally {
      setExcluindoDisciplina(false);
    }
  };

  const handleAbrirQuestoes = (disc: Disciplina) => {
    setCarregandoQuestoes(true);
    setDisciplinaQuestoes(disc);
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

  const handleSalvarQuestoes = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!disciplinaQuestoes || carregandoQuestoes) return;

    // O vínculo com caderno saiu de Disciplina (DC-04): aqui só atualizamos questões.
    setErroQuestoes('');
    setSalvandoQuestoes(true);
    try {
      const atualizada = await EstudosService.buscarTrilha(trilha.id);
      onTrilhaAtualizada(atualizada);
      toast.success('Estatísticas de questões atualizadas!');
      setDisciplinaQuestoes(null);
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Erro ao atualizar as questões.');
    } finally {
      setSalvandoQuestoes(false);
    }
  };

  const abrirNovaDisciplina = () => {
    // Sem IDs sintéticos no cliente (N-5): o servidor gera o hex de 24 na criação.
    const novoRascunho: Disciplina = {
      id: '',
      nome: '',
      cor: obterCorDisponivel(trilha.disciplinas),
      peso: 1,
      ordem: trilha.disciplinas.length,
      topicos: []
    };
    setDisciplinaRascunho(novoRascunho);
    setDisciplinaTopicosInicial(null);
    setModalTopicosAberto(true);
  };

  const abrirTopicosDisciplina = (disciplina: Disciplina) => {
    setDisciplinaTopicosInicial(disciplina.id);
    setModalTopicosAberto(true);
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

  const disciplinasModal = disciplinaRascunho
    ? [...trilha.disciplinas, disciplinaRascunho]
    : trilha.disciplinas;

  // Contagens gerais
  const totalTopicos = trilha.disciplinas.flatMap((d) => d.topicos).length;
  const concluidosSet = new Set(trilha.topicosConcluidosIds ?? []);

  const disciplinasFiltradas = useMemo(() => {
    if (filtroStatus === 'todos') {
      return trilha.disciplinas;
    }
    return trilha.disciplinas.filter((disc) =>
      disc.topicos.some((top) => {
        const isDone = concluidosSet.has(top.id);
        return filtroStatus === 'pendentes' ? !isDone : isDone;
      })
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trilha.disciplinas, trilha.topicosConcluidosIds, filtroStatus]);

  const concluidosTotal = trilha.disciplinas
    .flatMap((d) => d.topicos)
    .filter((t) => concluidosSet.has(t.id)).length;
  const pctGeral = totalTopicos > 0 ? Math.round((concluidosTotal / totalTopicos) * 100) : 0;
  const totalQuestoesConsolidadas = Object.values(resumoQuestoes)
    .reduce((total, resumo) => total + resumo.questoes, 0);
  const totalAcertosConsolidados = Object.values(resumoQuestoes)
    .reduce((total, resumo) => total + resumo.acertos, 0);
  const pctAcertosConsolidado = totalQuestoesConsolidadas > 0
    ? Math.round((totalAcertosConsolidados / totalQuestoesConsolidadas) * 100)
    : null;
  const totalQuestoesNumero = Number(totalQ);
  const acertosQuestoesNumero = Number(acertosQ);
  const taxaQuestoes = totalQ !== '' && acertosQ !== '' &&
    Number.isInteger(totalQuestoesNumero) && Number.isInteger(acertosQuestoesNumero) &&
    totalQuestoesNumero > 0
    ? Math.round((acertosQuestoesNumero / totalQuestoesNumero) * 100)
    : null;
  const classeTaxaQuestoes = taxaQuestoes === null
    ? ''
    : taxaQuestoes >= 80
      ? styles.successText
      : taxaQuestoes >= 60
        ? styles.warningText
        : styles.errorText;
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

  const modalDisciplinasLote = (
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
  );

  if (trilha.disciplinas.length === 0) {
    return (
      <div className={styles.container}>
        <PageBanner
          title="Disciplinas & Assuntos do Edital"
          description="Acompanhe a cobertura do edital e organize as matérias da sua trilha."
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
            </div>
          }
        />
        <EmptyJourneyState hasTrilha onPrimaryAction={abrirNovaDisciplina} />
        {modalDisciplinasLote}
        <DisciplinasTopicosModal
          isOpen={modalTopicosAberto}
          initialDisciplinaId={disciplinaTopicosInicial}
          onClose={() => {
            setModalTopicosAberto(false);
            setDisciplinaTopicosInicial(null);
            setDisciplinaRascunho(null);
          }}
          title="Editar disciplinas e assuntos"
          subtitle="Gerencie as disciplinas e os assuntos cadastrados."
          disciplinas={disciplinasModal}
          progresso={concluidosSet}
          onToggleTopico={(topicoId, concluido) => void handleToggleTopicoCheck(topicoId, concluido)}
          onToggleTopicos={(disciplinaId, concluido) =>
            void handleToggleTopicosDisciplina(disciplinaId, concluido)
          }
          editable
          initialEditing={Boolean(disciplinaRascunho)}
          creatingDiscipline={Boolean(disciplinaRascunho)}
          savingNewDiscipline={salvandoRascunho}
          onSaveNewDiscipline={(disciplina) => void salvarDisciplinaRascunho(disciplina)}
          savingDiscipline={salvandoDisciplina}
          onSaveDiscipline={(disciplina) => void salvarDisciplinaEditada(disciplina)}
          deletingDiscipline={excluindoDisciplina}
          onDeleteDiscipline={(disciplina) => void excluirDisciplina(disciplina)}
          onChange={(disciplinas) => void handleAlterarDisciplinasNoModal(disciplinas)}
          mostrarPeso
        />
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <PageBanner
        title="Disciplinas &amp; Assuntos do Edital"
        description="Acompanhe a cobertura do edital e marque os assuntos estudados de cada matéria."
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
          </div>
        }
      />

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
              <strong>{concluidosTotal}</strong> de <strong>{totalTopicos}</strong> assuntos ({pctGeral}%)
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
            return (
              <article
                key={disc.id}
                className={styles.disciplinaCard}
              >
                {/* Header da Disciplina */}
                <div className={styles.discHeader}>
                <div className={styles.discTitleBlock}>
                  <h3 className={styles.discNome}>
                    <DisciplineLabel name={disc.nome} color={disc.cor} />
                  </h3>
                  <div className={styles.discMeta}>
                    <DisciplinaPesoButton
                      peso={disc.peso}
                      onClick={() => abrirTopicosDisciplina(disc)}
                    />
                    <Badge
                      variant={pctDisc >= 60 ? 'success' : 'secondary'}
                      size="md"
                      className={styles.discCount}
                    >
                      {concluidosDisc} / {topicos.length} assuntos ({pctDisc}%)
                    </Badge>
                    <Button
                      size="sm"
                      variant={obterResumoQuestoes(disc.id).variant}
                      className={styles.questoesScoreButton}
                      onClick={(event) => {
                        event.stopPropagation();
                        handleAbrirQuestoes(disc);
                      }}
                      aria-label={`${obterResumoQuestoes(disc.id).percentual}% de acerto`}
                    >
                      {obterResumoQuestoes(disc.id).percentual}% de acerto
                    </Button>
                  </div>
                </div>

                </div>
              </article>
          );
        })
      )}
      </div>

      {disciplinaQuestoes && (
        <FormModal
          isOpen={true}
          onClose={() => setDisciplinaQuestoes(null)}
          title={`Questões: ${disciplinaQuestoes.nome}`}
          subtitle="Confira as estatísticas consolidadas de questões da disciplina."
          icon={<Award size={20} />}
          onSubmit={handleSalvarQuestoes}
          submitText="Atualizar"
          loading={salvandoQuestoes}
          error={erroQuestoes}
        >
          <>
            {carregandoQuestoes && (
              <p className={styles.calculationText} role="status">Carregando os dados atuais de questões...</p>
            )}
            {erroCarregamentoQuestoes && (
              <div className={styles.questoesLoadError} role="alert">
                <span>Não foi possível carregar os dados atuais: {erroCarregamentoQuestoes}</span>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => setTentativaQuestoes((tentativa) => tentativa + 1)}
                >
                  Tentar novamente
                </Button>
              </div>
            )}
            {!carregandoQuestoes && !erroCarregamentoQuestoes && (
              <p className={styles.calculationText}>
                Atualmente registrados: <strong>{totalQ || 0}</strong> questões,{' '}
                <strong>{acertosQ || 0}</strong> acertos.
              </p>
            )}
            <div className={styles.formGrid}>
              <Input
                label="Total de Questões Resolvidas"
                type="number"
                disabled={true}
                helperText="Computado automaticamente a partir das sessões de estudo."
                value={totalQ}
              />
              <Input
                label="Quantidade de Acertos"
                type="number"
                disabled={true}
                helperText="Computado automaticamente a partir das sessões de estudo."
                value={acertosQ}
              />
            </div>

            {!carregandoQuestoes && !erroCarregamentoQuestoes && (
              <p className={styles.calculationText}>
                Taxa de acerto ao vivo:{' '}
                <strong className={classeTaxaQuestoes || styles.neutralText}>
                  {taxaQuestoes === null ? '—' : `${taxaQuestoes}%`}
                </strong>
                {acertosQuestoesNumero <= totalQuestoesNumero && (
                  totalQuestoesNumero > 0 && <> ({totalQuestoesNumero - acertosQuestoesNumero} erros). O servidor registra a diferença no histórico.</>
                )}
              </p>
            )}
          </>
        </FormModal>
      )}

      {modalDisciplinasLote}
      <DisciplinasTopicosModal
        isOpen={modalTopicosAberto}
        initialDisciplinaId={disciplinaTopicosInicial}
        onClose={() => {
          setModalTopicosAberto(false);
          setDisciplinaTopicosInicial(null);
          setDisciplinaRascunho(null);
        }}
        title="Editar disciplinas e assuntos"
        subtitle="Gerencie as disciplinas e os assuntos cadastrados."
        disciplinas={disciplinasModal}
        progresso={concluidosSet}
        onToggleTopico={(topicoId, concluido) => void handleToggleTopicoCheck(topicoId, concluido)}
        onToggleTopicos={(disciplinaId, concluido) =>
          void handleToggleTopicosDisciplina(disciplinaId, concluido)
        }
        editable
        initialEditing={Boolean(disciplinaRascunho)}
        creatingDiscipline={Boolean(disciplinaRascunho)}
        savingNewDiscipline={salvandoRascunho}
        onSaveNewDiscipline={(disciplina) => void salvarDisciplinaRascunho(disciplina)}
        savingDiscipline={salvandoDisciplina}
        onSaveDiscipline={(disciplina) => void salvarDisciplinaEditada(disciplina)}
        deletingDiscipline={excluindoDisciplina}
        onDeleteDiscipline={(disciplina) => void excluirDisciplina(disciplina)}
        onChange={(disciplinas) => void handleAlterarDisciplinasNoModal(disciplinas)}
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
                <strong>• Manter histórico:</strong> Preserva todas as sessões, horas estudadas e questões acumuladas. O nome é atualizado no ciclo e planejamento.
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
