import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';

/**
 * ESLint flat config — R0 / D-44.
 *
 * Escopo desta fase: registrar as regras e MEDIR os achados. As correções
 * pertencem às fases de domínio e a R13 (D-44 exige zero ao final).
 *
 * Regras exigidas por D-44:
 *  - @typescript-eslint/no-unused-vars: error
 *  - react-hooks/*
 *  - jsx-a11y/recommended
 *  - no-console: error
 *  - proibição de HTML perigoso (equivalente a react/no-danger; o plugin
 *    eslint-plugin-react não está nas dependências aprovadas, então a regra é
 *    expressa via no-restricted-syntax + no-restricted-properties).
 *  - `react/no-danger` é mapeada para no-restricted-syntax cobrindo
 *    dangerouslySetInnerHTML em JSX.
 */
export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.es2022,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'no-console': 'error',
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message:
            'dangerouslySetInnerHTML é proibido (XSS). Use renderização por nós React.',
        },
      ],
    },
  },
  {
    // Testes: o Anexo C permite console.* em código de teste.
    files: ['src/test/**/*.{ts,tsx}'],
    rules: {
      'no-console': 'off',
    },
  },
);
