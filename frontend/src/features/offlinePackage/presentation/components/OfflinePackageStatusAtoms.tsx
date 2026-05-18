import {
  Car,
  Clock3,
  FileText,
  Grid3X3,
  Map,
  MapPin,
  MapPinned,
  Smartphone,
  UserRound,
} from 'lucide-react';

import type { OfflinePackageItemType } from '../../api/offlinePackageApi';
import styles from '../pages/OfflinePackageStatusPage.module.css';

export function SectionTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className={styles.sectionTitle}>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

export function DeviceTypeIcon({ accountType }: { accountType: string }) {
  if (accountType === 'PATROL_CAR') {
    return <Car className={styles.deviceTypeIconGlyph} size={22} strokeWidth={2.2} />;
  }

  return <Smartphone className={styles.deviceTypeIconGlyph} size={22} strokeWidth={2.2} />;
}

export function createDeviceIconClassName(accountType: string) {
  return accountType === 'PATROL_CAR'
    ? `${styles.deviceTypeIcon} ${styles.deviceTypeIconPatrolCar}`
    : `${styles.deviceTypeIcon} ${styles.deviceTypeIconPhone}`;
}

export function ManifestGroupIcon({ type }: { type: OfflinePackageItemType }) {
  switch (type) {
    case 'INCIDENT_META':
      return <FileText className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'MISSING_PERSON_CACHE':
      return <UserRound className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'OP_LIST':
      return <Clock3 className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'ASSIGNED_AREA':
      return <MapPinned className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'INITIAL_MARKER':
      return <MapPin className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'OVERALL_SEARCH_AREA':
      return <Map className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
    case 'TILE':
      return <Grid3X3 className={styles.manifestGroupIconGlyph} size={30} strokeWidth={2.2} />;
  }
}

export function manifestGroupIconClassName(type: OfflinePackageItemType) {
  switch (type) {
    case 'INCIDENT_META':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconIncidentMeta}`;
    case 'MISSING_PERSON_CACHE':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconMissingPerson}`;
    case 'OP_LIST':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconOpList}`;
    case 'ASSIGNED_AREA':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconAssignedArea}`;
    case 'INITIAL_MARKER':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconInitialMarker}`;
    case 'OVERALL_SEARCH_AREA':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconOverallSearchArea}`;
    case 'TILE':
      return `${styles.manifestGroupIcon} ${styles.manifestGroupIconTile}`;
  }
}
