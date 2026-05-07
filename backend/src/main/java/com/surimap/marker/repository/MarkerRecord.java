package com.surimap.marker.repository;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

/** MyBatis marker row. */
public class MarkerRecord {

  private UUID id;
  private UUID operationalPeriodId;
  private UUID dutyShiftId;
  private String markerType;
  private String supportRequestType;
  private Point location;
  private String memo;
  private Instant occurredAt;
  private UUID createdByAccountId;
  private UUID policePhoneId;
  private String markerSource;
  private String status;
  private long version;

  public static MarkerRecord fromSeedRecord(MarkerSeedRecord record) {
    MarkerRecord marker = new MarkerRecord();
    marker.setId(record.id());
    marker.setOperationalPeriodId(record.operationalPeriodId());
    marker.setDutyShiftId(record.dutyShiftId());
    marker.setMarkerType(record.markerType().name());
    marker.setSupportRequestType(
        record.supportRequestType() == null ? null : record.supportRequestType().name());
    marker.setLocation(record.location());
    marker.setMemo(record.memo());
    marker.setOccurredAt(record.occurredAt());
    marker.setCreatedByAccountId(record.createdByAccountId());
    marker.setPolicePhoneId(record.policePhoneId());
    marker.setMarkerSource(record.markerSource().name());
    marker.setStatus(record.status().name());
    marker.setVersion(record.version());
    return marker;
  }

  public static MarkerRecord fromCreateRecord(MarkerCreateRecord record) {
    MarkerRecord marker = new MarkerRecord();
    marker.setId(record.id());
    marker.setOperationalPeriodId(record.operationalPeriodId());
    marker.setDutyShiftId(record.dutyShiftId());
    marker.setMarkerType(record.markerType().name());
    marker.setSupportRequestType(
        record.supportRequestType() == null ? null : record.supportRequestType().name());
    marker.setLocation(record.location());
    marker.setMemo(record.memo());
    marker.setOccurredAt(record.occurredAt());
    marker.setCreatedByAccountId(record.createdByAccountId());
    marker.setPolicePhoneId(record.policePhoneId());
    marker.setMarkerSource(record.markerSource().name());
    marker.setStatus(record.status().name());
    marker.setVersion(record.version());
    return marker;
  }

  public MarkerView toView(UUID incidentId) {
    return new MarkerView(
        id,
        incidentId,
        operationalPeriodId,
        createdByAccountId,
        policePhoneId,
        MarkerType.valueOf(markerType),
        supportRequestType == null ? null : MarkerSupportRequestType.valueOf(supportRequestType),
        MarkerSource.valueOf(markerSource),
        MarkerStatus.valueOf(status),
        version,
        location,
        memo,
        occurredAt,
        List.of());
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOperationalPeriodId() {
    return operationalPeriodId;
  }

  public void setOperationalPeriodId(UUID operationalPeriodId) {
    this.operationalPeriodId = operationalPeriodId;
  }

  public UUID getDutyShiftId() {
    return dutyShiftId;
  }

  public void setDutyShiftId(UUID dutyShiftId) {
    this.dutyShiftId = dutyShiftId;
  }

  public String getMarkerType() {
    return markerType;
  }

  public void setMarkerType(String markerType) {
    this.markerType = markerType;
  }

  public String getSupportRequestType() {
    return supportRequestType;
  }

  public void setSupportRequestType(String supportRequestType) {
    this.supportRequestType = supportRequestType;
  }

  public Point getLocation() {
    return location;
  }

  public void setLocation(Point location) {
    this.location = location;
  }

  public String getMemo() {
    return memo;
  }

  public void setMemo(String memo) {
    this.memo = memo;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public UUID getCreatedByAccountId() {
    return createdByAccountId;
  }

  public void setCreatedByAccountId(UUID createdByAccountId) {
    this.createdByAccountId = createdByAccountId;
  }

  public UUID getPolicePhoneId() {
    return policePhoneId;
  }

  public void setPolicePhoneId(UUID policePhoneId) {
    this.policePhoneId = policePhoneId;
  }

  public String getMarkerSource() {
    return markerSource;
  }

  public void setMarkerSource(String markerSource) {
    this.markerSource = markerSource;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
