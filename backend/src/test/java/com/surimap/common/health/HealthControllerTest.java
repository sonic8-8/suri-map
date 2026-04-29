package com.surimap.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReturnsUpStatus() {
        var response = new HealthController().health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("suri-map-api");
    }
}

