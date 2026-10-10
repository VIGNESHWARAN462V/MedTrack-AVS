package com.medtrack.avs.service;

import com.medtrack.avs.entity.AuditLog;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAuthEvent(User user, String action, String description) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType("USER")
                    .entityId(user != null ? user.getId() : null)
                    .description(description)
                    .build();
            auditLogRepository.save(auditLog);
            log.info("Audit log recorded: action={}, userId={}", action, user != null ? user.getId() : "anonymous");
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    @Transactional
    public void logAssetEvent(User user, String action, Long assetId, String description) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType("ASSET")
                    .entityId(assetId)
                    .description(description)
                    .build();
            auditLogRepository.save(auditLog);
            log.info("Asset audit log recorded: action={}, assetId={}, userId={}", action, assetId, user != null ? user.getId() : "anonymous");
        } catch (Exception e) {
            log.error("Failed to record asset audit log: {}", e.getMessage());
        }
    }

    @Transactional
    public void logTransferEvent(User user, String action, Long transferId, Long assetId, String description) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType("TRANSFER")
                    .entityId(transferId)
                    .description(description)
                    .build();
            auditLogRepository.save(auditLog);
            log.info("Transfer audit log recorded: action={}, transferId={}, assetId={}, userId={}",
                    action, transferId, assetId, user != null ? user.getId() : "anonymous");
        } catch (Exception e) {
            log.error("Failed to record transfer audit log: {}", e.getMessage());
        }
    }
}
