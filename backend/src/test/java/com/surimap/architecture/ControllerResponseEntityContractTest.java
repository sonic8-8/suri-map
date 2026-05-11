package com.surimap.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ControllerResponseEntityContractTest {

  private static final Pattern MAPPING_METHOD =
      Pattern.compile(
          "(?m)@(GetMapping|PostMapping|PatchMapping|PutMapping|DeleteMapping)[^\\r\\n]*"
              + "(?:\\R\\s*@[^\\r\\n]+)*"
              + "\\R\\s*public\\s+([^\\s(]+)\\s+(\\w+)\\s*\\(");

  @Test
  void publicControllerMappingMethodsReturnResponseEntity() throws IOException {
    Path sourceRoot = Path.of("src/main/java");
    List<String> violations = new ArrayList<>();

    try (var paths = Files.walk(sourceRoot)) {
      paths
          .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
          .forEach(path -> collectViolations(path, sourceRoot, violations));
    }

    assertThat(violations)
        .as("Controller mapping methods should return ResponseEntity<contract response DTO>")
        .isEmpty();
  }

  private static void collectViolations(
      Path path, Path sourceRoot, List<String> violations) {
    String source;
    try {
      source = Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read " + path, exception);
    }

    var matcher = MAPPING_METHOD.matcher(source);
    while (matcher.find()) {
      String returnType = matcher.group(2);
      if (!returnType.startsWith("ResponseEntity")) {
        violations.add(sourceRoot.relativize(path) + "#" + matcher.group(3) + " -> " + returnType);
      }
    }
  }
}
