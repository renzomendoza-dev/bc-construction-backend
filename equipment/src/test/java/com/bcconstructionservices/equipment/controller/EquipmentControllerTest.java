package com.bcconstructionservices.equipment.controller;

import com.bcconstructionservices.equipment.dto.EquipmentCheckInRequest;
import com.bcconstructionservices.equipment.dto.EquipmentCheckOutRequest;
import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.exception.EquipmentAlreadyAtWarehouseException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.NoOpenAssignmentException;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.service.EquipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link EquipmentController}.
 *
 * ASSUMPTIONS — please verify against real project conventions:
 *
 * 1. EXCEPTION MAPPING: assumes a @RestControllerAdvice in the equipment package
 *    maps EquipmentNotFoundException -> 404, InvalidEquipmentStatusException -> 409,
 *    NoOpenAssignmentException -> 409. @WebMvcTest auto-detects @ControllerAdvice
 *    beans in the same slice, so no explicit @Import should be required IF that
 *    class exists. If it doesn't exist yet, these tests will fail against an
 *    unhandled-exception 500 until it's added — that failure is the correct signal
 *    that the advice is missing, not a bug in this test.
 *
 * 2. EquipmentMapper is mocked via @MockitoBean, not the real generated impl, even
 *    though only EquipmentService was requested to be mocked. The controller has
 *    EquipmentMapper as a constructor dependency, and @WebMvcTest won't discover a
 *    MapStruct @Component bean automatically — mocking it lets this test control
 *    exact response JSON without needing to @Import the real generated impl class
 *    (mapper correctness itself belongs in a dedicated EquipmentMapperTest).
 *
 * 3. SECURITY: no confirmed test security config pattern was available from
 *    inventory/user modules, so this uses SecurityMockMvcRequestPostProcessors.jwt()
 *    directly against Spring Boot's default oauth2-resource-server auto-configuration,
 *    with a realm_access.roles claim matching KeycloakJwtAuthenticationConverter.
 *    TODO: replace with the project's actual shared test security config/import if
 *    one exists (e.g. @Import(SecurityTestConfig.class)) — this is the part most
 *    likely to need changing.
 */
