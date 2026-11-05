package com.hardbasseq.eq.text;

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
public final class AndroidTextProvider_Factory implements Factory<AndroidTextProvider> {
  private final Provider<Context> contextProvider;

  private AndroidTextProvider_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidTextProvider get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidTextProvider_Factory create(Provider<Context> contextProvider) {
    return new AndroidTextProvider_Factory(contextProvider);
  }

  public static AndroidTextProvider newInstance(Context context) {
    return new AndroidTextProvider(context);
  }
}
