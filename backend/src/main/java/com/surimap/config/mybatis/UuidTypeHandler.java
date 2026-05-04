package com.surimap.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PostgreSQL UUID 컬럼과 Java UUID를 매핑하는 MyBatis TypeHandler.
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = {JdbcType.OTHER, JdbcType.VARCHAR}, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * Java UUID 값을 JDBC 파라미터에 바인딩한다.
     *
     * @param ps 값을 바인딩할 PreparedStatement
     * @param i 바인딩할 파라미터 인덱스
     * @param parameter JDBC에 전달할 UUID 값
     * @param jdbcType 바인딩 대상 JDBC 타입
     * @throws SQLException JDBC 바인딩 중 오류가 발생한 경우
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int i,
            UUID parameter,
            JdbcType jdbcType
    ) throws SQLException {
        ps.setObject(i, parameter);
    }

    /**
     * ResultSet의 컬럼명을 기준으로 UUID 값을 읽는다.
     *
     * @param rs 결과를 읽을 ResultSet
     * @param columnName 읽을 컬럼명
     * @return 읽은 UUID 값, null 가능
     * @throws SQLException ResultSet 읽기 중 오류가 발생한 경우
     */
    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    /**
     * ResultSet의 컬럼 인덱스를 기준으로 UUID 값을 읽는다.
     *
     * @param rs 결과를 읽을 ResultSet
     * @param columnIndex 읽을 컬럼 인덱스
     * @return 읽은 UUID 값, null 가능
     * @throws SQLException ResultSet 읽기 중 오류가 발생한 경우
     */
    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    /**
     * CallableStatement의 컬럼 인덱스를 기준으로 UUID 값을 읽는다.
     *
     * @param cs 결과를 읽을 CallableStatement
     * @param columnIndex 읽을 컬럼 인덱스
     * @return 읽은 UUID 값, null 가능
     * @throws SQLException CallableStatement 읽기 중 오류가 발생한 경우
     */
    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    /**
     * JDBC에서 읽은 값을 UUID로 변환한다.
     *
     * @param value 변환할 JDBC 값
     * @return 변환된 UUID 값, null 가능
     * @throws SQLException UUID로 변환할 수 없는 값인 경우
     */
    private UUID toUuid(Object value) throws SQLException {
        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        if (value instanceof String text) {
            return UUID.fromString(text);
        }

        throw new SQLException("UUID로 변환할 수 없는 값 타입입니다: " + value.getClass().getName());
    }
}
