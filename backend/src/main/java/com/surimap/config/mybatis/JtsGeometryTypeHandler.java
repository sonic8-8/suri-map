package com.surimap.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostGIS geometry 값을 JTS Geometry로 변환하는 MyBatis TypeHandler.
 */
@MappedTypes({Geometry.class, Polygon.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JtsGeometryTypeHandler extends BaseTypeHandler<Geometry> {

    private static final int DEFAULT_SRID = 4326;

    private final WKTReader reader = new WKTReader();
    private final WKTWriter writer = new WKTWriter();

    /**
     * JTS Geometry를 EWKT 문자열로 변환해 JDBC 파라미터에 넣는다.
     *
     * @param ps 값을 바인딩할 PreparedStatement
     * @param i 바인딩할 파라미터 인덱스
     * @param parameter JDBC에 전달할 Geometry 값
     * @param jdbcType 바인딩 대상 JDBC 타입
     * @throws SQLException JDBC 바인딩 중 오류가 발생한 경우
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int i,
            Geometry parameter,
            JdbcType jdbcType
    ) throws SQLException {
        int srid = parameter.getSRID() > 0 ? parameter.getSRID() : DEFAULT_SRID;
        ps.setString(i, "SRID=" + srid + ";" + writer.write(parameter));
    }

    /**
     * ResultSet의 컬럼명을 기준으로 EWKT 문자열을 Geometry로 복원한다.
     *
     * @param rs 결과를 읽을 ResultSet
     * @param columnName 읽을 컬럼명
     * @return 복원된 Geometry 값, null 가능
     * @throws SQLException ResultSet 읽기 중 오류가 발생한 경우
     */
    @Override
    public Geometry getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseEwkt(rs.getString(columnName));
    }

    /**
     * ResultSet의 컬럼 인덱스를 기준으로 EWKT 문자열을 Geometry로 복원한다.
     *
     * @param rs 결과를 읽을 ResultSet
     * @param columnIndex 읽을 컬럼 인덱스
     * @return 복원된 Geometry 값, null 가능
     * @throws SQLException ResultSet 읽기 중 오류가 발생한 경우
     */
    @Override
    public Geometry getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseEwkt(rs.getString(columnIndex));
    }

    /**
     * CallableStatement의 컬럼 인덱스를 기준으로 EWKT 문자열을 Geometry로 복원한다.
     *
     * @param cs 결과를 읽을 CallableStatement
     * @param columnIndex 읽을 컬럼 인덱스
     * @return 복원된 Geometry 값, null 가능
     * @throws SQLException CallableStatement 읽기 중 오류가 발생한 경우
     */
    @Override
    public Geometry getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseEwkt(cs.getString(columnIndex));
    }

    /**
     * EWKT 문자열을 파싱해 Geometry 객체로 만든다.
     *
     * @param value 파싱할 EWKT 문자열
     * @return 파싱된 Geometry 값, null 가능
     * @throws SQLException EWKT 형식이 올바르지 않거나 파싱에 실패한 경우
     */
    private Geometry parseEwkt(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return null;
        }

        int srid = DEFAULT_SRID;
        String wkt = value;

        if (value.startsWith("SRID=")) {
            int separatorIndex = value.indexOf(';');
            if (separatorIndex < 0) {
                throw new SQLException("EWKT 형식이 올바르지 않습니다: " + value);
            }
            srid = Integer.parseInt(value.substring("SRID=".length(), separatorIndex));
            wkt = value.substring(separatorIndex + 1);
        }

        try {
            Geometry geometry = reader.read(wkt);
            geometry.setSRID(srid);
            return geometry;
        } catch (ParseException e) {
            throw new SQLException("EWKT Geometry를 파싱하지 못했습니다.", e);
        }
    }
}
