import { SuriMapPageHeader } from '../../../../../shared/ui';

type SituationBoardHeaderProps = {
  onOpenIncidentList: () => void;
  onOpenAreaEdit: () => void;
};

export function SituationBoardHeader({ onOpenIncidentList, onOpenAreaEdit }: SituationBoardHeaderProps) {
  return (
    <SuriMapPageHeader
      activeTab="situationBoard"
      onOpenAreaEdit={onOpenAreaEdit}
      onOpenIncidentList={onOpenIncidentList}
    />
  );
}
