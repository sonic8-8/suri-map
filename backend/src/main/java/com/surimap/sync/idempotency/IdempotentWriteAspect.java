package com.surimap.sync.idempotency;

import java.util.Objects;
import java.util.function.Supplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class IdempotentWriteAspect {

  private final IdempotentWriteService idempotentWriteService;

  public IdempotentWriteAspect(IdempotentWriteService idempotentWriteService) {
    this.idempotentWriteService =
        Objects.requireNonNull(idempotentWriteService, "idempotentWriteService must not be null");
  }

  @Around("@annotation(com.surimap.sync.idempotency.IdempotentWrite)")
  public Object reserveAndReplay(ProceedingJoinPoint pjp) throws Throwable {
    IdempotentWriteRequest request = extractRequest(pjp.getArgs());
    try {
      IdempotentWriteResponse response =
          idempotentWriteService.reserveAndReplay(request, ownerOperation(pjp));
      if ("idempotency_mismatch".equals(response.error())) {
        throw new IdempotencyMismatchException();
      }
      if ("write_conflict".equals(response.error())) {
        throw new WriteConflictException();
      }
      return response;
    } catch (OwnerInvocationFailure failure) {
      throw failure.getCause();
    }
  }

  private Supplier<IdempotentWriteResponse> ownerOperation(ProceedingJoinPoint pjp) {
    return () -> {
      try {
        Object result = pjp.proceed();
        if (result instanceof IdempotentWriteResponse response) {
          return response;
        }
        throw new IllegalStateException("@IdempotentWrite method must return IdempotentWriteResponse");
      } catch (Throwable throwable) {
        throw new OwnerInvocationFailure(throwable);
      }
    };
  }

  private IdempotentWriteRequest extractRequest(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof IdempotentWriteRequest request) {
        return request;
      }
    }
    throw new IllegalStateException("@IdempotentWrite method requires IdempotentWriteRequest argument");
  }

  private static final class OwnerInvocationFailure extends RuntimeException {

    private OwnerInvocationFailure(Throwable cause) {
      super(cause);
    }

    @Override
    public synchronized Throwable getCause() {
      return super.getCause();
    }
  }
}
