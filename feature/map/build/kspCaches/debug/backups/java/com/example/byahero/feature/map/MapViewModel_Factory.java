package com.example.byahero.feature.map;

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

  private MapViewModel_Factory(Provider<RouteRepository> routeRepositoryProvider) {
    this.routeRepositoryProvider = routeRepositoryProvider;
  }

  @Override
  public MapViewModel get() {
    return newInstance(routeRepositoryProvider.get());
  }

  public static MapViewModel_Factory create(Provider<RouteRepository> routeRepositoryProvider) {
    return new MapViewModel_Factory(routeRepositoryProvider);
  }

  public static MapViewModel newInstance(RouteRepository routeRepository) {
    return new MapViewModel(routeRepository);
  }
}
