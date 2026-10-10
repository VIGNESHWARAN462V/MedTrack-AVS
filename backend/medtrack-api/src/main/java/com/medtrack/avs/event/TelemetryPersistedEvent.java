package com.medtrack.avs.event;

import com.medtrack.avs.dto.TrackingTelemetryEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired when tracking telemetry has been successfully persisted.
 * Handled by @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) to guarantee
 * that STOMP WebSocket messages are broadcast ONLY after database persistence is committed.
 */
@Getter
public class TelemetryPersistedEvent extends ApplicationEvent {

    private final TrackingTelemetryEvent telemetryEvent;

    public TelemetryPersistedEvent(Object source, TrackingTelemetryEvent telemetryEvent) {
        super(source);
        this.telemetryEvent = telemetryEvent;
    }
}
