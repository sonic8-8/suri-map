import { DeviceStatusList } from './DeviceStatusList';
import { OfflinePackageNotice } from './OfflinePackageNotice';
import { RecentMarkerList } from './RecentMarkerList';
import { SearchAreaTree } from './SearchAreaTree';

export function SituationBoardRightPanel() {
  return (
    <aside className="situation-board-right-panel" aria-label="상황판 우측 패널">
      <DeviceStatusList />
      <SearchAreaTree />
      <RecentMarkerList />
      <OfflinePackageNotice />
    </aside>
  );
}
