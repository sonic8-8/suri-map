package com.surimap.board;

import com.surimap.incident.repository.IncidentMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentBoardQueryService {

  private final BoardAssembler boardAssembler = new BoardAssembler();
  private final Clock clock;
  private final IncidentBoardSourceRowCollector sourceRowCollector;
  private final IncidentMapper incidentMapper;

  public IncidentBoardQueryService(
      Clock clock,
      IncidentBoardSourceRowCollector sourceRowCollector,
      IncidentMapper incidentMapper) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.sourceRowCollector =
        Objects.requireNonNull(sourceRowCollector, "sourceRowCollector must not be null");
    this.incidentMapper = Objects.requireNonNull(incidentMapper, "incidentMapper must not be null");
  }

  public BoardDTO getBoard(
      UUID incidentId, List<UUID> opIds, List<String> includeSlots, Long sinceVersion) {
    // 이벤트 순번은 업무 데이터를 커밋한 뒤에 확정된다.
    // 순번을 원본 데이터보다 먼저 읽으면 이 순번 이하의 변경은 모두 응답에 들어간다.
    long lastSseSequence =
        Objects.requireNonNullElse(incidentMapper.findLastSseSequence(incidentId), 0L);
    IncidentBoardSourceRowSnapshot snapshot =
        sourceRowCollector.collect(new BoardSourceRowContext(incidentId, opIds, includeSlots, sinceVersion));
    BoardAssemblyRequest request =
        new BoardAssemblyRequest(
            incidentId.toString(),
            "bs-" + incidentId,
            0L,
            OffsetDateTime.now(clock),
            toString(snapshot.activeOpId()),
            snapshot.selectedOpIds().stream().map(UUID::toString).toList(),
            snapshot.geometryHash(),
            snapshot.sourceRows(),
            lastSseSequence);
    return boardAssembler.assemble(request);
  }

  private static String toString(UUID value) {
    return value == null ? null : value.toString();
  }
}
