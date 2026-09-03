package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.MaterialRequestNotDeletableException;
import com.bcconstructionservices.inventory.exception.MaterialRequestNotEditableException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.MaterialRequestMapper;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for a site's requests for materials to be pulled from a MAIN
 * warehouse. There is deliberately no fulfill() method here — fulfillment
 * happens by creating a {@link com.bcconstructionservices.inventory.entity.TransferBatch}
 * with sourceMaterialRequestId set and submitting it via
 * {@link TransferBatchService#submit}, which is also what transitions this
 * request's status to PARTIALLY_FULFILLED/FULFILLED.
 */
@Service
@RequiredArgsConstructor
public class MaterialRequestService {

    private final MaterialRequestRepository materialRequestRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final MaterialRequestMapper materialRequestMapper;
    private final CurrentUserService currentUserService;

    @Transactional
    public MaterialRequestResponse create(MaterialRequestCreateRequest request) {
        Warehouse site = warehouseRepository.findById(request.getSiteWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getSiteWarehouseId()));

        if (site.getType() != WarehouseType.SITE) {
            throw new InvalidStockOperationException(
                    "Material requests can only be created against a SITE warehouse (warehouseId: "
                            + site.getId() + " is type " + site.getType() + ")");
        }

        // materialRequestMapper.toEntity only covers dateNeeded/notes — site,
        // requestedBy, status, and lineItems are all ignore=true by design (see
        // MaterialRequestMapper's javadoc), so they're assembled here.
        MaterialRequest materialRequest = materialRequestMapper.toEntity(request);
        materialRequest.setSite(site);
        materialRequest.setRequestedBy(currentUserService.getCurrentUserId());
        materialRequest.setStatus(MaterialRequestStatus.SUBMITTED);

        List<MaterialRequestLineItem> lineItems = new ArrayList<>();
        for (MaterialRequestLineItemRequest lineRequest : request.getLines()) {
            Item item = itemRepository.findById(lineRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lineRequest.getItemId()));

            lineItems.add(MaterialRequestLineItem.builder()
                    .materialRequest(materialRequest)
                    .item(item)
                    .quantityRequested(lineRequest.getQuantityRequested())
                    .notes(lineRequest.getNotes())
                    .build());
        }
        materialRequest.setLineItems(lineItems);

        MaterialRequest saved = materialRequestRepository.save(materialRequest);
        return materialRequestMapper.toResponse(saved);
    }

    /**
     * Replaces dateNeeded, notes, and line items on an existing request.
     * Full replacement, not a partial patch: dateNeeded/notes are copied as
     * given (including null, clearing the field), and lineItems is entirely
     * cleared and rebuilt from the request — orphanRemoval on
     * MaterialRequest.lineItems handles deleting whatever isn't re-added.
     * <p>
     * Rejected with {@link MaterialRequestNotEditableException} once status
     * is PARTIALLY_FULFILLED or FULFILLED — i.e. once a submitted
     * TransferBatch has already moved real stock against this request. A
     * DRAFT batch that merely references this request does not lock it,
     * since nothing has actually happened to stock yet.
     */
    @Transactional
    public MaterialRequestResponse update(Long materialRequestId, MaterialRequestUpdateRequest request) {
        MaterialRequest materialRequest = materialRequestRepository.findByIdWithSite(materialRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialRequest", materialRequestId));

        if (materialRequest.getStatus() == MaterialRequestStatus.PARTIALLY_FULFILLED
                || materialRequest.getStatus() == MaterialRequestStatus.FULFILLED) {
            throw new MaterialRequestNotEditableException(materialRequestId, materialRequest.getStatus());
        }

        // materialRequestMapper.updateEntityFromRequest only covers dateNeeded/
        // notes — site/requestedBy/status never change here, and lineItems is
        // rebuilt by hand below (needs per-line Item resolution).
        materialRequestMapper.updateEntityFromRequest(request, materialRequest);

        materialRequest.getLineItems().clear();
        for (MaterialRequestLineItemRequest lineRequest : request.getLines()) {
            Item item = itemRepository.findById(lineRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lineRequest.getItemId()));

            materialRequest.getLineItems().add(MaterialRequestLineItem.builder()
                    .materialRequest(materialRequest)
                    .item(item)
                    .quantityRequested(lineRequest.getQuantityRequested())
                    .notes(lineRequest.getNotes())
                    .build());
        }

        MaterialRequest saved = materialRequestRepository.save(materialRequest);
        return materialRequestMapper.toResponse(saved);
    }

    /**
     * Same lock condition as {@link #update}: rejected once status is
     * PARTIALLY_FULFILLED or FULFILLED, since a submitted TransferBatch has
     * already moved real stock against the request by then. A SUBMITTED
     * request — its actual initial persisted state, since create() never
     * leaves one at DRAFT — can still be deleted, same as it can still be
     * edited. sourceMaterialRequestId on TransferBatch is a plain column,
     * not a real FK (see TransferBatch's javadoc), so deleting the request
     * never touches a draft batch that references it. Line items
     * cascade-delete via MaterialRequest.lineItems' orphanRemoval.
     */
    @Transactional
    public void delete(Long materialRequestId) {
        MaterialRequest materialRequest = materialRequestRepository.findById(materialRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialRequest", materialRequestId));

        if (materialRequest.getStatus() == MaterialRequestStatus.PARTIALLY_FULFILLED
                || materialRequest.getStatus() == MaterialRequestStatus.FULFILLED) {
            throw new MaterialRequestNotDeletableException(materialRequestId, materialRequest.getStatus());
        }

        materialRequestRepository.delete(materialRequest);
    }

    @Transactional(readOnly = true)
    public MaterialRequestResponse getById(Long materialRequestId) {
        MaterialRequest materialRequest = materialRequestRepository.findByIdWithSite(materialRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialRequest", materialRequestId));
        return materialRequestMapper.toResponse(materialRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialRequestResponse> search(Long siteWarehouseId, MaterialRequestStatus status,
                                                          Pageable pageable) {
        Page<MaterialRequest> page = materialRequestRepository.search(siteWarehouseId, status, pageable);
        return PageResponse.of(page, materialRequestMapper::toResponse);
    }
}
