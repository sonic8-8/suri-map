package com.surimap.incident.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 메서드 인자의 incidentId가 OPEN 사건인지 확인하는 S1-1 guard 선언. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOpenIncident {}
