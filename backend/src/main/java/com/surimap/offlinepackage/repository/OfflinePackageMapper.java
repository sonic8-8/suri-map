package com.surimap.offlinepackage.repository;

import java.time.OffsetDateTime;
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
      @Param("overallSearchAreaVersion") long overallSearchAreaVersion,
      @Param("manifestHash") String manifestHash,
      @Param("expiresAt") OffsetDateTime expiresAt,
      @Param("createdAt") OffsetDateTime createdAt);

  void deleteInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  void insertInstallation(@Param("record") OfflinePackageInstallationRecord record);

  int countInstallation(@Param("id") String id);

  int countActiveInstallationsByManifest(@Param("manifestId") String manifestId);

  int countPurgedInstallationsByManifest(@Param("manifestId") String manifestId);

  int countPurgeTargetInstallationsByIncident(@Param("incidentId") String incidentId);

  OfflinePackageManifestRecord findCurrentManifestByIncident(
      @Param("incidentId") String incidentId);

  void insertNextManifestFrom(
      @Param("sourceManifestId") String sourceManifestId,
      @Param("newManifestId") String newManifestId,
      @Param("manifestVersion") int manifestVersion,
      @Param("overallSearchAreaId") String overallSearchAreaId,
      @Param("overallSearchAreaVersion") long overallSearchAreaVersion,
      @Param("manifestHash") String manifestHash,
      @Param("expiresAt") OffsetDateTime expiresAt,
      @Param("createdAt") OffsetDateTime createdAt);

  int markReadyAndPartialInstallationsStale(
      @Param("sourceManifestId") String sourceManifestId,
      @Param("updatedAt") OffsetDateTime updatedAt);

  OfflinePackageInstallationRecord findInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  int tombstoneInstallationsByIncident(
      @Param("incidentId") String incidentId, @Param("updatedAt") OffsetDateTime updatedAt);

  int sanitizeManifestPayloadsByIncident(
      @Param("incidentId") String incidentId,
      @Param("purgedManifestHash") String purgedManifestHash,
      @Param("updatedAt") OffsetDateTime updatedAt);
}
