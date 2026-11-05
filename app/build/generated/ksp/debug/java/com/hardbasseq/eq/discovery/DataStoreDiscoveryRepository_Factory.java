package com.hardbasseq.eq.discovery;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class DataStoreDiscoveryRepository_Factory implements Factory<DataStoreDiscoveryRepository> {
  private final Provider<Context> contextProvider;

  private DataStoreDiscoveryRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStoreDiscoveryRepository get() {
    return newInstance(contextProvider.get());
  }

  public static DataStoreDiscoveryRepository_Factory create(Provider<Context> contextProvider) {
    return new DataStoreDiscoveryRepository_Factory(contextProvider);
  }

  public static DataStoreDiscoveryRepository newInstance(Context context) {
    return new DataStoreDiscoveryRepository(context);
  }
}
