package com.surimap.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@ActiveProfiles("test")
class ApiImplementationStatusCoverageTest {

  private static final Pattern API_SPEC_ENDPOINT_HEADING =
      Pattern.compile("^#### (GET|POST|PATCH|DELETE|PUT) `([^`]+)`$");
  private static final Set<String> ROUTE_REQUIRED_STATUSES = Set.of("구현", "부분", "불일치");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Autowired private ServerProperties serverProperties;

  @Test
  @DisplayName("api-spec public endpoints are all tracked by the implementation status table")
  void apiSpecPublicEndpointsAreTrackedByImplementationStatusTable() throws IOException {
    Set<String> apiSpecEndpoints = apiSpecEndpoints();
    Map<String, String> implementationStatuses = implementationStatuses();

    assertThat(implementationStatuses.keySet())
        .as("docs/tasks/api-implementation-status.md must track every public endpoint in api-spec")
        .containsAll(apiSpecEndpoints);
  }

  @Test
  @DisplayName("implemented, partial, and mismatched status rows must have runtime Spring routes")
  void nonMissingStatusRowsHaveRuntimeRoutes() throws IOException {
    Map<String, String> implementationStatuses = implementationStatuses();
    Set<String> runtimeRoutes = runtimeRoutes();

    Set<String> requiredRoutes =
        implementationStatuses.entrySet().stream()
            .filter(entry -> ROUTE_REQUIRED_STATUSES.contains(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    assertThat(runtimeRoutes)
        .as(
            "status rows marked 구현/부분/불일치 must point to real Spring routes; "
                + "미구현 rows are allowed to remain absent but must stay tracked")
        .containsAll(requiredRoutes);
  }

  private static Set<String> apiSpecEndpoints() throws IOException {
    return Files.readAllLines(repoRoot().resolve("docs/api/api-spec.md")).stream()
        .map(API_SPEC_ENDPOINT_HEADING::matcher)
        .filter(Matcher::matches)
        .map(matcher -> matcher.group(1) + " " + matcher.group(2))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Map<String, String> implementationStatuses() throws IOException {
    Map<String, String> statuses = new LinkedHashMap<>();
    Path statusDoc = repoRoot().resolve("docs/tasks/api-implementation-status.md");
    for (String line : Files.readAllLines(statusDoc)) {
      String[] cells = line.split("\\|");
      if (cells.length < 4) {
        continue;
      }
      String endpoint = stripCode(cells[1].trim());
      if (!isEndpoint(endpoint)) {
        continue;
      }
      statuses.put(endpoint, cells[3].trim());
    }
    return statuses;
  }

  private Set<String> runtimeRoutes() {
    String contextPath = normalizeContextPath(serverProperties.getServlet().getContextPath());
    return handlerMapping.getHandlerMethods().entrySet().stream()
        .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.surimap"))
        .flatMap(entry -> routeKeys(contextPath, entry.getKey()).stream())
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<String> routeKeys(String contextPath, RequestMappingInfo info) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    if (methods.isEmpty()) {
      return Set.of();
    }
    return info.getPatternValues().stream()
        .flatMap(
            path ->
                methods.stream()
                    .map(method -> method.name() + " " + effectivePath(contextPath, path)))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static boolean isEndpoint(String value) {
    return value.startsWith("GET ")
        || value.startsWith("POST ")
        || value.startsWith("PATCH ")
        || value.startsWith("DELETE ")
        || value.startsWith("PUT ");
  }

  private static String stripCode(String value) {
    if (value.startsWith("`") && value.endsWith("`")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static Path repoRoot() {
    return Path.of(System.getProperty("user.dir")).resolve("..").normalize();
  }

  private static String normalizeContextPath(String contextPath) {
    if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
      return "";
    }
    return contextPath;
  }

  private static String effectivePath(String contextPath, String controllerPath) {
    if (controllerPath == null || controllerPath.isBlank() || "/".equals(controllerPath)) {
      return contextPath.isBlank() ? "/" : contextPath;
    }
    return contextPath + (controllerPath.startsWith("/") ? controllerPath : "/" + controllerPath);
  }
}
