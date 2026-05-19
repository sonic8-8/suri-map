import {
  OperationalPeriodReviewWorkspace,
  type OperationalPeriodReviewWorkspaceProps,
} from '../../../handover/presentation/pages/OperationalPeriodReviewWorkspace';

export type SearchHistoryPageProps = Omit<OperationalPeriodReviewWorkspaceProps, 'viewMode'>;

export function SearchHistoryPage(props: SearchHistoryPageProps) {
  return <OperationalPeriodReviewWorkspace {...props} viewMode="searchHistory" />;
}
