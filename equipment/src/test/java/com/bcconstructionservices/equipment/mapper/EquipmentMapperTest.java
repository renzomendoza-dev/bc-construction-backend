package com.bcconstructionservices.equipment.mapper;

import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link EquipmentMapper}, following the same pattern used for
 * PurchaseReceiptMapperTest / StockMovementMapperTest in the inventory module:
 * the MapStruct-generated impl is instantiated directly (no Spring context),
 * wrapped as a Mockito spy, and its UserLookupHelper delegate is injected
 * manually via ReflectionTestUtils since MapStruct's `uses`/`qualifiedByName`
 * wiring only happens through Spring's component scanning at runtime.
 *
 * ASSUMPTIONS — verify against the real classes and correct if they differ:
 * - EquipmentMapper is a MapStruct interface with a generated impl class
 *   EquipmentMapperImpl (componentModel = "spring", uses = UserLookupHelper.class).
 * - EquipmentMapper exposes:
 *     EquipmentResponse toResponse(Equipment equipment)
 *     Equipment toEntity(EquipmentCreateRequest request)
 *     void updateEntityFromRequest(EquipmentUpdateRequest request, @MappingTarget Equipment equipment)
 * - EquipmentResponse has a `holderName` (String) field, populated from
 *   currentHolderId via a @Named UserLookupHelper method — assumed here to be
 *   `resolveFullName(Long userId)` returning a nullable/empty-safe String,
 *   mirroring how PurchaseReceiptMapper/StockMovementMapper resolve
 *   createdBy/updatedBy names. Adjust the mocked method name/signature to
 *   match the real @Named-qualified method.
 * - EquipmentCreateRequest fields: assetTag, name, category, serialNumber,
 *   purchasePrice, purchaseDate, purchaseVendor. It intentionally has no
 *   status/currentHolderId/currentSite/checkedOutAt fields — those are
 *   system-controlled per V15's schema and the create flow, not client input.
 * - EquipmentUpdateRequest fields: name, category, serialNumber,
 *   purchasePrice, purchaseDate, purchaseVendor — all nullable, only non-null
 *   fields applied (same "only non-null fields present are applied" pattern
 *   already used in WarehouseUpdateRequest). It has no status/holder/site/
 *   checkedOutAt fields either — those are changed via dedicated
 *   check-out/check-in operations, not this general update endpoint.
 */
class EquipmentMapperTest {

    private EquipmentMapper equipmentMapper;
    private UserLookupHelper userLookupHelper;

    @BeforeEach
    void setUp() {
        equipmentMapper = Mockito.spy(new EquipmentMapperImpl());
        userLookupHelper = Mockito.mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(equipmentMapper, "userLookupHelper", userLookupHelper);
    }

    @Test
    void toResponse_resolvesHolderName_whenCurrentHolderIdIsSet() {
        Equipment equipment = Equipment.builder()
                .assetTag("EQ-201")
                .name("Excavator")
                .status(EquipmentStatus.CHECKED_OUT)
                .currentHolderId(5L)
                .currentSite("Site A")
                .build();

        when(userLookupHelper.resolveUserName(eq(5L))).thenReturn("Juan Dela Cruz");

        EquipmentResponse response = equipmentMapper.toResponse(equipment);

        assertThat(response.getCurrentHolderName()).isEqualTo("Juan Dela Cruz");
    }

    @Test
    void toResponse_leavesHolderNameNullOrEmpty_whenCurrentHolderIdIsNull() {
        Equipment equipment = Equipment.builder()
                .assetTag("EQ-202")
                .name("Bulldozer")
                .status(EquipmentStatus.AVAILABLE)
                .currentHolderId(null)
                .build();

        EquipmentResponse response = equipmentMapper.toResponse(equipment);

        assertThat(response.getCurrentHolderName()).isNullOrEmpty();
        // The generated mapper calls UserLookupHelper unconditionally rather
        // than short-circuiting on null — it just passes null through, and
        // the (unstubbed) mock returns null by default, so the response
        // field ends up null too. Verify that call happened as expected
        // rather than asserting no interaction, which was the wrong
        // assumption.
        Mockito.verify(userLookupHelper).resolveUserName(null);
    }

    @Test
    void toEntity_mapsAllCreateRequestFields_andLeavesSystemControlledFieldsUnset() {
        EquipmentCreateRequest request = new EquipmentCreateRequest();
        request.setAssetTag("EQ-301");
        request.setName("Crane");
        request.setCategory("Heavy Machinery");
        request.setSerialNumber("SN-12345");
        request.setPurchasePrice(new BigDecimal("150000.00"));
        request.setPurchaseDate(LocalDate.of(2024, 3, 15));
        request.setPurchaseVendor("ACME Equipment Co.");

        Equipment entity = equipmentMapper.toEntity(request);

        assertThat(entity.getAssetTag()).isEqualTo("EQ-301");
        assertThat(entity.getName()).isEqualTo("Crane");
        assertThat(entity.getCategory()).isEqualTo("Heavy Machinery");
        assertThat(entity.getSerialNumber()).isEqualTo("SN-12345");
        assertThat(entity.getPurchasePrice()).isEqualByComparingTo("150000.00");
        assertThat(entity.getPurchaseDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(entity.getPurchaseVendor()).isEqualTo("ACME Equipment Co.");

        // System-controlled fields must not be populated by this mapping —
        // status defaults at the DB level, holder/site/checkedOutAt are set
        // only via check-out/check-in operations.
        assertThat(entity.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(entity.getCurrentHolderId()).isNull();
        assertThat(entity.getCurrentSite()).isNull();
        assertThat(entity.getCheckedOutAt()).isNull();
    }

    @Test
    void updateEntityFromRequest_updatesEditableFields_andLeavesSystemControlledFieldsUntouched() {
        Equipment existing = Equipment.builder()
                .assetTag("EQ-401")
                .name("Old Name")
                .category("Old Category")
                .serialNumber("OLD-SN")
                .purchasePrice(new BigDecimal("1000.00"))
                .purchaseDate(LocalDate.of(2020, 1, 1))
                .purchaseVendor("Old Vendor")
                .status(EquipmentStatus.CHECKED_OUT)
                .currentHolderId(9L)
                .currentSite("Site B")
                .build();

        EquipmentUpdateRequest request = new EquipmentUpdateRequest();
        request.setName("New Name");
        request.setCategory("New Category");
        request.setSerialNumber("NEW-SN");
        request.setPurchasePrice(new BigDecimal("2000.00"));
        request.setPurchaseDate(LocalDate.of(2023, 6, 1));
        request.setPurchaseVendor("New Vendor");

        equipmentMapper.updateEntityFromRequest(request, existing);

        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getCategory()).isEqualTo("New Category");
        assertThat(existing.getSerialNumber()).isEqualTo("NEW-SN");
        assertThat(existing.getPurchasePrice()).isEqualByComparingTo("2000.00");
        assertThat(existing.getPurchaseDate()).isEqualTo(LocalDate.of(2023, 6, 1));
        assertThat(existing.getPurchaseVendor()).isEqualTo("New Vendor");

        // Status/holder/site/checkedOutAt are not part of this DTO and must
        // remain exactly as they were before the update was applied.
        assertThat(existing.getStatus()).isEqualTo(EquipmentStatus.CHECKED_OUT);
        assertThat(existing.getCurrentHolderId()).isEqualTo(9L);
        assertThat(existing.getCurrentSite()).isEqualTo("Site B");
    }
}