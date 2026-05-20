package com.mock112.assignment;

import com.mock112.domain.MockAssignment;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AssignableOrganizationCatalog {

    public static final String DEFAULT_ORGANIZATION_CODE =
            "GWANGJU_GWANGSAN_SUWAN_PATROL_DIVISION";

    private final List<AssignableOrganization> organizations;
    private final Map<String, AssignableOrganization> organizationsByCode;

    public AssignableOrganizationCatalog() {
        this.organizations = List.of(
                organization(
                        DEFAULT_ORGANIZATION_CODE,
                        "광주광산경찰서 수완지구대",
                        "POLICE_SUBSTATION",
                        account(
                                "11111111-1111-1111-1111-111111110001",
                                "acct-precinct-cmd",
                                "COMMAND",
                                "POLICE_SUBSTATION",
                                DEFAULT_ORGANIZATION_CODE,
                                "광주광산경찰서 수완지구대",
                                "광주광산경찰서 수완지구대 경위 김도현",
                                "FIELD_COMMANDER"),
                        account(
                                "11111111-1111-1111-1111-111111110002",
                                "acct-precinct-car",
                                "PATROL_CAR",
                                "POLICE_SUBSTATION",
                                DEFAULT_ORGANIZATION_CODE,
                                "광주광산경찰서 수완지구대",
                                "광주광산경찰서 수완지구대 경사 박민수",
                                "MEMBER"),
                        account(
                                "11111111-1111-1111-1111-111111110003",
                                "acct-precinct-team",
                                "TEAM",
                                "POLICE_SUBSTATION",
                                DEFAULT_ORGANIZATION_CODE,
                                "광주광산경찰서 수완지구대",
                                "광주광산경찰서 수완지구대 순경 이준호",
                                "MEMBER")),
                organization(
                        "GWANGJU_POLICE_WOMEN_JUVENILE_MISSING_TEAM",
                        "광주경찰청 여성청소년과 실종팀",
                        "MISSING_TEAM",
                        account(
                                "11111111-1111-1111-1111-111111110004",
                                "acct-cmd-alpha",
                                "COMMAND",
                                "MISSING_TEAM",
                                "GWANGJU_POLICE_WOMEN_JUVENILE_MISSING_TEAM",
                                "광주경찰청 여성청소년과 실종팀",
                                "광주경찰청 여성청소년과 실종팀 경감 정서윤",
                                "INCIDENT_COMMANDER"),
                        account(
                                "11111111-1111-1111-1111-111111110005",
                                "acct-team-alpha",
                                "TEAM",
                                "MISSING_TEAM",
                                "GWANGJU_POLICE_WOMEN_JUVENILE_MISSING_TEAM",
                                "광주경찰청 여성청소년과 실종팀",
                                "광주경찰청 여성청소년과 실종팀 경사 최지훈",
                                "MEMBER")),
                organization(
                        "GWANGJU_POLICE_MOBILE_UNIT",
                        "광주경찰청 기동대",
                        "SUPPORT_UNIT",
                        account(
                                "11111111-1111-1111-1111-111111110006",
                                "acct-support-cmd",
                                "COMMAND",
                                "SUPPORT_UNIT",
                                "GWANGJU_POLICE_MOBILE_UNIT",
                                "광주경찰청 기동대",
                                "광주경찰청 기동대 경위 강현우",
                                "FIELD_COMMANDER"),
                        account(
                                "11111111-1111-1111-1111-111111110007",
                                "acct-support-car",
                                "PATROL_CAR",
                                "SUPPORT_UNIT",
                                "GWANGJU_POLICE_MOBILE_UNIT",
                                "광주경찰청 기동대",
                                "광주경찰청 기동대 경사 윤태영",
                                "MEMBER"),
                        account(
                                "11111111-1111-1111-1111-111111110008",
                                "acct-support-team",
                                "TEAM",
                                "SUPPORT_UNIT",
                                "GWANGJU_POLICE_MOBILE_UNIT",
                                "광주경찰청 기동대",
                                "광주경찰청 기동대 순경 오민재",
                                "MEMBER")));

        Map<String, AssignableOrganization> byCode = new LinkedHashMap<>();
        for (AssignableOrganization organization : organizations) {
            byCode.put(organization.organizationCode(), organization);
        }
        this.organizationsByCode = Map.copyOf(byCode);
    }

    public List<AssignableOrganization> findAll() {
        return organizations;
    }

    public AssignableOrganization requireByCode(String organizationCode) {
        String resolvedCode = normalizeOrganizationCode(organizationCode);
        AssignableOrganization organization = organizationsByCode.get(resolvedCode);
        if (organization == null) {
            throw new IllegalArgumentException("Unknown assignable organization: " + resolvedCode);
        }
        return organization;
    }

    public List<MockAssignment> assignmentsFor(
            String sourceIncidentId,
            String organizationCode,
            OffsetDateTime assignedAt) {
        return requireByCode(organizationCode).accounts().stream()
                .map(account -> new MockAssignment(
                        sourceIncidentId + ":" + account.accountCode(),
                        account.accountCode(),
                        account.incidentRole(),
                        assignedAt))
                .toList();
    }

    private String normalizeOrganizationCode(String organizationCode) {
        if (organizationCode == null || organizationCode.isBlank()) {
            return DEFAULT_ORGANIZATION_CODE;
        }
        return organizationCode.trim();
    }

    private AssignableOrganization organization(
            String organizationCode,
            String organizationName,
            String organizationType,
            AssignableAccount... accounts) {
        return new AssignableOrganization(
                organizationCode,
                organizationName,
                organizationType,
                List.of(accounts));
    }

    private AssignableAccount account(
            String accountId,
            String accountCode,
            String accountType,
            String organizationType,
            String organizationCode,
            String organizationName,
            String displayName,
            String incidentRole) {
        return new AssignableAccount(
                accountId,
                accountCode,
                accountType,
                organizationType,
                organizationCode,
                organizationName,
                displayName,
                incidentRole);
    }
}
