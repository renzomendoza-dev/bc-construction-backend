package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialRequestRepository extends JpaRepository<MaterialRequest, Long> {

    /** Used by PurchaseOrderService.getSuggestions to find still-open requests. */
    List<MaterialRequest> findByStatusIn(List<MaterialRequestStatus> statuses);

    /**
     * Fetches a single request with its site warehouse eagerly loaded. Line
     * items are intentionally not join-fetched here, same reasoning as
     * TransferBatchRepository.findByIdWithWarehouses.
     */
    @Query("""
            SELECT mr FROM MaterialRequest mr
            JOIN FETCH mr.site
            WHERE mr.id = :id
            """)
    Optional<MaterialRequest> findByIdWithSite(@Param("id") Long id);

    /**
     * Filters requests by optional site warehouse and status.
     */
    @Query("""
            SELECT mr FROM MaterialRequest mr
            JOIN FETCH mr.site
            WHERE (:siteWarehouseId IS NULL OR mr.site.id = :siteWarehouseId)
              AND (:status IS NULL OR mr.status = :status)
            """)
    Page<MaterialRequest> search(@Param("siteWarehouseId") Long siteWarehouseId,
                                  @Param("status") MaterialRequestStatus status,
                                  Pageable pageable);
}
