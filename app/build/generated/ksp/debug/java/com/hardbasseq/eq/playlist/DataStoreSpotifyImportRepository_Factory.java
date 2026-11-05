package com.hardbasseq.eq.playlist;

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
public final class DataStoreSpotifyImportRepository_Factory implements Factory<DataStoreSpotifyImportRepository> {
  private final Provider<Context> contextProvider;

  private DataStoreSpotifyImportRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStoreSpotifyImportRepository get() {
    return newInstance(contextProvider.get());
  }

  public static DataStoreSpotifyImportRepository_Factory create(Provider<Context> contextProvider) {
    return new DataStoreSpotifyImportRepository_Factory(contextProvider);
  }

  public static DataStoreSpotifyImportRepository newInstance(Context context) {
    return new DataStoreSpotifyImportRepository(context);
  }
}
