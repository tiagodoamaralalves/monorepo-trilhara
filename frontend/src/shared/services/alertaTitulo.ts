/**
 * Alerta de título da aba, compartilhado por Estudos e Pomodoro.
 *
 * Antes existiam **duas** cópias idênticas desta lógica (`alarmService.ts` e
 * `PomodoroApp.tsx`), com `setInterval` de 900 ms alternando `document.title` por ~24
 * iterações — drift garantido e nenhuma delas respeitava `prefers-reduced-motion`.
 *
 * Requisitos atendidos:
 * WCAG 2.2 §12.1.5: com movimento reduzido, o título recebe `⏰ {mensagem}` **estático**
 * (informa sem piscar) e é restaurado no foco da janela ou ao fim do tempo máximo;
 * restauração determinística: `restaurarTituloAba` é idempotente e também chamada
 * quando a aba recebe foco.
 */

const INTERVALO_MS = 900;
const MAXIMO_ITERACOES = 24;

let intervalo: number | null = null;
let tituloOriginal = typeof document !== 'undefined' ? document.title : '';
let limparAoFocar: (() => void) | null = null;

function encerrarIntervalo(): void {
  if (intervalo !== null) {
    clearInterval(intervalo);
    intervalo = null;
  }
}

/** Restaura o título original e remove listeners. Seguro chamar mais de uma vez. */
export function restaurarTituloAba(): void {
  encerrarIntervalo();
  if (typeof document !== 'undefined') {
    document.title = tituloOriginal;
  }
  if (limparAoFocar && typeof window !== 'undefined') {
    window.removeEventListener('focus', limparAoFocar);
    limparAoFocar = null;
  }
}

export interface AlertaTituloOptions {
  /** Quando `true`, não alterna o título: exibe a mensagem uma única vez. */
  reduzirMovimento?: boolean;
}

/**
 * Versão não-hook da preferência de movimento reduzido.
 *
 * Para serviços fora do React (`AlarmService`) que não podem usar
 * `useReducedMotion`. Degrada para `false` sem `matchMedia`.
 */
export function preferenciaMovimentoReduzido(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Anuncia no título da aba que um timer terminou.
 *
 * @param mensagem texto curto, ex.: "Pausa curta finalizada!"
 */
export function alertarTitulo(mensagem: string, options: AlertaTituloOptions = {}): void {
  if (typeof document === 'undefined') return;

  encerrarIntervalo();
  if (limparAoFocar && typeof window !== 'undefined') {
    window.removeEventListener('focus', limparAoFocar);
    limparAoFocar = null;
  }

  tituloOriginal = document.title;

  if (options.reduzirMovimento) {
    document.title = `⏰ ${mensagem}`;
    if (typeof window !== 'undefined') {
      limparAoFocar = () => restaurarTituloAba();
      window.addEventListener('focus', limparAoFocar);
    }
    return;
  }

  let alternar = false;
  let iteracoes = 0;

  intervalo = window.setInterval(() => {
    document.title = alternar ? `⏰ ${mensagem}` : tituloOriginal;
    alternar = !alternar;
    iteracoes += 1;
    if (iteracoes > MAXIMO_ITERACOES) {
      restaurarTituloAba();
    }
  }, INTERVALO_MS);

  limparAoFocar = () => restaurarTituloAba();
  window.addEventListener('focus', limparAoFocar);
}
