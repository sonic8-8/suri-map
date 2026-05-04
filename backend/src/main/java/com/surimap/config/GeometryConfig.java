package com.surimap.config;

import com.surimap.maparea.geometry.policy.GeometryPolicy;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * S2 geometry 관련 Bean을 등록하는 설정 클래스
 */
@Configuration
public class GeometryConfig {

    /**
     * 하네스 기준 GeometryPolicy Bean을 등록한다.
     *
     * @return S2 하네스 기준 도형 정책
     */
    @Bean
    public GeometryPolicy geometryPolicy() {
        return GeometryPolicy.s2HarnessDefault();
    }

    /**
     * GeometryPolicy를 사용하는 GeometryValidator Bean을 등록한다.
     *
     * @param geometryPolicy S2 도형 검증 정책
     * @return S2 도형 Validator
     */
    @Bean
    public GeometryValidator geometryValidator(GeometryPolicy geometryPolicy) {
        return new GeometryValidator(geometryPolicy);
    }
}
