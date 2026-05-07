package com.surimap.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.surimap", annotationClass = Mapper.class)
public class MyBatisConfig {}
