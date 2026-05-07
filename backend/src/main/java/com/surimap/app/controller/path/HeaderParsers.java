package com.surimap.app.controller.path;

import com.surimap.domain.path.exception.SearchPathGuardException;
import java.util.UUID;

final class HeaderParsers {

  private HeaderParsers() {}

  static UUID parsePolicePhoneId(String header) {
    if (header == null || header.isBlank()) {
      throw new SearchPathGuardException("police_phone_required");
    }
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException exception) {
      throw new SearchPathGuardException("police_phone_required");
    }
  }
}
