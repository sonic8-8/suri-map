package com.surimap.app.service.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublisher;
import com.surimap.policephone.PolicePhonePersistenceService;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PolicePhoneHeartbeatConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(PolicePhoneHeartbeatConfig.class)
          .withBean(
              PolicePhonePersistenceService.class, () -> mock(PolicePhonePersistenceService.class))
          .withBean(Clock.class, Clock::systemUTC);

  @Test
  @DisplayName("이벤트 발행자 빈이 없으면 기록용 발행자를 대체 등록한다")
  void missing_publisher_registers_capturing_publisher() {
    // given: 별도로 등록한 이벤트 발행자가 없다.
    // when: 업무폰 설정으로 Spring 컨텍스트를 시작한다.
    contextRunner.run(
        context -> {
          // then: 기록용 발행자 하나가 등록된다.
          assertThat(context).hasSingleBean(EventPublisher.class);
          assertThat(context.getBean(EventPublisher.class))
              .isInstanceOf(CapturingEventPublisher.class);
        });
  }

  @Test
  @DisplayName("이벤트 발행자 빈이 있으면 기록용 발행자로 대체하지 않는다")
  void existing_publisher_prevents_fallback_registration() {
    // given: 사용할 이벤트 발행자를 이미 등록했다.
    EventPublisher publisher = mock(EventPublisher.class);
    // when: 업무폰 설정을 함께 시작한다.
    contextRunner
        .withBean(EventPublisher.class, () -> publisher)
        .run(
            context -> {
              // then: 기존 발행자를 유지하고 대체 빈을 만들지 않는다.
              assertThat(context).hasSingleBean(EventPublisher.class);
              assertThat(context).doesNotHaveBean(CapturingEventPublisher.class);
              assertThat(context.getBean(EventPublisher.class)).isSameAs(publisher);
            });
  }
}
