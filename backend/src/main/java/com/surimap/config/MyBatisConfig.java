package com.surimap.config;

import com.surimap.config.mybatis.MyBatisObjectFactory;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.surimap", annotationClass = Mapper.class)
public class MyBatisConfig {

  @Bean
  public ConfigurationCustomizer configureObjectFactory() {
    return configuration -> configuration.setObjectFactory(new MyBatisObjectFactory());
  }
}
