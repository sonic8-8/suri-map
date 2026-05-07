package com.surimap.board;

public interface BoardRowCursor {

  String id();

  String status();

  long version();

  long sequence();

  String sourceSpec();

  String sourceHash();

  String latestEventId();
}
