package com.surimap.handover.command;

import java.time.Instant;
import java.util.UUID;

/**
 * HandoverMemo 생성 커맨드 포트 (S8.json §api_contracts POST /handover-memos).
 *
 * <p>APP/WEB 채널 모두 사용. APP에서는 S6 outbox 경유 가능.
 *
 * <p>guard aliases: field-or-web-write + incident-read + write-common
 */
public interface HandoverMemoCreateCommand {

  HandoverMemoCreateResult create(HandoverMemoCreateRequest request);
}
