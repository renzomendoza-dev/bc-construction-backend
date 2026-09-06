package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.entity.Attendance;
import com.bcconstructionservices.workers.entity.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceMapperTest {

    private AttendanceMapper mapper;

    private UserLookupHelper userLookupHelper;
    private ProjectLookupHelper projectLookupHelper;

    @BeforeEach
    void setUp() {
        mapper = new AttendanceMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        projectLookupHelper = mock(ProjectLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);
        ReflectionTestUtils.setField(mapper, "projectLookupHelper", projectLookupHelper);
    }

    private Worker buildWorker() {
        Worker worker = new Worker();
        worker.setId(1L);
        worker.setName("Ramon Villanueva");
        worker.setDailyRate(new BigDecimal("800.00"));
        return worker;
    }

    private Attendance buildAttendance() {
        Attendance attendance = new Attendance();
        attendance.setId(501L);
        attendance.setWorker(buildWorker());
        attendance.setProjectId(12L);
        attendance.setAttendanceDate(LocalDate.of(2026, 9, 5));
        attendance.setDaysPresent(new BigDecimal("1.0"));
        attendance.setRateSnapshot(new BigDecimal("800.00"));
        attendance.setNotes("Rebar tying");
        attendance.setProjectExpenseId(305L);
        attendance.setRecordedBy(1L);
        attendance.setCreatedAt(Instant.parse("2026-09-05T09:15:30Z"));
        return attendance;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapAttendanceToResponseWithAllFields() {
            when(projectLookupHelper.resolveProjectName(12L)).thenReturn("Sta. Maria Warehouse Expansion");
            when(userLookupHelper.resolveUserName(1L)).thenReturn("Renzo Mendoza");
            Attendance attendance = buildAttendance();

            AttendanceResponse response = mapper.toResponse(attendance);

            assertThat(response.getId()).isEqualTo(501L);
            assertThat(response.getWorkerId()).isEqualTo(1L);
            assertThat(response.getWorkerName()).isEqualTo("Ramon Villanueva");
            assertThat(response.getProjectId()).isEqualTo(12L);
            assertThat(response.getProjectName()).isEqualTo("Sta. Maria Warehouse Expansion");
            assertThat(response.getAttendanceDate()).isEqualTo(LocalDate.of(2026, 9, 5));
            assertThat(response.getDaysPresent()).isEqualByComparingTo("1.0");
            assertThat(response.getRateSnapshot()).isEqualByComparingTo("800.00");
            assertThat(response.getAmount()).isEqualByComparingTo("800.00");
            assertThat(response.getNotes()).isEqualTo("Rebar tying");
            assertThat(response.getProjectExpenseId()).isEqualTo(305L);
            assertThat(response.getRecordedBy()).isEqualTo(1L);
            assertThat(response.getRecordedByName()).isEqualTo("Renzo Mendoza");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-09-05T09:15:30Z"));
        }

        @Test
        void shouldComputeAmountAsRateSnapshotTimesDaysPresent() {
            Attendance attendance = buildAttendance();
            attendance.setDaysPresent(new BigDecimal("0.5"));
            attendance.setRateSnapshot(new BigDecimal("800.00"));

            AttendanceResponse response = mapper.toResponse(attendance);

            assertThat(response.getAmount()).isEqualByComparingTo("400.00");
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            AttendanceCreateRequest request = AttendanceCreateRequest.builder()
                    .workerId(1L)
                    .projectId(12L)
                    .attendanceDate(LocalDate.of(2026, 9, 5))
                    .daysPresent(new BigDecimal("1.0"))
                    .notes("Rebar tying")
                    .build();

            Attendance entity = mapper.toEntity(request);

            assertThat(entity.getProjectId()).isEqualTo(12L);
            assertThat(entity.getAttendanceDate()).isEqualTo(LocalDate.of(2026, 9, 5));
            assertThat(entity.getDaysPresent()).isEqualByComparingTo("1.0");
            assertThat(entity.getNotes()).isEqualTo("Rebar tying");
        }

        @Test
        void shouldNotSetServerManagedFields() {
            AttendanceCreateRequest request = AttendanceCreateRequest.builder()
                    .workerId(1L)
                    .projectId(12L)
                    .attendanceDate(LocalDate.of(2026, 9, 5))
                    .daysPresent(new BigDecimal("1.0"))
                    .build();

            Attendance entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getWorker()).isNull();
            assertThat(entity.getRateSnapshot()).isNull();
            assertThat(entity.getProjectExpenseId()).isNull();
            assertThat(entity.getRecordedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
        }
    }
}
