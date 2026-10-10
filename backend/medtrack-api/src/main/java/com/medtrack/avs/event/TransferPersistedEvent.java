package com.medtrack.avs.event;

import com.medtrack.avs.dto.TransferWebSocketEvent;
import com.medtrack.avs.entity.AssetTransfer;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired when an asset transfer state change has been successfully persisted.
 * Handled strictly AFTER_COMMIT to guarantee that STOMP transfer messages are broadcast
 * ONLY after the database transaction commits.
 */
@Getter
public class TransferPersistedEvent extends ApplicationEvent {

    private final TransferWebSocketEvent transferEvent;
    private final AssetTransfer transfer;

    public TransferPersistedEvent(Object source, TransferWebSocketEvent transferEvent, AssetTransfer transfer) {
        super(source);
        this.transferEvent = transferEvent;
        this.transfer = transfer;
    }
}
