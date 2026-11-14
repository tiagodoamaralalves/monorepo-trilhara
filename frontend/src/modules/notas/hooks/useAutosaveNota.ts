import { useCallback, useEffect, useRef, useState } from 'react';
import { SyncStatus } from '@shared/design-system';
import { useUnsavedChanges } from '@shared/hooks';
import { NotaService } from '../services/notaService';
import { RascunhoNotaLocal } from '../types/nota';

/** Debounce do autosave. */
export const DEBOUNCE_AUTOSAVE_MS = 800;

export interface UseAutosaveNotaParams {
  usuarioId: string;
  notaId: string;
  tituloInicial: string;
  conteudoInicial: string;
  atualizadoEmServidor: string | null;
  /** Persiste no servidor; deve lançar em falha (o hook decide o estado de erro). */
  salvar: (
    notaId: string,
    titulo: string,
    conteudoMarkdown: string,
    signal: AbortSignal
  ) => Promise<{ atualizadoEm: string } | void>;
}

export interface UseAutosaveNotaResultado {
  status: SyncStatus;
  titulo: string;
  conteudoMarkdown: string;
  atualizadoEmServidor: string | null;
  alterarTitulo: (titulo: string) => void;
  alterarConteudo: (conteudoMarkdown: string) => void;
  /** Aplica título e conteúdo de uma vez (restauração de rascunho) e envia sem debounce. */
  aplicarRascunho: (titulo: string, conteudoMarkdown: string) => void;
  tentarNovamente: () => void;
  /** Grava o rascunho imediatamente sem debounce (troca de nota, `beforeunload`). */
  flush: () => void;
}

/**
 * Autosave de página com rascunho de contingência.
 *
 * Regras implementadas:
 * **debounce de 800 ms** entre a digitação e o `PUT`;
 * cada `PUT` carrega um `seq` local por página; respostas com `seq` menor que o último
 * enviado (ou de outra página) são descartadas, então uma resposta atrasada não sobrescreve
 * uma edição mais nova;
 * a troca de página **grava o rascunho antes** de abortar a requisição pendente
 * (`AbortController`), então nada é perdido;
 * o rascunho só é apagado depois de um `200` cujo conteúdo enviado seja **igual** ao
 * rascunho atual — se o usuário digitou durante o voo, o rascunho permanece;
 * `visibilitychange: hidden` dispara `flush` imediato;
 * o registro global `unsavedChangesRegistry` fica ativo enquanto o estado for
 * `salvando`, `alteracoes-locais` ou `erro`.
 */
