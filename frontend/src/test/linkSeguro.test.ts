import { describe, it, expect } from 'vitest';
import { protocoloDeLinkPermitido, LinkSeguro } from '../modules/notas/components/Editor/linkSeguro';

/**
 * Política de links do editor de Notas.
 *
 * O mark existe porque `tiptap-markdown` só fornece o serializador de link; sem ele `[a](b)`
 * sairia do schema. Aqui verificamos a allowlist de protocolos e os atributos fixos.
 */
describe('protocoloDeLinkPermitido — allowlist', () => {
  it('aceita http, https, mailto e caminhos relativos', () => {
    expect(protocoloDeLinkPermitido('http://exemplo.com')).toBe(true);
    expect(protocoloDeLinkPermitido('https://exemplo.com/pagina')).toBe(true);
    expect(protocoloDeLinkPermitido('HTTPS://EXEMPLO.COM')).toBe(true);
    expect(protocoloDeLinkPermitido('mailto:alguem@exemplo.com')).toBe(true);
    expect(protocoloDeLinkPermitido('/notas/123')).toBe(true);
    expect(protocoloDeLinkPermitido('#ancora')).toBe(true);
  });

  it('rejeita esquemas executáveis e valores não textuais', () => {
    expect(protocoloDeLinkPermitido('javascript:alert(1)')).toBe(false);
    expect(protocoloDeLinkPermitido('JaVaScRiPt:alert(1)')).toBe(false);
    expect(protocoloDeLinkPermitido('data:text/html;base64,PHNjcmlwdD4=')).toBe(false);
    expect(protocoloDeLinkPermitido('vbscript:msgbox(1)')).toBe(false);
    expect(protocoloDeLinkPermitido('file:///etc/passwd')).toBe(false);
    expect(protocoloDeLinkPermitido('')).toBe(false);
    expect(protocoloDeLinkPermitido('   ')).toBe(false);
    expect(protocoloDeLinkPermitido(null)).toBe(false);
    expect(protocoloDeLinkPermitido(undefined)).toBe(false);
  });

  it('rejeita href com caracteres de controle embutidos', () => {
    expect(protocoloDeLinkPermitido('java\u0000script:alert(1)')).toBe(false);
    expect(protocoloDeLinkPermitido('https://exemplo.com/\nfoobar')).toBe(false);
  });

  it('o mark canônico se chama "link" para herdar o serializador do tiptap-markdown', () => {
    expect(LinkSeguro.name).toBe('link');
  });

  it('renderiza sempre rel e target fixos, ignorando valores do documento', () => {
    const render = LinkSeguro.config.renderHTML;
    expect(render).toBeTypeOf('function');

    const saida = render!.call(
      LinkSeguro as never,
      {
        node: {} as never,
        HTMLAttributes: { href: 'https://exemplo.com', target: '_self', rel: 'opener' }
      } as never
    ) as unknown as [string, Record<string, string>, number];

    expect(saida[0]).toBe('a');
    expect(saida[1].href).toBe('https://exemplo.com');
    expect(saida[1].target).toBe('_blank');
    expect(saida[1].rel).toBe('noopener noreferrer nofollow');
  });

  it('não emite href navegável quando o protocolo não é permitido', () => {
    const render = LinkSeguro.config.renderHTML;

    const saida = render!.call(
      LinkSeguro as never,
      { node: {} as never, HTMLAttributes: { href: 'javascript:alert(1)' } } as never
    ) as unknown as [string, Record<string, string>, number];

    expect(saida[1].href).toBeUndefined();
    expect(saida[1].target).toBeUndefined();
    expect(saida[1].rel).toBeUndefined();
  });
});
