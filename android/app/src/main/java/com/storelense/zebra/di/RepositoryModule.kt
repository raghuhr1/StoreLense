package com.storelense.zebra.di

import com.storelense.zebra.data.repository.AuthRepositoryImpl
import com.storelense.zebra.data.repository.RefillRepositoryImpl
import com.storelense.zebra.data.repository.RfidRepositoryImpl
import com.storelense.zebra.data.repository.SohRepositoryImpl
import com.storelense.zebra.domain.repository.AuthRepository
import com.storelense.zebra.domain.repository.RefillRepository
import com.storelense.zebra.domain.repository.RfidRepository
import com.storelense.zebra.domain.repository.SohRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuth(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindSoh(impl: SohRepositoryImpl): SohRepository

    @Binds @Singleton
    abstract fun bindRfid(impl: RfidRepositoryImpl): RfidRepository

    @Binds @Singleton
    abstract fun bindRefill(impl: RefillRepositoryImpl): RefillRepository
}
