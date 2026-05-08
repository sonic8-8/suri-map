package com.surimap.offlinepackage.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OfflinePackageManifestResponse(
    String manifestId,
    String incidentId,
    int manifestVersion,
    OffsetDateTime expiresAt,
    String packageHash,
    PolicePhoneContext policePhoneContext,
    IncidentMetadata incident,
    MissingPerson missingPerson,
    List<OperationalPeriod> operationalPeriods,
    List<AssignedArea> assignedAreas,
    List<InitialMarker> initialMarkers,
    OverallSearchArea overallSearchArea,
    List<TileItem> tileItems,
    List<PackageItem> packageItems) {

  public record PolicePhoneContext(
      String policePhoneId, String accountId, String accountType, String teamId, String role) {}

  public record IncidentMetadata(
      String incidentId, String status, String packageContext, String sourceFixture) {}

  public record MissingPerson(
      String incidentId,
      String displayName,
      String photoObjectKey,
      String appearanceText,
      String lastSeenLocationText,
      OffsetDateTime lastSeenAt) {}

  public record OperationalPeriod(
      String opId, String incidentId, int sequenceNumber, String status, long version) {}

  public record AssignedArea(
      String areaId, String incidentId, String opId, String status, long version) {}

  public record InitialMarker(
      String markerId,
      String incidentId,
      String opId,
      List<BigDecimal> coordinate,
      String status) {}

  public record OverallSearchArea(
      String areaId,
      String incidentId,
      String areaLevel,
      String status,
      String overallAreaHash,
      List<List<BigDecimal>> polygon) {}

  public record TileItem(
      String itemKey,
      String styleId,
      int z,
      int x,
      int y,
      String url,
      String checksum,
      int bytes) {}

  public record PackageItem(
      String itemKey, String itemType, String status, long sourceVersion, String sourceHash) {}
}
