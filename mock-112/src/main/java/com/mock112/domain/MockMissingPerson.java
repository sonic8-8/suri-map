package com.mock112.domain;

import java.time.OffsetDateTime;

/**
 * 실종자 기본 정보.
 * Suri-Map DB의 missing_person 테이블로 매핑된다.
 */
public class MockMissingPerson {

    private String displayName;
    private String photoObjectKey;
    private String appearanceText;
    private String lastSeenLocationText;
    private OffsetDateTime lastSeenAt;

    public MockMissingPerson() {}

    public MockMissingPerson(String displayName, String photoObjectKey,
                             String appearanceText, String lastSeenLocationText,
                             OffsetDateTime lastSeenAt) {
        this.displayName = displayName;
        this.photoObjectKey = photoObjectKey;
        this.appearanceText = appearanceText;
        this.lastSeenLocationText = lastSeenLocationText;
        this.lastSeenAt = lastSeenAt;
    }

    // --- getters & setters ---

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhotoObjectKey() { return photoObjectKey; }
    public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }

    public String getAppearanceText() { return appearanceText; }
    public void setAppearanceText(String appearanceText) { this.appearanceText = appearanceText; }

    public String getLastSeenLocationText() { return lastSeenLocationText; }
    public void setLastSeenLocationText(String lastSeenLocationText) { this.lastSeenLocationText = lastSeenLocationText; }

    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
