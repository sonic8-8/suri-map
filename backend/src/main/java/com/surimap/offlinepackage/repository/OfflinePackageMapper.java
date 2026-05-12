package com.surimap.offlinepackage.repository;

import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OfflinePackageMapper {

  int countManifest(@Param("manifestId") String manifestId);

  int countPurgeTargetManifestsByIncident(
      @Param("incidentId") String incidentId,
      @Param("purgedManifestHash") String purgedManifestHash);

  void insertManifest(
      @Param("manifestId") String manifestId,
      @Param("incidentId") String incidentId,
      @Param("manifestVersion") int manifestVersion,
      @Param("operationalPeriodId") String operationalPeriodId,
      @Param("overallSearchAreaId") String overallSearchAreaId,
      @Param("expiresAt") OffsetDateTime expiresAt,
      @Param("createdAt") OffsetDateTime createdAt);

  void deleteInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  void insertInstallation(@Param("record") OfflinePackageInstallationRecord record);

  int countInstallation(@Param("id") String id);

  int countActiveInstallationsByManifest(@Param("manifestId") String manifestId);

  int countPurgedInstallationsByManifest(@Param("manifestId") String manifestId);

  int countPurgeTargetInstallationsByIncident(@Param("incidentId") String incidentId);

  List<String> findStaleCandidateInstallationIds(@Param("manifestId") String manifestId);

  OfflinePackageManifestRecord findCurrentManifestByIncident(
      @Param("incidentId") String incidentId);

  void insertNextManifestFrom(
      @Param("sourceManifestId") String sourceManifestId,
      @Param("newManifestId") String newManifestId,
      @Param("manifestVersion") int manifestVersion,
      @Param("overallSearchAreaId") String overallSearchAreaId,
      @Param("overallSearchAreaVersion") long overallSearchAreaVersion,
      @Param("manifestHash") String manifestHash,
      @Param("createdAt") OffsetDateTime createdAt);

  int markReadyAndPartialInstallationsStale(
      @Param("sourceManifestId") String sourceManifestId,
      @Param("updatedAt") OffsetDateTime updatedAt);

  OfflinePackageInstallationRecord findInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  List<OfflinePackageInstallationStatus> findStatusesByManifest(
      @Param("manifestId") String manifestId);

  List<OfflinePackageInstallationStatus> findStatusesByIds(
      @Param("ids") List<String> ids, @Param("activeManifestVersion") int activeManifestVersion);

  List<OfflinePackageInstallationStatus> findStatusesByIncident(
      @Param("incidentId") String incidentId);

  int tombstoneInstallationsByIncident(
      @Param("incidentId") String incidentId, @Param("updatedAt") OffsetDateTime updatedAt);

  int sanitizeManifestPayloadsByIncident(
      @Param("incidentId") String incidentId,
      @Param("purgedManifestHash") String purgedManifestHash,
      @Param("updatedAt") OffsetDateTime updatedAt);
}
