package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.ItemSupplier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemSupplierRepository extends JpaRepository<ItemSupplier, Long> {

    /** Read-only lookup. Code that changes the link uses {@link #lockOrCreate} instead. */
    Optional<ItemSupplier> findByItemIdAndSupplierId(Long itemId, Long supplierId);

    /**
     * Fetches all supplier links for an item, eagerly joining both item and
     * supplier so ItemSupplierMapper.toResponse's flattened itemName/
     * supplierName fields don't trigger a lazy-load per row.
     */
    @Query("SELECT isup FROM ItemSupplier isup JOIN FETCH isup.item JOIN FETCH isup.supplier WHERE isup.item.id = :itemId")
    List<ItemSupplier> findByItemIdWithSupplier(@Param("itemId") Long itemId);

    /**
     * The item-supplier link, locked for the rest of the transaction, created
     * first (with no SKU or cost yet) if it doesn't exist. For every write to
     * a link: creation is atomic, so two concurrent first links of the same
     * pair can't both insert (the second used to fail on
     * uq_item_supplier_item_supplier with a 500); and the lock stops a
     * concurrent write from being overwritten, since Hibernate's UPDATE
     * rewrites every column — a receipt setting only unit_cost would
     * otherwise write back a stale supplier_sku. Same find-or-create pattern
     * as InventoryStockRepository (see CLAUDE.md).
     */
    default ItemSupplier lockOrCreate(Long itemId, Long supplierId) {
        return findForUpdate(itemId, supplierId).orElseGet(() -> {
            insertIfAbsent(itemId, supplierId);
            return findForUpdate(itemId, supplierId).orElseThrow();
        });
    }

    /** Locked lookup (SELECT ... FOR UPDATE); no fetch joins, which Postgres can't lock through. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT isup FROM ItemSupplier isup WHERE isup.item.id = :itemId AND isup.supplier.id = :supplierId")
    Optional<ItemSupplier> findForUpdate(@Param("itemId") Long itemId, @Param("supplierId") Long supplierId);

    /**
     * Creates the link unless it already exists, atomically: a concurrent
     * second insert waits for the first to commit, then does nothing.
     * Returns 1 if a row was inserted, 0 otherwise.
     */
    @Modifying
    @Query(value = """
            INSERT INTO item_supplier (item_id, supplier_id)
            VALUES (:itemId, :supplierId)
            ON CONFLICT ON CONSTRAINT uq_item_supplier_item_supplier DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("itemId") Long itemId, @Param("supplierId") Long supplierId);
}
