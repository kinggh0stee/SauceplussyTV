package com.saucedplussytv.androidtv.di

import com.saucedplussytv.androidtv.repository.SaucedplussySubscriptionRepository
import com.saucedplussytv.androidtv.repository.SaucedplussyVideoRepository
import com.saucedplussytv.androidtv.repository.SubscriptionRepository
import com.saucedplussytv.androidtv.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SaucedplussySubscriptionRepository
    ): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: SaucedplussyVideoRepository
    ): VideoRepository
}
