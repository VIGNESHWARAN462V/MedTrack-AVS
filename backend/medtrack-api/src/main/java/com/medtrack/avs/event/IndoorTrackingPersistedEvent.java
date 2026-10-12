package com.medtrack.avs.event;

import com.medtrack.avs.dto.IndoorTrackingEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired when indoor asset positioning has been successfully persisted.
 * Handled by @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) to guarantee
 * that STOMP WebSocket messages are broadcast ONLY after database persistence is committed.
 */
@Getter
public class IndoorTrackingPersistedEvent extends ApplicationEvent {

    private final IndoorTrackingEvent indoorTrackingEvent;

    public IndoorTrackingPersistedEvent(Object source, IndoorTrackingEvent indoorTrackingEvent) {
        super(source);
        this.indoorTrackingEvent = indoorTrackingEvent;
    }
}
