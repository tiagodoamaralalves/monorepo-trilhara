export function hojeISO(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function dataLocal(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

export function parseDataLocal(valor: string): Date {
  const [ano, mes, dia] = valor.split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

export function adicionarDias(dataISO: string, dias: number): string {
  const data = parseDataLocal(dataISO);
  data.setDate(data.getDate() + dias);
  return dataLocal(data);
}

export const NOMES_DIAS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

export function criarGradeMes(mesVisivel: Date): Date[] {
  const primeiroDia = new Date(mesVisivel.getFullYear(), mesVisivel.getMonth(), 1);
  const inicio = new Date(primeiroDia);
  inicio.setDate(primeiroDia.getDate() - ((primeiroDia.getDay() + 6) % 7));
  return Array.from({ length: 42 }, (_, indice) => {
    const dia = new Date(inicio);
    dia.setDate(inicio.getDate() + indice);
    return dia;
  });
}

export function criarGradeSemana(inicioISO: string): Date[] {
  const inicio = parseDataLocal(inicioISO);
  return Array.from({ length: 7 }, (_, indice) => {
    const dia = new Date(inicio);
    dia.setDate(inicio.getDate() + indice);
    return dia;
  });
}
