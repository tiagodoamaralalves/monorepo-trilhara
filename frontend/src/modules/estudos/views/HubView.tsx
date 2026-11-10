import React from 'react';
import {
  Plus,
  Clock,
  CheckCircle,
  Pencil,
  Library,
  Route
} from 'lucide-react';
import { Button, Badge, PageBanner } from '@shared/design-system';
import { EmptyJourneyState } from '../components/EmptyJourneyState/EmptyJourneyState';
import { Trilha, PaginaAtiva } from '../types/trilhara';
import {
  TrilhaCard,
  TrilhaCardTag,
  TrilhaGrid,
  trilhaCardStyles
} from '../components/TrilhaCard/TrilhaCard';
import { diasAte, formatarDataBR } from '../utils/format';
import styles from './HubView.module.css';

export interface HubViewProps {
  trilhas: Trilha[];
  trilhaAtivaId?: string;
  onSelecionarTrilha: (id: string) => void;
  onNavegar: (pagina: PaginaAtiva) => void;
  onAbrirNovaTrilha: () => void;
  onEditarTrilha: (trilha: Trilha) => void;
}

export const HubView: React.FC<HubViewProps> = ({
  trilhas,
  trilhaAtivaId,
  onSelecionarTrilha,
  onNavegar,
  onAbrirNovaTrilha,
  onEditarTrilha
}) => {
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

      <section className={styles.section}>
        {trilhas.length === 0 ? (
          <EmptyJourneyState
            hasTrilha={false}
            onPrimaryAction={onAbrirNovaTrilha}
            onViewCatalog={() => onNavegar('catalogo')}
          />
        ) : (
          <TrilhaGrid>
            {trilhas.map((trilha) => {
              const isAtiva = trilha.id === trilhaAtivaId;
              const prazo = diasAte(trilha.dataProva);

              // Contagem de assuntos e progresso geral (consome contadores normalizados da FASE 2 sem carregar arvore de topicos)
              const totalDisciplinas =
                typeof trilha.totalDisciplinas === 'number'
                  ? trilha.totalDisciplinas
                  : (trilha.disciplinas?.length ?? 0);
              const totalTopicos =
                typeof trilha.totalTopicos === 'number'
                  ? trilha.totalTopicos
                  : (trilha.disciplinas?.flatMap((d) => d.topicos || []).length ?? 0);
              const concluidos =
                typeof trilha.topicosConcluidos === 'number'
                  ? trilha.topicosConcluidos
                  : (trilha.disciplinas?.flatMap((d) => d.topicos || []).filter((t) => trilha.progresso?.[t.id]?.done).length ?? 0);
              const pct =
                typeof trilha.percentualProgresso === 'number'
                  ? trilha.percentualProgresso
                  : (totalTopicos > 0 ? Math.round((concluidos / totalTopicos) * 100) : 0);

              return (
                <TrilhaCard
                  key={trilha.id}
                  ativo={isAtiva}
                  nome={trilha.nome}
                  subtitulo={
                    [trilha.areaCargo, trilha.banca].filter(Boolean).join(' - ') || 'Foco em Concurso'
                  }
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
                    { label: 'Disciplinas', value: totalDisciplinas },
                    {
                      label: 'Progresso Geral',
                      tone: 'sucesso',
                      value: (
                        <>
                          <CheckCircle size={13} className={trilhaCardStyles.metricIcon} />
                          {pct}% ({concluidos}/{totalTopicos})
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
            })}
          </TrilhaGrid>
        )}
      </section>
    </div>
  );
};
