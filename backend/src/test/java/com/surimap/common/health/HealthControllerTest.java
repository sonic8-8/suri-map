package com.surimap.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

  @Test
  void healthReturnsUpStatus() {
    var response = new HealthController().health();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("UP");
    assertThat(response.getBody().service()).isEqualTo("suri-map-api");
  }
}