@WebMvcTest(EquipmentController.class)
class EquipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // NOT @Autowired: under Jackson 3.x (Spring Boot 4.1's default), the registered
    // bean is tools.jackson.databind.json.JsonMapper, not
    // com.fasterxml.jackson.databind.ObjectMapper — so autowiring the classic type
    // fails with "No qualifying bean of type ...ObjectMapper". This test only needs
    // to serialize simple request DTOs to JSON strings for MockMvc bodies, so a
    // plain local instance is sufficient and sidesteps the bean-registration
    // mismatch entirely.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private EquipmentService equipmentService;

    @MockitoBean
    private EquipmentMapper equipmentMapper;

    /**
     * Authenticated JWT, optionally granted the given permission(s) (e.g.
     * "EQUIPMENT_CREATE"). Authorities are set directly via
     * {@code .authorities(...)} rather than a {@code realm_access} claim —
     * {@code SecurityMockMvcRequestPostProcessors.jwt()} uses a plain
     * scope-based {@code JwtGrantedAuthoritiesConverter} by default, not the
     * app module's {@code KeycloakJwtAuthenticationConverter} (which this
     * module can't reference without a circular dependency), so a
     * {@code realm_access} claim alone would never actually grant a
     * {@code ROLE_*} authority here.
     */
    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    private Equipment sampleAvailableEquipment() {
        return Equipment.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .status(EquipmentStatus.AVAILABLE)
                .build();
    }

    private EquipmentResponse sampleResponse(Long id, EquipmentStatus status) {
        return EquipmentResponse.builder()
                .id(id)
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .status(status)
                .build();
    }

    // ---------------------------------------------------------------
    // POST /api/equipment
    // ---------------------------------------------------------------

    @Test
    void create_withValidBody_returns201AndCreatedResource() throws Exception {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .warehouseId(1L)
                .build();

        Equipment createdEntity = sampleAvailableEquipment();
        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.AVAILABLE);

        when(equipmentService.create(any(EquipmentCreateRequest.class))).thenReturn(createdEntity);
        when(equipmentMapper.toResponse(createdEntity)).thenReturn(response);

        mockMvc.perform(post("/api/equipment")
                        .with(authenticatedJwt("EQUIPMENT_CREATE"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assetTag").value("EQ-2026-0001"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void create_withMissingRequiredFields_returns400() throws Exception {
        // assetTag and name are both @NotBlank on EquipmentCreateRequest; omit both.
        String invalidBody = "{\"category\":\"Power Tools\"}";

        mockMvc.perform(post("/api/equipment")
                        .with(authenticatedJwt())
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withoutEquipmentCreatePermission_returns403() throws Exception {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .warehouseId(1L)
                .build();

        mockMvc.perform(post("/api/equipment")
                        .with(authenticatedJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // PATCH /api/equipment/{id}
    // ---------------------------------------------------------------

    @Test
    void update_withValidBody_returns200AndUpdatedResource() throws Exception {
        EquipmentUpdateRequest request = EquipmentUpdateRequest.builder()
                .name("DeWalt 20V Cordless Drill (renamed)")
                .build();

        Equipment updatedEntity = sampleAvailableEquipment();
        EquipmentResponse response = EquipmentResponse.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill (renamed)")
                .status(EquipmentStatus.AVAILABLE)
                .build();

        when(equipmentService.update(eq(1L), any(EquipmentUpdateRequest.class))).thenReturn(updatedEntity);
        when(equipmentMapper.toResponse(updatedEntity)).thenReturn(response);

        mockMvc.perform(patch("/api/equipment/{id}", 1L)
                        .with(authenticatedJwt("EQUIPMENT_EDIT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("DeWalt 20V Cordless Drill (renamed)"));
    }

    @Test
    void update_withoutEquipmentEditPermission_returns403() throws Exception {
        EquipmentUpdateRequest request = EquipmentUpdateRequest.builder()
                .name("DeWalt 20V Cordless Drill (renamed)")
                .build();

        mockMvc.perform(patch("/api/equipment/{id}", 1L)
                        .with(authenticatedJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // GET /api/equipment
    // ---------------------------------------------------------------

    @Test
    void findAll_returnsList() throws Exception {
        Equipment equipment = sampleAvailableEquipment();
        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.AVAILABLE);

        when(equipmentService.findAll(isNull())).thenReturn(List.of(equipment));
        when(equipmentMapper.toResponse(equipment)).thenReturn(response);

        mockMvc.perform(get("/api/equipment")
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void findAll_respectsStatusQueryParameter() throws Exception {
        Equipment equipment = sampleAvailableEquipment();
        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.AVAILABLE);

        when(equipmentService.findAll(EquipmentStatus.AVAILABLE)).thenReturn(List.of(equipment));
        when(equipmentMapper.toResponse(equipment)).thenReturn(response);

        mockMvc.perform(get("/api/equipment")
                        .param("status", "AVAILABLE")
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    // ---------------------------------------------------------------
    // GET /api/equipment/{id}
    // ---------------------------------------------------------------

    @Test
    void findById_returns404_whenServiceReportsNotFound() throws Exception {
        when(equipmentService.findById(999L)).thenThrow(new EquipmentNotFoundException(999L));

        mockMvc.perform(get("/api/equipment/{id}", 999L)
                        .with(authenticatedJwt()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // POST /api/equipment/{id}/checkout
    // ---------------------------------------------------------------

    @Test
    void checkOut_succeeds_returns200() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(17L)
                .siteWarehouseId(2L)
                .build();

        Equipment checkedOutEntity = Equipment.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .status(EquipmentStatus.CHECKED_OUT)
                .currentHolderId(17L)
                .currentWarehouseId(2L)
                .checkedOutAt(Instant.now())
                .build();

        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.CHECKED_OUT);

        when(equipmentService.checkOut(eq(1L), eq(17L), eq(2L), any()))
                .thenReturn(checkedOutEntity);
        when(equipmentMapper.toResponse(checkedOutEntity)).thenReturn(response);

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKOUT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"));
    }

    @Test
    void checkOut_transfersEquipment_returns200() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(21L)
                .siteWarehouseId(3L)
                .build();

        Equipment transferredEntity = Equipment.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .status(EquipmentStatus.CHECKED_OUT)
                .currentHolderId(21L)
                .currentWarehouseId(3L)
                .checkedOutAt(Instant.now())
                .build();

        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.CHECKED_OUT);

        when(equipmentService.checkOut(eq(1L), eq(21L), eq(3L), any()))
                .thenReturn(transferredEntity);
        when(equipmentMapper.toResponse(transferredEntity)).thenReturn(response);

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKOUT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"));
    }

    @Test
    void checkOut_returns400_whenTransferTargetsCurrentWarehouse() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(21L)
                .siteWarehouseId(2L)
                .build();

        when(equipmentService.checkOut(eq(1L), eq(21L), eq(2L), any()))
                .thenThrow(new EquipmentAlreadyAtWarehouseException(1L, 2L));

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKOUT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkOut_returns409_whenEquipmentNotAvailable() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(17L)
                .siteWarehouseId(2L)
                .build();

        when(equipmentService.checkOut(eq(1L), anyLong(), any(), any()))
                .thenThrow(new InvalidEquipmentStatusException(
                        "Equipment EQ-2026-0001 is not available for checkout (current status: CHECKED_OUT)"));

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKOUT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkOut_returns404_whenUserDoesNotExist() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(999L)
                .siteWarehouseId(2L)
                .build();

        when(equipmentService.checkOut(eq(1L), eq(999L), any(), any()))
                .thenThrow(new InvalidCheckoutUserException(999L));

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKOUT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkOut_withoutEquipmentCheckoutPermission_returns403() throws Exception {
        EquipmentCheckOutRequest request = EquipmentCheckOutRequest.builder()
                .userId(17L)
                .siteWarehouseId(2L)
                .build();

        mockMvc.perform(post("/api/equipment/{id}/checkout", 1L)
                        .with(authenticatedJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/equipment/{id}/checkin
    // ---------------------------------------------------------------

    @Test
    void checkIn_succeeds_returns200() throws Exception {
        EquipmentCheckInRequest request = EquipmentCheckInRequest.builder()
                .destinationWarehouseId(1L)
                .conditionIn("Returned in working order")
                .build();

        Equipment checkedInEntity = sampleAvailableEquipment();
        EquipmentResponse response = sampleResponse(1L, EquipmentStatus.AVAILABLE);

        when(equipmentService.checkIn(eq(1L), eq(1L), any())).thenReturn(checkedInEntity);
        when(equipmentMapper.toResponse(checkedInEntity)).thenReturn(response);

        mockMvc.perform(post("/api/equipment/{id}/checkin", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void checkIn_returns409_whenNoOpenAssignment() throws Exception {
        EquipmentCheckInRequest request = EquipmentCheckInRequest.builder()
                .destinationWarehouseId(1L)
                .conditionIn("Returned in working order")
                .build();

        when(equipmentService.checkIn(eq(1L), eq(1L), any()))
                .thenThrow(new NoOpenAssignmentException(1L));

        mockMvc.perform(post("/api/equipment/{id}/checkin", 1L)
                        .with(authenticatedJwt("EQUIPMENT_CHECKIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkIn_withoutEquipmentCheckinPermission_returns403() throws Exception {
        EquipmentCheckInRequest request = EquipmentCheckInRequest.builder()
                .destinationWarehouseId(1L)
                .conditionIn("Returned in working order")
                .build();

        mockMvc.perform(post("/api/equipment/{id}/checkin", 1L)
                        .with(authenticatedJwt())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // GET /api/equipment/overdue
    // ---------------------------------------------------------------

    @Test
    void findOverdue_returnsFilteredList() throws Exception {
        Equipment overdueEntity = Equipment.builder()
                .id(5L)
                .assetTag("EQ-2026-0005")
                .status(EquipmentStatus.CHECKED_OUT)
                .checkedOutAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();

        EquipmentResponse response = sampleResponse(5L, EquipmentStatus.CHECKED_OUT);

        when(equipmentService.findOverdue(7)).thenReturn(List.of(overdueEntity));
        when(equipmentMapper.toResponse(overdueEntity)).thenReturn(response);

        mockMvc.perform(get("/api/equipment/overdue")
                        .param("days", "7")
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5));
    }

    // ---------------------------------------------------------------
    // Authentication required
    // ---------------------------------------------------------------

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/equipment"))
                .andExpect(status().isUnauthorized());
    }
}