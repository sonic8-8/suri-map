package com.surimap.maparea.command.exception;

/**
 * 같은 incident에 ACTIVE overall_search_area가 이미 존재할 때 발생하는 예외.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §edge_cases (partial unique index on ACTIVE status).
 */
public class AreaStateConflictException extends RuntimeException {

  public AreaStateConflictException(String message) {
    super(message);
  }
}
