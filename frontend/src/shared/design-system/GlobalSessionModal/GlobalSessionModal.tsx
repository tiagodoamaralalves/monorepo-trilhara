import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { X, AlertCircle } from 'lucide-react';
import { Button } from '../Button/Button';
import { Input } from '../Input/Input';
import { Select } from '../Select/Select';
import { DatePicker } from '../DatePicker/DatePicker';
import { useToast } from '../Toast/Toast';
import { useGlobalSessionModal } from './GlobalSessionModalContext';
import { sessaoGlobalService } from '../../services/sessaoGlobalService';
import {
  SessaoRegistradaDTO,
  TrilhaResumoDTO,
  TrilhaDetalheDTO
} from '../../contracts/domainContracts';
import { request } from '../../auth/api';
import styles from './GlobalSessionModal.module.css';

function obterHojeISO(): string {
  const d = new Date();
  const ano = d.getFullYear();
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

export const GlobalSessionModal: React.FC = () => {
  const { isOpen, params, fecharModal } = useGlobalSessionModal();
  const toast = useToast();

  const titleId = useId();
  const modalBoxRef = useRef<HTMLDivElement>(null);
  const previousActiveElementRef = useRef<HTMLElement | null>(null);
  const hoursInputRef = useRef<HTMLInputElement>(null);

  // Estados do formulário
  const [chaveIdempotencia, setChaveIdempotencia] = useState<string>('');
  const [horas, setHoras] = useState<string>('0');
  const [minutos, setMinutos] = useState<string>('0');
  const [dataSessao, setDataSessao] = useState<string>(obterHojeISO());
  const [questoes, setQuestoes] = useState<string>('');
  const [acertos, setAcertos] = useState<string>('');

  // Seleção e criação inline de Trilha e Disciplina
  const [trilhas, setTrilhas] = useState<TrilhaResumoDTO[]>([]);
  const [selectedTrilhaId, setSelectedTrilhaId] = useState<string>('');
  const [disciplinas, setDisciplinas] = useState<{ id: string; nome: string; cor?: string }[]>([]);
  const [selectedDisciplinaId, setSelectedDisciplinaId] = useState<string>('');
  const [novaTrilhaNome, setNovaTrilhaNome] = useState<string>('');
  const [novaDisciplinaNome, setNovaDisciplinaNome] = useState<string>('');

  // Controle de submissão e fluxo em 2 etapas
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [sessaoSalva, setSessaoSalva] = useState<boolean>(false);
  const [sessaoSalvaDTO, setSessaoSalvaDTO] = useState<SessaoRegistradaDTO | null>(null);
  const [erroParcialEtapa2, setErroParcialEtapa2] = useState<string | null>(null);
  const [erroValidacao, setErroValidacao] = useState<string | null>(null);

  // Inicialização ao abrir modal
  useEffect(() => {
    if (!isOpen || !params) return;

    previousActiveElementRef.current = (params.triggerElement || document.activeElement) as HTMLElement | null;
    const novaChave = crypto.randomUUID();
    setChaveIdempotencia(novaChave);

    const duracaoTotalSegundos = Math.max(0, params.duracaoInicialSegundos ?? 0);
    const duracaoMinutosCalculados = Math.floor(duracaoTotalSegundos / 60);
    const h = Math.floor(duracaoMinutosCalculados / 60);
    const m = duracaoMinutosCalculados % 60;
    setHoras(String(h));
    setMinutos(String(m));

    setDataSessao(params.dataSessao || obterHojeISO());
    setQuestoes('');
    setAcertos('');
    setSelectedTrilhaId(params.trilhaId || '');
    setSelectedDisciplinaId(params.disciplinaId || '');
    setNovaTrilhaNome('');
    setNovaDisciplinaNome('');
    setErroValidacao(null);
    setErroParcialEtapa2(null);
    setSessaoSalva(false);
    setSessaoSalvaDTO(null);
    setIsSaving(false);

    // Carrega lista de trilhas se estiver em modo MANUAL ou se faltar trilhaId
    if (params.modo === 'MANUAL' || !params.trilhaId) {
      request<TrilhaResumoDTO[]>('/trilhas')
        .then((lista) => {
          setTrilhas(lista || []);
          if (!params.trilhaId && lista && lista.length > 0) {
            setSelectedTrilhaId(lista[0].id);
          }
        })
        .catch(() => {
          // Ignora erro inicial de listagem
        });
    }

    // Foco inicial somente se nenhum elemento dentro do modal já estiver focado
    const timerFocus = setTimeout(() => {
      if (modalBoxRef.current && !modalBoxRef.current.contains(document.activeElement)) {
        hoursInputRef.current?.focus();
      }
    }, 0);

    return () => clearTimeout(timerFocus);
  }, [isOpen, params]);

  // Carrega disciplinas quando a trilha selecionada mudar
  useEffect(() => {
    if (!isOpen || !selectedTrilhaId || selectedTrilhaId === '__nova__') {
      if (selectedTrilhaId === '__nova__') {
        setDisciplinas([]);
        setSelectedDisciplinaId('__nova__');
      }
      return;
    }

    request<TrilhaDetalheDTO>(`/trilhas/${selectedTrilhaId}`)
      .then((detalhe) => {
        const discList = (detalhe.disciplinas || []).map((d) => ({
          id: d.id,
          nome: d.nome,
          cor: d.cor
        }));
        setDisciplinas(discList);
        if (!selectedDisciplinaId || !discList.some((d) => d.id === selectedDisciplinaId)) {
          if (discList.length > 0) {
            setSelectedDisciplinaId(discList[0].id);
          }
        }
      })
      .catch(() => {
        setDisciplinas([]);
      });
  }, [isOpen, selectedTrilhaId, selectedDisciplinaId]);

  // Restaura foco ao fechar
  const handleFechar = useCallback(() => {
    if (isSaving) return;
    fecharModal();
    if (previousActiveElementRef.current && typeof previousActiveElementRef.current.focus === 'function') {
      previousActiveElementRef.current.focus();
    }
  }, [isSaving, fecharModal]);

  // Focus trap e Escape key
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (!isSaving) {
          e.preventDefault();
          handleFechar();
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

  const duracaoTotalSegundos = (Number(horas) || 0) * 3600 + (Number(minutos) || 0) * 60;

  // Ação secundária exclusiva para BLOCO_DIRETO: "Concluir sem salvar sessão"
  const handleConcluirSemSalvar = async () => {
    if (!params?.blocoId || isSaving) return;
    setIsSaving(true);
    setErroValidacao(null);
    try {
      await sessaoGlobalService.concluirBloco(params.blocoId, true);
      toast.success('Bloco marcado como concluído.');
      if (params.onSuccess) {
        params.onSuccess(undefined, true);
      }
      handleFechar();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Erro ao concluir bloco.';
      setErroValidacao(msg);
      toast.error(msg);
    } finally {
      setIsSaving(false);
    }
  };

  // Submissão principal (Salvar sessão / Fluxo 2 etapas / Retry etapa 2)
  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (isSaving || !params) return;

    // Se estiver em estado de retry da Etapa 2 (sessão já salva, bloco pendente)
    if (sessaoSalva && params.blocoId) {
      setIsSaving(true);
      setErroParcialEtapa2(null);
      setErroValidacao(null);
      try {
        await sessaoGlobalService.concluirBloco(params.blocoId, true);
        toast.success('Bloco concluído com sucesso!');
        if (params.onSuccess) {
          params.onSuccess(sessaoSalvaDTO || undefined, true);
        }
        handleFechar();
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Erro ao concluir bloco.';
        setErroParcialEtapa2('A sessão de estudo foi salva com sucesso, mas não foi possível marcar o bloco como concluído.');
        toast.error(msg);
      } finally {
        setIsSaving(false);
      }
      return;
    }

    // Validações
    if (duracaoTotalSegundos < 60) {
      setErroValidacao('A duração mínima para registrar uma sessão é de 1 minuto (60 segundos).');
      return;
    }

    if (params.modo === 'MANUAL') {
      const hoje = obterHojeISO();
      if (dataSessao > hoje) {
        setErroValidacao('A data da sessão não pode ser futura.');
        return;
      }
    }

    if (numQuestoes !== undefined) {
      if (isNaN(numQuestoes) || numQuestoes < 0) {
        setErroValidacao('O número de questões deve ser zero ou positivo.');
        return;
      }
      if (numAcertos !== undefined) {
        if (isNaN(numAcertos) || numAcertos < 0) {
          setErroValidacao('O número de acertos deve ser zero ou positivo.');
          return;
        }
        if (numAcertos > numQuestoes) {
          setErroValidacao('O número de acertos não pode ser maior que o total de questões.');
          return;
        }
      }
    }

    // Trilha e disciplina efetivas
    let trilhaEfetivaId = selectedTrilhaId || params.trilhaId;
    let disciplinaEfetivaId = selectedDisciplinaId || params.disciplinaId;

    setIsSaving(true);
    setErroValidacao(null);
    setErroParcialEtapa2(null);

    try {
      // Criação inline de trilha se necessário
      if (trilhaEfetivaId === '__nova__' || !trilhaEfetivaId) {
        if (!novaTrilhaNome.trim()) {
          setErroValidacao('Informe o nome da nova trilha.');
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
      } else if (disciplinaEfetivaId === '__nova__' || !disciplinaEfetivaId) {
        if (!novaDisciplinaNome.trim()) {
          setErroValidacao('Informe o nome da nova disciplina.');
          setIsSaving(false);
          return;
        }
        const novaDisc = await request<{ id: string }>(`/trilhas/${trilhaEfetivaId}/disciplinas`, {
          method: 'POST',
          body: JSON.stringify({ nome: novaDisciplinaNome.trim(), peso: 1, topicos: [] })
        }).catch(() => null);

        if (novaDisc) {
          disciplinaEfetivaId = novaDisc.id;
        }
      }

      if (!trilhaEfetivaId || !disciplinaEfetivaId) {
        setErroValidacao('Selecione ou crie uma trilha e disciplina válidas.');
        setIsSaving(false);
        return;
      }

      const payload = {
        disciplinaId: disciplinaEfetivaId,
        dataSessao: params.modo === 'MANUAL' ? dataSessao : obterHojeISO(),
        duracaoSegundos: duracaoTotalSegundos,
        questoes: numQuestoes,
        acertos: numAcertos,
        chaveIdempotencia
      };

      if (!params.blocoId) {
        // Sessão avulsa sem bloco vinculado
        const sessao = await sessaoGlobalService.registrarSessao(
          trilhaEfetivaId,
          payload,
          chaveIdempotencia
        );
        toast.success('Sessão registrada com sucesso!');
        if (params.onSuccess) {
          params.onSuccess(sessao, false);
        }
        handleFechar();
      } else {
        // Fluxo em 2 etapas com bloco vinculado
        let sessao: SessaoRegistradaDTO;
        try {
          sessao = await sessaoGlobalService.registrarSessao(
            trilhaEfetivaId,
            payload,
            chaveIdempotencia
          );
          setSessaoSalva(true);
          setSessaoSalvaDTO(sessao);
        } catch (errSessao: unknown) {
          const msg = errSessao instanceof Error ? errSessao.message : 'Falha ao salvar sessão.';
          setErroValidacao(msg);
          toast.error(msg);
          setIsSaving(false);
          return;
        }

        // Etapa 2: Concluir o bloco
        try {
          await sessaoGlobalService.concluirBloco(params.blocoId, true);
          toast.success('Sessão e conclusão de bloco salvas com sucesso!');
          if (params.onSuccess) {
            params.onSuccess(sessao, true);
          }
          handleFechar();
        } catch (errBloco: unknown) {
          const msg = errBloco instanceof Error ? errBloco.message : 'Falha ao concluir bloco.';
          setErroParcialEtapa2('A sessão de estudo foi salva com sucesso, mas não foi possível marcar o bloco como concluído.');
          toast.error(msg);
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Erro ao processar solicitação.';
      setErroValidacao(msg);
      toast.error(msg);
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
          {erroParcialEtapa2 && (
            <div className={styles.alertWarning} role="alert">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: 4 }}>
                <AlertCircle size={18} />
                Atenção: Ação parcial
              </div>
              {erroParcialEtapa2}
            </div>
          )}

          {erroValidacao && (
            <div className={styles.alertError} role="alert">
              {erroValidacao}
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
                  value={selectedTrilhaId}
                  onChange={(val) => setSelectedTrilhaId(val)}
                  disabled={isSaving || sessaoSalva}
                />
              </div>

              {selectedTrilhaId === '__nova__' && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-nova-trilha">Nome da Nova Trilha</label>
                  <Input
                    id="sessao-nova-trilha"
                    value={novaTrilhaNome}
                    onChange={(e) => setNovaTrilhaNome(e.target.value)}
                    placeholder="Ex: Concurso Auditor Fiscal"
                    disabled={isSaving || sessaoSalva}
                  />
                </div>
              )}

              {selectedTrilhaId !== '__nova__' && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-disciplina-select">Disciplina</label>
                  <Select
                    id="sessao-disciplina-select"
                    options={disciplinaOptions}
                    value={selectedDisciplinaId}
                    onChange={(val) => setSelectedDisciplinaId(val)}
                    disabled={isSaving || sessaoSalva}
                  />
                </div>
              )}

              {(selectedDisciplinaId === '__nova__' || selectedTrilhaId === '__nova__') && (
                <div className={styles.formGroup}>
                  <label className={styles.label} htmlFor="sessao-nova-disciplina">Nome da Nova Disciplina</label>
                  <Input
                    id="sessao-nova-disciplina"
                    value={novaDisciplinaNome}
                    onChange={(e) => setNovaDisciplinaNome(e.target.value)}
                    placeholder="Ex: Direito Constitucional"
                    disabled={isSaving || sessaoSalva}
                  />
                </div>
              )}
            </>
          )}

          {/* Duração em Horas e Minutos */}
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-horas">Horas</label>
              <div className={styles.inputRow}>
                <Input
                  id="sessao-horas"
                  ref={hoursInputRef}
                  type="number"
                  min="0"
                  max="24"
                  value={horas}
                  onChange={(e) => setHoras(e.target.value)}
                  disabled={isSaving || sessaoSalva}
                  className={styles.inputNumber}
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
                  min="0"
                  max="59"
                  value={minutos}
                  onChange={(e) => setMinutos(e.target.value)}
                  disabled={isSaving || sessaoSalva}
                  className={styles.inputNumber}
                />
                <span className={styles.unit}>min</span>
              </div>
            </div>
          </div>

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
                onChange={(e) => setQuestoes(e.target.value)}
                placeholder="Ex: 20"
                disabled={isSaving || sessaoSalva}
                className={styles.inputNumber}
              />
            </div>

            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="sessao-acertos">Acertos (opcional)</label>
              <Input
                id="sessao-acertos"
                type="number"
                min="0"
                value={acertos}
                onChange={(e) => setAcertos(e.target.value)}
                placeholder="Ex: 17"
                disabled={isSaving || sessaoSalva}
                className={styles.inputNumber}
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
            {params.modo === 'BLOCO_DIRETO' && !sessaoSalva && (
              <div className={styles.footerLeft}>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={handleConcluirSemSalvar}
                  disabled={isSaving}
                >
                  Concluir sem salvar sessão
                </Button>
              </div>
            )}

            <Button
              type="button"
              variant="secondary"
              onClick={handleFechar}
              disabled={isSaving}
            >
              Cancelar
            </Button>

            <Button
              type="submit"
              variant="primary"
              disabled={isSaving}
            >
              {isSaving
                ? 'Salvando...'
                : erroParcialEtapa2
                ? 'Tentar concluir bloco novamente'
                : params.blocoId
                ? 'Salvar sessão e concluir bloco'
                : 'Salvar Sessão'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
