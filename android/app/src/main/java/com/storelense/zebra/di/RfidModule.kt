package com.storelense.zebra.di

import android.content.Context
import com.storelense.zebra.BuildConfig
import com.storelense.zebra.rfid.EmDkRfidReader
import com.storelense.zebra.rfid.MockRfidReader
import com.storelense.zebra.rfid.RfidReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RfidModule {

    @Provides @Singleton
    fun provideRfidReader(@ApplicationContext context: Context): RfidReader =
        if (BuildConfig.USE_MOCK_RFID) {
            MockRfidReader()
        } else {
            EmDkRfidReader(context)
        }
}
