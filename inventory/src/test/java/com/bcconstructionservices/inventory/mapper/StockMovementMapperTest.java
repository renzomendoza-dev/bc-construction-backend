package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class StockMovementMapperTest {

    private StockMovementMapper mapper;

    private UserLookupHelper userLookupHelper;

    private Item item;
    private Warehouse warehouse;
    private StorageLocation locationA;
    private StorageLocation locationB;

    @BeforeEach
    void setUp() {
        mapper = new StockMovementMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);

        item = new Item();
        item.setId(42L);
        item.setSku("SKU-001");
        item.setName("Portland Cement 40kg");

        warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setCode("WH-MAIN");
        warehouse.setName("Main Yard Warehouse");

        locationA = new StorageLocation();
        locationA.setId(21L);
        locationA.setWarehouse(warehouse);
        locationA.setCode("A-01-02");

        locationB = new StorageLocation();
        locationB.setId(22L);
        locationB.setWarehouse(warehouse);
        locationB.setCode("B-02-05");
    }

    private StockMovement buildMovement(MovementType type,
                                        StorageLocation fromLocation,
                                        StorageLocation toLocation) {
        StockMovement movement = new StockMovement();
        movement.setId(9001L);
        movement.setItem(item);
        movement.setWarehouse(warehouse);
        movement.setFromLocation(fromLocation);
        movement.setToLocation(toLocation);
        movement.setType(type);
        movement.setDirection(MovementDirection.OUT);
        movement.setQuantity(50);
        movement.setReason("Delivery from supplier PO-2026-0713");
        movement.setCreatedAt(Instant.parse("2026-07-13T02:40:00Z"));
        return movement;
    }

    @Nested
    class FullyPopulatedMovement {

        @Test
        void shouldMapMovementToResponseWithAllFields() {
            StockMovement movement =
                    buildMovement(MovementType.TRANSFER, locationA, locationB);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(9001L);
            assertThat(response.getItemId()).isEqualTo(42L);
            assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getWarehouseId()).isEqualTo(3L);
            assertThat(response.getFromLocationId()).isEqualTo(21L);
            assertThat(response.getToLocationId()).isEqualTo(22L);
            assertThat(response.getType()).isEqualTo(MovementType.TRANSFER);
            assertThat(response.getDirection()).isEqualTo(MovementDirection.OUT);
            assertThat(response.getQuantity()).isEqualTo(50);
            assertThat(response.getReason()).isEqualTo("Delivery from supplier PO-2026-0713");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-13T02:40:00Z"));
        }
    }

    @Nested
    class LocationNullSafety {

        @Test
        void shouldMapBothLocationIdsToNullWhenBothLocationsAreNullWithoutThrowing() {
            StockMovement movement =
                    buildMovement(MovementType.ADJUSTMENT, null, null);

            assertThatCode(() -> mapper.toResponse(movement))
                    .doesNotThrowAnyException();

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getFromLocationId()).isNull();
            assertThat(response.getToLocationId()).isNull();
        }

        @Test
        void shouldMapOnlyToLocationWhenFromLocationIsNull() {
            // Typical inbound (IN) movement: stock arrives into a location.
            StockMovement movement =
                    buildMovement(MovementType.IN, null, locationB);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getFromLocationId()).isNull();
            assertThat(response.getToLocationId()).isEqualTo(22L);
        }

        @Test
        void shouldMapOnlyFromLocationWhenToLocationIsNull() {
            // Typical outbound (OUT) movement: stock leaves a location.
            StockMovement movement =
                    buildMovement(MovementType.OUT, locationA, null);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getFromLocationId()).isEqualTo(21L);
            assertThat(response.getToLocationId()).isNull();
        }

        @Test
        void shouldStillMapOtherFieldsWhenBothLocationsAreNull() {
            StockMovement movement =
                    buildMovement(MovementType.ADJUSTMENT, null, null);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getId()).isEqualTo(9001L);
            assertThat(response.getItemId()).isEqualTo(42L);
            assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getWarehouseId()).isEqualTo(3L);
            assertThat(response.getQuantity()).isEqualTo(50);
            assertThat(response.getReason()).isEqualTo("Delivery from supplier PO-2026-0713");
        }
    }

    @Nested
    class TypeEnumMapping {

        @ParameterizedTest
        @EnumSource(MovementType.class)
        void shouldMapEveryMovementTypeThroughUnchanged(MovementType type) {
            StockMovement movement = buildMovement(type, locationA, locationB);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getType()).isEqualTo(type);
        }
    }

    @Nested
    class DirectionEnumMapping {

        @ParameterizedTest
        @EnumSource(MovementDirection.class)
        void shouldMapEveryMovementDirectionThroughUnchanged(MovementDirection direction) {
            StockMovement movement = buildMovement(MovementType.TRANSFER, locationA, locationB);
            movement.setDirection(direction);

            StockMovementResponse response = mapper.toResponse(movement);

            assertThat(response.getDirection()).isEqualTo(direction);
        }
    }
}