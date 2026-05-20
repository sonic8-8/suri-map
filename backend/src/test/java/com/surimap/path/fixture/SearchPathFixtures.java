package com.surimap.path.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import java.util.UUID;

/**
 * S3-1 search_path lifecycle 고정 fixture ID.
 *
 * <p>기준: docs/spec/specs/S3-1.json harness_constraints.preconditions SC-05
 */
public final class SearchPathFixtures {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  public static final String OP1_ALIAS = "op-precinct-001-op1";
  public static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;

  public static final String POLICE_PHONE_ALIAS = "dev-precinct-car-01";
  public static final UUID POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000000001");
  public static final String ACCOUNT_ALIAS = "acct-precinct-member-001";
  public static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110002");

  public static final String PATH_ALIAS = "path-precinct-mixed-001";
  public static final UUID PATH_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

  /** SEARCH_PATH_STARTED event version. */
  public static final long PATH_STARTED_VERSION = 1L;

  /** SEARCH_PATH_ENDED event version: STARTED version + 1. */
  public static final long PATH_ENDED_VERSION = 2L;

  private SearchPathFixtures() {}
}
