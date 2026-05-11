package com.surimap.retention.purge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 위치정보를 포함하는 read path에서 접근 감사 기록을 남긴다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RecordLocationAccess {

  String accessPurpose() default "BOARD_VIEW";
}
