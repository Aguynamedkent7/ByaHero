package com.example.byahero.core.data.di

import android.content.Context
import com.example.byahero.core.data.repository.AuthRepository
import com.example.byahero.core.data.repository.AuthRepositoryImpl
import com.example.byahero.core.data.repository.RouteRepository
import com.example.byahero.core.data.repository.RouteRepositoryImpl
import com.example.byahero.core.data.repository.DirectionsRepository
import com.example.byahero.core.data.repository.DirectionsRepositoryImpl
import com.example.byahero.core.data.repository.LocationRepository
import com.example.byahero.core.data.repository.LocationRepositoryImpl
import com.example.byahero.core.data.repository.PlacesRepository
import com.example.byahero.core.data.repository.PlacesRepositoryImpl
import com.example.byahero.core.data.repository.SettingsRepository
import com.example.byahero.core.data.repository.SettingsRepositoryImpl
import com.example.byahero.core.data.SupabaseConfig
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

    @Provides
    @Singleton
    fun provideLocationRepository(@ApplicationContext context: Context): LocationRepository {
        return LocationRepositoryImpl(context, SupabaseConfig.client)
    }

    @Provides
    @Singleton
    fun providePlacesRepository(@ApplicationContext context: Context): PlacesRepository {
        return PlacesRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository {
        return SettingsRepositoryImpl()
    }
}
