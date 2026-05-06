package com.example.byahero.core.data.di

import android.content.Context
import com.example.byahero.core.data.repository.AuthRepository
import com.example.byahero.core.data.repository.AuthRepositoryImpl
import com.example.byahero.core.data.repository.RouteRepository
import com.example.byahero.core.data.repository.RouteRepositoryImpl
import com.example.byahero.core.data.repository.DirectionsRepository
import com.example.byahero.core.data.repository.DirectionsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Provides
    @Singleton
    fun provideDirectionsRepository(@ApplicationContext context: Context): DirectionsRepository {
        return DirectionsRepositoryImpl(context)
    }
}
