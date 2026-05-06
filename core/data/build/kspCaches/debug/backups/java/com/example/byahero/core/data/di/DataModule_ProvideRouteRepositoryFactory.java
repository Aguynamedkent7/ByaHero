package com.example.byahero.core.data.di;

import com.example.byahero.core.data.repository.RouteRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DataModule_ProvideRouteRepositoryFactory implements Factory<RouteRepository> {
  @Override
  public RouteRepository get() {
    return provideRouteRepository();
  }

  public static DataModule_ProvideRouteRepositoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RouteRepository provideRouteRepository() {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideRouteRepository());
  }

  private static final class InstanceHolder {
    static final DataModule_ProvideRouteRepositoryFactory INSTANCE = new DataModule_ProvideRouteRepositoryFactory();
  }
}
