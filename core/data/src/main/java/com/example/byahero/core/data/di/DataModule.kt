package com.example.byahero.core.data.di

import com.example.byahero.core.data.repository.AuthRepository
import com.example.byahero.core.data.repository.AuthRepositoryImpl
import com.example.byahero.core.data.repository.RouteRepository
import com.example.byahero.core.data.repository.RouteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository {
        return AuthRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideRouteRepository(): RouteRepository {
        return RouteRepositoryImpl()
    }
}
