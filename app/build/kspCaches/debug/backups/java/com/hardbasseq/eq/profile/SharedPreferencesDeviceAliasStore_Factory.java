package com.hardbasseq.eq.profile;

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
public final class SharedPreferencesDeviceAliasStore_Factory implements Factory<SharedPreferencesDeviceAliasStore> {
  private final Provider<Context> contextProvider;

  private SharedPreferencesDeviceAliasStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SharedPreferencesDeviceAliasStore get() {
    return newInstance(contextProvider.get());
  }

  public static SharedPreferencesDeviceAliasStore_Factory create(
      Provider<Context> contextProvider) {
    return new SharedPreferencesDeviceAliasStore_Factory(contextProvider);
  }

  public static SharedPreferencesDeviceAliasStore newInstance(Context context) {
    return new SharedPreferencesDeviceAliasStore(context);
  }
}
