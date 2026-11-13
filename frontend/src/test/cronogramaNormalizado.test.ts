import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EstudosService } from '../modules/estudos/services/estudosService';
import * as apiModule from '../modules/estudos/services/api';

describe('Cronogramas, blocos e disponibilidade semanal', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('deve listar cronogramas de uma trilha', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce([
      {
        id: 'crono-1',
        trilhaId: 'trilha-123',
        modalidade: 'SEQUENCIAL',
        ativo: true,
        versaoAtiva: 1,
        duracaoBlocoMinutos: 60,
        tipoPeriodo: 'UM_MES',
        dataInicio: '2026-11-09',
        dataFim: '2026-12-09',
        quantidadeBlocos: 48,
        cargaMinutosPorDia: [120, 120, 120, 120, 120, 0, 0],
        estadoPublicacao: 'ATIVO'
      }
    ]);

    const lista = await EstudosService.listarCronogramas('trilha-123');

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/cronogramas');
    expect(lista).toHaveLength(1);
    expect(lista[0].modalidade).toBe('SEQUENCIAL');
    expect(lista[0].ativo).toBe(true);
  });

  it('deve criar cronograma enviando payload completo', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'crono-2',
      trilhaId: 'trilha-123',
      modalidade: 'PONDERADO',
      ativo: true,
      versaoAtiva: 1,
      duracaoBlocoMinutos: 50,
      tipoPeriodo: 'ATE_PROVA',
      dataInicio: '2026-11-09',
      dataFim: '2026-12-15',
      quantidadeBlocos: 60,
      cargaMinutosPorDia: [100, 100, 100, 100, 100, 50, 0],
      estadoPublicacao: 'ATIVO'
    });

    const payload = {
      modalidade: 'PONDERADO' as const,
      duracaoBlocoMinutos: 50,
      tipoPeriodo: 'ATE_PROVA',
      dataInicio: '2026-11-09',
      dataFim: '2026-12-15',
      diasAtivosSemana: [1, 2, 3, 4, 5, 6],
      blocosPorDia: 2,
      disciplinasParticipantesIds: ['d1', 'd2']
    };

    const criado = await EstudosService.criarCronograma('trilha-123', payload);

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/cronogramas', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    expect(criado.id).toBe('crono-2');
    expect(criado.modalidade).toBe('PONDERADO');
  });

  it('deve atualizar cronograma regenerando blocos', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'crono-1',
      trilhaId: 'trilha-123',
      modalidade: 'SEQUENCIAL',
      ativo: true,
      versaoAtiva: 2,
      duracaoBlocoMinutos: 60,
      tipoPeriodo: 'TRES_MESES',
      dataInicio: '2026-11-09',
      dataFim: '2027-02-09',
      quantidadeBlocos: 120,
      cargaMinutosPorDia: [120, 120, 120, 120, 120, 0, 0],
      estadoPublicacao: 'ATIVO'
    });

    const payload = {
      modalidade: 'SEQUENCIAL' as const,
      duracaoBlocoMinutos: 60,
      tipoPeriodo: 'TRES_MESES',
      dataInicio: '2026-11-09',
      dataFim: '2027-02-09',
      diasAtivosSemana: [1, 2, 3, 4, 5],
      blocosPorDia: 2,
      disciplinasParticipantesIds: ['d1']
    };

    const atualizado = await EstudosService.atualizarCronograma('trilha-123', 'crono-1', payload);

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/cronogramas/crono-1', {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
    expect(atualizado.versaoAtiva).toBe(2);
  });

  it('deve ativar e excluir cronograma sob rotas REST dedicadas', async () => {
    const ativarSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'crono-2',
      trilhaId: 'trilha-123',
      modalidade: 'MANUAL',
      ativo: true,
      versaoAtiva: 1,
      duracaoBlocoMinutos: 60,
      tipoPeriodo: 'UM_MES',
      dataInicio: '2026-11-09',
      dataFim: '2026-12-09',
      quantidadeBlocos: 30,
      cargaMinutosPorDia: [60, 60, 60, 60, 60, 0, 0]
    });

    const ativado = await EstudosService.ativarCronograma('trilha-123', 'crono-2');
    expect(ativarSpy).toHaveBeenCalledWith('/trilhas/trilha-123/cronogramas/crono-2/ativar', {
      method: 'PATCH'
    });
    expect(ativado.ativo).toBe(true);

    const excluirSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce(undefined);
    await EstudosService.excluirCronograma('trilha-123', 'crono-2');
    expect(excluirSpy).toHaveBeenCalledWith('/trilhas/trilha-123/cronogramas/crono-2', {
      method: 'DELETE'
    });
  });

  it('deve listar blocos paginados de um cronograma', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      itens: [
        {
          id: 'b-1',
          trilhaId: 'trilha-123',
          cronogramaId: 'crono-1',
          versaoGeracao: 1,
          data: '2026-11-09',
          ordem: 1,
          disciplinaId: 'd1',
          disciplinaNome: 'Direito Constitucional',
          disciplinaCor: '#4F46E5',
          duracaoMinutos: 60,
          concluido: false,
          concluidoEm: null,
          statusData: 'HOJE'
        }
      ],
      pagina: 1,
      tamanho: 50,
      totalItens: 40,
      totalPaginas: 1
    });

    const pagina = await EstudosService.listarBlocosCronograma('trilha-123', 'crono-1', {
      concluido: false,
      pagina: 1,
      tamanho: 50
    });

    expect(requestSpy).toHaveBeenCalledWith(
      '/trilhas/trilha-123/cronogramas/crono-1/blocos?concluido=false&pagina=1&tamanho=50'
    );
    expect(pagina.itens).toHaveLength(1);
    expect(pagina.itens[0].disciplinaNome).toBe('Direito Constitucional');
  });

  it('deve consultar blocos pendentes de hoje', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce([
      {
        id: 'b-1',
        blocoId: 'b-1',
        trilhaId: 'trilha-123',
        trilhaNome: 'Trilha Auditor Fiscal',
        cronogramaId: 'crono-1',
        disciplinaId: 'd1',
        disciplinaNome: 'Contabilidade',
        disciplinaCor: '#059669',
        data: '2026-11-09',
        ordem: 1,
        duracaoMinutos: 60,
        concluido: false,
        statusData: 'hoje'
      }
    ]);

    const hoje = await EstudosService.obterBlocosHoje();

    expect(requestSpy).toHaveBeenCalledWith('/blocos/hoje');
    expect(hoje).toHaveLength(1);
    expect(hoje[0].trilhaNome).toBe('Trilha Auditor Fiscal');
    expect(hoje[0].concluido).toBe(false);
  });

  it('deve atualizar conclusão individual de bloco idempotentemente', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'b-1',
      trilhaId: 'trilha-123',
      cronogramaId: 'crono-1',
      versaoGeracao: 1,
      data: '2026-11-09',
      ordem: 1,
      disciplinaId: 'd1',
      disciplinaNome: 'Português',
      disciplinaCor: '#DC2626',
      duracaoMinutos: 60,
      concluido: true,
      concluidoEm: '2026-11-09T15:00:00Z',
      statusData: 'HOJE'
    });

    const atualizado = await EstudosService.atualizarConclusaoBlocoIndividual('b-1', true);

    expect(requestSpy).toHaveBeenCalledWith('/blocos/b-1/conclusao', {
      method: 'PATCH',
      body: JSON.stringify({ concluido: true })
    });
    expect(atualizado.concluido).toBe(true);
    expect(atualizado.concluidoEm).toBe('2026-11-09T15:00:00Z');
  });

  it('deve concluir blocos atrasados em lote com operationId', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      operationId: 'op-12345',
      totalProcessados: 5,
      totalConcluidos: 5,
      falhas: []
    });

    const resultado = await EstudosService.concluirBlocosAtrasados('trilha-123', 'crono-1', 'op-12345');

    expect(requestSpy).toHaveBeenCalledWith(
      '/trilhas/trilha-123/cronogramas/crono-1/blocos/concluir-atrasados',
      {
        method: 'POST',
        body: JSON.stringify({ operationId: 'op-12345' })
      }
    );
    expect(resultado.totalConcluidos).toBe(5);
    expect(resultado.falhas).toHaveLength(0);
  });

  it('deve obter e atualizar disponibilidade semanal do usuário', async () => {
    const getSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      disponibilidadeSemanalMinutos: [120, 120, 120, 120, 120, 60, 0],
      cargaAtivaMinutosPorDia: [100, 100, 100, 100, 100, 0, 0],
      sobrecargaPorDia: [false, false, false, false, false, false, false]
    });

    const disp = await EstudosService.obterDisponibilidadeSemanal();
    expect(getSpy).toHaveBeenCalledWith('/usuarios/me/disponibilidade-semanal');
    expect(disp.disponibilidadeSemanalMinutos[0]).toBe(120);

    const putSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      disponibilidadeSemanalMinutos: [180, 180, 180, 180, 180, 60, 0],
      cargaAtivaMinutosPorDia: [100, 100, 100, 100, 100, 0, 0],
      sobrecargaPorDia: [false, false, false, false, false, false, false]
    });

    const atualizada = await EstudosService.atualizarDisponibilidadeSemanal([180, 180, 180, 180, 180, 60, 0]);
    expect(putSpy).toHaveBeenCalledWith('/usuarios/me/disponibilidade-semanal', {
      method: 'PUT',
      body: JSON.stringify({ disponibilidadeSemanalMinutos: [180, 180, 180, 180, 180, 60, 0] })
    });
    expect(atualizada.disponibilidadeSemanalMinutos[0]).toBe(180);
  });
});
