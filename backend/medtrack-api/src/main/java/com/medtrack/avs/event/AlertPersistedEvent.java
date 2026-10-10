package com.medtrack.avs.event;

import com.medtrack.avs.dto.AlertWebSocketEvent;
import com.medtrack.avs.entity.Alert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired when a geofence alert has been successfully persisted.
 * Handled strictly AFTER_COMMIT to guarantee that STOMP alert messages are broadcast
 * ONLY after the database transaction commits.
 */
@Getter
public class AlertPersistedEvent extends ApplicationEvent {

    private final AlertWebSocketEvent alertEvent;
    private final Alert alert;

    public AlertPersistedEvent(Object source, AlertWebSocketEvent alertEvent, Alert alert) {
        super(source);
        this.alertEvent = alertEvent;
        this.alert = alert;
    }
}
