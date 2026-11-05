package com.hardbasseq.eq.audio;

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
public final class AndroidAudioRouteRepository_Factory implements Factory<AndroidAudioRouteRepository> {
  private final Provider<Context> contextProvider;

  private AndroidAudioRouteRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidAudioRouteRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidAudioRouteRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidAudioRouteRepository_Factory(contextProvider);
  }

  public static AndroidAudioRouteRepository newInstance(Context context) {
    return new AndroidAudioRouteRepository(context);
  }
}
