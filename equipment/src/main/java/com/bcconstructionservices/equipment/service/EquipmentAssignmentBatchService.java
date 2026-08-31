package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchLineRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.equipment.exception.EquipmentAssignmentBatchNotFoundException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentBatchRequestException;
import com.bcconstructionservices.equipment.exception.WarehouseNotFoundException;
import com.bcconstructionservices.equipment.mapper.EquipmentAssignmentBatchMapper;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentBatchLineRepository;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentBatchRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for batch equipment assignment (checkout), site-to-site
 * transfer, and return (check-in) — the equipment-tracking analogue of
 * inventory's TransferBatchService. Direction is never stored as its own
 * field, and — unlike when this only had two directions — it's no longer
 * even fully resolved at the batch level: destinationWarehouseId's type
 * (SITE vs. MAIN) only tells us whether holderId is required
 * (SITE — covers BOTH assign-out and transfer, since both require a holder)
 * or must be omitted (MAIN — return). Which of assign-out or transfer a SITE
 * line actually is depends on that specific equipment's current status,
 * which isn't checked until submit() — so it's resolved per line, inside
 * {@link EquipmentService#checkOut} itself, not here. submit() only needs to
 * know "does this batch have a holder" to pick checkOut vs. checkIn per
 * line; checkOut internally branches assign-out vs. transfer, and checkIn
 * still only ever means return. All three delegate to
 * {@link EquipmentService#checkOut}/{@link EquipmentService#checkIn} — the
 * same single-item logic used elsewhere — rather than reimplementing status
 * transitions here.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentAssignmentBatchService {

    private final EquipmentAssignmentBatchRepository equipmentAssignmentBatchRepository;
    private final EquipmentAssignmentBatchLineRepository equipmentAssignmentBatchLineRepository;
    private final EquipmentRepository equipmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final EquipmentService equipmentService;
    private final EquipmentAssignmentBatchMapper equipmentAssignmentBatchMapper;

    public EquipmentAssignmentBatchResponse createDraft(EquipmentAssignmentBatchCreateRequest request) {
        Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(request.getDestinationWarehouseId()));

        // WarehouseType only has two values (SITE, MAIN), so a non-SITE
        // destination is always MAIN — no separate "wrong type entirely"
        // branch is reachable here. A SITE destination covers BOTH
        // assign-out and transfer (both require a holder); which one a
        // given line actually is isn't decided here — see class javadoc.
        boolean holderRequired = destinationWarehouse.getType() == WarehouseType.SITE;

        if (holderRequired && request.getHolderId() == null) {
            throw new InvalidEquipmentBatchRequestException(
                    "holderId is required when destinationWarehouseId is a SITE warehouse (destinationWarehouseId "
                            + destinationWarehouse.getId() + "), whether this batch turns out to be an "
                            + "assign-out or a site-to-site transfer");
        }
        if (!holderRequired && request.getHolderId() != null) {
            throw new InvalidEquipmentBatchRequestException(
                    "holderId must be omitted for a return batch (destinationWarehouseId "
                            + destinationWarehouse.getId() + " is a MAIN warehouse)");
        }
        if (holderRequired && !userRepository.existsById(request.getHolderId())) {
            throw new InvalidCheckoutUserException(request.getHolderId());
        }

        // equipmentAssignmentBatchMapper.toEntity only covers destinationWarehouseId/
        // holderId/notes — status, initiatedBy, and lines are all ignore=true by
        // design (see the mapper's javadoc), so lines are still assembled here.
        EquipmentAssignmentBatch batch = equipmentAssignmentBatchMapper.toEntity(request);

        List<EquipmentAssignmentBatchLine> lines = new ArrayList<>();
        for (EquipmentAssignmentBatchLineRequest lineRequest : request.getLines()) {
            Equipment equipment = equipmentRepository.findById(lineRequest.getEquipmentId())
                    .orElseThrow(() -> new EquipmentNotFoundException(lineRequest.getEquipmentId()));

            lines.add(EquipmentAssignmentBatchLine.builder()
                    .batch(batch)
                    .equipment(equipment)
                    .conditionNotes(lineRequest.getConditionNotes())
                    .build());
        }
        batch.setLines(lines);

        EquipmentAssignmentBatch saved = equipmentAssignmentBatchRepository.save(batch);
        return equipmentAssignmentBatchMapper.toResponse(saved);
    }

    /**
     * Applies every line in one transaction — if any line fails (e.g. its
     * equipment isn't in a valid status for its resolved direction, or a
     * transfer line's destination is the warehouse it's already at), nothing
     * is applied and the batch stays in its prior state, same "all or
     * nothing" pattern as TransferBatchService.submit and
     * PurchaseReceiptService.confirmPurchaseReceipt. Delegates to
     * EquipmentService.checkOut/checkIn per line: checkOut is called
     * whenever this batch has a holder (covers both assign-out and
     * transfer — checkOut itself resolves which, per line, from that
     * equipment's current status), checkIn otherwise (return). Per-line
     * status validation (409 InvalidEquipmentStatusException),
     * same-warehouse-transfer validation (400 EquipmentAlreadyAtWarehouseException),
     * and destination-warehouse-type validation all live in EquipmentService,
     * not here.
     */
    public EquipmentAssignmentBatchResponse submit(Long batchId) {
        EquipmentAssignmentBatch batch = equipmentAssignmentBatchRepository.findById(batchId)
                .orElseThrow(() -> new EquipmentAssignmentBatchNotFoundException(batchId));

        List<EquipmentAssignmentBatchLine> lines = equipmentAssignmentBatchLineRepository.findByBatchId(batchId);
        if (lines.isEmpty()) {
            throw new InvalidEquipmentBatchRequestException(
                    "Equipment assignment batch " + batchId + " has no lines to submit");
        }

        batch.setStatus(EquipmentAssignmentBatchStatus.SUBMITTED);

        boolean hasHolder = batch.getHolderId() != null;
        for (EquipmentAssignmentBatchLine line : lines) {
            Long equipmentId = line.getEquipment().getId();
            if (hasHolder) {
                equipmentService.checkOut(
                        equipmentId, batch.getHolderId(), batch.getDestinationWarehouseId(), line.getConditionNotes());
            } else {
                equipmentService.checkIn(equipmentId, batch.getDestinationWarehouseId(), line.getConditionNotes());
            }
        }

        batch.setStatus(EquipmentAssignmentBatchStatus.COMPLETED);
        EquipmentAssignmentBatch saved = equipmentAssignmentBatchRepository.save(batch);
        return equipmentAssignmentBatchMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EquipmentAssignmentBatchResponse getById(Long batchId) {
        EquipmentAssignmentBatch batch = equipmentAssignmentBatchRepository.findById(batchId)
                .orElseThrow(() -> new EquipmentAssignmentBatchNotFoundException(batchId));
        return equipmentAssignmentBatchMapper.toResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<EquipmentAssignmentBatchResponse> findAll(EquipmentAssignmentBatchStatus statusFilter) {
        List<EquipmentAssignmentBatch> batches = statusFilter != null
                ? equipmentAssignmentBatchRepository.findByStatus(statusFilter)
                : equipmentAssignmentBatchRepository.findAll();
        return batches.stream().map(equipmentAssignmentBatchMapper::toResponse).toList();
    }
}
