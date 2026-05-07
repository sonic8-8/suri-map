package com.surimap.operationalperiod.guard;

/** current OP가 없을 때 발생하는 예외. HTTP 409 op_required 응답에 매핑된다 (S8.json §api_contracts.errors). */
public class OpRequiredException extends RuntimeException {

  public OpRequiredException() {
    super("op_required");
  }
}
