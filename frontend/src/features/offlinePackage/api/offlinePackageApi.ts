import { useQuery } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';

export type OfflinePackageItemType =
  | 'INCIDENT_META'
  | 'MISSING_PERSON_CACHE'
  | 'OP_LIST'
  | 'ASSIGNED_AREA'
  | 'INITIAL_MARKER'
  | 'OVERALL_SEARCH_AREA'
  | 'TILE';

export interface OfflinePackageManifestResponse {
  readonly manifestId: string;
  readonly incidentId: string;
  readonly manifestVersion: number;
  readonly expiresAt: string;
  readonly packageHash: string;
  readonly policePhoneContext: OfflinePackagePolicePhoneContext | null;
  readonly incident: OfflinePackageIncidentMetadata | null;
  readonly missingPerson: OfflinePackageMissingPerson | null;
  readonly operationalPeriods: readonly OfflinePackageOperationalPeriod[];
  readonly assignedAreas: readonly OfflinePackageAssignedArea[];
  readonly initialMarkers: readonly OfflinePackageInitialMarker[];
  readonly overallSearchArea: OfflinePackageOverallSearchArea | null;
  readonly tileItems: readonly OfflinePackageTileItem[];
  readonly packageItems: readonly OfflinePackagePackageItem[];
}

export interface OfflinePackagePolicePhoneContext {
  readonly policePhoneId: string;
  readonly accountId: string;
  readonly accountType: string;
  readonly teamId: string | null;
  readonly role: string;
}

export interface OfflinePackageIncidentMetadata {
  readonly incidentId: string;
  readonly status: string;
  readonly packageContext: string;
  readonly sourceFixture: string | null;
}

export interface OfflinePackageMissingPerson {
  readonly incidentId: string;
  readonly displayName: string | null;
  readonly photoObjectKey: string | null;
  readonly appearanceText: string | null;
  readonly lastSeenLocationText: string | null;
  readonly lastSeenAt: string | null;
}

export interface OfflinePackageOperationalPeriod {
  readonly opId: string;
  readonly incidentId: string;
  readonly sequenceNumber: number;
  readonly status: string;
  readonly version: number;
}

export interface OfflinePackageAssignedArea {
  readonly areaId: string;
  readonly incidentId: string;
  readonly opId: string;
  readonly status: string;
  readonly version: number;
}

export interface OfflinePackageInitialMarker {
  readonly markerId: string;
  readonly incidentId: string;
  readonly opId: string;
  readonly coordinate: readonly number[];
  readonly status: string;
}

export interface OfflinePackageOverallSearchArea {
  readonly areaId: string;
  readonly incidentId: string;
  readonly areaLevel: string;
  readonly status: string;
  readonly overallAreaHash: string;
  readonly polygon: readonly (readonly number[])[];
}

export interface OfflinePackageTileItem {
  readonly itemKey: string;
  readonly styleId: string;
  readonly z: number;
  readonly x: number;
  readonly y: number;
  readonly url: string;
  readonly checksum: string | null;
  readonly bytes: number;
}

export interface OfflinePackagePackageItem {
  readonly itemKey: string;
  readonly itemType: OfflinePackageItemType;
  readonly status: string;
  readonly sourceVersion: number;
  readonly sourceHash: string;
}

export interface OfflinePackageManifestQuery {
  readonly incidentId: string | null | undefined;
}

export interface OfflinePackageApi {
  fetchOfflinePackageManifest(incidentId: string): Promise<OfflinePackageManifestResponse>;
}

export const offlinePackageQueryKeys = {
  all: ['offlinePackage'] as const,
  manifest: (incidentId: string | null | undefined) =>
    [...offlinePackageQueryKeys.all, 'manifest', incidentId ?? ''] as const,
};

export function createOfflinePackageApi(client: ApiClient = apiClient): OfflinePackageApi {
  return {
    fetchOfflinePackageManifest: (incidentId) =>
      client.get<OfflinePackageManifestResponse>(
        `/incidents/${encodeURIComponent(incidentId)}/offline-package/manifest`,
      ),
  };
}

export const offlinePackageApi = createOfflinePackageApi();

export function fetchOfflinePackageManifest(incidentId: string) {
  return offlinePackageApi.fetchOfflinePackageManifest(incidentId);
}

export function useOfflinePackageManifestQuery(
  query: OfflinePackageManifestQuery,
  api: OfflinePackageApi = offlinePackageApi,
) {
  return useQuery({
    queryKey: offlinePackageQueryKeys.manifest(query.incidentId),
    queryFn: () => api.fetchOfflinePackageManifest(query.incidentId ?? ''),
    enabled: Boolean(query.incidentId),
    placeholderData: (previousData) => previousData,
  });
}
