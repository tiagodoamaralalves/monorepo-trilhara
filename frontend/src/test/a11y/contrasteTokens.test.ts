import { readFileSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

/**
 * Contraste dos tokens de tema (WCAG 2.2 AA) — R12.
 *
 * O axe em jsdom não calcula contraste (`color-contrast` é inaplicável sem layout
 * real), então o critério "texto/fundo ≥ 4.5:1 e UI/gráfico ≥ 3:1" é provado aqui,
 * diretamente sobre os tokens declarados em `shared/styles/index.css`.
 */

const CSS = readFileSync(resolve(__dirname, '../../shared/styles/index.css'), 'utf-8');

type Tokens = Record<string, string>;

/** Extrai as custom properties de um bloco cujo seletor contenha `marcador`. */
function tokensDoBloco(marcador: string): Tokens {
  const semComentarios = CSS.replace(/\/\*[\s\S]*?\*\//g, '');
  const tokens: Tokens = {};

  let i = 0;
  let seletor = '';
  let profundidade = 0;
  let buffer = '';

  while (i < semComentarios.length) {
    const c = semComentarios[i];

    if (c === '{') {
      if (profundidade === 0) seletor = buffer.trim();
      profundidade += 1;
      buffer = '';
      i += 1;
      continue;
    }

    if (c === '}') {
      profundidade -= 1;
      if (profundidade === 0 && seletor.includes(marcador)) {
        for (const decl of buffer.split(';')) {
          const [nome, ...resto] = decl.split(':');
          if (!nome.trim().startsWith('--')) continue;
          tokens[nome.trim()] = resto.join(':').trim();
        }
      }
      buffer = '';
      i += 1;
      continue;
    }

    if (profundidade >= 1) {
      buffer += c;
    } else if (c === ';') {
      buffer = '';
    } else {
      buffer += c;
    }
    i += 1;
  }

  return tokens;
}

const escuro = tokensDoBloco("data-theme='dark'");
const claro = tokensDoBloco("data-theme='light'");

/** Resolve nomes de token e cadeias de `var(--x)` até chegar a um literal de cor. */
function resolver(valor: string, tokens: Tokens, visitados: Set<string> = new Set()): string {
  const bruto = valor.trim();
  const nome = bruto.startsWith('--')
    ? bruto
    : bruto.match(/^var\((--[a-z0-9-]+)\)$/i)?.[1];

  if (!nome) return bruto;
  if (visitados.has(nome)) throw new Error(`Ciclo de var() em ${nome}`);
  const alvo = tokens[nome];
  if (!alvo) throw new Error(`Token ${nome} não declarado`);
  visitados.add(nome);
  return resolver(alvo, tokens, visitados);
}

function paraRgb(cor: string): [number, number, number] {
  const hex = cor.replace('#', '').trim();
  if (!/^[0-9a-f]{6}$/i.test(hex)) {
    throw new Error(`Esperado hexadecimal de 6 dígitos, recebido: ${cor}`);
  }
  return [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255) as [number, number, number];
}

function luminancia([r, g, b]: [number, number, number]): number {
  const linear = (canal: number) =>
    canal <= 0.03928 ? canal / 12.92 : Math.pow((canal + 0.055) / 1.055, 2.4);
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

function contraste(frente: string, fundo: string, tokens: Tokens): number {
  const l1 = luminancia(paraRgb(resolver(frente, tokens)));
  const l2 = luminancia(paraRgb(resolver(fundo, tokens)));
  const [maior, menor] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (maior + 0.05) / (menor + 0.05);
}

interface ParContraste {
  rotulo: string;
  frente: string;
  fundo: string;
  minimo: number;
}

const SUPERFICIES = ['--bg', '--bg-elevated', '--surface-1', '--surface-2', '--surface-3'];

function sobreSuperficies(frente: string, rotulo: string, minimo = 4.5): ParContraste[] {
  return SUPERFICIES.map((fundo) => ({ rotulo: `${rotulo} sobre ${fundo}`, frente, fundo, minimo }));
}

/** Texto normal (mínimo 4.5:1). */
const PARES_TEXTO: ParContraste[] = [
  ...sobreSuperficies('--text', 'texto principal'),
  ...sobreSuperficies('--text-mid', 'texto secundário'),
  ...sobreSuperficies('--text-soft', 'texto suave'),
  ...sobreSuperficies('--green-text', 'texto de sucesso'),
  ...sobreSuperficies('--red-text', 'texto de perigo'),
  ...sobreSuperficies('--yellow', 'texto de alerta'),
  ...sobreSuperficies('--purple-text', 'texto roxo'),
  ...sobreSuperficies('--pink-text', 'texto rosa'),
  { rotulo: 'contraste do preenchimento primário', frente: '--primary-contrast', fundo: '--primary', minimo: 4.5 },
  {
    rotulo: 'texto de controle sobre preenchimento primário',
    frente: '--on-primary-solid',
    fundo: '--primary',
    minimo: 4.5
  }
];

/** Componentes de UI / elementos gráficos (mínimo 3:1). */
const PARES_UI: ParContraste[] = [
  { rotulo: 'anel de foco sobre fundo', frente: '--border-focus', fundo: '--bg', minimo: 3 },
  { rotulo: 'anel de foco sobre superfície', frente: '--border-focus', fundo: '--surface-1', minimo: 3 },
  { rotulo: 'limite de controle sobre superfície', frente: '--border-control', fundo: '--surface-1', minimo: 3 },
  { rotulo: 'limite de controle sobre control-bg', frente: '--border-control', fundo: '--control-bg', minimo: 3 }
];

describe.each([
  ['tema escuro (Obsidian Dark UI)', escuro],
  ['tema claro (Daylight Editorial UI)', claro]
])('%s — contraste dos tokens', (_nome, tokens) => {
  it('declara os tokens de tema esperados', () => {
    expect(Object.keys(tokens).length).toBeGreaterThan(40);
    expect(tokens['--bg']).toBeDefined();
    expect(tokens['--text']).toBeDefined();
    expect(tokens['--primary']).toBeDefined();
  });

  it.each(PARES_TEXTO)('$rotulo atende 4.5:1', ({ rotulo, frente, fundo, minimo }) => {
    const razao = contraste(frente, fundo, tokens);
    expect(razao, `${rotulo}: ${frente} sobre ${fundo} = ${razao.toFixed(2)}`).toBeGreaterThanOrEqual(minimo);
  });

  it.each(PARES_UI)('$rotulo atende 3:1', ({ rotulo, frente, fundo, minimo }) => {
    const razao = contraste(frente, fundo, tokens);
    expect(razao, `${rotulo}: ${frente} sobre ${fundo} = ${razao.toFixed(2)}`).toBeGreaterThanOrEqual(minimo);
  });

  it('usa numeração tabular em cronômetros, contadores e tabelas', () => {
    expect(CSS).toContain('font-variant-numeric: tabular-nums');
  });
});
