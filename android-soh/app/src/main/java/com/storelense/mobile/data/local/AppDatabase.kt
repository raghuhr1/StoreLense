package com.storelense.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun epcReadDao(): EpcReadDao
    abstract fun sohSessionDao(): SohSessionDao
    abstract fun inboundReadDao(): InboundReadDao
    abstract fun inboundShipmentDao(): InboundShipmentDao
    abstract fun refillDao(): RefillDao
}
