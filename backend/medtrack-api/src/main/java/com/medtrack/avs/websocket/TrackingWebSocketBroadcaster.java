package com.medtrack.avs.websocket;

import com.medtrack.avs.dto.TrackingTelemetryEvent;
import com.medtrack.avs.event.TelemetryPersistedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for TelemetryPersistedEvent and broadcasts the event over STOMP WebSocket topics.
 *
 * Transaction guarantees:
 * - Handled strictly AFTER_COMMIT.
 * - If the database transaction fails, rolls back, or is aborted, this listener is NEVER called.
 * - The event timestamp (recordedAt) is the exact authoritative Instant stored in MySQL DATETIME(6).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrackingWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTelemetryPersisted(TelemetryPersistedEvent event) {
        TrackingTelemetryEvent wsEvent = event.getTelemetryEvent();
        if (wsEvent == null) {
            return;
        }

        log.debug("Broadcasting STOMP telemetry for asset {}: type={}, lat={}, lon={}, recordedAt={}",
                wsEvent.getAssetCode(), wsEvent.getEventType(), wsEvent.getLatitude(), wsEvent.getLongitude(), wsEvent.getRecordedAt());

        // 1. Broadcast to global authorized asset tracking topic
        messagingTemplate.convertAndSend("/topic/tracking/assets", wsEvent);

        // 2. Broadcast to asset-specific topic
        if (wsEvent.getAssetId() != null) {
            messagingTemplate.convertAndSend("/topic/tracking/assets/" + wsEvent.getAssetId(), wsEvent);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertPersisted(com.medtrack.avs.event.AlertPersistedEvent event) {
        com.medtrack.avs.dto.AlertWebSocketEvent wsAlert = event.getAlertEvent();
        if (wsAlert == null) {
            return;
        }

        log.info("Broadcasting STOMP geofence alert for asset {}: type={}, severity={}, occurredAt={}",
                wsAlert.getAssetCode(), wsAlert.getAlertType(), wsAlert.getSeverity(), wsAlert.getOccurredAt());

        // 1. Broadcast to global authorized alerts topic
        messagingTemplate.convertAndSend("/topic/alerts", wsAlert);

        // 2. Broadcast to asset-specific alerts topic
        if (wsAlert.getAssetId() != null) {
            messagingTemplate.convertAndSend("/topic/alerts/" + wsAlert.getAssetId(), wsAlert);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTransferPersisted(com.medtrack.avs.event.TransferPersistedEvent event) {
        com.medtrack.avs.dto.TransferWebSocketEvent wsTransfer = event.getTransferEvent();
        if (wsTransfer == null) {
            return;
        }

        log.info("Broadcasting STOMP transfer update for asset {}: type={}, status={}, occurredAt={}",
                wsTransfer.getAssetCode(), wsTransfer.getEventType(), wsTransfer.getStatus(), wsTransfer.getOccurredAt());

        // 1. Broadcast to global authorized transfers topic
        messagingTemplate.convertAndSend("/topic/transfers", wsTransfer);

        // 2. Broadcast to asset-specific transfer topic
        if (wsTransfer.getAssetId() != null) {
            messagingTemplate.convertAndSend("/topic/transfers/" + wsTransfer.getAssetId(), wsTransfer);
        }
    }
}
