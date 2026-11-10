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
public final class AndroidAudioPlaybackMonitor_Factory implements Factory<AndroidAudioPlaybackMonitor> {
  private final Provider<Context> contextProvider;

  private AndroidAudioPlaybackMonitor_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidAudioPlaybackMonitor get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidAudioPlaybackMonitor_Factory create(Provider<Context> contextProvider) {
    return new AndroidAudioPlaybackMonitor_Factory(contextProvider);
  }

  public static AndroidAudioPlaybackMonitor newInstance(Context context) {
    return new AndroidAudioPlaybackMonitor(context);
  }
}
