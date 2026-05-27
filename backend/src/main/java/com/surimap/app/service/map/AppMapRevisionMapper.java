package com.surimap.app.service.map;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppMapRevisionMapper {

  String incidentDetailRevision(@Param("incidentId") UUID incidentId);

  String overallSearchAreaRevision(@Param("incidentId") UUID incidentId);

  String opSearchAreasRevision(
      @Param("incidentId") UUID incidentId, @Param("opId") UUID opId);

  String searchPathsRevision(
      @Param("incidentId") UUID incidentId, @Param("opId") UUID opId);

  String liveMarkersRevision(
      @Param("incidentId") UUID incidentId, @Param("opId") UUID opId);

  String initialMarkersRevision(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("policePhoneId") UUID policePhoneId);
}
