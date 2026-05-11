package com.surimap.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@ActiveProfiles("test")
class PublicRouteContractTest {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Autowired private ServerProperties serverProperties;

  @Test
  @DisplayName("public routes expose JSON APIs under /api and tiles under /tiles without /api/api")
  void public_routes_use_canonical_prefixes() {
    Set<String> effectivePaths = effectiveApplicationPaths();

    assertThat(effectivePaths)
        .as("context-path plus controller mapping must not duplicate the JSON API prefix")
        .noneMatch(path -> path.startsWith("/api/api/"));

    assertThat(effectivePaths)
        .contains(
            "/api/health",
            "/api/incidents/{incidentId}/board",
            "/tiles/styles/{styleId}.json");
    assertThat(effectivePaths).doesNotContain("/api/tiles/styles/{styleId}.json");

    assertThat(
            effectivePaths.stream()
                .filter(path -> !path.startsWith("/tiles/"))
                .filter(path -> !path.startsWith("/actuator/"))
                .collect(Collectors.toList()))
        .as("non-tile application routes must use the canonical /api JSON prefix")
        .allMatch(path -> path.startsWith("/api/"));
  }

  private Set<String> effectiveApplicationPaths() {
    String contextPath = normalizeContextPath(serverProperties.getServlet().getContextPath());
    return handlerMapping.getHandlerMethods().entrySet().stream()
        .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.surimap"))
        .map(entry -> entry.getKey().getPatternValues())
        .flatMap(Set::stream)
        .map(path -> effectivePath(contextPath, path))
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
