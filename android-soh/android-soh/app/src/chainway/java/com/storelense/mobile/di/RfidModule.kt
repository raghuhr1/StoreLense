package com.storelense.mobile.di

import android.content.Context
import com.storelense.mobile.BuildConfig
import com.storelense.mobile.rfid.ChainwayRfidReader
import com.storelense.mobile.rfid.MockRfidReader
import com.storelense.mobile.rfid.RfidReader
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
    fun provideRfidReader(@ApplicationContext ctx: Context): RfidReader =
        if (BuildConfig.USE_MOCK_RFID) MockRfidReader()
        else ChainwayRfidReader(ctx)
}
