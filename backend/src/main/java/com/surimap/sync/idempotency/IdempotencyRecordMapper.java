package com.surimap.sync.idempotency;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyRecordMapper {

  Optional<IdempotencyRecordRow> findByIdempotencyKeyAndEndpoint(
      @Param("idempotencyKey") String idempotencyKey,
      @Param("requestPath") String requestPath,
      @Param("requestMethod") String requestMethod);

  int insert(IdempotencyRecordRow row);

  int updateByIdempotencyKeyAndEndpoint(IdempotencyRecordRow row);

  void deleteByIdempotencyKeyAndEndpoint(
      @Param("idempotencyKey") String idempotencyKey,
      @Param("requestPath") String requestPath,
      @Param("requestMethod") String requestMethod);
}
