package com.hardbasseq.eq.discovery;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class AndroidDiscoverySource_Factory implements Factory<AndroidDiscoverySource> {
  private final Provider<Context> contextProvider;

  private AndroidDiscoverySource_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidDiscoverySource get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidDiscoverySource_Factory create(Provider<Context> contextProvider) {
    return new AndroidDiscoverySource_Factory(contextProvider);
  }

  public static AndroidDiscoverySource newInstance(Context context) {
    return new AndroidDiscoverySource(context);
  }
}
