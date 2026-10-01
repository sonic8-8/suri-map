package com.surimap.config.mybatis;

import java.util.List;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;

public class MyBatisObjectFactory extends DefaultObjectFactory {

  @Override
  public <T> T create(
      Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    if (constructorArgTypes != null && constructorArgs != null) {
      return super.create(type, constructorArgTypes, constructorArgs);
    }

    try {
      // protected 기본 생성자는 호출 전에 접근을 준비해 행마다 예외가 발생하지 않게 한다.
      return type.cast(BeanUtils.instantiateClass(resolveInterface(type)));
    } catch (BeanInstantiationException exception) {
      throw new ReflectionException("Could not instantiate " + type.getName(), exception);
    }
  }
}
