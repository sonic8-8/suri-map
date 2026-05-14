package com.surimap.board;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.retention.purge.RecordLocationAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/board")
public class IncidentBoardController {

  private final IncidentBoardQueryService queryService;
  private final BoardAreaPopupQueryService areaPopupQueryService;

  public IncidentBoardController(
      IncidentBoardQueryService queryService, BoardAreaPopupQueryService areaPopupQueryService) {
    this.queryService = queryService;
    this.areaPopupQueryService = areaPopupQueryService;
  }

  @GetMapping
  @RequireChannel({Channel.WEB})
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "BOARD_VIEW")
  public ResponseEntity<IncidentBoardResponse> getBoard(
      @PathVariable UUID incidentId,
      @RequestParam(value = "opIds", required = false) List<UUID> opIds,
      @RequestParam(value = "includeSlots", required = false) List<String> includeSlots,
      @RequestParam(value = "sinceVersion", required = false) Long sinceVersion) {
    BoardDTO board = queryService.getBoard(incidentId, opIds, includeSlots, sinceVersion);
    return ResponseEntity.ok(IncidentBoardResponse.from(board));
  }

  @GetMapping("/search-areas/{searchAreaId}/popup")
  @RequireChannel({Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<BoardAreaPopupResponse> getAreaPopup(
      @PathVariable UUID incidentId, @PathVariable UUID searchAreaId) {
    return areaPopupQueryService
        .getAreaPopup(incidentId, searchAreaId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
