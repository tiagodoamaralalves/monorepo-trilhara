import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('../shared/auth/api', () => ({
  request: vi.fn()
}));

import { request } from '../shared/auth/api';
import {
  agendaHojeService,
  derivarAgenda
} from '../shared/agenda/agendaHojeService';
import { agruparAtrasadasPorSerie } from '../shared/agenda/agruparAtrasadasPorSerie';
import {
  compararAtrasadas,
  compararBlocosHoje,
  compararSemData,
  compararTarefasHoje,
  ordenar
} from '../shared/agenda/ordenarAgenda';
import { BlocoHojeDTO, TarefaDTO, TarefasHojeDTO } from '../shared/contracts/domainContracts';

const USUARIO_A = '6af20000000000000000000a';
const USUARIO_B = '6af20000000000000000000b';

function respostaHoje(hojeLocal: string, partes: Partial<TarefasHojeDTO> = {}): TarefasHojeDTO {
  return { hojeLocal, atrasadas: [], hoje: [], semData: [], ...partes };
}

function bloco(id: string, extras: Partial<BlocoHojeDTO> = {}): BlocoHojeDTO {
  return {
    blocoId: id,
    trilhaId: 'trilha-1',
    cronogramaId: 'crono-1',
    disciplinaId: 'disc-1',
    disciplinaNome: 'Disciplina',
    disciplinaCor: '#3b82f6',
    trilhaNome: 'Trilha A',
    data: '2026-11-09',
    ordem: 0,
    duracaoMinutos: 50,
    concluido: false,
    ...extras
  };
}

const TAREFA_BASE = { titulo: 'tarefa', concluida: false };

