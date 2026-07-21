package com.storelense.inventory.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.EpcSoldEvent;
import com.storelense.common.event.SohUpdatedEvent;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.inventory.domain.entity.EpcPositionHistory;
import com.storelense.inventory.domain.entity.EpcRegistry;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.domain.repository.EpcPositionHistoryRepository;
import com.storelense.inventory.domain.repository.EpcRegistryRepository;
import com.storelense.inventory.domain.repository.InventoryStateRepository;
import com.storelense.inventory.dto.CommissionRequest;
import com.storelense.inventory.dto.CommissionResponse;
import com.storelense.inventory.dto.EpcLedgerRow;
import com.storelense.inventory.dto.IdentifyEpcResponse;
import com.storelense.inventory.dto.EpcLocationResponse;
import com.storelense.inventory.dto.EpcsByEanResponse;
import com.storelense.inventory.dto.SkuInventoryResponse;
import com.storelense.inventory.dto.SkuLedgerRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryStateRepository    inventoryStateRepository;
    private final EpcRegistryRepository       epcRegistryRepository;
    private final EpcPositionHistoryRepository epcPositionHistoryRepository;
    private final JdbcClient                  jdbcClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @SuppressWarnings("null")
    @KafkaListener(topics = KafkaTopics.RFID_SOH_UPDATED, groupId = "inventory-service")
    @Transactional
    public void onSohUpdated(SohUpdatedEvent event) {
        if (event.productId() == null) return;

        OffsetDateTime seenAt = OffsetDateTime.ofInstant(event.processedAt(), ZoneOffset.UTC);

        // Upsert EPC registry and write position history when zone or status changes.
        // isNewPhysicalUnit distinguishes a genuinely new tag (increment on-hand once)
        // from a re-scan of an already-known tag (refresh last-seen only — re-scanning
        // the same physical item in a later session must never inflate on-hand again).
        var existingRegistry = epcRegistryRepository.findByEpcAndStoreId(event.epc(), event.storeId());
        boolean isNewPhysicalUnit = existingRegistry.isEmpty();

        if (existingRegistry.isPresent()) {
            EpcRegistry existing = existingRegistry.get();
            boolean zoneChanged = !Objects.equals(existing.getZoneId(), event.zoneId());
            if (zoneChanged) {
                epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                        .epc(event.epc())
                        .storeId(event.storeId())
                        .productId(event.productId())
                        .fromZoneId(existing.getZoneId())
                        .toZoneId(event.zoneId())
                        .fromStatus(existing.getStatus())
                        .toStatus("in_store")
                        .triggeredBy("scan_session")
                        .sessionId(event.sohSessionId())
                        .build());
            }
            epcRegistryRepository.updateSighting(
                    event.epc(), event.storeId(), "in_store", seenAt, event.zoneId(), null);
        } else {
            epcRegistryRepository.save(EpcRegistry.builder()
                    .epc(event.epc())
                    .storeId(event.storeId())
                    .productId(event.productId())
                    .zoneId(event.zoneId())
                    .status("in_store")
                    .lastSeenAt(seenAt)
                    .build());
            // First sighting — record the initial position.
            epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                    .epc(event.epc())
                    .storeId(event.storeId())
                    .productId(event.productId())
                    .fromZoneId(null)
                    .toZoneId(event.zoneId())
                    .fromStatus(null)
                    .toStatus("in_store")
                    .triggeredBy("scan_session")
                    .sessionId(event.sohSessionId())
                    .build());
        }

        if (!isNewPhysicalUnit) {
            // Already-known tag re-scanned (this session or a later one) — nothing new
            // physically found, so on-hand must not be incremented again.
            return;
        }

        // On-hand is recomputed live from epc_registry (the source of truth) rather than
        // blindly incremented — this makes it self-correcting: no matter how many times or
        // in how many sessions a product's tags get scanned, on-hand always reflects exactly
        // how many of its tags are currently in_store, and can never drift out of sync.
        long liveOnHand = epcRegistryRepository.countLiveOnHand(
                event.storeId(), event.productId(), event.zoneId(), "in_store");

        inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(
                event.storeId(), event.productId(), event.zoneId())
                .map(state -> {
                    state.setQuantityOnHand((int) liveOnHand);
                    state.setLastCountedAt(seenAt);
                    state.setLastSohSessionId(event.sohSessionId());
                    state.setAccuracyPct(calcAccuracy(state.getQuantityOnHand(), state.getQuantityExpected()));
                    return inventoryStateRepository.save(state);
                })
                .orElseGet(() -> inventoryStateRepository.save(
                        InventoryState.builder()
                                .storeId(event.storeId())
                                .productId(event.productId())
                                .zoneId(event.zoneId())
                                .quantityOnHand((int) liveOnHand)
                                .quantityExpected(0)
                                .accuracyPct(calcAccuracy((int) liveOnHand, 0))
                                .lastCountedAt(seenAt)
                                .lastSohSessionId(event.sohSessionId())
                                .build()));
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

    // See InventoryStateRepository.sumUnscannedExpected — "missing" as shown on the
    // Stock Levels page means ERP-expected units never physically scanned, not the
    // epc_registry 'missing' status flag (which requires an explicit manual "mark
    // missing" action and stays 0 for the vast majority of real shortfall).
    @Transactional(readOnly = true)
    public long countUnscannedExpected(UUID storeId) {
        return inventoryStateRepository.sumUnscannedExpected(storeId);
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
                                .accuracyPct(calcAccuracy(0, quantityExpected))
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
                WHERE UPPER(b.barcode_value) = UPPER(:ean)
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
                WHERE UPPER(b.barcode_value) = UPPER(:ean)
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
                .sql("SELECT id FROM products.products WHERE sku = :sku AND is_active = true")
                .param("sku", sku)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));

        // Quantities split by zone type — ERP imports have zone_id=NULL, treated as floor
        int onFloor = Objects.requireNonNullElse(jdbcClient.sql("""
                SELECT COALESCE(SUM(s.quantity_on_hand), 0)::int
                FROM inventory.inventory_state s
                LEFT JOIN stores.zones z ON z.id = s.zone_id
                WHERE s.store_id = CAST(:storeId AS uuid)
                  AND s.product_id = CAST(:productId AS uuid)
                  AND COALESCE(z.zone_type, 'floor') IN ('floor','display','fitting_room','entrance')
                """)
                .param("storeId", storeId.toString())
                .param("productId", productId.toString())
                .query(Integer.class).single(), 0);

        int inBackroom = Objects.requireNonNullElse(jdbcClient.sql("""
                SELECT COALESCE(SUM(s.quantity_on_hand), 0)::int
                FROM inventory.inventory_state s
                JOIN stores.zones z ON z.id = s.zone_id
                WHERE s.store_id = CAST(:storeId AS uuid)
                  AND s.product_id = CAST(:productId AS uuid)
                  AND z.zone_type IN ('backroom','stockroom')
                """)
                .param("storeId", storeId.toString())
                .param("productId", productId.toString())
                .query(Integer.class).single(), 0);

        int total = onFloor + inBackroom;

        // Use epc_tags (all registered RFID tags for this product) so the Geiger locator
        // can find items even if they haven't been scanned in this store yet
        List<String> epcs = jdbcClient.sql("""
                SELECT epc FROM products.epc_tags
                WHERE product_id = CAST(:productId AS uuid) AND is_active = true
                """)
                .param("productId", productId.toString())
                .query(String.class)
                .list();

        return new SkuInventoryResponse(sku, productId, storeId, onFloor, inBackroom, total, epcs);
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

        // Notify refill-service so it can re-check Sales Floor par live, without waiting
        // for the next completed SOH session.
        toSell.stream()
                .collect(Collectors.groupingBy(EpcRegistry::getProductId, Collectors.counting()))
                .forEach((productId, qty) ->
                        kafkaTemplate.send(KafkaTopics.INVENTORY_EPC_SOLD, productId.toString(),
                                new EpcSoldEvent(storeId, productId, qty.intValue())));

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

    /**
     * Returns all EPCs currently with status='inbound' at the store,
     * joined with product info. Used by the web put-away screen.
     */
    @Transactional(readOnly = true)
    public List<com.storelense.inventory.dto.InboundEpcRow> getInboundPendingEpcs(UUID storeId) {
        return jdbcClient.sql("""
                SELECT
                    er.epc,
                    er.product_id,
                    p.sku,
                    p.name      AS product_name,
                    er.first_seen_at,
                    er.last_seen_at
                FROM inventory.epc_registry er
                LEFT JOIN products.products p ON p.id = er.product_id
                WHERE er.store_id = :storeId
                  AND er.status   = 'inbound'
                ORDER BY er.last_seen_at DESC NULLS LAST
                """)
                .param("storeId", storeId)
                .query((rs, n) -> new com.storelense.inventory.dto.InboundEpcRow(
                        rs.getString("epc"),
                        rs.getObject("product_id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getTimestamp("first_seen_at") != null
                                ? rs.getTimestamp("first_seen_at").toInstant().atOffset(ZoneOffset.UTC).toString()
                                : null,
                        rs.getTimestamp("last_seen_at") != null
                                ? rs.getTimestamp("last_seen_at").toInstant().atOffset(ZoneOffset.UTC).toString()
                                : null
                ))
                .list();
    }

    /**
     * Transitions a list of EPCs from 'inbound' to 'in_store' in the given zone.
     * Writes position history for each moved EPC. EPCs not found or not in 'inbound'
     * status are skipped (counted in skippedCount).
     */
    @SuppressWarnings("null")
    @Transactional
    public com.storelense.inventory.dto.PutawayResponse putawayEpcs(
            UUID storeId, UUID zoneId, List<String> epcs) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int moved   = 0;
        int skipped = 0;

        for (String epc : epcs) {
            var opt = epcRegistryRepository.findByEpcAndStoreId(epc, storeId);
            if (opt.isEmpty() || !"inbound".equals(opt.get().getStatus())) {
                skipped++;
                continue;
            }
            EpcRegistry reg = opt.get();
            epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                    .epc(epc)
                    .storeId(storeId)
                    .productId(reg.getProductId())
                    .fromZoneId(null)
                    .toZoneId(zoneId)
                    .fromStatus("inbound")
                    .toStatus("in_store")
                    .triggeredBy("receiving")
                    .build());
            reg.setStatus("in_store");
            reg.setZoneId(zoneId);
            reg.setLastSeenAt(now);
            epcRegistryRepository.save(reg);

            // Reflect put-away in inventory_state quantity — recomputed live from
            // epc_registry rather than incremented, so it can't drift.
            long liveOnHand = epcRegistryRepository.countLiveOnHand(
                    storeId, reg.getProductId(), zoneId, "in_store");
            inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(storeId, reg.getProductId(), zoneId)
                    .map(state -> {
                        state.setQuantityOnHand((int) liveOnHand);
                        state.setLastCountedAt(now);
                        return inventoryStateRepository.save(state);
                    })
                    .orElseGet(() -> inventoryStateRepository.save(
                            InventoryState.builder()
                                    .storeId(storeId)
                                    .productId(reg.getProductId())
                                    .zoneId(zoneId)
                                    .quantityOnHand((int) liveOnHand)
                                    .quantityExpected(0)
                                    .lastCountedAt(now)
                                    .build()));
            moved++;
        }

        return new com.storelense.inventory.dto.PutawayResponse(
                moved, skipped,
                "Put away " + moved + " EPC(s) to zone " + zoneId);
    }

    /**
     * Tag Items: registers an EPC→product mapping on the spot when no T5 file was received.
     * Inserts into products.epc_tags (global tag registry) and inventory.epc_registry (store-level).
     * Increments quantity_on_hand for the chosen zone in inventory_state.
     * All three inserts are idempotent — rescanning the same EPC is safe.
     */
    @SuppressWarnings("null")
    @Transactional
    public CommissionResponse commissionTagItem(CommissionRequest req) {
        record ProductInfo(UUID id, String name) {}
        var product = jdbcClient.sql("""
                SELECT id, name FROM products.products WHERE sku = :sku AND is_active = true LIMIT 1
                """)
                .param("sku", req.sku())
                .query((rs, n) -> new ProductInfo(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Product", req.sku()));

        // Tag Item only lets staff commission a physical unit of a SKU that this store's
        // latest completed ERP import actually expects — otherwise any SKU in the global
        // catalog could be tagged into a store it was never stocked at, with no ERP
        // quantity to reconcile against.
        boolean stockedAtStore = jdbcClient.sql("""
                SELECT 1
                FROM erp.erp_soh_snapshot s
                JOIN erp.erp_import_batch b ON b.id = s.batch_id
                JOIN products.barcodes bc   ON bc.barcode_value = s.ean
                WHERE b.store_id = CAST(:storeId AS uuid)
                  AND b.status   = 'COMPLETED'
                  AND bc.product_id = CAST(:productId AS uuid)
                  AND b.id = (
                      SELECT id FROM erp.erp_import_batch
                      WHERE store_id = CAST(:storeId AS uuid) AND status = 'COMPLETED'
                      ORDER BY created_at DESC LIMIT 1
                  )
                LIMIT 1
                """)
                .param("storeId", req.storeId().toString())
                .param("productId", product.id().toString())
                .query(Integer.class)
                .optional()
                .isPresent();

        if (!stockedAtStore) {
            throw new IllegalStateException(
                    "Product '" + req.sku() + "' is not in this store's latest ERP import — " +
                    "cannot tag an item for a SKU this store doesn't stock.");
        }

        UUID zoneId = jdbcClient.sql("""
                SELECT id FROM stores.zones
                WHERE store_id = CAST(:storeId AS uuid)
                  AND zone_code = :zoneCode
                  AND is_active = true
                LIMIT 1
                """)
                .param("storeId", req.storeId().toString())
                .param("zoneCode", req.zone())
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Zone", req.zone()));

        String epc = req.epc().toUpperCase();
        String replacesEpc = req.replacesEpc() != null ? req.replacesEpc().toUpperCase() : null;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Commissioning is additive by default — each call tags one more physical unit
        // of this SKU, and should NOT disturb any other EPC already tagged for it (a
        // product with 100 physical units ends up with 100 in_store EPCs). A tag is only
        // ever retired when the associate explicitly names it via replacesEpc (e.g. the
        // old sticker fell off / was damaged) — never inferred from "some other EPC
        // happens to already be in_store for this product."
        boolean replacementDetected = false;
        if (replacesEpc != null) {
            int replacedEpcCount = jdbcClient.sql("""
                    UPDATE inventory.epc_registry
                    SET status = 'replaced', updated_at = :now
                    WHERE store_id   = CAST(:storeId AS uuid)
                      AND product_id = CAST(:productId AS uuid)
                      AND epc        = :replacesEpc
                      AND status     = 'in_store'
                    """)
                    .param("storeId", req.storeId().toString())
                    .param("productId", product.id().toString())
                    .param("replacesEpc", replacesEpc)
                    .param("now", now)
                    .update();
            replacementDetected = replacedEpcCount > 0;

            if (replacementDetected) {
                jdbcClient.sql("""
                        UPDATE products.epc_tags
                        SET is_active = false, updated_at = :now
                        WHERE product_id = CAST(:productId AS uuid)
                          AND epc        = :replacesEpc
                        """)
                        .param("productId", product.id().toString())
                        .param("replacesEpc", replacesEpc)
                        .param("now", now)
                        .update();
            }
        }
        // Re-tagging an already-registered EPC (e.g. moving it from Sales Floor to Back
        // Room via Tag Item) must UPDATE that same physical item's row, not silently
        // no-op — DO NOTHING here left the registry showing the item's old zone forever
        // while the UI reported success, so it looked like a duplicate/ghost move.
        var existingRegistry = epcRegistryRepository.findByEpcAndStoreId(epc, req.storeId());
        UUID previousZoneId = null;
        UUID previousProductId = null;
        if (existingRegistry.isPresent()) {
            EpcRegistry existing = existingRegistry.get();
            boolean zoneChanged = !Objects.equals(existing.getZoneId(), zoneId);
            if (zoneChanged) {
                previousZoneId    = existing.getZoneId();
                previousProductId = existing.getProductId();
                epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                        .epc(epc)
                        .storeId(req.storeId())
                        .productId(product.id())
                        .fromZoneId(existing.getZoneId())
                        .toZoneId(zoneId)
                        .fromStatus(existing.getStatus())
                        .toStatus("in_store")
                        .triggeredBy("tag_item")
                        .build());
            }
        }

        jdbcClient.sql("""
                INSERT INTO products.epc_tags
                       (epc, epc_encoding, product_id, is_encoded, is_active, created_at, updated_at)
                VALUES (:epc, 'SGTIN_96', CAST(:productId AS uuid), false, true, :now, :now)
                ON CONFLICT (epc) DO UPDATE
                    SET product_id = EXCLUDED.product_id,
                        is_active  = true,
                        updated_at = EXCLUDED.updated_at
                """)
                .param("epc", epc)
                .param("productId", product.id().toString())
                .param("now", now)
                .update();

        jdbcClient.sql("""
                INSERT INTO inventory.epc_registry
                       (epc, store_id, product_id, zone_id, status,
                        first_seen_at, last_seen_at, created_at, updated_at)
                VALUES (:epc, CAST(:storeId AS uuid), CAST(:productId AS uuid), CAST(:zoneId AS uuid),
                        'in_store', :now, :now, :now, :now)
                ON CONFLICT (epc, store_id) DO UPDATE
                    SET product_id   = EXCLUDED.product_id,
                        zone_id      = EXCLUDED.zone_id,
                        status       = 'in_store',
                        last_seen_at = EXCLUDED.last_seen_at,
                        updated_at   = EXCLUDED.updated_at
                """)
                .param("epc", epc)
                .param("storeId", req.storeId().toString())
                .param("productId", product.id().toString())
                .param("zoneId", zoneId.toString())
                .param("now", now)
                .update();

        // The zone this item moved OUT of also needs its on-hand refreshed — otherwise
        // it keeps counting a unit that physically left, until something else happens
        // to touch that zone's inventory_state row.
        if (previousZoneId != null && (!previousZoneId.equals(zoneId) || !previousProductId.equals(product.id()))) {
            long previousZoneOnHand = epcRegistryRepository.countLiveOnHand(
                    req.storeId(), previousProductId, previousZoneId, "in_store");
            inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(req.storeId(), previousProductId, previousZoneId)
                    .ifPresent(state -> {
                        state.setQuantityOnHand((int) previousZoneOnHand);
                        state.setAccuracyPct(calcAccuracy(state.getQuantityOnHand(), state.getQuantityExpected()));
                        inventoryStateRepository.save(state);
                    });
        }

        // quantity_on_hand is recomputed live from epc_registry rather than incremented,
        // so it always reflects exactly how many of this product's tags are in_store —
        // it can't drift regardless of replacement/duplicate-submission edge cases.
        long liveOnHand = epcRegistryRepository.countLiveOnHand(
                req.storeId(), product.id(), zoneId, "in_store");
        inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(req.storeId(), product.id(), zoneId)
                .ifPresentOrElse(
                        state -> {
                            state.setQuantityOnHand((int) liveOnHand);
                            state.setAccuracyPct(calcAccuracy(state.getQuantityOnHand(), state.getQuantityExpected()));
                            inventoryStateRepository.save(state);
                        },
                        () -> {
                            int onHand = (int) liveOnHand;
                            inventoryStateRepository.save(
                                    InventoryState.builder()
                                            .storeId(req.storeId())
                                            .productId(product.id())
                                            .zoneId(zoneId)
                                            .quantityOnHand(onHand)
                                            .quantityExpected(0)
                                            .accuracyPct(calcAccuracy(onHand, 0))
                                            .build());
                        });

        int totalTagged = jdbcClient.sql("""
                SELECT COUNT(*)::int FROM inventory.epc_registry
                WHERE store_id    = CAST(:storeId AS uuid)
                  AND product_id  = CAST(:productId AS uuid)
                  AND status NOT IN ('sold', 'damaged', 'transferred', 'replaced')
                """)
                .param("storeId", req.storeId().toString())
                .param("productId", product.id().toString())
                .query(Integer.class)
                .single();

        log.info("Tag Items: EPC {} → SKU {} at store {} zone {}", epc, req.sku(), req.storeId(), req.zone());
        return new CommissionResponse(epc, req.sku(), product.name(), product.id(), req.storeId(), req.zone(), totalTagged);
    }

    /**
     * Identify an EPC: returns the product it is mapped to in epc_tags, plus its current
     * status and zone in the given store. Returns empty if the EPC has never been registered.
     * Used by Tag Items to warn staff before re-assigning an already-tagged item.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<IdentifyEpcResponse> identifyEpc(String epc, UUID storeId) {
        return jdbcClient.sql("""
                SELECT
                    et.epc,
                    p.id         AS product_id,
                    p.sku,
                    p.name       AS product_name,
                    er.status    AS status_in_store,
                    z.name       AS zone_name,
                    COALESCE((
                        SELECT STRING_AGG(b.barcode_value, ',')
                        FROM   products.barcodes b
                        WHERE  b.product_id = p.id
                          AND  b.barcode_type IN ('ean13','ean8','upc_a')
                    ), '') AS eans
                FROM  products.epc_tags et
                JOIN  products.products p  ON p.id = et.product_id
                LEFT JOIN inventory.epc_registry er
                       ON er.epc = et.epc AND er.store_id = CAST(:storeId AS uuid)
                LEFT JOIN stores.zones z ON z.id = er.zone_id
                WHERE et.epc = :epc AND et.is_active = true
                LIMIT 1
                """)
                .param("epc", epc.toUpperCase())
                .param("storeId", storeId.toString())
                .query((rs, n) -> {
                    String eansRaw = rs.getString("eans");
                    java.util.List<String> eans = (eansRaw != null && !eansRaw.isBlank())
                            ? java.util.Arrays.asList(eansRaw.split(","))
                            : java.util.List.of();
                    return new IdentifyEpcResponse(
                            rs.getString("epc"),
                            rs.getObject("product_id", UUID.class),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            eans,
                            rs.getString("status_in_store"),
                            rs.getString("zone_name"),
                            true
                    );
                })
                .optional();
    }

    private java.math.BigDecimal calcAccuracy(int onHand, int expected) {
        // expected==0 with stock on hand is orphan/unexpected inventory, not a perfect match.
        if (expected == 0) return onHand == 0 ? java.math.BigDecimal.valueOf(100) : java.math.BigDecimal.ZERO;
        // Cap at 100 — overstock (onHand > expected) is still a discrepancy, not >100% "accurate".
        double pct = Math.min(100.0, 100.0 * onHand / expected);
        return java.math.BigDecimal.valueOf(pct).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
