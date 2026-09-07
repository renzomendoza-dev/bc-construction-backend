package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.workers.dto.AttendanceBatchCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchLineRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchResponse;
import com.bcconstructionservices.workers.dto.AttendanceBatchSkippedEntry;
import com.bcconstructionservices.workers.dto.AttendanceCalendarEntry;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.exception.DuplicateAttendanceException;
import com.bcconstructionservices.workers.exception.InactiveWorkerException;
import com.bcconstructionservices.workers.exception.InvalidAttendanceBatchRequestException;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.service.AttendanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private AttendanceService attendanceService;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    private AttendanceCreateRequest validCreateRequest() {
        AttendanceCreateRequest request = new AttendanceCreateRequest();
        request.setWorkerId(1L);
        request.setProjectId(12L);
        request.setAttendanceDate(LocalDate.of(2026, 9, 5));
        request.setDaysPresent(new BigDecimal("1.0"));
        return request;
    }

    private AttendanceResponse sampleResponse(Long id) {
        AttendanceResponse response = new AttendanceResponse();
        response.setId(id);
        response.setWorkerId(1L);
        response.setProjectId(12L);
        response.setAttendanceDate(LocalDate.of(2026, 9, 5));
        response.setDaysPresent(new BigDecimal("1.0"));
        response.setAmount(new BigDecimal("800.00"));
        return response;
    }

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedAttendanceForValidBody() throws Exception {
            when(attendanceService.createAttendance(any(AttendanceCreateRequest.class)))
                    .thenReturn(sampleResponse(501L));

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt("ATTENDANCE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(501));
        }

        @Test
        void shouldReturn400WhenDaysPresentIsMissing() throws Exception {
            AttendanceCreateRequest request = validCreateRequest();
            request.setDaysPresent(null);

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenWorkerIsInactive() throws Exception {
            when(attendanceService.createAttendance(any(AttendanceCreateRequest.class)))
                    .thenThrow(new InactiveWorkerException(1L));

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt("ATTENDANCE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenWorkerNotFound() throws Exception {
            when(attendanceService.createAttendance(any(AttendanceCreateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Worker", 999L));

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt("ATTENDANCE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenAttendanceAlreadyRecordedForThatWorkerAndDay() throws Exception {
            when(attendanceService.createAttendance(any(AttendanceCreateRequest.class)))
                    .thenThrow(new DuplicateAttendanceException(1L, LocalDate.of(2026, 9, 5)));

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt("ATTENDANCE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn422WhenProjectIsNotEditable() throws Exception {
            when(attendanceService.createAttendance(any(AttendanceCreateRequest.class)))
                    .thenThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt("ATTENDANCE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksAttendanceCreatePermission() throws Exception {
            mockMvc.perform(post("/api/attendance")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/attendance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class CreateBatchTests {

        private AttendanceBatchCreateRequest validBatchRequest() {
            return AttendanceBatchCreateRequest.builder()
                    .projectId(12L)
                    .date(LocalDate.of(2026, 9, 7))
                    .entries(List.of(AttendanceBatchLineRequest.builder()
                            .workerId(1L).timeIn(LocalTime.of(7, 0)).timeOut(LocalTime.of(16, 0)).build()))
                    .build();
        }

        @Test
        void shouldReturn201WithCreatedAndSkippedLists() throws Exception {
            when(attendanceService.createBatch(any(AttendanceBatchCreateRequest.class)))
                    .thenReturn(AttendanceBatchResponse.builder()
                            .created(List.of(sampleResponse(501L)))
                            .skipped(List.of(AttendanceBatchSkippedEntry.builder()
                                    .workerId(2L).reason("Attendance already recorded for this date").build()))
                            .build());

            mockMvc.perform(post("/api/attendance/batch")
                            .with(authenticatedJwt("ATTENDANCE_BATCH_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validBatchRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.created[0].id").value(501))
                    .andExpect(jsonPath("$.skipped[0].workerId").value(2));
        }

        @Test
        void shouldReturn400WhenEntriesIsEmpty() throws Exception {
            AttendanceBatchCreateRequest request = AttendanceBatchCreateRequest.builder()
                    .projectId(12L).date(LocalDate.of(2026, 9, 7)).entries(List.of()).build();

            mockMvc.perform(post("/api/attendance/batch")
                            .with(authenticatedJwt("ATTENDANCE_BATCH_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenTimeOutIsNotAfterTimeIn() throws Exception {
            when(attendanceService.createBatch(any(AttendanceBatchCreateRequest.class)))
                    .thenThrow(new InvalidAttendanceBatchRequestException("timeOut must be after timeIn"));

            mockMvc.perform(post("/api/attendance/batch")
                            .with(authenticatedJwt("ATTENDANCE_BATCH_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validBatchRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn422WhenProjectIsNotEditable() throws Exception {
            when(attendanceService.createBatch(any(AttendanceBatchCreateRequest.class)))
                    .thenThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(post("/api/attendance/batch")
                            .with(authenticatedJwt("ATTENDANCE_BATCH_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validBatchRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksAttendanceBatchCreatePermission() throws Exception {
            mockMvc.perform(post("/api/attendance/batch")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validBatchRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/attendance/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validBatchRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class CalendarTests {

        @Test
        void shouldReturn200WithCalendarEntries() throws Exception {
            when(attendanceService.getCalendar(any(), any(), any())).thenReturn(List.of(
                    AttendanceCalendarEntry.builder()
                            .date(LocalDate.of(2026, 9, 1)).projectId(12L)
                            .projectName("Sta. Maria Warehouse Expansion").workerCount(6).build()));

            mockMvc.perform(get("/api/attendance/calendar").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].projectId").value(12))
                    .andExpect(jsonPath("$[0].workerCount").value(6));
        }

        @Test
        void shouldReturn200WithEmptyListWhenNothingRecorded() throws Exception {
            when(attendanceService.getCalendar(any(), any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/attendance/calendar").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void shouldReturn204WhenDeleted() throws Exception {
            mockMvc.perform(delete("/api/attendance/{id}", 501L)
                            .with(authenticatedJwt("ATTENDANCE_DELETE")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenAttendanceNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Attendance", 999L))
                    .when(attendanceService).deleteAttendance(999L);

            mockMvc.perform(delete("/api/attendance/{id}", 999L)
                            .with(authenticatedJwt("ATTENDANCE_DELETE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenLinkedProjectIsNotEditable() throws Exception {
            org.mockito.Mockito.doThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED))
                    .when(attendanceService).deleteAttendance(501L);

            mockMvc.perform(delete("/api/attendance/{id}", 501L)
                            .with(authenticatedJwt("ATTENDANCE_DELETE")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksAttendanceDeletePermission() throws Exception {
            mockMvc.perform(delete("/api/attendance/{id}", 501L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithAttendanceWhenFound() throws Exception {
            when(attendanceService.getById(501L)).thenReturn(sampleResponse(501L));

            mockMvc.perform(get("/api/attendance/{id}", 501L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(501));
        }

        @Test
        void shouldReturn404WhenNotFound() throws Exception {
            when(attendanceService.getById(999L)).thenThrow(new ResourceNotFoundException("Attendance", 999L));

            mockMvc.perform(get("/api/attendance/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfAttendanceRecords() throws Exception {
            when(attendanceService.search(any(), any(), any(), any(), any()))
                    .thenReturn(PageResponse.<AttendanceResponse>builder()
                            .content(List.of(sampleResponse(501L)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/attendance").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(501));
        }
    }
}
