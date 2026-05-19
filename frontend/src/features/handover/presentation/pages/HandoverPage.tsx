import {
  OperationalPeriodReviewWorkspace,
  type OperationalPeriodReviewWorkspaceProps,
} from './OperationalPeriodReviewWorkspace';

export type HandoverPageProps = Omit<OperationalPeriodReviewWorkspaceProps, 'viewMode'>;

export function HandoverPage(props: HandoverPageProps) {
  return <OperationalPeriodReviewWorkspace {...props} viewMode="handover" />;
}
