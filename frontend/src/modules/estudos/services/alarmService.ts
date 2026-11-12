/**
 * Serviço de Alarmes e Notificações do Cronômetro Trilhara
 * - Síntese de áudio via Web Audio API (tons harmônicos de sino, sem dependência de arquivos externos)
 * - Notificações nativas do navegador (Desktop / OS Notification API)
 * - Alerta visual de alternância no título da aba
 */

import { registrarFalha } from '@shared/diagnostico';

const CHAVE_ALARME_SOM = 'trilhara_alarme_som';
const CHAVE_ALARME_NOTIF = 'trilhara_alarme_notif';

class SoundEngine {
  private ctx: AudioContext | null = null;

  private getContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    try {
      if (!this.ctx) {
        const AudioCtx = window.AudioContext
          || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        if (AudioCtx) {
          this.ctx = new AudioCtx();
        }
      }
      if (this.ctx && this.ctx.state === 'suspended') {
        void this.ctx.resume();
      }
      return this.ctx;
    } catch {
      return null;
    }
  }

  playChime(notes: number[], noteDuration = 0.2, type: OscillatorType = 'sine') {
    try {
      const ctx = this.getContext();
      if (!ctx) return;

      const now = ctx.currentTime;
      notes.forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = type;
        osc.frequency.setValueAtTime(freq, now + idx * noteDuration);

        const startTime = now + idx * noteDuration;
        const endTime = startTime + noteDuration * 1.8;

        gain.gain.setValueAtTime(0.0001, startTime);
        gain.gain.exponentialRampToValueAtTime(0.35, startTime + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, endTime);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(startTime);
        osc.stop(endTime);
      });
    } catch (err) {
      registrarFalha('alarme.som', err);
    }
  }

  // 1. Fim do tempo predefinido no ciclo atual (Acorde triunfal ascendente C5, E5, G5, C6)
  playCicloConcluido() {
    this.playChime([523.25, 659.25, 783.99, 1046.50], 0.22);
  }

  // 2. Fim da pausa curta (Toque amigável para retomar foco)
  playPausaCurtaConcluida() {
    this.playChime([659.25, 880.00, 659.25], 0.22);
  }

  // 3. Fim da pausa longa (Sequência melódica para novo bloco)
  playPausaLongaConcluida() {
    this.playChime([587.33, 783.99, 987.77], 0.24);
  }

  // Som de teste
  playTeste() {
    this.playChime([523.25, 659.25, 783.99], 0.18);
  }
}

const soundEngine = new SoundEngine();

let titleInterval: number | null = null;
let originalTitle = typeof document !== 'undefined' ? document.title : '';

function alertarAbaNavegador(mensagem: string) {
  if (typeof document === 'undefined') return;
  if (titleInterval) {
    clearInterval(titleInterval);
    titleInterval = null;
  }

  originalTitle = document.title;
  let alternar = false;
  let iteracoes = 0;

  titleInterval = window.setInterval(() => {
    document.title = alternar ? `⏰ ${mensagem}` : originalTitle;
    alternar = !alternar;
    iteracoes++;
    if (iteracoes > 24) {
      if (titleInterval) clearInterval(titleInterval);
      titleInterval = null;
      document.title = originalTitle;
    }
  }, 900);

  const limparAoFocar = () => {
    if (titleInterval) {
      clearInterval(titleInterval);
      titleInterval = null;
    }
    document.title = originalTitle;
    window.removeEventListener('focus', limparAoFocar);
  };
  window.addEventListener('focus', limparAoFocar);
}

export const AlarmService = {
  isSomAtivado(): boolean {
    if (typeof window === 'undefined') return true;
    const item = window.localStorage.getItem(CHAVE_ALARME_SOM);
    return item === null ? true : item === 'true';
  },

  setSomAtivado(ativado: boolean) {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(CHAVE_ALARME_SOM, String(ativado));
  },

  isNotificacaoAtivada(): boolean {
    if (typeof window === 'undefined') return true;
    const item = window.localStorage.getItem(CHAVE_ALARME_NOTIF);
    return item === null ? true : item === 'true';
  },

  setNotificacaoAtivada(ativado: boolean) {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(CHAVE_ALARME_NOTIF, String(ativado));
  },

  async solicitarPermissaoNotificacao(): Promise<boolean> {
    if (typeof window === 'undefined' || !('Notification' in window)) return false;
    if (Notification.permission === 'granted') return true;
    if (Notification.permission === 'denied') return false;

    try {
      const resultado = await Notification.requestPermission();
      return resultado === 'granted';
    } catch {
      return false;
    }
  },

  enviarNotificacao(titulo: string, corpo: string) {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    if (!this.isNotificacaoAtivada()) return;
    if (Notification.permission !== 'granted') return;

    try {
      const notif = new Notification(titulo, {
        body: corpo,
        icon: '/favicon.svg',
        silent: true // Mantém mudo na notificação do SO pois tocamos nosso próprio chime
      });
      notif.onclick = () => {
        window.focus();
        notif.close();
      };
    } catch (err) {
      registrarFalha('alarme.notificacao', err);
    }
  },

  // Disparos dos 3 alarmes solicitados:

  /**
   * Alarme 1: Fim do tempo predefinido no ciclo atual (duracaoMinutos)
   */
  dispararAlarmeFimCiclo(duracaoMinutos: number) {
    if (this.isSomAtivado()) {
      soundEngine.playCicloConcluido();
    }
    const titulo = 'Meta do ciclo concluída!';
    const corpo = `Você atingiu o tempo planejado de ${duracaoMinutos} min para este bloco. Excelente trabalho!`;
    this.enviarNotificacao(titulo, corpo);
    alertarAbaNavegador('Meta do ciclo concluída!');
  },

  /**
   * Alarme 2: Fim da pausa curta
   */
  dispararAlarmeFimPausaCurta() {
    if (this.isSomAtivado()) {
      soundEngine.playPausaCurtaConcluida();
    }
    const titulo = 'Pausa curta finalizada!';
    const corpo = 'Sua pausa curta terminou. Pronto para retomar o foco nos estudos?';
    this.enviarNotificacao(titulo, corpo);
    alertarAbaNavegador('Pausa curta finalizada!');
  },

  /**
   * Alarme 3: Fim da pausa longa
   */
  dispararAlarmeFimPausaLonga() {
    if (this.isSomAtivado()) {
      soundEngine.playPausaLongaConcluida();
    }
    const titulo = 'Pausa longa finalizada!';
    const corpo = 'Seu descanso longo terminou. Que tal iniciar o próximo bloco de estudos?';
    this.enviarNotificacao(titulo, corpo);
    alertarAbaNavegador('Pausa longa finalizada!');
  },

  /**
   * Teste sonoro
   */
  tocarTeste() {
    soundEngine.playTeste();
  }
};

