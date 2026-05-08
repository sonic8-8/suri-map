package com.surimap.retention.purge;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocationAccessAuditMapper {

  void insert(LocationAccessAuditRecord record);
}
