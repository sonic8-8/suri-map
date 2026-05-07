package com.surimap.operationalperiod.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * current OP가 없거나 요청 opId가 current OP와 다르면 409를 반환하는 guard 선언 (S8.json §Guard/Security,
 * backend/AGENTS.md §Guard/Security).
 *
 * <p>적용 메서드는 파라미터에 incidentId(UUID)와 opId(UUID)를 포함해야 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCurrentOp {}
