package com.surimap.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.surimap.domain") // mapper interface는 domain 애그리거트 패키지 안에 둔다
public class MybatisConfig {
}
