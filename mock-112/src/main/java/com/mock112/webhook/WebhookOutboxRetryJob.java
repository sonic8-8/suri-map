package com.mock112.webhook;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookOutboxRetryJob {

    private final SuriMapWebhookDispatcher dispatcher;

    public WebhookOutboxRetryJob(SuriMapWebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${suri-map.webhook.retry-delay-ms:5000}")
    public void retryPending() {
        dispatcher.retryPending();
    }
}
