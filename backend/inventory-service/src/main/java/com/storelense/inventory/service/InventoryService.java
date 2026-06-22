package com.storelense.inventory.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.SohUpdatedEvent;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.inventory.domain.entity.EpcRegistry;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.domain.repository.EpcRegistryRepository;
import com.storelense.inventory.domain.repository.InventoryStateRepository;
import com.storelense.inventory.dto.EpcLedgerRow;
import com.storelense.inventory.dto.EpcLocationResponse;
import com.storelense.inventory.dto.EpcsByEanResponse;
import com.storelense.inventory.dto.SkuInventoryResponse;
import com.storelense.inventory.dto.SkuLedgerRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryStateRepository inventoryStateRepository;
    private final EpcRegistryRepository    epcRegistryRepository;
    private final JdbcClient              jdbcClient;

    @KafkaListener(topics = KafkaTopics.RFID_SOH_UPDATED, groupId = "inventory-service")
    @Transactional
    public void onSohUpdated(SohUpdatedEvent event) {
        if (event.productId() == null) return;

        OffsetDateTime seenAt = OffsetDateTime.ofInstant(event.processedAt(), ZoneOffset.UTC);

        // Upsert EPC registry — marks the EPC as seen at this store+zone
        epcRegistryRepository.findByEpcAndStoreId(event.epc(), event.storeId())
                .ifPresentOrElse(
                        existing -> epcRegistryRepository.updateSighting(
                                event.epc(), event.storeId(), "in_store", seenAt, event.zoneId(), null),
                        () -> epcRegistryRepository.save(EpcRegistry.builder()
                                .epc(event.epc())
                                .storeId(event.storeId())
                                .productId(event.productId())
                                .zoneId(event.zoneId())
                                .status("in_store")
                                .lastSeenAt(seenAt)
                                .build())
                );

        // Increment on-hand count in inventory_state
        inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(
                event.storeId(), event.productId(), event.zoneId())
                .ifPresent(state -> {
                    state.setQuantityOnHand(state.getQuantityOnHand() + 1);
                    state.setLastCountedAt(seenAt);
                    state.setLastSohSessionId(event.sohSessionId());
                    inventoryStateRepository.save(state);
                });
    }

    @Transactional(readOnly = true)
    public List<InventoryState> getStoreInventory(UUID storeId) {
        return inventoryStateRepository.findByStoreId(storeId);
    }

    @Transactional(readOnly = true)
    public List<InventoryState> getLowAccuracyItems(UUID storeId, double threshold) {
        return inventoryStateRepository.findLowAccuracy(storeId, threshold);
    }

    @Transactional(readOnly = true)
    public long countByStatus(UUID storeId, String status) {
        return epcRegistryRepository.countByStoreIdAndStatus(storeId, status);
    }

    /**
     * Upsert the ERP expected quantity for a store × product × zone.
     * Called by the XLS upload tool (POST /api/inventory/expected).
     * Preserves quantity_on_hand from previous RFID scans.
     *
     * @SuppressWarnings: JpaRepository.save() is contractually @NonNull;
     * Eclipse null analysis cannot verify this through generic type parameters.
     */
    @SuppressWarnings("null")
    @Transactional
    public InventoryState upsertExpectedQty(UUID storeId, UUID productId,
                                            UUID zoneId, int quantityExpected) {
        return inventoryStateRepository
                .findByStoreIdAndProductIdAndZoneId(storeId, productId, zoneId)
                .map(existing -> {
                    existing.setQuantityExpected(quantityExpected);
                    existing.setAccuracyPct(calcAccuracy(existing.getQuantityOnHand(), quantityExpected));
                    return inventoryStateRepository.save(existing);
                })
                .orElseGet(() -> inventoryStateRepository.save(
                        InventoryState.builder()
                                .storeId(storeId)
                                .productId(productId)
                                .zoneId(zoneId)
                                .quantityExpected(quantityExpected)
                                .quantityOnHand(0)
                                .build()));
    }

    /**
     * Returns the last-seen zone and timestamp for a single EPC.
     * Joins epc_registry → stores.zones to resolve zone name.
     * Returns empty Optional (not exception) so callers can degrade gracefully.
     */
    @Transactional(readOnly = true)
    public EpcLocationResponse getEpcLocation(String epc, UUID storeId) {
        return jdbcClient.sql("""
                SELECT
                    er.epc,
                    er.store_id,
                    er.last_seen_at,
                    z.name AS zone_name
                FROM inventory.epc_registry er
                LEFT JOIN stores.zones z ON z.id = er.zone_id
                WHERE er.epc      = :epc
                  AND er.store_id = :storeId
                LIMIT 1
                """)
                .param("epc",     epc)
                .param("storeId", storeId)
                .query((rs, rowNum) -> new EpcLocationResponse(
                        rs.getString("epc"),
                        rs.getString("zone_name"),
                        rs.getTimestamp("last_seen_at") != null
                                ? rs.getTimestamp("last_seen_at").toInstant()
                                       .atOffset(ZoneOffset.UTC).toString()
                                : null,
                        rs.getObject("store_id", UUID.class)
                ))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("EPC", epc));
    }

    /**
     * Resolves an EAN barcode to the list of EPCs currently in_store at the given store.
     * Used by the C66 security gate app to match bill line items against RFID bag reads.
     *
     * Lookup path: products.barcodes → products.products → inventory.epc_registry
     */
    @Transactional(readOnly = true)
    public EpcsByEanResponse getEpcsByEan(String ean, UUID storeId) {
        // Fetch sku + name for the product matching this EAN
        record ProductInfo(String sku, String name) {}
        var product = jdbcClient.sql("""
                SELECT p.sku, p.name
                FROM products.products p
                JOIN products.barcodes b ON b.product_id = p.id
                WHERE b.barcode_value = :ean
                  AND b.barcode_type IN ('ean13', 'ean8', 'upc_a')
                  AND p.is_active = true
                LIMIT 1
                """)
                .param("ean", ean)
                .query((rs, rowNum) -> new ProductInfo(rs.getString("sku"), rs.getString("name")))
                .optional();

        if (product.isEmpty()) {
            return new EpcsByEanResponse(ean, null, "Unknown product", List.of());
        }

        // Fetch all in_store EPCs for this product at this store
        List<String> epcs = jdbcClient.sql("""
                SELECT er.epc
                FROM inventory.epc_registry er
                JOIN products.products p ON p.id = er.product_id
                JOIN products.barcodes b ON b.product_id = p.id
                WHERE b.barcode_value = :ean
                  AND b.barcode_type IN ('ean13', 'ean8', 'upc_a')
                  AND er.store_id = :storeId
                  AND er.status = 'in_store'
                """)
                .param("ean", ean)
                .param("storeId", storeId)
                .query(String.class)
                .list();

        return new EpcsByEanResponse(ean, product.get().sku(), product.get().name(), epcs);
    }

    @Transactional(readOnly = true)
    public List<SkuLedgerRow> getSkuLedger(UUID storeId) {
        return jdbcClient.sql("""
                SELECT
                    er.product_id,
                    SUM(CASE WHEN er.status = 'in_store'    THEN 1 ELSE 0 END)::int AS in_store,
                    SUM(CASE WHEN er.status = 'sold'        THEN 1 ELSE 0 END)::int AS sold,
                    SUM(CASE WHEN er.status = 'missing'     THEN 1 ELSE 0 END)::int AS missing,
                    SUM(CASE WHEN er.status = 'damaged'     THEN 1 ELSE 0 END)::int AS damaged,
                    SUM(CASE WHEN er.status = 'transferred' THEN 1 ELSE 0 END)::int AS transferred,
                    COUNT(*)::int AS total,
                    MAX(er.last_seen_at) AS last_seen_at
                FROM inventory.epc_registry er
                WHERE er.store_id = :storeId
                GROUP BY er.product_id
                ORDER BY in_store DESC
                """)
                .param("storeId", storeId)
                .query((rs, rowNum) -> new SkuLedgerRow(
                        rs.getObject("product_id", UUID.class),
                        rs.getInt("in_store"),
                        rs.getInt("sold"),
                        rs.getInt("missing"),
                        rs.getInt("damaged"),
                        rs.getInt("transferred"),
                        rs.getInt("total"),
                        rs.getTimestamp("last_seen_at") != null
                                ? rs.getTimestamp("last_seen_at").toInstant()
                                       .atOffset(ZoneOffset.UTC).toString()
                                : null
                ))
                .list();
    }

    @Transactional(readOnly = true)
    public SkuInventoryResponse getSkuInventory(String sku, UUID storeId) {
        UUID productId = jdbcClient
                .sql("SELECT id FROM products.products WHERE sku = :sku AND active = true")
                .param("sku", sku)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));

        List<InventoryState> states = inventoryStateRepository.findByStoreIdAndProductId(storeId, productId);
        int total = states.stream().mapToInt(InventoryState::getQuantityOnHand).sum();

        List<String> epcs = epcRegistryRepository
                .findByStoreIdAndProductIdAndStatus(storeId, productId, "in_store")
                .stream()
                .map(EpcRegistry::getEpc)
                .toList();

        return new SkuInventoryResponse(sku, productId, storeId, total, 0, total, epcs);
    }

    /**
     * Marks the given EPCs as 'sold' in the EPC registry (called by C66 security gate app).
     * Also decrements quantity_on_hand in inventory_state for each matched EPC.
     *
     * @return number of EPCs successfully marked as sold
     */
    @Transactional
    public int markEpcsSold(UUID storeId, List<String> epcs) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Snapshot the product+zone for each EPC before marking sold so we can decrement inventory_state
        List<EpcRegistry> toSell = epcRegistryRepository.findByEpcInAndStoreId(epcs, storeId)
                .stream()
                .filter(r -> "in_store".equals(r.getStatus()))
                .toList();

        int marked = epcRegistryRepository.markSold(epcs, storeId, now);

        // Decrement on-hand count per product+zone
        toSell.forEach(reg -> inventoryStateRepository
                .findByStoreIdAndProductIdAndZoneId(storeId, reg.getProductId(), reg.getZoneId())
                .ifPresent(state -> {
                    state.setQuantityOnHand(Math.max(0, state.getQuantityOnHand() - 1));
                    state.setAccuracyPct(calcAccuracy(state.getQuantityOnHand(), state.getQuantityExpected()));
                    inventoryStateRepository.save(state);
                }));

        log.info("Marked {} / {} EPCs as sold at store {}", marked, epcs.size(), storeId);
        return marked;
    }

    @Transactional(readOnly = true)
    public PageResponse<EpcLedgerRow> getEpcLedger(UUID storeId, String status, Pageable pageable) {
        String statusParam = (status != null && !status.isBlank()) ? status : "";

        long total = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM inventory.epc_registry er
                WHERE er.store_id = :storeId
                  AND (:status = '' OR er.status = :status)
                """)
                .param("storeId", storeId)
                .param("status",  statusParam)
                .query(Long.class)
                .single();

        List<EpcLedgerRow> rows = jdbcClient.sql("""
                SELECT
                    er.epc,
                    er.product_id,
                    er.status,
                    er.last_seen_at,
                    er.first_seen_at,
                    p.sku,
                    p.name      AS product_name,
                    z.name      AS zone_name
                FROM inventory.epc_registry er
                LEFT JOIN products.products p ON p.id = er.product_id
                LEFT JOIN stores.zones z      ON z.id = er.zone_id
                WHERE er.store_id = :storeId
                  AND (:status = '' OR er.status = :status)
                ORDER BY er.last_seen_at DESC NULLS LAST
                LIMIT :size OFFSET :offset
                """)
                .param("storeId", storeId)
                .param("status",  statusParam)
                .param("size",    pageable.getPageSize())
                .param("offset",  pageable.getOffset())
                .query((rs, n) -> new EpcLedgerRow(
                        rs.getString("epc"),
                        rs.getObject("product_id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getString("zone_name"),
                        rs.getString("status"),
                        rs.getTimestamp("last_seen_at")  != null
                                ? rs.getTimestamp("last_seen_at").toInstant().atOffset(ZoneOffset.UTC).toString()
                                : null,
                        rs.getTimestamp("first_seen_at") != null
                                ? rs.getTimestamp("first_seen_at").toInstant().atOffset(ZoneOffset.UTC).toString()
                                : null
                ))
                .list();

        int pageNum   = pageable.getPageNumber();
        int pageSize  = pageable.getPageSize();
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 1;
        return new PageResponse<>(rows, pageNum, pageSize, total, totalPages, (pageNum + 1) >= totalPages);
    }

    private java.math.BigDecimal calcAccuracy(int onHand, int expected) {
        if (expected == 0) return java.math.BigDecimal.valueOf(100);
        return java.math.BigDecimal.valueOf(100.0 * onHand / expected)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
