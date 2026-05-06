package com.example.byahero.core.data.di;

import android.content.Context;
import com.example.byahero.core.data.repository.DirectionsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DataModule_ProvideDirectionsRepositoryFactory implements Factory<DirectionsRepository> {
  private final Provider<Context> contextProvider;

  private DataModule_ProvideDirectionsRepositoryFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DirectionsRepository get() {
    return provideDirectionsRepository(contextProvider.get());
  }

  public static DataModule_ProvideDirectionsRepositoryFactory create(
      Provider<Context> contextProvider) {
    return new DataModule_ProvideDirectionsRepositoryFactory(contextProvider);
  }

  public static DirectionsRepository provideDirectionsRepository(Context context) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideDirectionsRepository(context));
  }
}
