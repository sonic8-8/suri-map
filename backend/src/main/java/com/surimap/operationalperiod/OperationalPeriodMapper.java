package com.surimap.operationalperiod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** operational_period MyBatis mapper (S8.json §domain_model). */
@Mapper
public interface OperationalPeriodMapper {

  void insert(OperationalPeriod op);

  Optional<OperationalPeriod> findByIncidentAndSequence(
      @Param("incidentId") UUID incidentId, @Param("sequenceNumber") int sequenceNumber);

  Optional<OperationalPeriod> findActiveByIncident(@Param("incidentId") UUID incidentId);

  List<OperationalPeriod> findAllByIncidentOrderBySequence(@Param("incidentId") UUID incidentId);
}
