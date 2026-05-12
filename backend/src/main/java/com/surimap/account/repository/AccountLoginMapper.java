package com.surimap.account.repository;

import com.surimap.common.auth.Channel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountLoginMapper {

  Optional<AccountLoginRow> findActiveAccountByLoginId(@Param("loginId") String loginId);

  Optional<PolicePhoneLoginRow> findActivePolicePhoneByCode(@Param("phoneCode") String phoneCode);

  void insertRefreshToken(
      @Param("id") UUID id,
      @Param("accountId") UUID accountId,
      @Param("policePhoneId") UUID policePhoneId,
      @Param("channel") Channel channel,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") Instant expiresAt,
      @Param("createdAt") Instant createdAt);

  Optional<AuthSessionRow> findActiveSessionByTokenHash(
      @Param("tokenHash") String tokenHash, @Param("now") Instant now);

  Optional<AuthSessionRow> findActiveSessionBySessionId(
      @Param("sessionId") UUID sessionId, @Param("now") Instant now);

  int revokeByTokenHash(
      @Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

  int revokeBySessionId(
      @Param("sessionId") UUID sessionId, @Param("revokedAt") Instant revokedAt);
}
