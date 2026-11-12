import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { X, AlertCircle, WifiOff } from 'lucide-react';
import { Button } from '../Button/Button';
import { Input } from '../Input/Input';
import { Select } from '../Select/Select';
import { DatePicker } from '../DatePicker/DatePicker';
import { useToast } from '../Toast/Toast';
import { useConfirm } from '../ConfirmDialog/ConfirmDialog';
import { useGlobalSessionModal } from './GlobalSessionModalContext';
import { sessaoGlobalService } from '../../services/sessaoGlobalService';
import { useStudyTimer } from '../../timers/estudoTimer';
import { AUTH_REQUIRED_EVENT } from '../../auth/api';
import { classificarErro, extrairErrosDeCampo } from '../../api';
import { publicar } from '../../sync/barramento';
import { useUnsavedChanges } from '../../hooks';
import { formatarRelogio } from '../../format';
import {
  SessaoRegistradaDTO,
  TrilhaResumoDTO,
  TrilhaDetalheDTO
} from '../../contracts/domainContracts';
import { request } from '../../auth/api';
import styles from './GlobalSessionModal.module.css';

function obterHojeISO(): string {
  const agora = new Date();
  const ano = agora.getFullYear();
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

/** "Tempo medido: 25 min 37 s" — auxiliar exibido quando o timer originou a duração. */
function descreverTempoMedido(segundos: number): string {
  const horas = Math.floor(segundos / 3600);
  const minutos = Math.floor((segundos % 3600) / 60);
  const resto = segundos % 60;
  const partes: string[] = [];
  if (horas > 0) partes.push(`${horas} h`);
  if (minutos > 0) partes.push(`${minutos} min`);
  if (resto > 0 || partes.length === 0) partes.push(`${resto} s`);
  return `Tempo medido: ${partes.join(' ')}`;
}

interface CriadosInline {
  trilhaId?: string;
  disciplinaId?: string;
}

interface ErroCampo {
  campo: string;
  mensagem: string;
}

export const GlobalSessionModal: React.FC = () => {
  const { isOpen, params, fecharModal } = useGlobalSessionModal();
  const toast = useToast();
  const { confirm } = useConfirm();
  const timer = useStudyTimer();

  const titleId = useId();
  const modalBoxRef = useRef<HTMLDivElement>(null);
  const previousActiveElementRef = useRef<HTMLElement | null>(null);
  const hoursInputRef = useRef<HTMLInputElement>(null);

  // Identificador da chave de idempotência desta abertura; só muda ao abrir um novo modal.
  const [chaveIdempotencia, setChaveIdempotencia] = useState<string>('');
  const [trilhaId, setTrilhaId] = useState<string>('');
  const [disciplinaId, setDisciplinaId] = useState<string>('');
  const [criadosInline, setCriadosInline] = useState<CriadosInline>({});
  const [sessaoSalva, setSessaoSalva] = useState<boolean>(false);
  const [sessaoId, setSessaoId] = useState<string | undefined>(undefined);
  const [erro, setErro] = useState<unknown>(null);
  const [sujo, setSujo] = useState<boolean>(false);

  // Campos do formulário
  const [horas, setHoras] = useState<string>('0');
  const [minutos, setMinutos] = useState<string>('0');
  const [dataSessao, setDataSessao] = useState<string>(obterHojeISO());
  const [questoes, setQuestoes] = useState<string>('');
  const [acertos, setAcertos] = useState<string>('');

  // Duração original em segundos (preservada se o usuário não editar horas/minutos).
  const [duracaoOriginalSegundos, setDuracaoOriginalSegundos] = useState<number>(0);
  const [duracaoEditada, setDuracaoEditada] = useState<boolean>(false);

  // Trilhas/disciplinas para o modo MANUAL
  const [trilhas, setTrilhas] = useState<TrilhaResumoDTO[]>([]);
  const [disciplinas, setDisciplinas] = useState<{ id: string; nome: string; cor?: string }[]>([]);
  const [novaTrilhaNome, setNovaTrilhaNome] = useState<string>('');
  const [novaDisciplinaNome, setNovaDisciplinaNome] = useState<string>('');

  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [errosCampo, setErrosCampo] = useState<ErroCampo[]>([]);
  const [erroOffline, setErroOffline] = useState<boolean>(false);
  const [blocoPendente, setBlocoPendente] = useState<boolean>(false);
  const [aguardandoLogin, setAguardandoLogin] = useState<boolean>(false);

  const pausarTimer = timer.pause;
  const resetarTimer = timer.reset;
  const modoComTimer = params?.modo === 'ESTUDOS_TIMER' || params?.modo === 'BLOCO_TIMER';
  const modoComBloco = Boolean(params?.blocoId);


  // Inicialização ao abrir modal (D-22): a chave de idempotência nasce aqui e só muda em uma
  // nova abertura; nos modos com timer o cronômetro é **pausado**, nunca zerado.
  useEffect(() => {
    if (!isOpen || !params) return;

    previousActiveElementRef.current = (params.triggerElement || document.activeElement) as HTMLElement | null;
    setChaveIdempotencia(crypto.randomUUID());

    const duracaoTotalSegundos = Math.max(0, params.duracaoInicialSegundos ?? 0);
    setDuracaoOriginalSegundos(duracaoTotalSegundos);
    setDuracaoEditada(false);
    setHoras(String(Math.floor(duracaoTotalSegundos / 3600)));
    setMinutos(String(Math.floor((duracaoTotalSegundos % 3600) / 60)));

    setDataSessao(params.dataSessao || obterHojeISO());
    setQuestoes('');
    setAcertos('');
    setTrilhaId(params.trilhaId || '');
    setDisciplinaId(params.disciplinaId || '');
    setCriadosInline({});
    setNovaTrilhaNome('');
    setNovaDisciplinaNome('');
    setErro(null);
    setErrosCampo([]);
    setErroOffline(false);
    setBlocoPendente(false);
    setAguardandoLogin(false);
    setSessaoSalva(false);
    setSessaoId(undefined);
    setIsSaving(false);
    setSujo(false);

    // D-22.1: pausa o cronômetro ao abrir; o tempo acumulado permanece para retry/cancelamento.
    if (params.modo === 'ESTUDOS_TIMER' || params.modo === 'BLOCO_TIMER') {
      pausarTimer();
    }

    // Carrega lista de trilhas se estiver em modo MANUAL ou se faltar trilhaId
    if (params.modo === 'MANUAL' || !params.trilhaId) {
      request<TrilhaResumoDTO[]>('/trilhas')
        .then((lista) => {
          setTrilhas(lista || []);
          if (!params.trilhaId && lista && lista.length > 0) {
            setTrilhaId(lista[0].id);
          }
        })
        .catch(() => {
          // Falha de listagem mantém o seletor vazio; o usuário pode criar inline.
        });
    }

    // Foco inicial no primeiro campo editável.
    const timerFocus = setTimeout(() => {
      if (modalBoxRef.current && !modalBoxRef.current.contains(document.activeElement)) {
        hoursInputRef.current?.focus();
      }
    }, 0);

    return () => clearTimeout(timerFocus);
  }, [isOpen, params, pausarTimer]);

  // Carrega disciplinas quando a trilha selecionada mudar
  useEffect(() => {
    if (!isOpen || !trilhaId || trilhaId === '__nova__') {
      if (trilhaId === '__nova__') {
        setDisciplinas([]);
        setDisciplinaId('__nova__');
      }
      return;
    }

    request<TrilhaDetalheDTO>(`/trilhas/${trilhaId}`)
      .then((detalhe) => {
        const discList = (detalhe.disciplinas || []).map((d) => ({
          id: d.id,
          nome: d.nome,
          cor: d.cor
        }));
        setDisciplinas(discList);
        if (!disciplinaId || !discList.some((d) => d.id === disciplinaId)) {
          if (discList.length > 0) setDisciplinaId(discList[0].id);
        }
      })
      .catch(() => {
        setDisciplinas([]);
      });
  }, [isOpen, trilhaId, disciplinaId]);


  useUnsavedChanges('sessao:global', isOpen && sujo && !sessaoSalva);

  /** Fecha de fato: devolve o foco ao elemento acionador. */
  const fecharAgora = useCallback(() => {
    fecharModal();
    if (previousActiveElementRef.current && typeof previousActiveElementRef.current.focus === 'function') {
      previousActiveElementRef.current.focus();
    }
  }, [fecharModal]);

  /**
   * D-22.2: com campos alterados pede confirmação ("Descartar registro?"); sem alterações
   * fecha direto. Cancelar mantém o timer pausado com o tempo acumulado (D-22.1).
   */
  const handleFechar = useCallback(async () => {
    if (isSaving) return;
    if (!sujo || sessaoSalva) {
      fecharAgora();
      return;
    }
    const descartar = await confirm({
      title: 'Descartar registro?',
      description: 'As alterações feitas não serão salvas.',
      confirmText: 'Descartar',
      cancelText: 'Continuar editando'
    });
    if (descartar) fecharAgora();
  }, [confirm, fecharAgora, isSaving, sessaoSalva, sujo]);


  // Focus trap e Escape key
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (!isSaving) {
          e.preventDefault();
          void handleFechar();
        }
        return;
      }

      if (e.key === 'Tab') {
        if (!modalBoxRef.current) return;
        const focusableElements = modalBoxRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (focusableElements.length === 0) return;

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isSaving, handleFechar]);

  // Cálculos derivados de questões
  const numQuestoes = questoes.trim() === '' ? undefined : Number(questoes);
  const numAcertos = acertos.trim() === '' ? undefined : Number(acertos);

  const errosDerivados = useMemo(() => {
    if (numQuestoes === undefined || isNaN(numQuestoes)) return null;
    const ac = numAcertos !== undefined && !isNaN(numAcertos) ? numAcertos : 0;
    return Math.max(0, numQuestoes - ac);
  }, [numQuestoes, numAcertos]);

  const aproveitamentoPercentual = useMemo(() => {
    if (numQuestoes === undefined || isNaN(numQuestoes) || numQuestoes <= 0) return null;
    const ac = numAcertos !== undefined && !isNaN(numAcertos) ? numAcertos : 0;
    return ((ac / numQuestoes) * 100).toFixed(1);
  }, [numQuestoes, numAcertos]);

  const duracaoTotalSegundos = duracaoEditada
    ? (Number(horas) || 0) * 3600 + (Number(minutos) || 0) * 60
    : duracaoOriginalSegundos;

  /** Mapeia um erro da API para o estado do modal (D-22.5). */
  const tratarErro = useCallback((falha: unknown): void => {
    const categoria = classificarErro(falha);
    setErro(falha);

    if (categoria === 'rede' || (typeof navigator !== 'undefined' && navigator.onLine === false)) {
      setErroOffline(true);
      setErrosCampo([]);
      return;
    }

    setErroOffline(false);

    if (categoria === 'autenticacao') {
      // Abre o AuthModal por cima; o retry com a mesma chave/payload é automático.
      setAguardandoLogin(true);
      window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT));
      return;
    }

    if (categoria === 'validacao') {
      const mapa = extrairErrosDeCampo(falha);
      const lista = Object.entries(mapa).map(([campo, mensagens]) => ({
        campo,
        mensagem: mensagens[0] ?? 'Valor inválido.'
      }));
      setErrosCampo(lista);
      if (lista.length === 0) {
        const mensagem = falha instanceof Error ? falha.message : 'Dados inválidos.';
        setErrosCampo([{ campo: 'geral', mensagem }]);
      }
      return;
    }

    setErrosCampo([]);
  }, []);

  const mensagemErroGeral = useMemo(() => {
    if (erroOffline) return null;
    const candidato = erro as { code?: string; message?: string } | null;
    if (candidato?.code === 'IDEMPOTENCY_KEY_CONFLICT') {
      return 'Esta chave de idempotência já foi usada com dados diferentes.';
    }
    if (!erro) return null;
    return candidato?.message ?? 'Não foi possível registrar a sessão. Tente novamente.';
  }, [erro, erroOffline]);

  const erroDoCampo = useCallback(
    (campo: string): string | undefined =>
      errosCampo.find((item) => item.campo === campo)?.mensagem,
    [errosCampo]
  );

  const sucessoFinalizado = useCallback(
    (sessao: SessaoRegistradaDTO | undefined, blocoConcluido: boolean) => {
      setSessaoSalva(true);
      setSujo(false);
      // D-22.1: só depois de 201/200 dos dois passos o cronômetro é zerado.
      if (modoComTimer && blocoConcluido !== false) resetarTimer();
      publicar('AGENDA_INVALIDADA', null);
      publicar('SESSAO_ENCERRADA', null);
      params?.onSuccess?.(sessao, blocoConcluido);
      fecharAgora();
    },
    [fecharAgora, modoComTimer, params, resetarTimer]
  );

  /** Ação secundária: apenas em modos com bloco (§6.4 / D-22.3). */
  const handleConcluirSemSalvar = async () => {
    if (!params?.blocoId || isSaving) return;
    setIsSaving(true);
    setErro(null);
    try {
      await sessaoGlobalService.concluirBloco(params.blocoId);
      toast.success('Bloco marcado como concluído.');
      setSujo(false);
      publicar('AGENDA_INVALIDADA', null);
      params.onSuccess?.(undefined, true);
      fecharAgora();
    } catch (falha) {
      tratarErro(falha);
      const mensagem = falha instanceof Error ? falha.message : 'Erro ao concluir bloco.';
      toast.error(mensagem);
    } finally {
      setIsSaving(false);
    }
  };

  /** Submissão principal: salvar sessão, fluxo em 2 etapas e retry isolado da etapa 2. */
  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (isSaving || !params) return;

    // Retry da etapa 2 (sessão já salva, bloco pendente): nenhum POST é refeito (§6.4).
    if (sessaoSalva && params.blocoId) {
      setIsSaving(true);
      setErro(null);
      try {
        await sessaoGlobalService.concluirBloco(params.blocoId);
        toast.success('Bloco concluído com sucesso!');
        setBlocoPendente(false);
        sucessoFinalizado(sessaoId ? ({ id: sessaoId } as SessaoRegistradaDTO) : undefined, true);
      } catch (falha) {
        setBlocoPendente(true);
        tratarErro(falha);
        toast.error(falha instanceof Error ? falha.message : 'Erro ao concluir bloco.');
      } finally {
        setIsSaving(false);
      }
      return;
    }

    setErrosCampo([]);
    setErro(null);

    // Validações locais com erro inline por campo (D-22.5).
    const validacoes: ErroCampo[] = [];
    if (duracaoTotalSegundos < 60) {
      validacoes.push({
        campo: 'duracaoSegundos',
        mensagem: 'A duração mínima para registrar uma sessão é de 1 minuto (60 segundos).'
      });
    }
    if (params.modo === 'MANUAL' && dataSessao > obterHojeISO()) {
      validacoes.push({ campo: 'dataSessao', mensagem: 'A data da sessão não pode ser futura.' });
    }
    if (numQuestoes !== undefined && (isNaN(numQuestoes) || numQuestoes < 0)) {
      validacoes.push({
        campo: 'questoes',
        mensagem: 'O número de questões deve ser zero ou positivo.'
      });
    }
    if (numAcertos !== undefined) {
      if (isNaN(numAcertos) || numAcertos < 0) {
        validacoes.push({
          campo: 'acertos',
          mensagem: 'O número de acertos deve ser zero ou positivo.'
        });
      } else if (numQuestoes !== undefined && numAcertos > numQuestoes) {
        validacoes.push({
          campo: 'acertos',
          mensagem: 'O número de acertos não pode ser maior que o total de questões.'
        });
      }
    }
    if (validacoes.length > 0) {
      setErrosCampo(validacoes);
      return;
    }

    // Trilha e disciplina efetivas
    let trilhaEfetivaId = trilhaId || params.trilhaId || '';
    let disciplinaEfetivaId = disciplinaId || params.disciplinaId || '';

    setIsSaving(true);

    try {
      // Criação inline (D-22.4): os IDs ficam em `criadosInline` e um retry nunca recria.
      if (trilhaEfetivaId === '__nova__' || !trilhaEfetivaId) {
        if (criadosInline.trilhaId) {
          trilhaEfetivaId = criadosInline.trilhaId;
          disciplinaEfetivaId = criadosInline.disciplinaId || disciplinaEfetivaId;
        } else {
          if (!novaTrilhaNome.trim()) {
            setErrosCampo([{ campo: 'novaTrilha', mensagem: 'Informe o nome da nova trilha.' }]);
            setIsSaving(false);
            return;
          }
          const discNome = novaDisciplinaNome.trim() || 'Geral';
          const novaTrilha = await request<TrilhaDetalheDTO>('/trilhas', {
            method: 'POST',
            body: JSON.stringify({
              nome: novaTrilhaNome.trim(),
              disciplinas: [{ nome: discNome, peso: 1, topicos: [] }]
            })
          });
          trilhaEfetivaId = novaTrilha.id;
          disciplinaEfetivaId = novaTrilha.disciplinas?.[0]?.id || '';
          setCriadosInline({ trilhaId: novaTrilha.id, disciplinaId: disciplinaEfetivaId });
        }
      } else if (disciplinaEfetivaId === '__nova__' || !disciplinaEfetivaId) {
        if (criadosInline.disciplinaId) {
          disciplinaEfetivaId = criadosInline.disciplinaId;
        } else {
          if (!novaDisciplinaNome.trim()) {
            setErrosCampo([
              { campo: 'novaDisciplina', mensagem: 'Informe o nome da nova disciplina.' }
            ]);
            setIsSaving(false);
            return;
          }
          const novaDisc = await request<{ id: string }>(
            `/trilhas/${encodeURIComponent(trilhaEfetivaId)}/disciplinas`,
            {
              method: 'POST',
              body: JSON.stringify({ nome: novaDisciplinaNome.trim(), peso: 1, topicos: [] })
            }
          );
          disciplinaEfetivaId = novaDisc.id;
          setCriadosInline((atual) => ({ ...atual, disciplinaId: novaDisc.id }));
        }
      }

      if (!trilhaEfetivaId || !disciplinaEfetivaId) {
        setErrosCampo([
          { campo: 'geral', mensagem: 'Selecione ou crie uma trilha e disciplina válidas.' }
        ]);
        setIsSaving(false);
        return;
      }

      const payload = {
        disciplinaId: disciplinaEfetivaId,
        dataSessao: params.modo === 'MANUAL' ? dataSessao : obterHojeISO(),
        duracaoSegundos: duracaoTotalSegundos,
        ...(numQuestoes !== undefined ? { questoes: numQuestoes } : {}),
        ...(numAcertos !== undefined ? { acertos: numAcertos } : {}),
        chaveIdempotencia
      };

      if (!params.blocoId) {
        // Sessão avulsa (sem bloco vinculado).
        const sessao = await sessaoGlobalService.registrarSessao(
          trilhaEfetivaId,
          payload,
          chaveIdempotencia
        );
        toast.success('Sessão registrada com sucesso!');
        sucessoFinalizado(sessao, true);
      } else {
        // Fluxo em 2 etapas: a etapa 2 não rejeita; reporta `blocoConcluido` e `erro`.
        const resultado = await sessaoGlobalService.executarFluxo({
          trilhaId: trilhaEfetivaId,
          blocoId: params.blocoId,
          payload,
          chave: chaveIdempotencia,
          sessaoSalva: sessaoId ? { id: sessaoId } : null
        });

        if (resultado.sessaoId) setSessaoId(resultado.sessaoId);
        setSessaoSalva(resultado.sessaoSalva);

        if (resultado.blocoConcluido) {
          toast.success('Sessão e conclusão de bloco salvas com sucesso!');
          sucessoFinalizado(
            resultado.sessaoId ? ({ id: resultado.sessaoId } as SessaoRegistradaDTO) : undefined,
            true
          );
        } else {
          setBlocoPendente(true);
          if (resultado.erro) tratarErro(resultado.erro);
        }
      }
    } catch (falha) {
      tratarErro(falha);
      if (!params.blocoId) {
        toast.error(falha instanceof Error ? falha.message : 'Erro ao registrar a sessão.');
      }
    } finally {
      setIsSaving(false);
    }
  };

  const getTituloModal = () => {
    if (!params) return 'Sessão de Estudo';
    switch (params.modo) {
      case 'ESTUDOS_TIMER':
        return 'Registrar Sessão de Estudo';
      case 'BLOCO_TIMER':
        return 'Concluir Bloco de Estudo';
      case 'BLOCO_DIRETO':
        return 'Concluir Bloco de Cronograma';
      case 'MANUAL':
        return 'Registrar Sessão Manual';
      default:
        return 'Sessão de Estudo';
    }
  };

  if (!isOpen || !params) return null;

  const trilhaOptions = [
    ...trilhas.map((t) => ({ value: t.id, label: t.nome })),
    { value: '__nova__', label: '+ Nova trilha...' }
  ];

  const disciplinaOptions = [
    ...disciplinas.map((d) => ({ value: d.id, label: d.nome, dotColor: d.cor })),
    { value: '__nova__', label: '+ Nova disciplina...' }
  ];

  return (
    <div className={styles.overlay} role="presentation">
      <div
        ref={modalBoxRef}
        className={styles.box}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <header className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {getTituloModal()}
          </h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={handleFechar}
            disabled={isSaving}
            aria-label="Fechar modal de sessão"
          >
            <X size={20} />
          </button>
        </header>

        <form onSubmit={handleSubmit} className={styles.body}>
          {erroOffline && (
            <div className={styles.alertWarning} role="alert">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: 4 }}>
                <WifiOff size={18} />
                Sem conexão
              </div>
              Sem conexão. Seus dados foram mantidos.
              <div style={{ marginTop: 8 }}>
                <Button type="button" variant="secondary" size="sm" onClick={() => void handleSubmit()}>
                  Tentar novamente
                </Button>
              </div>
            </div>
          )}

          {blocoPendente && (
            <div className={styles.alertWarning} role="alert">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: 4 }}>
                <AlertCircle size={18} />
                Atenção: Ação parcial
              </div>
              A sessão de estudo foi salva, mas não foi possível marcar o bloco como concluído.
            </div>
          )}

          {aguardandoLogin && !erroOffline && (
            <div className={styles.alertError} role="status">
              Sua sessão expirou. Entre novamente para concluir o registro — os dados foram
              mantidos e serão reenviados com a mesma chave.
            </div>
          )}

          {mensagemErroGeral && !blocoPendente && (
            <div className={styles.alertError} role="alert">
              {mensagemErroGeral}
            </div>
          )}

          {erroDoCampo('geral') && (
            <div className={styles.alertError} role="alert">
              {erroDoCampo('geral')}
            </div>
          )}

          {/* Contexto de Trilha / Disciplina em modos com contexto predefinido */}
          {params.modo !== 'MANUAL' && (params.disciplinaNome || params.trilhaNome) && (
            <div className={styles.contextBadge}>
              {params.disciplinaCor && (
                <span
                  className={styles.colorDot}
                  style={{ backgroundColor: params.disciplinaCor }}
                />
              )}
              <span>
                Disciplina: <strong className={styles.contextHighlight}>{params.disciplinaNome || 'Disciplina vinculada'}</strong>
                {params.trilhaNome && (
                  <> &bull; Trilha: <span className={styles.contextHighlight}>{params.trilhaNome}</span></>
                )}
              </span>
            </div>
          )}

          {/* Seleção em modo MANUAL ou quando não houver contexto fixo */}
          {params.modo === 'MANUAL' && (
            <>
              <div className={styles.formGroup}>
                <label className={styles.label} htmlFor="sessao-trilha-select">Trilha</label>
                <Select
                  id="sessao-trilha-select"
                  options={trilhaOptions}
                  value={trilhaId}
                  onChange={(val) => {
                    setTrilhaId(val);
                    setSujo(true);
                  }}
                  disabled={isSaving || sessaoSalva}
                  error={erroDoCampo('trilhaId')}
                />
              </div>

              {trilhaId === '__nova__' && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-nova-trilha">Nome da Nova Trilha</label>
                  <Input
                    id="sessao-nova-trilha"
                    value={novaTrilhaNome}
                    onChange={(e) => {
                      setNovaTrilhaNome(e.target.value);
                      setSujo(true);
                    }}
                    placeholder="Ex: Concurso Auditor Fiscal"
                    error={erroDoCampo('novaTrilha')}
                    disabled={isSaving || sessaoSalva}
                  />
                </div>
              )}

              {trilhaId !== '__nova__' && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-disciplina-select">Disciplina</label>
                  <Select
                    id="sessao-disciplina-select"
                    options={disciplinaOptions}
                    value={disciplinaId}
                    onChange={(val) => {
                      setDisciplinaId(val);
                      setSujo(true);
                    }}
                    disabled={isSaving || sessaoSalva}
                    error={erroDoCampo('disciplinaId')}
                  />
                </div>
              )}

              {(disciplinaId === '__nova__' || trilhaId === '__nova__') && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-nova-disciplina">Nome da Nova Disciplina</label>
                  <Input
                    id="sessao-nova-disciplina"
                    value={novaDisciplinaNome}
                    onChange={(e) => {
                      setNovaDisciplinaNome(e.target.value);
                      setSujo(true);
                    }}
                    placeholder="Ex: Direito Constitucional"
                    error={erroDoCampo('novaDisciplina')}
                    disabled={isSaving || sessaoSalva}
                  />
                </div>
              )}
            </>
          )}

          {/* Duração em Horas e Minutos (D-22: segundos originais preservados) */}
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-horas">Horas</label>
              <div className={styles.inputRow}>
                <Input
                  id="sessao-horas"
                  ref={hoursInputRef}
                  type="number"
                  inputMode="numeric"
                  min="0"
                  max="24"
                  value={horas}
                  onChange={(e) => {
                    setHoras(e.target.value);
                    setDuracaoEditada(true);
                    setSujo(true);
                  }}
                  disabled={isSaving || sessaoSalva}
                  className={styles.inputNumber}
                  error={erroDoCampo('duracaoSegundos')}
                />
                <span className={styles.unit}>h</span>
              </div>
            </div>

            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-minutos">Minutos</label>
              <div className={styles.inputRow}>
                <Input
                  id="sessao-minutos"
                  type="number"
                  inputMode="numeric"
                  min="0"
                  max="59"
                  value={minutos}
                  onChange={(e) => {
                    setMinutos(e.target.value);
                    setDuracaoEditada(true);
                    setSujo(true);
                  }}
                  disabled={isSaving || sessaoSalva}
                  className={styles.inputNumber}
                />
                <span className={styles.unit}>min</span>
              </div>
            </div>
          </div>

          {duracaoOriginalSegundos > 0 && !duracaoEditada && (
            <p className={styles.helperText} data-testid="tempo-medido">
              {descreverTempoMedido(duracaoOriginalSegundos)} ({formatarRelogio(duracaoOriginalSegundos)})
            </p>
          )}

          {/* Data da Sessão: editável em MANUAL; somente leitura nos demais */}
          <div className={styles.formGroup}>
            <label className={styles.label} htmlFor="sessao-data">Data da Sessão</label>
            {params.modo === 'MANUAL' ? (
              <DatePicker
                id="sessao-data"
                value={dataSessao}
                onChange={(val) => setDataSessao(val)}
                max={obterHojeISO()}
                disabled={isSaving || sessaoSalva}
              />
            ) : (
              <Input
                id="sessao-data"
                value={dataSessao}
                readOnly
                disabled
              />
            )}
          </div>

          {/* Questões e Acertos (Opcionais) */}
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-questoes">Questões Feitas (opcional)</label>
              <Input
                id="sessao-questoes"
                type="number"
                min="0"
                value={questoes}
                onChange={(e) => {
                  setQuestoes(e.target.value);
                  setSujo(true);
                }}
                placeholder="Ex: 20"
                disabled={isSaving || sessaoSalva}
                className={styles.inputNumber}
                error={erroDoCampo('questoes')}
              />
            </div>

            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-acertos">Acertos (opcional)</label>
              <Input
                id="sessao-acertos"
                type="number"
                inputMode="numeric"
                min="0"
                value={acertos}
                onChange={(e) => {
                  setAcertos(e.target.value);
                  setSujo(true);
                }}
                placeholder="Ex: 17"
                disabled={isSaving || sessaoSalva}
                className={styles.inputNumber}
                error={erroDoCampo('acertos')}
              />
            </div>
          </div>

          {/* Métricas Derivadas em Tempo Real */}
          {numQuestoes !== undefined && !isNaN(numQuestoes) && numQuestoes > 0 && (
            <div className={styles.statsGrid}>
              <div className={styles.statItem}>
                <span className={styles.statLabel}>Erros</span>
                <span className={styles.statValue}>{errosDerivados ?? 0}</span>
              </div>
              <div className={styles.statItem}>
                <span className={styles.statLabel}>Aproveitamento</span>
                <span className={styles.statValue}>
                  {aproveitamentoPercentual !== null ? `${aproveitamentoPercentual}%` : '-'}
                </span>
              </div>
            </div>
          )}

          <div className={styles.footer}>
            {modoComBloco && !sessaoSalva && (
              <div className={styles.footerLeft}>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => void handleConcluirSemSalvar()}
                  disabled={isSaving}
                >
                  Concluir sem salvar sessão
                </Button>
              </div>
            )}

            <Button
              type="button"
              variant="secondary"
              onClick={() => void handleFechar()}
              disabled={isSaving}
            >
              Cancelar
            </Button>

            <Button
              type="submit"
              variant="primary"
              loading={isSaving}
              disabled={isSaving}
            >
              {blocoPendente ? 'Tentar concluir bloco novamente' : 'Salvar sessão'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
