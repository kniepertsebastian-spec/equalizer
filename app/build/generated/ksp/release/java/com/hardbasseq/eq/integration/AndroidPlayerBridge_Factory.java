package com.hardbasseq.eq.integration;

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
public final class AndroidPlayerBridge_Factory implements Factory<AndroidPlayerBridge> {
  private final Provider<Context> contextProvider;

  private AndroidPlayerBridge_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidPlayerBridge get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidPlayerBridge_Factory create(Provider<Context> contextProvider) {
    return new AndroidPlayerBridge_Factory(contextProvider);
  }

  public static AndroidPlayerBridge newInstance(Context context) {
    return new AndroidPlayerBridge(context);
  }
}
