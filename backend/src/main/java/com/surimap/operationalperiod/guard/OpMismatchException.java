package com.surimap.operationalperiod.guard;

/**
 * 요청 opId가 current OP와 다를 때 발생하는 예외. HTTP 409 op_mismatch 응답에 매핑된다 (S8.json §api_contracts.errors).
 */
public class OpMismatchException extends RuntimeException {

  public OpMismatchException() {
    super("op_mismatch");
  }
}
