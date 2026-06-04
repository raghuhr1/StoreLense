package com.storelense.erp.adapter;

import com.storelense.erp.dto.*;

/**
 * Port (interface) for all outbound calls to the ERP system.
 * Implementations swap the underlying protocol (REST, EDI, file-based)
 * without changing any service logic.
 */
public interface ErpClient {

    /**
     * Fetch a page of the product catalogue from ERP.
     * @param cursor pagination cursor from the previous response, or null for first page
     * @param pageSize number of records per page
     */
    ErpProductPage fetchProducts(String cursor, int pageSize);

    /**
     * Fetch expected inventory quantities for all products at a given ERP store.
     * @param erpStoreCode store identifier in ERP's namespace
     * @param cursor       pagination cursor
     */
    ErpInventoryPage fetchExpectedInventory(String erpStoreCode, String cursor, int pageSize);

    /**
     * Push an SOH count result back to ERP.
     * @return true if ERP acknowledged successfully
     */
    boolean pushSohResult(ErpSohPushRequest request);

    /**
     * Health-check: returns true if the ERP API is reachable.
     */
    boolean isReachable();
}
