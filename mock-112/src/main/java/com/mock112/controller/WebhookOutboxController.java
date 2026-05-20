package com.mock112.controller;

import com.mock112.webhook.WebhookOutboxSourceStatus;
import com.mock112.webhook.WebhookOutboxStore;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-112/webhook-outbox")
public class WebhookOutboxController {

    private final WebhookOutboxStore webhookOutboxStore;

    public WebhookOutboxController(WebhookOutboxStore webhookOutboxStore) {
        this.webhookOutboxStore = webhookOutboxStore;
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, WebhookOutboxSourceStatus>> sourceStatuses(
            @RequestParam(name = "sourceIncidentId", required = false) List<String> sourceIncidentIds) {
        return ResponseEntity.ok(webhookOutboxStore.summarizeBySourceIncidentIds(sourceIncidentIds));
    }
}
