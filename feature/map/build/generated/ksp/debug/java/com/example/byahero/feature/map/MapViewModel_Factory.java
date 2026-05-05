package com.example.byahero.feature.map;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
  @Override
  public MapViewModel get() {
    return newInstance();
  }

  public static MapViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MapViewModel newInstance() {
    return new MapViewModel();
  }

  private static final class InstanceHolder {
    static final MapViewModel_Factory INSTANCE = new MapViewModel_Factory();
  }
}
