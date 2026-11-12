import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { getPomodoroTimerStorageKey } from '../modules/pomodoro/PomodoroApp';
import { __resetarParaTeste, definirUsuarioCorrente } from '../shared/sync/barramento';

describe('pomodoro (R6/D-21) — namespace anônimo separado do cronômetro de Estudos', () => {
  beforeEach(() => {
    localStorage.clear();
    __resetarParaTeste();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('visitante usa trilhara:anon:pomodoro_timer', () => {
    expect(getPomodoroTimerStorageKey(null)).toBe('trilhara:anon:pomodoro_timer');
    expect(getPomodoroTimerStorageKey(undefined)).toBe('trilhara:anon:pomodoro_timer');
  });

  it('usuário autenticado usa trilhara:u:{usuarioId}:pomodoro_timer', () => {
    expect(getPomodoroTimerStorageKey('6af20000000000000000000a')).toBe(
      'trilhara:u:6af20000000000000000000a:pomodoro_timer'
    );
  });

  it('login não migra o estado anônimo para a chave da conta', () => {
    const estadoAnonimo = {
      pomodoroMode: true,
      timerSeconds: 900,
      pomodoroDurationSeconds: 1500,
      pausas: { short: 5, long: 15 },
      somAtivo: false,
      notificacaoAtiva: false,
      updatedAt: Date.now()
    };
    localStorage.setItem('trilhara:anon:pomodoro_timer', JSON.stringify(estadoAnonimo));

    // Simula a autenticação: o app lê a chave nova, sem copiar a anônima.
    definirUsuarioCorrente('6af20000000000000000000a');
    const chaveConta = getPomodoroTimerStorageKey('6af20000000000000000000a');

    expect(localStorage.getItem(chaveConta)).toBeNull();
    expect(JSON.parse(localStorage.getItem('trilhara:anon:pomodoro_timer') ?? '{}')).toMatchObject({
      timerSeconds: 900
    });
  });

  it('não há chaves globais de configuração do Pomodoro', () => {
    const proibidas = [
      'trilhara_pomodoro_focus_minutes',
      'trilhara_pomodoro_focus_seconds',
      'trilhara_pomodoro_config',
      'trilhara_pomodoro_mode_active',
      'trilhara_pomodoro_short',
      'trilhara_pomodoro_long'
    ];
    for (const chave of proibidas) {
      expect(localStorage.getItem(chave)).toBeNull();
    }
  });

  it('configuração pertence ao objeto persistido da conta (pausas, som, notificação)', () => {
    const chave = getPomodoroTimerStorageKey('userA');
    localStorage.setItem(
      chave,
      JSON.stringify({
        pomodoroMode: false,
        timerSeconds: 0,
        pomodoroDurationSeconds: 3000,
        pausas: { short: 10, long: 20 },
        somAtivo: false,
        notificacaoAtiva: true,
        updatedAt: Date.now()
      })
    );

    const salvo = JSON.parse(localStorage.getItem(chave) ?? '{}');
    expect(salvo.pausas).toEqual({ short: 10, long: 20 });
    expect(salvo.somAtivo).toBe(false);
    expect(salvo.pomodoroDurationSeconds).toBe(3000);
  });
});
