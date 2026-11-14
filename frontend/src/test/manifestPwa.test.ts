import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';

/**
 * / §11 — requisito técnico de PWA para Web Push no iOS 16.4+.
 *
 * <p>O manifest precisa existir na raiz, ser servido como `/manifest.webmanifest` e ter
 * `display: standalone`, senão o Safari não oferece a instalação que habilita o push.</p>
 */
describe('manifest.webmanifest — requisito de iOS Web Push', () => {
  const caminho = path.resolve(__dirname, '../../public/manifest.webmanifest');

  it('existe na raiz pública', () => {
    expect(fs.existsSync(caminho)).toBe(true);
  });

  it('declara display standalone e identidade do produto', () => {
    const manifest = JSON.parse(fs.readFileSync(caminho, 'utf-8'));

    expect(manifest.display).toBe('standalone');
    expect(manifest.name).toBe('Trilhara');
    expect(manifest.short_name).toBe('Trilhara');
    expect(manifest.start_url).toBe('/');
    expect(Array.isArray(manifest.icons)).toBe(true);
    expect(manifest.icons.length).toBeGreaterThan(0);
  });

  it('está vinculado no index.html', () => {
    const html = fs.readFileSync(path.resolve(__dirname, '../../index.html'), 'utf-8');
    expect(html).toContain('rel="manifest"');
    expect(html).toContain('/manifest.webmanifest');
  });
});
