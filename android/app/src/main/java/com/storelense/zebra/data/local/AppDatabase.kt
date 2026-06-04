package com.storelense.zebra.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.storelense.zebra.data.local.dao.RefillTaskDao
import com.storelense.zebra.data.local.dao.RfidReadDao
import com.storelense.zebra.data.local.dao.SohSessionDao
import com.storelense.zebra.data.local.entity.RefillTaskEntity
import com.storelense.zebra.data.local.entity.RefillTaskItemEntity
import com.storelense.zebra.data.local.entity.RfidReadEntity
import com.storelense.zebra.data.local.entity.SohSessionEntity

@Database(
    entities  = [
        SohSessionEntity::class,
        RfidReadEntity::class,
        RefillTaskEntity::class,
        RefillTaskItemEntity::class,
    ],
    version   = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sohSessionDao(): SohSessionDao
    abstract fun rfidReadDao():   RfidReadDao
    abstract fun refillTaskDao(): RefillTaskDao
}
