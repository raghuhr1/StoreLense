package com.storelense.gateBt.di

import com.storelense.gateBt.BuildConfig
import com.storelense.gateBt.rfid.BluetoothRfidReader
import com.storelense.gateBt.rfid.MockRfidReader
import com.storelense.gateBt.rfid.RfidReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RfidModule {

    /**
     * Selects the RFID reader implementation at build time:
     *  - mock flavor / debug local.properties → [MockRfidReader] (no hardware needed)
     *  - bluetooth flavor release              → [BluetoothRfidReader] (real MUBR01)
     */
    @Provides @Singleton
    fun provideRfidReader(
        bluetoothReader: BluetoothRfidReader,
        mockReader:      MockRfidReader
    ): RfidReader = if (BuildConfig.USE_MOCK_RFID) mockReader else bluetoothReader
}
