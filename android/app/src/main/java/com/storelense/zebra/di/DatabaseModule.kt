package com.storelense.zebra.di

import android.content.Context
import androidx.room.Room
import com.storelense.zebra.data.local.AppDatabase
import com.storelense.zebra.data.local.dao.RefillTaskDao
import com.storelense.zebra.data.local.dao.RfidReadDao
import com.storelense.zebra.data.local.dao.SohSessionDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSohDao(db: AppDatabase):    SohSessionDao = db.sohSessionDao()
    @Provides fun provideRfidDao(db: AppDatabase):   RfidReadDao   = db.rfidReadDao()
    @Provides fun provideRefillDao(db: AppDatabase): RefillTaskDao = db.refillTaskDao()
}
