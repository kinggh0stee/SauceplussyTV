package com.saucedplussytv.androidtv.di

import android.content.Context
import com.saucedplussytv.androidtv.authenticate.AuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context): AuthManager =
        AuthManager.getInstance(context)
}
