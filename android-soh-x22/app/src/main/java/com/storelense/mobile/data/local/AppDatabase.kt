package com.storelense.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.storelense.mobile.data.local.dao.*
import com.storelense.mobile.data.local.entity.*

@Database(
    entities = [
        EpcReadEntity::class,
        SohSessionEntity::class,
        InboundReadEntity::class,
        InboundShipmentEntity::class,
        RefillTaskEntity::class,
        RefillTaskItemEntity::class,
        ProductEntity::class,
        StoreEntity::class,
        TransferOutEntity::class,
        TransferManifestEntity::class,
        ExceptionCacheEntity::class,
        GhostAnalysisEntity::class,
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun epcReadDao(): EpcReadDao
    abstract fun sohSessionDao(): SohSessionDao
    abstract fun inboundReadDao(): InboundReadDao
    abstract fun inboundShipmentDao(): InboundShipmentDao
    abstract fun refillDao(): RefillDao
    abstract fun productDao(): ProductDao
    abstract fun storeDao(): StoreDao
    abstract fun transferDao(): TransferDao
    abstract fun exceptionCacheDao(): ExceptionCacheDao
    abstract fun ghostAnalysisDao(): GhostAnalysisDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between v8 and v9 — version bump only.
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // epc_reads: add location/section columns for cycle count attribution
                db.execSQL("ALTER TABLE epc_reads ADD COLUMN locationCode TEXT")
                db.execSQL("ALTER TABLE epc_reads ADD COLUMN sectionCode TEXT")

                // soh_sessions: add location/section/cycleCountId for cycle count support
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN locationCode TEXT")
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN sectionCode TEXT")
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN cycleCountId TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add pendingQty to refill_task_items
                try {
                    db.execSQL("ALTER TABLE refill_task_items ADD COLUMN pendingQty INTEGER NOT NULL DEFAULT -1")
                } catch (e: Exception) { /* already exists */ }

                // 2. Add cachedAt to tables that were missing it in their original schema
                // We use DEFAULT 0 to match @ColumnInfo(defaultValue = "0")
                try {
                    db.execSQL("ALTER TABLE soh_sessions ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { /* already exists */ }

                try {
                    db.execSQL("ALTER TABLE inbound_shipments ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { /* already exists */ }

                try {
                    db.execSQL("ALTER TABLE refill_tasks ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { /* already exists */ }

                // 3. Fix expectedCount default value in soh_sessions if it was created with 'undefined'
                // Room validation is very strict about this.
                try {
                    // In SQLite we can't easily change DEFAULT value of existing column.
                    // However, we can try to re-apply the column if it was somehow missing or incorrect
                    // But usually, Room's error 'Expected: 0, Found: undefined' means the SQL used to create
                    // the table or add the column didn't specify the default value correctly.
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS products (
                        id TEXT NOT NULL PRIMARY KEY,
                        sku TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        brand TEXT,
                        category TEXT,
                        erpCode TEXT,
                        storeId TEXT,
                        onHandQty INTEGER NOT NULL DEFAULT 0,
                        expectedQty INTEGER NOT NULL DEFAULT 0,
                        imageUrl TEXT,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_sku ON products(sku)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_erpCode ON products(erpCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_storeId ON products(storeId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN expectedCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE epc_reads ADD COLUMN zoneId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix 1: Remove UNIQUE constraint on products.sku — same SKU can appear in
                // multiple stores. The unique index silently deleted cross-store products on upsert.
                db.execSQL("DROP INDEX IF EXISTS idx_products_sku")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku)")

                // Fix 2: Add storeId to exception_cache so exceptions are store-scoped.
                db.execSQL("ALTER TABLE exception_cache ADD COLUMN storeId TEXT NOT NULL DEFAULT ''")

                // Fix 3: Add sourceStoreId to transfers_out for store-scoped transfer queries.
                db.execSQL("ALTER TABLE transfers_out ADD COLUMN sourceStoreId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reconcile index names: old migrations used "idx_products_*" prefix but
                // Room @Entity generates "index_products_*" — drop all old variants and
                // recreate with the names Room expects.
                db.execSQL("DROP INDEX IF EXISTS idx_products_sku")
                db.execSQL("DROP INDEX IF EXISTS index_products_sku")   // drop unique remnant
                db.execSQL("DROP INDEX IF EXISTS idx_products_erpCode")
                db.execSQL("DROP INDEX IF EXISTS idx_products_storeId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_erpCode ON products(erpCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_storeId ON products(storeId)")

                // Ensure lastSyncedAt exists (it might be missing if added recently to M1_2 but not to a migration)
                try {
                    db.execSQL("ALTER TABLE products ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column might already exist
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extend soh_sessions with ERP-triggered session fields
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE soh_sessions ADD COLUMN zoneRegion TEXT")

                // Store lookup cache (for Transfer Out destination dropdown)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stores (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        code TEXT,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Offline buffer for outbound transfers
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfers_out (
                        id TEXT NOT NULL PRIMARY KEY,
                        destStoreId TEXT NOT NULL,
                        transferType TEXT NOT NULL,
                        epcsText TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        uploadedAt INTEGER
                    )
                """.trimIndent())

                // Inbound transfer EPC manifest (for Transfer Receive matching)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfer_manifest (
                        transferId TEXT NOT NULL,
                        epc TEXT NOT NULL,
                        receivedAt INTEGER,
                        PRIMARY KEY (transferId, epc)
                    )
                """.trimIndent())

                // Exception events cache (Missing EPCs, Ghost Tags, Read Misses)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exception_cache (
                        epc TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        confidence INTEGER NOT NULL DEFAULT 0,
                        classification TEXT,
                        lastSeen TEXT,
                        status TEXT NOT NULL DEFAULT 'OPEN',
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_exception_cache_type_status " +
                    "ON exception_cache(type, status)"
                )

                // Ghost analysis detail cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ghost_analysis (
                        epc TEXT NOT NULL PRIMARY KEY,
                        confidenceScore INTEGER NOT NULL DEFAULT 0,
                        reasonsText TEXT NOT NULL,
                        status TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
    }
}