export function useAutosaveNota({
  usuarioId,
  notaId,
  tituloInicial,
  conteudoInicial,
  atualizadoEmServidor: atualizadoEmServidorInicial,
  salvar
}: UseAutosaveNotaParams): UseAutosaveNotaResultado {
  const [status, setStatus] = useState<SyncStatus>('salvo');
  const [titulo, setTitulo] = useState(tituloInicial);
  const [conteudoMarkdown, setConteudoMarkdown] = useState(conteudoInicial);
  const [atualizadoEmServidor, setAtualizadoEmServidor] = useState<string | null>(
    atualizadoEmServidorInicial
  );

  const seqRef = useRef(0);
  const ultimoSeqEnviadoRef = useRef(0);
  const notaIdRef = useRef(notaId);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const pendenteRef = useRef<{ titulo: string; conteudoMarkdown: string } | null>(null);
  const salvarRef = useRef(salvar);
  const baseServidorRef = useRef<string | null>(atualizadoEmServidorInicial);

  useEffect(() => {
    salvarRef.current = salvar;
  }, [salvar]);

  const limparTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const gravarRascunho = useCallback(
    (dados: { titulo: string; conteudoMarkdown: string }) => {
      if (!usuarioId || !notaIdRef.current) return;
      const rascunho: RascunhoNotaLocal = {
        titulo: dados.titulo,
        conteudoMarkdown: dados.conteudoMarkdown,
        atualizadoEmLocal: new Date().toISOString(),
        baseAtualizadoEmServidor: baseServidorRef.current
      };
      NotaService.salvarRascunhoLocal(usuarioId, notaIdRef.current, rascunho);
    },
    [usuarioId]
  );

  const enviar = useCallback(async () => {
    const pendente = pendenteRef.current;
    if (!pendente) return;

    const notaAlvo = notaIdRef.current;
    if (!notaAlvo) return;

    const seq = ++seqRef.current;
    ultimoSeqEnviadoRef.current = seq;

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    setStatus('salvando');
    try {
      const resultado = await salvarRef.current(
        notaAlvo,
        pendente.titulo,
        pendente.conteudoMarkdown,
        controller.signal
      );

      // Resposta obsoleta (outra página ou `seq` já superado) é ignorada por completo.
      if (notaIdRef.current !== notaAlvo || seq < ultimoSeqEnviadoRef.current) {
        return;
      }

      const novoAtualizadoEm =
        resultado && typeof resultado === 'object' && 'atualizadoEm' in resultado
          ? resultado.atualizadoEm
          : null;
      if (novoAtualizadoEm) {
        setAtualizadoEmServidor(novoAtualizadoEm);
        baseServidorRef.current = novoAtualizadoEm;
      }

      const corrente = pendenteRef.current;
      const aindaEhOMesmo =
        corrente?.titulo === pendente.titulo &&
        corrente?.conteudoMarkdown === pendente.conteudoMarkdown;

      if (aindaEhOMesmo) {
        // Conteúdo enviado === rascunho corrente: pode apagar o rascunho.
        NotaService.removerRascunhoLocal(usuarioId, notaAlvo);
        pendenteRef.current = null;
        setStatus('salvo');
      } else if (corrente) {
        // O usuário digitou durante o voo: mantém o rascunho e reenvia.
        gravarRascunho(corrente);
        setStatus('alteracoes-locais');
      }
    } catch {
      if (notaIdRef.current !== notaAlvo) return;
      setStatus('erro');
    }
  }, [gravarRascunho, usuarioId]);

  const agendar = useCallback(() => {
    limparTimer();
    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      void enviar();
    }, DEBOUNCE_AUTOSAVE_MS);
  }, [enviar, limparTimer]);

  const registrarAlteracao = useCallback(
    (novoTitulo: string, novoConteudo: string) => {
      pendenteRef.current = { titulo: novoTitulo, conteudoMarkdown: novoConteudo };
      gravarRascunho(pendenteRef.current);
      setStatus('alteracoes-locais');
      agendar();
    },
    [agendar, gravarRascunho]
  );

  const alterarTitulo = useCallback(
    (novoTitulo: string) => {
      setTitulo(novoTitulo);
      registrarAlteracao(novoTitulo, conteudoMarkdown);
    },
    [conteudoMarkdown, registrarAlteracao]
  );

  const alterarConteudo = useCallback(
    (novoConteudo: string) => {
      setConteudoMarkdown(novoConteudo);
      registrarAlteracao(titulo, novoConteudo);
    },
    [registrarAlteracao, titulo]
  );

  const flush = useCallback(() => {
    if (!pendenteRef.current) return;
    limparTimer();
    gravarRascunho(pendenteRef.current);
    void enviar();
  }, [enviar, gravarRascunho, limparTimer]);

  /**
 * Aplica título e conteúdo juntos e envia imediatamente.
 *
 * Existe porque duas chamadas seguidas de `alterarTitulo`/`alterarConteudo` leriam o estado
 * defasado do render (o segundo `alterar*` regravaria o valor antigo do outro campo). Aqui os
 * dois valores chegam prontos, então o pendente enviado é exatamente o rascunho restaurado.
 */
  const aplicarRascunho = useCallback(
    (novoTitulo: string, novoConteudo: string) => {
      setTitulo(novoTitulo);
      setConteudoMarkdown(novoConteudo);
      pendenteRef.current = { titulo: novoTitulo, conteudoMarkdown: novoConteudo };
      gravarRascunho(pendenteRef.current);
      setStatus('alteracoes-locais');
      limparTimer();
      void enviar();
    },
    [enviar, gravarRascunho, limparTimer]
  );

  const tentarNovamente = useCallback(() => {
    if (!pendenteRef.current) {
      pendenteRef.current = { titulo, conteudoMarkdown };
    }
    limparTimer();
    void enviar();
  }, [conteudoMarkdown, enviar, limparTimer, titulo]);

  // Troca de página: grava o rascunho ANTES de abortar o PUT pendente.
  useEffect(() => {
    if (notaIdRef.current === notaId) return;
    if (pendenteRef.current) {
      gravarRascunho(pendenteRef.current);
    }
    limparTimer();
    controllerRef.current?.abort();
    pendenteRef.current = null;
    notaIdRef.current = notaId;
    setTitulo(tituloInicial);
    setConteudoMarkdown(conteudoInicial);
    setAtualizadoEmServidor(atualizadoEmServidorInicial);
    baseServidorRef.current = atualizadoEmServidorInicial;
    setStatus('salvo');
  }, [
    atualizadoEmServidorInicial,
    conteudoInicial,
    gravarRascunho,
    limparTimer,
    notaId,
    tituloInicial
  ]);

  // Flush imediato quando a aba fica oculta.
  useEffect(() => {
    const aoMudarVisibilidade = () => {
      if (document.visibilityState === 'hidden') {
        flush();
      }
    };
    document.addEventListener('visibilitychange', aoMudarVisibilidade);
    return () => document.removeEventListener('visibilitychange', aoMudarVisibilidade);
  }, [flush]);

  // Registro global de alterações não salvas.
  useUnsavedChanges(
    `nota:${notaId}`,
    notaId.length > 0 &&
      (status === 'salvando' || status === 'alteracoes-locais' || status === 'erro'),
    flush
  );

  // Desmontagem: grava rascunho e aborta sem deixar requisição órfã.
  useEffect(() => {
    return () => {
      if (pendenteRef.current) {
        gravarRascunho(pendenteRef.current);
      }
      limparTimer();
      controllerRef.current?.abort();
    };
  }, [gravarRascunho, limparTimer]);

  return {
    status,
    titulo,
    conteudoMarkdown,
    atualizadoEmServidor,
    alterarTitulo,
    alterarConteudo,
    aplicarRascunho,
    tentarNovamente,
    flush
  };
}
