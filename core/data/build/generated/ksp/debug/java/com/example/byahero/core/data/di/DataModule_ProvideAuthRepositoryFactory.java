package com.example.byahero.core.data.di;

import com.example.byahero.core.data.repository.AuthRepository;
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
public final class DataModule_ProvideAuthRepositoryFactory implements Factory<AuthRepository> {
  @Override
  public AuthRepository get() {
    return provideAuthRepository();
  }

  public static DataModule_ProvideAuthRepositoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AuthRepository provideAuthRepository() {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideAuthRepository());
  }

  private static final class InstanceHolder {
    static final DataModule_ProvideAuthRepositoryFactory INSTANCE = new DataModule_ProvideAuthRepositoryFactory();
  }
}
