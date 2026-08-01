package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AdminAction;
import com.bcconstructionservices.user.entity.AdminAuditLog;
import com.bcconstructionservices.user.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Records admin actions taken against a user (activation, role changes,
 * manual re-sync) to {@link AdminAuditLog} for later review.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public void record(Long actorUserId, Long targetUserId, AdminAction action, String detail) {
        AdminAuditLog log = AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .action(action)
                .detail(detail)
                .createdAt(Instant.now())
                .build();
        adminAuditLogRepository.save(log);
    }
}
