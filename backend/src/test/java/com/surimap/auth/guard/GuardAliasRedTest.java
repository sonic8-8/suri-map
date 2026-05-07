package com.surimap.auth.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneAssigned;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.RequireRole;
import com.surimap.common.auth.Role;
import com.surimap.config.SecurityConfig;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RED test for L2-T02: guard alias definitions.
 *
 * <p>Tests that each guard alias (public-session, incident-read, web-command, app-police-phone,
 * field-or-web-write) rejects invalid channel / role / policePhone requests with the correct HTTP
 * 403 and error body, and that domain rows are NOT created when a guard fails.
 *
 * <p>All tests currently FAIL because the annotation stubs carry no enforcement logic. The coder
 * must implement the guard interceptors to make these pass (GREEN).
 */
@WebMvcTest(controllers = GuardAliasRedTest.GuardHarnessController.class)
@Import({SecurityConfig.class, GuardAliasRedTest.GuardHarnessController.class, GuardPortTestStubs.class})
class GuardAliasRedTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private GuardHarnessController controller;

  @BeforeEach
  void resetWriteCounter() {
    controller.resetWriteCounter();
  }

  // ---------------------------------------------------------------------------
  // public-session: @RequireChannel(APP, WEB)
  // INTERNAL channel must be rejected with channel_not_allowed (403)
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void publicSession_appChannel_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/public-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void publicSession_webChannel_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/public-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  /**
   * INTERNAL channel must be rejected. Currently FAILS (RED) because @RequireChannel has no
   * enforcement logic.
   */
  @Test
  @WithMockAccount(
      channel = Channel.INTERNAL,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void publicSession_internalChannel_isRejectedWithChannelNotAllowed() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/public-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  // ---------------------------------------------------------------------------
  // incident-read: @RequireIncidentAccess
  // Account NOT in incident_assignment must fail with incident_access_denied / team_not_assigned
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void incidentRead_assignedAccount_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/incident-read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  /**
   * Unassigned account. Currently FAILS (RED) because @RequireIncidentAccess has no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.SUPPORT_UNIT,
      accountId = "00000000-0000-0000-0000-000000000099")
  void incidentRead_unassignedAccount_isRejectedWithIncidentAccessDenied() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/incident-read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("incident_access_denied"));

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when incident_access_denied guard rejects the request")
        .isEqualTo(0);
  }

  /** Team account not yet assigned. Fails with team_not_assigned (RED). */
  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      accountId = "00000000-0000-0000-0000-000000000098")
  void incidentRead_teamNotAssigned_isRejectedWithTeamNotAssigned() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/incident-read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("team_not_assigned"));

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when team_not_assigned guard rejects the request")
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // web-command: @RequireChannel(WEB) + @RequireRole
  // Wrong channel -> channel_not_allowed; wrong role -> role_denied
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void webCommand_webChannelWithRole_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/web-command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  /**
   * APP channel calling a web-command endpoint. Currently FAILS (RED) because @RequireChannel has
   * no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void webCommand_appChannel_isRejectedWithChannelNotAllowed() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/web-command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  /**
   * WEB channel but MEMBER role — role_denied. Currently FAILS (RED) because @RequireRole has no
   * enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MEMBER)
  void webCommand_memberRole_isRejectedWithRoleDenied() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/web-command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("role_denied"));
  }

  // ---------------------------------------------------------------------------
  // app-police-phone: @RequireChannel(APP) + @RequirePolicePhone
  //                   + @RequirePolicePhoneRegistered + @RequirePolicePhoneAssigned
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  void appPolicePhone_appChannelWithPolicePhone_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-PolicePhone-Id", "00000000-0000-0000-0000-000000000101")
                .content("{}"))
        .andExpect(status().isOk());
  }

  /**
   * WEB channel calling app-police-phone. Currently FAILS (RED) — @RequireChannel has no
   * enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void appPolicePhone_webChannel_isRejectedWithChannelNotAllowed() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  /**
   * APP channel but NO police phone header or session policePhoneId. Currently FAILS (RED)
   * — @RequirePolicePhone has no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void appPolicePhone_missingPolicePhoneId_isRejectedWithPolicePhoneRequired() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("police_phone_required"));
  }

  /**
   * PolicePhone present in session but not registered. Currently FAILS (RED)
   * — @RequirePolicePhoneRegistered has no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "00000000-0000-0000-0000-000000000201")
  void appPolicePhone_unregisteredPolicePhone_isRejectedWithPolicePhoneNotRegistered()
      throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-PolicePhone-Id", "00000000-0000-0000-0000-000000000201")
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_registered"));

    assertThat(controller.getWriteCount())
        .as(
            "domain row must NOT be created when police_phone_not_registered guard rejects the"
                + " request")
        .isEqualTo(0);
  }

  /**
   * PolicePhone registered but not assigned to any active incident. Currently FAILS (RED)
   * — @RequirePolicePhoneAssigned has no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "00000000-0000-0000-0000-000000000301")
  void appPolicePhone_unassignedPolicePhone_isRejectedWithPolicePhoneNotAssigned()
      throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-PolicePhone-Id", "00000000-0000-0000-0000-000000000301")
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_assigned"));

    assertThat(controller.getWriteCount())
        .as(
            "domain row must NOT be created when police_phone_not_assigned guard rejects the"
                + " request")
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // field-or-web-write: @RequireChannel(APP,WEB)
  //   APP path additionally requires @RequirePolicePhone + @RequirePolicePhoneRegistered
  //                                  + @RequirePolicePhoneAssigned
  //   WEB path requires no police phone guards
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  void fieldOrWebWrite_appChannelWithPolicePhone_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/field-or-web-write")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-PolicePhone-Id", "00000000-0000-0000-0000-000000000101")
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.FIELD_COMMANDER)
  void fieldOrWebWrite_webChannelNoPolicePhone_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/field-or-web-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  /**
   * INTERNAL channel calling field-or-web-write. Currently FAILS (RED) — @RequireChannel has no
   * enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.INTERNAL,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void fieldOrWebWrite_internalChannel_isRejectedWithChannelNotAllowed() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/field-or-web-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  /**
   * APP channel without police phone on field-or-web-write. Currently FAILS (RED)
   * — @RequirePolicePhone has no enforcement.
   */
  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void fieldOrWebWrite_appChannelNoPolicePhone_isRejectedWithPolicePhoneRequired()
      throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/field-or-web-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("police_phone_required"));

    assertThat(controller.getWriteCount())
        .as(
            "domain row must NOT be created when police_phone_required guard rejects the request"
                + " on field-or-web-write")
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // Guard-failure no-write test:
  // When guard fails, the domain write counter must remain at zero.
  // Currently FAILS (RED) because without guard enforcement the counter is incremented.
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.INTERNAL,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void guardFailure_publicSession_doesNotCreateDomainRow() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/public-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when guard rejects the request")
        .isEqualTo(0);
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  void guardFailure_webCommand_wrongChannel_doesNotCreateDomainRow() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/web-command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when channel guard rejects the request")
        .isEqualTo(0);
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MEMBER)
  void guardFailure_webCommand_wrongRole_doesNotCreateDomainRow() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/web-command")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when role guard rejects the request")
        .isEqualTo(0);
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void guardFailure_appPolicePhone_noPolicePhoneId_doesNotCreateDomainRow() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    assertThat(controller.getWriteCount())
        .as("domain row must NOT be created when police_phone_required guard rejects the request")
        .isEqualTo(0);
  }

  // ---------------------------------------------------------------------------
  // Precedence test: first error in alias chain wins
  // (web channel + no police phone) hitting app-police-phone endpoint
  // must return channel_not_allowed, NOT police_phone_required
  // Currently FAILS (RED).
  // ---------------------------------------------------------------------------

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MEMBER)
  void appPolicePhone_precedence_channelErrorBeforePolicePhoneError() throws Exception {
    mockMvc
        .perform(
            post("/api/guard-harness/app-police-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  // ---------------------------------------------------------------------------
  // Harness controller — carries the guard annotations under test.
  // The write counter simulates a domain row insertion so tests can assert
  // that guard failure prevents the counter from being incremented.
  // ---------------------------------------------------------------------------

  @RestController
  public static class GuardHarnessController {

    private final AtomicInteger writeCounter = new AtomicInteger(0);

    public void resetWriteCounter() {
      writeCounter.set(0);
    }

    public int getWriteCount() {
      return writeCounter.get();
    }

    /** public-session: APP or WEB channel only. */
    @PostMapping("/api/guard-harness/public-session")
    @RequireChannel({Channel.APP, Channel.WEB})
    ResponseEntity<Void> publicSession() {
      writeCounter.incrementAndGet();
      return ResponseEntity.ok().build();
    }

    /** incident-read: account must be in incident_assignment. */
    @PostMapping("/api/guard-harness/incident-read")
    @RequireIncidentAccess
    ResponseEntity<Void> incidentRead() {
      writeCounter.incrementAndGet();
      return ResponseEntity.ok().build();
    }

    /** web-command: WEB channel + required role. */
    @PostMapping("/api/guard-harness/web-command")
    @RequireChannel({Channel.WEB})
    @RequireRole
    ResponseEntity<Void> webCommand() {
      writeCounter.incrementAndGet();
      return ResponseEntity.ok().build();
    }

    /**
     * app-police-phone: APP channel + policePhone present, registered, and assigned. (S1-2.json
     * api_guard_aliases)
     */
    @PostMapping("/api/guard-harness/app-police-phone")
    @RequireChannel({Channel.APP})
    @RequirePolicePhone
    @RequirePolicePhoneRegistered
    @RequirePolicePhoneAssigned
    ResponseEntity<Void> appPolicePhone() {
      writeCounter.incrementAndGet();
      return ResponseEntity.ok().build();
    }

    /**
     * field-or-web-write: APP or WEB; for APP additionally requires policePhone guards.
     * (boundaries.md §7)
     */
    @PostMapping("/api/guard-harness/field-or-web-write")
    @RequireChannel({Channel.APP, Channel.WEB})
    @RequirePolicePhone
    @RequirePolicePhoneRegistered
    @RequirePolicePhoneAssigned
    ResponseEntity<Void> fieldOrWebWrite() {
      writeCounter.incrementAndGet();
      return ResponseEntity.ok().build();
    }
  }
}
