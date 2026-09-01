package com.bcconstructionservices.inventory.dto;

/**
 * Where a suggested purchase-order line item came from — a suggestion can
 * carry more than one, if the same item independently qualifies through
 * multiple sources (their suggested quantities are summed, not deduplicated;
 * see PurchaseOrderService.getSuggestions's javadoc for why).
 */
public enum PurchaseOrderSuggestionSource {
    /**
     * Item is on a line of some TransferBatch currently AWAITING_PURCHASE,
     * and re-checking current stock at that batch's origin warehouse still
     * shows a shortfall.
     */
    AWAITING_PURCHASE_TRANSFER,
    /** Item is at or below its reorder threshold somewhere (GET /api/inventory/low-stock). */
    LOW_STOCK,
    /** Item is on a line of an open (SUBMITTED/PARTIALLY_FULFILLED) MaterialRequest, not yet fully dispatched. */
    OPEN_MATERIAL_REQUEST
}
