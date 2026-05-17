package com.surimap.account.repository;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountLoginMapper {

  Optional<AccountLoginRow> findActiveAccountByLoginId(@Param("loginId") String loginId);

  Optional<PolicePhoneLoginRow> findActivePolicePhoneByCode(@Param("phoneCode") String phoneCode);
}
