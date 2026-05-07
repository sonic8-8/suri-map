package com.surimap.retention.purge.testdouble;

import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import com.surimap.retention.purge.fixture.PurgeLifecycleFixtures;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 실제 파기 오케스트레이션 구현 전 테스트에서 사용하는 S1-3 모의 파기 훅 등록기. */
public final class MockPurgeHookRegistry {

  private static final List<PurgeHookName> DEFAULT_ORDER =
      List.of(
          PurgeHookName.LOCAL_SYNC,
          PurgeHookName.PATH,
          PurgeHookName.MARKER_PHOTO,
          PurgeHookName.OFFLINE_PACKAGE);

  private final Map<PurgeHookName, PurgeHookResult> configuredResults =
      new EnumMap<>(PurgeHookName.class);
  private final Map<PurgeHookName, PurgeHook> hooksByName = new EnumMap<>(PurgeHookName.class);
  private final List<PurgeHook> hooks;
  private final List<Observation> observations = new ArrayList<>();

  public MockPurgeHookRegistry() {
    for (PurgeHookName name : DEFAULT_ORDER) {
      configuredResults.put(name, PurgeLifecycleFixtures.successResult());
      hooksByName.put(name, new MockPurgeHook(name));
    }
    hooks = DEFAULT_ORDER.stream().map(hooksByName::get).toList();
  }

  public static MockPurgeHookRegistry createDefault() {
    return new MockPurgeHookRegistry();
  }

  public List<PurgeHook> hooks() {
    return hooks;
  }

  public PurgeHook hook(PurgeHookName name) {
    PurgeHook hook = hooksByName.get(Objects.requireNonNull(name, "name은 null일 수 없습니다"));
    if (hook == null) {
      throw new IllegalArgumentException("알 수 없는 파기 훅입니다: " + name);
    }
    return hook;
  }

  public MockPurgeHookRegistry withResult(PurgeHookName name, PurgeHookResult result) {
    configuredResults.put(
        Objects.requireNonNull(name, "name은 null일 수 없습니다"),
        Objects.requireNonNull(result, "result는 null일 수 없습니다"));
    return this;
  }

  public List<Observation> observations() {
    return List.copyOf(observations);
  }

  public List<Observation> observationsFor(PurgeHookName name) {
    Objects.requireNonNull(name, "name은 null일 수 없습니다");
    return observations.stream().filter(observation -> observation.hookName() == name).toList();
  }

  public void resetObservations() {
    observations.clear();
  }

  private PurgeHookResult purge(PurgeHookName name, PurgeHookRequest request) {
    Objects.requireNonNull(request, "request는 null일 수 없습니다");
    PurgeHookResult result = configuredResults.get(name);
    observations.add(new Observation(name, request));
    return result;
  }

  public record Observation(PurgeHookName hookName, PurgeHookRequest request) {

    public Observation {
      Objects.requireNonNull(hookName, "hookName은 null일 수 없습니다");
      Objects.requireNonNull(request, "request는 null일 수 없습니다");
    }
  }

  private final class MockPurgeHook implements PurgeHook {

    private final PurgeHookName name;

    private MockPurgeHook(PurgeHookName name) {
      this.name = name;
    }

    @Override
    public PurgeHookName name() {
      return name;
    }

    @Override
    public PurgeHookResult purge(PurgeHookRequest request) {
      return MockPurgeHookRegistry.this.purge(name, request);
    }
  }
}