describe('agendaHojeService (R8 / D-23 / D-46)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    agendaHojeService.resetarParaTestes();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('ordenarAgenda — ordens normativas', () => {
    it('ordenaAtrasadasPorDataPrioridadeOrdemComIdComoDesempate', () => {
      const itens: TarefaDTO[] = [
        { ...TAREFA_BASE, id: 'c', prioridade: 'ALTA', dataVencimento: '2026-11-02', ordem: 0 },
        { ...TAREFA_BASE, id: 'b', prioridade: 'MEDIA', dataVencimento: '2026-11-01', ordem: 0 },
        { ...TAREFA_BASE, id: 'a2', prioridade: 'ALTA', dataVencimento: '2026-11-01', ordem: 5 },
        { ...TAREFA_BASE, id: 'a1', prioridade: 'ALTA', dataVencimento: '2026-11-01', ordem: 5 }
      ];

      const ordenadas = ordenar(itens, compararAtrasadas);

      expect(ordenadas.map((t) => t.id)).toEqual(['a1', 'a2', 'b', 'c']);
      // `ordenar` nunca muta a coleção recebida.
      expect(itens.map((t) => t.id)).toEqual(['c', 'b', 'a2', 'a1']);
    });

    it('ordenaTarefasDeHojeComHorarioNuloPorUltimo', () => {
      const base = { ...TAREFA_BASE, dataVencimento: '2026-11-09' };
      const itens: TarefaDTO[] = [
        { ...base, id: 'sem-hora-b', prioridade: 'ALTA', ordem: 1 },
        { ...base, id: 'com-hora-10', prioridade: 'BAIXA', horario: '10:00', ordem: 0 },
        { ...base, id: 'com-hora-08', prioridade: 'BAIXA', horario: '08:00', ordem: 0 },
        { ...base, id: 'sem-hora-a', prioridade: 'ALTA', ordem: 0 }
      ];

      expect(ordenar(itens, compararTarefasHoje).map((t) => t.id)).toEqual([
        'com-hora-08',
        'com-hora-10',
        'sem-hora-a',
        'sem-hora-b'
      ]);
    });

    it('ordenaSemDataPorPrioridadeDescEOrdemAsc', () => {
      const itens: TarefaDTO[] = [
        { ...TAREFA_BASE, id: 'media', prioridade: 'MEDIA', ordem: 0 },
        { ...TAREFA_BASE, id: 'alta-2', prioridade: 'ALTA', ordem: 2 },
        { ...TAREFA_BASE, id: 'alta-1', prioridade: 'ALTA', ordem: 1 }
      ];

      expect(ordenar(itens, compararSemData).map((t) => t.id)).toEqual([
        'alta-1',
        'alta-2',
        'media'
      ]);
    });

    it('ordenaBlocosDeHojePorTrilhaOrdemEBlocoId', () => {
      const itens = [
        bloco('b-2', { trilhaNome: 'Trilha B', ordem: 1 }),
        bloco('a-2', { trilhaNome: 'Trilha A', ordem: 2 }),
        bloco('a-1', { trilhaNome: 'Trilha A', ordem: 1 })
      ];

      expect(ordenar(itens, compararBlocosHoje).map((b) => b.blocoId)).toEqual([
        'a-1',
        'a-2',
        'b-2'
      ]);
    });
  });

  describe('agruparAtrasadasPorSerie (D-23)', () => {
    it('agrupaSerieSobOcorrenciaMaisRecenteComAnterioresAscendentes', () => {
      const atrasadas: TarefaDTO[] = [
        { ...TAREFA_BASE, id: 's-3', serieId: 'serie', prioridade: 'MEDIA', dataVencimento: '2026-11-05' },
        { ...TAREFA_BASE, id: 's-1', serieId: 'serie', prioridade: 'MEDIA', dataVencimento: '2026-11-01' },
        { ...TAREFA_BASE, id: 's-2', serieId: 'serie', prioridade: 'MEDIA', dataVencimento: '2026-11-03' },
        { ...TAREFA_BASE, id: 'avulsa', prioridade: 'ALTA', dataVencimento: '2026-11-02' }
      ];

      const grupos = agruparAtrasadasPorSerie(atrasadas);

      expect(grupos).toHaveLength(2);
      const grupoSerie = grupos.find((g) => g.principal.serieId === 'serie');
      expect(grupoSerie?.principal.id).toBe('s-3');
      expect(grupoSerie?.anteriores.map((t) => t.id)).toEqual(['s-1', 's-2']);
      // Grupo da avulsa (02/11) vem antes do grupo cuja principal é 05/11.
      expect(grupos[0].principal.id).toBe('avulsa');
    });

    it('tarefaDesvinculadaDaSerieNaoEhAgrupada', () => {
      const grupos = agruparAtrasadasPorSerie([
        { ...TAREFA_BASE, id: 'v-1', serieId: 'serie', prioridade: 'MEDIA', dataVencimento: '2026-11-01' },
        {
          ...TAREFA_BASE,
          id: 'd-1',
          serieId: 'serie',
          desvinculadaDaSerie: true,
          prioridade: 'MEDIA',
          dataVencimento: '2026-11-02'
        }
      ]);

      expect(grupos).toHaveLength(2);
    });
  });

  describe('derivarAgenda — três seções', () => {
    it('derivaAtrasadasHojeESemDataNasOrdensNormativas', () => {
      const agenda = derivarAgenda({
        status: 'sucesso',
        hojeLocal: '2026-11-09',
        tarefas: {
          status: 'sucesso',
          dados: respostaHoje('2026-11-09', {
            atrasadas: [
              { ...TAREFA_BASE, id: 'atr-2', prioridade: 'MEDIA', dataVencimento: '2026-11-02' },
              { ...TAREFA_BASE, id: 'atr-1', prioridade: 'ALTA', dataVencimento: '2026-11-01' }
            ],
            hoje: [
              { ...TAREFA_BASE, id: 'hoje-sem-hora', prioridade: 'ALTA', dataVencimento: '2026-11-09', ordem: 0 },
              { ...TAREFA_BASE, id: 'hoje-com-hora', prioridade: 'BAIXA', horario: '09:00', dataVencimento: '2026-11-09', ordem: 1 }
            ],
            semData: [
              { ...TAREFA_BASE, id: 'sd-media', prioridade: 'MEDIA', ordem: 0 },
              { ...TAREFA_BASE, id: 'sd-alta', prioridade: 'ALTA', ordem: 0 }
            ]
          })
        },
        blocos: {
          status: 'sucesso',
          dados: [
            bloco('bloco-b', { trilhaNome: 'Trilha B', ordem: 1 }),
            bloco('bloco-a', { trilhaNome: 'Trilha A', ordem: 1 })
          ]
        }
      })!;

      expect(agenda.atrasadas.map((t) => t.id)).toEqual(['atr-1', 'atr-2']);
      expect(agenda.blocosHoje.map((b) => b.blocoId)).toEqual(['bloco-a', 'bloco-b']);
      expect(agenda.tarefasHoje.map((t) => t.id)).toEqual(['hoje-com-hora', 'hoje-sem-hora']);
      expect(agenda.semData.map((t) => t.id)).toEqual(['sd-alta', 'sd-media']);
      expect(agenda.vazioTotal).toBe(false);
      expect(agenda.erroTarefas).toBeNull();
      expect(agenda.erroBlocos).toBeNull();
    });

    it('ignoraConcluidasEItensDeOutrosDias', () => {
      const agenda = derivarAgenda({
        status: 'sucesso',
        hojeLocal: '2026-11-09',
        tarefas: {
          status: 'sucesso',
          dados: respostaHoje('2026-11-09', {
            hoje: [
              { ...TAREFA_BASE, id: 'ok', prioridade: 'MEDIA', dataVencimento: '2026-11-09' },
              { ...TAREFA_BASE, id: 'concluida', prioridade: 'MEDIA', concluida: true, dataVencimento: '2026-11-09' },
              { ...TAREFA_BASE, id: 'outro-dia', prioridade: 'MEDIA', dataVencimento: '2026-11-10' }
            ],
            atrasadas: [
              { ...TAREFA_BASE, id: 'atrasada-concluida', prioridade: 'MEDIA', concluida: true, dataVencimento: '2026-11-01' }
            ]
          })
        },
        blocos: { status: 'sucesso', dados: [bloco('bloco-concluido', { concluido: true })] }
      })!;

      expect(agenda.tarefasHoje.map((t) => t.id)).toEqual(['ok']);
      expect(agenda.atrasadas).toHaveLength(0);
      expect(agenda.blocosHoje).toHaveLength(0);
    });

    it('tudoVazioComAsDuasFontesOkMarcaVazioTotal', () => {
      const agenda = derivarAgenda({
        status: 'sucesso',
        hojeLocal: '2026-11-09',
        tarefas: { status: 'sucesso', dados: respostaHoje('2026-11-09') },
        blocos: { status: 'sucesso', dados: [] }
      })!;

      expect(agenda.vazioTotal).toBe(true);
    });

    it('visitanteSemUsuarioNaoTemAgendaDerivada', () => {
      expect(derivarAgenda(null)).toBeNull();
    });
  });

  describe('carga e falhas parciais', () => {
    it('falhaDeBlocosMantemTarefasEErroBlocos', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') {
          return respostaHoje('2026-11-09', {
            hoje: [{ ...TAREFA_BASE, id: 'tar-1', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }]
          });
        }
        throw new Error('Serviço de Estudos indisponível (503)');
      });

      const agenda = derivarAgenda(await agendaHojeService.recarregar(USUARIO_A))!;

      expect(agenda.erroTarefas).toBeNull();
      expect(agenda.erroBlocos).toBeInstanceOf(Error);
      expect(agenda.erroBlocos?.message).toContain('503');
      expect(agenda.tarefasHoje).toHaveLength(1);
      expect(agenda.blocosHoje).toHaveLength(0);
      expect(agenda.vazioTotal).toBe(false);
    });

    it('falhaDeTarefasMantemBlocosEErroTarefas', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/blocos/hoje') return [bloco('bloco-1')];
        throw new Error('Falha de rede');
      });

      const agenda = derivarAgenda(await agendaHojeService.recarregar(USUARIO_A))!;

      expect(agenda.erroBlocos).toBeNull();
      expect(agenda.erroTarefas).toBeInstanceOf(Error);
      expect(agenda.blocosHoje).toHaveLength(1);
      expect(agenda.tarefasHoje).toHaveLength(0);
      expect(agenda.vazioTotal).toBe(false);
    });

    it('retryRecarregaSomenteAFonteComFalha', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/blocos/hoje') throw new Error('Falha em estudos');
        return respostaHoje('2026-11-09');
      });

      await agendaHojeService.recarregar(USUARIO_A);
      vi.mocked(request).mockClear();

      await agendaHojeService.recarregarBlocos(USUARIO_A);

      expect(vi.mocked(request).mock.calls.map(([caminho]) => caminho)).toEqual(['/blocos/hoje']);
    });

    it('retryDeTarefasNaoRecarregaBlocos', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') throw new Error('Falha em tarefas');
        return [bloco('bloco-1')];
      });

      await agendaHojeService.recarregar(USUARIO_A);
      vi.mocked(request).mockClear();

      const estado = await agendaHojeService.recarregarTarefas(USUARIO_A);

      expect(vi.mocked(request).mock.calls.map(([caminho]) => caminho)).toEqual(['/tarefas/hoje']);
      expect(estado.blocos.status).toBe('sucesso');
    });
  });

  describe('throttle de foco/visibilidade (5 s)', () => {
    it('throttleDeCincoSegundosEvitaRajadasDeRequisicoes', async () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-11-09T12:00:00Z'));
      vi.mocked(request).mockImplementation(async () => respostaHoje('2026-11-09'));

      await agendaHojeService.recarregar(USUARIO_A);
      const chamadas = vi.mocked(request).mock.calls.length;

      await agendaHojeService.recarregarComThrottle(USUARIO_A);
      expect(vi.mocked(request).mock.calls.length).toBe(chamadas);

      vi.setSystemTime(new Date('2026-11-09T12:00:05Z'));
      await agendaHojeService.recarregarComThrottle(USUARIO_A);
      expect(vi.mocked(request).mock.calls.length).toBe(chamadas + 2);
    });
  });

  describe('isolamento por conta (D-46)', () => {
    it('trocaDeContaZeraEstadoSemItensDaContaAnterior', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') {
          return respostaHoje('2026-11-09', {
            hoje: [
              { ...TAREFA_BASE, id: 'tarefa-de-A', titulo: 'Somente de A', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }
            ]
          });
        }
        return [];
      });

      expect(derivarAgenda(await agendaHojeService.recarregar(USUARIO_A))!.tarefasHoje[0].titulo).toBe(
        'Somente de A'
      );

      // Logout propaga `trilhara:logout` (authStorage) e zera a fatia da conta.
      window.dispatchEvent(new CustomEvent('trilhara:logout', { detail: { usuarioId: USUARIO_A } }));
      expect(agendaHojeService.obterEstado(USUARIO_A)).toBeNull();

      vi.mocked(request).mockImplementation(async () => respostaHoje('2026-11-09'));
      const agendaB = derivarAgenda(await agendaHojeService.recarregar(USUARIO_B))!;

      expect(agendaB.tarefasHoje).toHaveLength(0);
      expect(agendaB.atrasadas).toHaveLength(0);
    });

    it('novaAssinaturaNuncaRecebeDadosDeOutraConta', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') {
          return respostaHoje('2026-11-09', {
            hoje: [
              { ...TAREFA_BASE, id: 'tarefa-de-A', titulo: 'Somente de A', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }
            ]
          });
        }
        return [];
      });

      await agendaHojeService.recarregar(USUARIO_A);

      const recebidos: Array<string[]> = [];
      const cancelar = agendaHojeService.subscrever(USUARIO_B, (estado) => {
        const derivado = derivarAgenda(estado);
        if (derivado) recebidos.push(derivado.tarefasHoje.map((t) => t.titulo));
      });

      // A assinatura de B nunca é notificada com o estado de A — nem de forma intermediária.
      expect(recebidos.flat()).not.toContain('Somente de A');
      cancelar();
    });

    it('contaExcluidaZeraEstadoDaConta', async () => {
      vi.mocked(request).mockImplementation(async () => respostaHoje('2026-11-09'));

      await agendaHojeService.recarregar(USUARIO_A);
      window.dispatchEvent(
        new CustomEvent('trilhara:conta-excluida', { detail: { usuarioId: USUARIO_A } })
      );

      expect(agendaHojeService.obterEstado(USUARIO_A)).toBeNull();
    });
  });

  describe('virada de dia', () => {
    it('viradaDeHojeLocalDescartaEstadoDoDiaAnteriorERecarrega', async () => {
      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') {
          return respostaHoje('2026-11-09', {
            hoje: [
              { ...TAREFA_BASE, id: 'dia-1', prioridade: 'MEDIA', dataVencimento: '2026-11-09' }
            ]
          });
        }
        return [bloco('bloco-dia-1')];
      });

      await agendaHojeService.recarregar(USUARIO_A);
      vi.mocked(request).mockClear();

      vi.mocked(request).mockImplementation(async (caminho: string) => {
        if (caminho === '/tarefas/hoje') {
          return respostaHoje('2026-11-10', {
            hoje: [
              { ...TAREFA_BASE, id: 'dia-2', prioridade: 'MEDIA', dataVencimento: '2026-11-10' }
            ]
          });
        }
        return [];
      });

      const estado = await agendaHojeService.recarregar(USUARIO_A);
      const agenda = derivarAgenda(estado)!;

      expect(estado.hojeLocal).toBe('2026-11-10');
      expect(agenda.tarefasHoje.map((t) => t.id)).toEqual(['dia-2']);
      expect(agenda.atrasadas.map((t) => t.id)).not.toContain('dia-1');
      // Consolidação do dia novo: as duas fontes são refeitas uma única vez.
      expect(vi.mocked(request).mock.calls.length).toBe(4);
    });
  });
});

