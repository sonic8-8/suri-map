package com.surimap.sync.idempotency;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyRecordMapper {

  Optional<IdempotencyRecordRow> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

  void insert(IdempotencyRecordRow row);

  int updateByIdempotencyKey(IdempotencyRecordRow row);

  void deleteByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
