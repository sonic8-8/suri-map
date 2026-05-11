package com.surimap.api.service.dutyshift;

import com.surimap.api.controller.dutyshift.response.DutyShiftListResponse;
import com.surimap.api.controller.dutyshift.response.DutyShiftResponse;
import com.surimap.dutyshift.DutyShiftMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DutyShiftQueryService {

  private final DutyShiftMapper dutyShiftMapper;

  public DutyShiftQueryService(DutyShiftMapper dutyShiftMapper) {
    this.dutyShiftMapper = dutyShiftMapper;
  }

  public DutyShiftListResponse list(
      UUID incidentId, UUID opId, UUID policePhoneId, String accountId, String status) {
    return new DutyShiftListResponse(
        dutyShiftMapper.findByFilters(incidentId, opId, policePhoneId, accountId, status).stream()
            .map(DutyShiftResponse::from)
            .toList());
  }
}
