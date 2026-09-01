package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLine, Long> {

    /**
     * All purchase lines for a given item across every receipt, ordered by the
     * parent receipt's purchase date descending. The parent receipt and its
     * supplier are join-fetched so purchase-history mapping needs no extra queries.
     */
    @Query("""
            SELECT prl FROM PurchaseReceiptLine prl
            JOIN FETCH prl.purchaseReceipt pr
            JOIN FETCH pr.supplier
            WHERE prl.item.id = :itemId
            ORDER BY pr.purchaseDate DESC
            """)
    List<PurchaseReceiptLine> findByItemIdOrderByReceiptPurchaseDateDesc(@Param("itemId") Long itemId);

    /**
     * Every line from a CONFIRMED receipt against this purchase order — used
     * by PurchaseOrderService to compute each of the order's own lines'
     * receivedQuantity (summed per item) and to decide whether the order is
     * now PARTIALLY_RECEIVED or fully RECEIVED. A draft (unconfirmed) receipt
     * hasn't touched inventory yet, so it doesn't count as "received".
     */
    @Query("""
            SELECT prl FROM PurchaseReceiptLine prl
            JOIN prl.purchaseReceipt pr
            WHERE pr.purchaseOrderId = :purchaseOrderId AND pr.confirmed = true
            """)
    List<PurchaseReceiptLine> findConfirmedByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);
}
