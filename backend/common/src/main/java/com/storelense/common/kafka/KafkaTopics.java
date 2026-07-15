package com.storelense.common.kafka;

/**
 * Canonical Kafka topic names used across all services.
 * Single source of truth — import this class instead of hardcoding strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // ── RFID pipeline ──────────────────────────────────────────────────────
    /** Raw EPC reads from rfid-ingest-service → rfid-processing-service */
    public static final String RFID_READS_RAW     = "rfid.reads.raw";

    /** Decoded, resolved EPC events → inventory-service, soh-service */
    public static final String RFID_SOH_UPDATED   = "rfid.soh.updated";

    /** Reader heartbeat events → store-service */
    public static final String RFID_READER_HEARTBEAT = "rfid.reader.heartbeat";

    // ── SOH pipeline ───────────────────────────────────────────────────────
    /** SOH session completed with result → notification-service, erp-integration */
    public static final String SOH_SESSION_COMPLETED = "soh.session.completed";

    // ── Inventory pipeline ─────────────────────────────────────────────────
    /** EPC(s) marked sold (gate-exit confirmed) → refill-service (live Sales Floor trigger) */
    public static final String INVENTORY_EPC_SOLD    = "inventory.epc.sold";

    // ── Refill pipeline ────────────────────────────────────────────────────
    /** New refill task created → notification-service */
    public static final String REFILL_TASK_CREATED   = "refill.task.created";

    /** Refill task completed → reporting-service */
    public static final String REFILL_TASK_COMPLETED = "refill.task.completed";

    // ── ERP integration ────────────────────────────────────────────────────
    /** Product master data synced from ERP → product-service */
    public static final String ERP_PRODUCT_SYNC      = "erp.product.sync";

    /** Expected inventory quantities from ERP → inventory-service */
    public static final String ERP_INVENTORY_EXPECTED = "erp.inventory.expected";

    /** ERP import batch completed → soh-service (triggers erp_triggered session) */
    public static final String ERP_IMPORT_COMPLETED   = "erp.import.completed";

    /** SOH results pushed outbound to ERP (consumed by erp-integration-service) */
    public static final String ERP_SOH_OUTBOUND      = "erp.soh.outbound";

    // ── Dead Letter Queues ─────────────────────────────────────────────────
    /** DLT suffix appended to any failed topic */
    public static final String DLT_SUFFIX = ".DLT";

    public static String dlt(String topic) { return topic + DLT_SUFFIX; }
}
