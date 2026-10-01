package com.surimap.config.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.path.GpsPoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.ibatis.reflection.ReflectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MyBatisObjectFactoryTest {

  private final MyBatisObjectFactory objectFactory = new MyBatisObjectFactory();

  @Test
  @DisplayName("기본 생성자로 요청할 때마다 서로 다른 도메인 객체를 만든다")
  void default_constructor_creates_independent_objects() {
    // given: 기본 생성자가 protected인 실제 도메인 타입을 사용한다.
    Class<GpsPoint> type = GpsPoint.class;

    // when: 같은 타입을 두 번 생성한다.
    GpsPoint first = objectFactory.create(type);
    GpsPoint second = objectFactory.create(type);

    // then: 객체를 캐시하거나 공유하지 않는다.
    assertThat(first).isExactlyInstanceOf(GpsPoint.class).isNotSameAs(second);
  }

  @Test
  @DisplayName("컬렉션 인터페이스는 기존 MyBatis 구현 타입으로 생성한다")
  void collection_interfaces_keep_default_implementations() {
    // given: Mapper 결과 수집에 쓰는 인터페이스와 MyBatis 기본 구현이다.
    Map<Class<?>, Class<?>> implementations =
        Map.of(
            List.class, ArrayList.class,
            Collection.class, ArrayList.class,
            Set.class, HashSet.class,
            SortedSet.class, TreeSet.class,
            Map.class, HashMap.class);

    // when, then: 각 인터페이스를 생성해 기존 구현 타입을 유지하는지 확인한다.
    implementations.forEach(
        (type, implementation) ->
            assertThat(objectFactory.create(type)).isExactlyInstanceOf(implementation));
    assertThat(objectFactory.isCollection(List.class)).isTrue();
    assertThat(objectFactory.isCollection(Map.class)).isFalse();
  }

  @Test
  @DisplayName("생성자 인자가 있으면 기존 MyBatis 방식으로 값을 전달한다")
  void explicit_constructor_arguments_keep_default_mapping() {
    // given: 기본 생성자와 인자 생성자가 모두 있는 타입을 사용한다.
    List<Class<?>> argumentTypes = List.of(String.class);
    List<Object> arguments = List.of("search-path");

    // when: 인자 생성자를 지정한다.
    StringBuilder value = objectFactory.create(StringBuilder.class, argumentTypes, arguments);

    // then: 기본 생성자로 대체하지 않고 전달한 값을 유지한다.
    assertThat(value.toString()).isEqualTo("search-path");
  }

  @Test
  @DisplayName("기본 생성자가 없으면 MyBatis 생성 오류를 전달한다")
  void missing_default_constructor_reports_creation_failure() {
    // given: 기본 생성자가 없는 타입이다.
    Class<ConstructorArgument> type = ConstructorArgument.class;

    // when, then: 빈 객체나 null로 성공 처리하지 않는다.
    assertThatThrownBy(() -> objectFactory.create(type))
        .isInstanceOf(ReflectionException.class)
        .hasRootCauseInstanceOf(NoSuchMethodException.class);
  }

  @Test
  @DisplayName("생성자 내부에서 실패하면 다시 실행하지 않고 원인을 전달한다")
  void failing_constructor_is_not_invoked_twice() {
    // given: 실행 횟수를 기록한 뒤 실패하는 생성자다.
    FailingConstructor.invocations = 0;

    // when, then: 실패를 숨기거나 재시도로 부수 효과를 반복하지 않는다.
    assertThatThrownBy(() -> objectFactory.create(FailingConstructor.class))
        .isInstanceOf(ReflectionException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("constructor failed");
    assertThat(FailingConstructor.invocations).isEqualTo(1);
  }

  private static class ConstructorArgument {
    private ConstructorArgument(String value) {}
  }

  private static class FailingConstructor {
    private static int invocations;

    private FailingConstructor() {
      invocations++;
      throw new IllegalStateException("constructor failed");
    }
  }
}
