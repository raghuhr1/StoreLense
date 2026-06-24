package com.storelense.mobile.di

import android.content.Context
import androidx.room.Room
import com.storelense.mobile.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "storelense.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

    @Provides fun provideEpcReadDao(db: AppDatabase)         = db.epcReadDao()
    @Provides fun provideSohSessionDao(db: AppDatabase)      = db.sohSessionDao()
    @Provides fun provideInboundReadDao(db: AppDatabase)     = db.inboundReadDao()
    @Provides fun provideInboundShipmentDao(db: AppDatabase) = db.inboundShipmentDao()
    @Provides fun provideRefillDao(db: AppDatabase)          = db.refillDao()
    @Provides fun provideProductDao(db: AppDatabase)         = db.productDao()
    @Provides fun provideStoreDao(db: AppDatabase)           = db.storeDao()
    @Provides fun provideTransferDao(db: AppDatabase)        = db.transferDao()
    @Provides fun provideExceptionCacheDao(db: AppDatabase)  = db.exceptionCacheDao()
    @Provides fun provideGhostAnalysisDao(db: AppDatabase)   = db.ghostAnalysisDao()
}
