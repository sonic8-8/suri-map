package com.surimap.api.controller.handover;

import com.surimap.api.controller.dutyshift.DutyShiftQueryController;
import com.surimap.api.controller.summary.SearchHistorySummaryController;
import com.surimap.api.service.handover.HandoverApiException;
import com.surimap.app.controller.dutyshift.AppDutyShiftController;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {
      AppDutyShiftController.class,
      DutyShiftQueryController.class,
      HandoverMemoController.class,
      SearchHistorySummaryController.class
    })
public class HandoverApiExceptionHandler {

  @ExceptionHandler(HandoverApiException.class)
  ResponseEntity<Map<String, String>> handleHandoverApiException(HandoverApiException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of("error", ex.errorCode()));
  }
}
