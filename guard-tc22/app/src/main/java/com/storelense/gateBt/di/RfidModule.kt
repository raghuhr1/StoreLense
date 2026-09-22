package com.storelense.gateBt.di

import com.storelense.gateBt.BuildConfig
import com.storelense.gateBt.rfid.BluetoothRfidReader
import com.storelense.gateBt.rfid.EmDkRfidReader
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
     *  - mock flavor / debug local.properties  → [MockRfidReader] (no hardware needed)
     *  - zebra flavor                          → [EmDkRfidReader] (TC22 integrated UHF RFID)
     *  - bluetooth flavor                      → [BluetoothRfidReader] (Identium MUBR01 over BLE)
     */
    @Provides @Singleton
    fun provideRfidReader(
        zebraReader:     EmDkRfidReader,
        bluetoothReader: BluetoothRfidReader,
        mockReader:      MockRfidReader
    ): RfidReader = when {
        BuildConfig.USE_MOCK_RFID -> mockReader
        BuildConfig.READER_TYPE == "BLUETOOTH" -> bluetoothReader
        else -> zebraReader
    }
}
