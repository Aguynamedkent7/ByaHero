package com.example.byahero.feature.map;

import com.example.byahero.core.data.repository.DirectionsRepository;
import com.example.byahero.core.data.repository.RouteRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class MapViewModel_Factory implements Factory<MapViewModel> {
  private final Provider<RouteRepository> routeRepositoryProvider;

  private final Provider<DirectionsRepository> directionsRepositoryProvider;

  private MapViewModel_Factory(Provider<RouteRepository> routeRepositoryProvider,
      Provider<DirectionsRepository> directionsRepositoryProvider) {
    this.routeRepositoryProvider = routeRepositoryProvider;
    this.directionsRepositoryProvider = directionsRepositoryProvider;
  }

  @Override
  public MapViewModel get() {
    return newInstance(routeRepositoryProvider.get(), directionsRepositoryProvider.get());
  }

  public static MapViewModel_Factory create(Provider<RouteRepository> routeRepositoryProvider,
      Provider<DirectionsRepository> directionsRepositoryProvider) {
    return new MapViewModel_Factory(routeRepositoryProvider, directionsRepositoryProvider);
  }

  public static MapViewModel newInstance(RouteRepository routeRepository,
      DirectionsRepository directionsRepository) {
    return new MapViewModel(routeRepository, directionsRepository);
  }
}
