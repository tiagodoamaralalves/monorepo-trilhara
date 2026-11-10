export function hojeISO(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function paraDataLocalISO(isoTimestamp: string): string {
  if (!isoTimestamp) return '';
  const d = new Date(isoTimestamp);
  if (isNaN(d.getTime())) return isoTimestamp.slice(0, 10);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatarDataBR(dataISO?: string | null): string {
  if (!dataISO) return 'Não definida';
  const [y, m, d] = dataISO.split('-');
  return `${d}/${m}/${y}`;
}

export function diasAte(dataISO?: string | null): { label: string; isUrgente: boolean } {
  if (!dataISO) return { label: 'Ritmo contínuo', isUrgente: false };

  const [y, m, d] = dataISO.split('-').map(Number);
  const alvo = new Date(y, m - 1, d);
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);

  const diffMs = alvo.getTime() - hoje.getTime();
  const dias = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

  if (dias < 0) return { label: 'Realizada', isUrgente: false };
  if (dias === 0) return { label: 'Hoje!', isUrgente: true };
  if (dias === 1) return { label: '1 dia', isUrgente: true };
  return { label: `${dias} dias`, isUrgente: dias <= 30 };
}

export function provaEmAndamento(dataISO?: string | null): boolean {
  return Boolean(dataISO) && (dataISO as string) > hojeISO();
}

export function formatarDuracaoSegundos(segundosTotais: number): string {
  if (!segundosTotais || segundosTotais <= 0) return '0 min';
  const horas = Math.floor(segundosTotais / 3600);
  const mins = Math.floor((segundosTotais % 3600) / 60);
  if (horas > 0 && mins > 0) return `${horas}h ${mins}m`;
  if (horas > 0) return `${horas}h`;
  if (mins > 0) return `${mins} min`;
  const segs = segundosTotais % 60;
  return `${segs}s`;
}

export function formatarMinutos(minutosTotais: number): string {
  if (!minutosTotais || minutosTotais <= 0) return '0 min';
  const horas = Math.floor(minutosTotais / 60);
  const mins = minutosTotais % 60;
  if (horas > 0 && mins > 0) return `${horas}h ${mins}m`;
  if (horas > 0) return `${horas}h`;
  return `${mins} min`;
}

export function formatarSegundos(segundosTotais: number): string {
  const mins = Math.floor(segundosTotais / 60);
  const secs = segundosTotais % 60;
  const horas = Math.floor(mins / 60);
  const minsRestantes = mins % 60;

  if (horas > 0) {
    return `${String(horas).padStart(2, '0')}:${String(minsRestantes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${String(minsRestantes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function slug(texto: string): string {
  return texto
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

