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
public final class DataStorePlaylistRepository_Factory implements Factory<DataStorePlaylistRepository> {
  private final Provider<Context> contextProvider;

  private DataStorePlaylistRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStorePlaylistRepository get() {
    return newInstance(contextProvider.get());
  }

  public static DataStorePlaylistRepository_Factory create(Provider<Context> contextProvider) {
    return new DataStorePlaylistRepository_Factory(contextProvider);
  }

  public static DataStorePlaylistRepository newInstance(Context context) {
    return new DataStorePlaylistRepository(context);
  }
}
