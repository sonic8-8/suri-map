package com.surimap.api.service.path;

import com.surimap.domain.path.SearchPathBoardPointRanges;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/** [DEBUG-cost6] 임시 진단: 실서버 좌표 SQL의 JDBC 수신과 MyBatis 변환 시간을 나눈다. 측정 뒤 되돌린다. */
@Component
@RequiredArgsConstructor
public class SearchPathBoardDebugTiming {
  private static final String POINTS = "com.surimap.domain.path.SearchPathMapper.findBoardPoints";
  private final DataSource dataSource;
  private final SqlSessionFactory sqlSessionFactory;

  public static long gcMillis() {
    long total = 0;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      total += Math.max(0, bean.getCollectionTime());
    }
    return total;
  }

  // 같은 트랜잭션 연결에서 같은 SQL을 다시 실행한다. 행을 넘기기만 한 시간과 열 값을 꺼낸 시간을 잰다.
  public String replayPoints(SearchPathBoardPointRanges ranges) {
    try {
      long noRead = replay(ranges, false);
      long primitive = replay(ranges, true);
      return String.format(
          "replay_noread_ms=%.1f replay_primitive_ms=%.1f", noRead / 1e6, primitive / 1e6);
    } catch (Exception e) {
      return "replay_failed=" + e.getClass().getSimpleName();
    }
  }

  private long replay(SearchPathBoardPointRanges ranges, boolean readColumns) throws Exception {
    MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(POINTS);
    MapperMethod.ParamMap<Object> param = new MapperMethod.ParamMap<>();
    param.put("ranges", ranges);
    param.put("param1", ranges);
    BoundSql bound = statement.getBoundSql(param);
    Connection connection = DataSourceUtils.getConnection(dataSource);
    long start = System.nanoTime();
    long sink = 0;
    try (PreparedStatement prepared = connection.prepareStatement(bound.getSql())) {
      prepared.setFetchSize(10000);
      new DefaultParameterHandler(statement, param, bound).setParameters(prepared);
      try (ResultSet rows = prepared.executeQuery()) {
        while (rows.next()) {
          if (readColumns) {
            sink +=
                rows.getInt(1)
                    + rows.getInt(2)
                    + (long) rows.getDouble(3)
                    + (long) rows.getDouble(4)
                    + rows.getLong(5);
            if (rows.getObject(6) != null) {
              sink++;
            }
          } else {
            sink++;
          }
        }
      }
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
    long elapsed = System.nanoTime() - start;
    if (sink == Long.MIN_VALUE) {
      System.out.println(sink);
    }
    return elapsed;
  }
}
