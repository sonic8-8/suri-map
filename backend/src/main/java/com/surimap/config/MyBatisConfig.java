package com.surimap.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.surimap.domain")
public class MyBatisConfig {}
