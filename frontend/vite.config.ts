/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  base: './',
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@shared': path.resolve(__dirname, './src/shared')
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    /**
     * R13/D-43: sob instrumentação de cobertura (`--coverage`) os testes de componente
     * montam árvores grandes com `user-event` e ficam 3–5× mais lentos; o padrão de 5 s do
     * Vitest gerava timeouts falsos (12 falhas em testes que passam em ~1 s sem cobertura).
     * 30 s é o teto de segurança, não o tempo esperado.
     */
    testTimeout: 30000,
    hookTimeout: 30000,
    /**
     * R13/D-43: cobertura em modo ENFORCE (ratchet sobre o valor medido).
     *
     * Medição real desta revisão (v8, 57 arquivos / 438 testes):
     *   global   lines 47,91% · functions 40,24% · statements 46,11% · branches 42,78%
     *   shared/  lines 69,9% (meta D-43: 80%)  · branches 61,4%
     *   modules/ lines 34,4% (meta D-43: 70%)  · branches 29,1%
     *
     * A meta de D-43 NÃO é atingida em `modules/` e o gap está registrado em
     * `docs/REVISAO-IMPLEMENTADA-R13.md` §Cobertura — não é declarado "Atendido".
     * Os limiares abaixo são o piso que o build não pode cruzar hoje (ratchet):
     * impedem regressão imediata e sobem conforme testes forem adicionados.
     *
     * Exclusões justificadas:
     * - `src/test/**`: o próprio código de teste;
     * - `src/**\/*.d.ts` e `src/vite-env.d.ts`: declarações sem código executável;
     * - `src/main.tsx`: bootstrap de 8 linhas (createRoot + tema inicial).
     */
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov', 'html'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/test/**',
        'src/**/*.d.ts',
        'src/vite-env.d.ts',
        'src/main.tsx'
      ],
      thresholds: {
        lines: 47,
        functions: 40,
        branches: 42,
        statements: 45
      }
    }
  }
});
