package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AdminAction;
import com.bcconstructionservices.user.entity.AdminAuditLog;
import com.bcconstructionservices.user.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks
    private AdminAuditService adminAuditService;

    @Test
    void record_buildsExpectedEntityAndSaves() {
        adminAuditService.record(1L, 42L, AdminAction.ASSIGN_ROLE, "MANAGER");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository, times(1)).save(captor.capture());

        AdminAuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(1L);
        assertThat(saved.getTargetUserId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo(AdminAction.ASSIGN_ROLE);
        assertThat(saved.getDetail()).isEqualTo("MANAGER");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void record_withNullActorAndDetail_stillSaves() {
        adminAuditService.record(null, 42L, AdminAction.ACTIVATE, null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository, times(1)).save(captor.capture());

        AdminAuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getTargetUserId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo(AdminAction.ACTIVATE);
        assertThat(saved.getDetail()).isNull();
    }
}
