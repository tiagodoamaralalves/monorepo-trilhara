import React from 'react';
import {
  Plus,
  Clock,
  CheckCircle,
  Pencil,
  Library,
  Route
} from 'lucide-react';
import {
  Button,
  Badge,
  PageBanner,
  Skeleton,
  EmptyState,
  SectionErrorCard
} from '@shared/design-system';
import { TrilhaResumoDTO, PaginaAtiva } from '../types/trilhara';
import {
  TrilhaCard,
  TrilhaCardTag,
  TrilhaGrid,
  trilhaCardStyles
} from '../components/TrilhaCard/TrilhaCard';
import { diasAte, formatarDataBR } from '../utils/format';
import styles from './HubView.module.css';

export type StatusCarregamento = 'carregando' | 'sucesso' | 'erro';

export interface HubViewProps {
  trilhas: TrilhaResumoDTO[] | undefined;
  status: StatusCarregamento;
  recarregando?: boolean;
  onRecarregar: () => void;
  trilhaAtivaId?: string;
  onSelecionarTrilha: (id: string) => void;
  onNavegar: (pagina: PaginaAtiva) => void;
  onAbrirNovaTrilha: () => void;
  onEditarTrilha: (trilha: TrilhaResumoDTO) => void;
}

export const HubView: React.FC<HubViewProps> = ({
  trilhas,
  status,
  recarregando = false,
  onRecarregar,
  trilhaAtivaId,
  onSelecionarTrilha,
  onNavegar,
  onAbrirNovaTrilha,
  onEditarTrilha
}) => {
  const renderConteudo = () => {
    if (status === 'carregando') {
      return (
        <div className={styles.skeletonGrid} data-testid="hub-skeleton">
          <Skeleton variant="card" carregando />
          <Skeleton variant="card" carregando />
          <Skeleton variant="card" carregando />
        </div>
      );
    }

    if (status === 'erro') {
      return (
        <SectionErrorCard
          titulo="Não foi possível carregar"
          descricao="Verifique sua conexão e tente novamente."
          onRetry={onRecarregar}
          recarregando={recarregando}
        />
      );
    }

    const lista = trilhas ?? [];
    if (lista.length === 0) {
      return (
        <EmptyState
          icone={<Route size={44} />}
          titulo="Crie sua primeira trilha"
          descricao="Defina o concurso, as disciplinas e os assuntos para acompanhar sua evolução."
          acaoPrimaria={
            <>
              <Button variant="primary" leftIcon={<Plus size={16} />} onClick={onAbrirNovaTrilha}>
                Nova trilha
              </Button>
              <Button variant="ghost" leftIcon={<Library size={15} />} onClick={() => onNavegar('catalogo')}>
                Catálogo de Trilhas
              </Button>
            </>
          }
        />
      );
    }

    return (
      <TrilhaGrid>
        {lista.map((trilha) => renderCard(trilha))}
      </TrilhaGrid>
    );
  };

  const renderCard = (trilha: TrilhaResumoDTO) => {
    const isAtiva = trilha.id === trilhaAtivaId;
    const prazo = diasAte(trilha.dataProva);
    const pct = Math.round(trilha.percentualProgresso ?? 0);

    return (
      <TrilhaCard
        key={trilha.id}
        ativo={isAtiva}
        nome={trilha.nome}
        subtitulo={[trilha.cargo, trilha.banca].filter(Boolean).join(' - ') || 'Foco em Concurso'}
        selo={
          isAtiva ? (
            <TrilhaCardTag>Trilha Selecionada</TrilhaCardTag>
          ) : (
            <Badge variant="primary" size="sm">
              {trilha.banca || 'Geral'}
            </Badge>
          )
        }
        acao={
          <Button
            type="button"
            variant="ghost"
            size="sm"
            iconOnly
            className={trilhaCardStyles.btnEditar}
            leftIcon={<Pencil size={15} />}
            title="Editar trilha"
            aria-label={`Editar trilha ${trilha.nome}`}
            onClick={(e) => {
              e.stopPropagation();
              onEditarTrilha(trilha);
            }}
          />
        }
        metricas={[
          { label: 'Data da Prova', value: formatarDataBR(trilha.dataProva) },
          {
            label: 'Tempo Restante',
            tone: prazo.isUrgente ? 'urgente' : undefined,
            value: (
              <>
                <Clock size={13} className={trilhaCardStyles.metricIcon} />
                {prazo.label}
              </>
            )
          },
          { label: 'Disciplinas', value: trilha.totalDisciplinas },
          {
            label: 'Progresso Geral',
            tone: 'sucesso',
            value: (
              <>
                <CheckCircle size={13} className={trilhaCardStyles.metricIcon} />
                {pct}% ({trilha.topicosConcluidos}/{trilha.totalTopicos})
              </>
            )
          }
        ]}
        onClick={() => {
          onSelecionarTrilha(trilha.id);
          onNavegar('disciplinas');
        }}
      />
    );
  };

  return (
    <div className={styles.container}>
      <PageBanner
        title="Minhas trilhas de estudo"
        description="Trilhe seu caminho até a aprovação: acompanhe seu progresso, gerencie disciplinas e mantenha a constância no ciclo."
        icon={<Route size={26} />}
        actions={
          <div className={styles.bannerActions}>
            <Button
              variant="secondary"
              size="md"
              leftIcon={<Library size={15} />}
              onClick={() => onNavegar('catalogo')}
            >
              Catálogo de Trilhas
            </Button>
            <Button
              variant="primary"
              size="md"
              leftIcon={<Plus size={16} />}
              onClick={onAbrirNovaTrilha}
            >
              Nova Trilha
            </Button>
          </div>
        }
      />

      <section className={styles.section}>{renderConteudo()}</section>
    </div>
  );
};
