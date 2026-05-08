package com.surimap.offlinepackage.repository;

import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OfflinePackageMapper {

  int countManifest(@Param("manifestId") String manifestId);

  void insertManifest(
      @Param("manifestId") String manifestId,
      @Param("incidentId") String incidentId,
      @Param("manifestVersion") int manifestVersion,
      @Param("expiresAt") OffsetDateTime expiresAt,
      @Param("createdAt") OffsetDateTime createdAt);

  void deleteInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  void insertInstallation(@Param("record") OfflinePackageInstallationRecord record);

  OfflinePackageInstallationRecord findInstallationForPhone(
      @Param("manifestId") String manifestId, @Param("policePhoneId") String policePhoneId);

  List<OfflinePackageInstallationStatus> findStatusesByIncident(
      @Param("incidentId") String incidentId);
}
