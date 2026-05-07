package com.surimap.retention.purge;

/** 파기 오케스트레이션이 소비하는 S1-3 내부 파기 훅 계약. */
public interface PurgeHook {

  PurgeHookName name();

  PurgeHookResult purge(PurgeHookRequest request);
}
