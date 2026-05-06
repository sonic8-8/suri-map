package com.surimap.external;

import java.time.OffsetDateTime;

/** mock 112 실종자 정보 DTO. missing_person 테이블 매핑용. */
public record ExternalMissingPerson(
    String displayName,
    String photoObjectKey,
    String appearanceText,
    String lastSeenLocationText,
    OffsetDateTime lastSeenAt) {}
