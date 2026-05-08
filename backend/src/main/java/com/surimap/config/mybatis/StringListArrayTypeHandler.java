package com.surimap.config.mybatis;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/** PostgreSQL TEXT[] 컬럼과 Java String List를 매핑한다. */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringListArrayTypeHandler extends BaseTypeHandler<List<String>> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
      throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray(String[]::new));
    ps.setArray(i, array);
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private static List<String> toList(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (!(raw instanceof Object[] values)) {
      return List.of();
    }
    List<String> result = new ArrayList<>(values.length);
    for (Object value : values) {
      if (value != null) {
        result.add(value.toString());
      }
    }
    return List.copyOf(result);
  }
}
