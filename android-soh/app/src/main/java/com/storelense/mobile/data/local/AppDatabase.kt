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
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun epcReadDao(): EpcReadDao
    abstract fun sohSessionDao(): SohSessionDao
    abstract fun inboundReadDao(): InboundReadDao
    abstract fun inboundShipmentDao(): InboundShipmentDao
    abstract fun refillDao(): RefillDao
    abstract fun productDao(): ProductDao

    companion object {
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
    }
}
