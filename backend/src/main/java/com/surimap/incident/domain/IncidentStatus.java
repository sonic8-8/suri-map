package com.surimap.incident.domain;

/** 사건 lifecycle 상태. import 시 {@code OPEN}으로 시작해 종료 시 {@code CLOSED} terminal로 전이된다. */
public enum IncidentStatus {
  OPEN,
  CLOSED
}
