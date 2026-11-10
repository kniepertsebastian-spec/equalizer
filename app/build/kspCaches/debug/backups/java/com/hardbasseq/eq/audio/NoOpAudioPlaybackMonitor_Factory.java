package com.hardbasseq.eq.audio;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class NoOpAudioPlaybackMonitor_Factory implements Factory<NoOpAudioPlaybackMonitor> {
  @Override
  public NoOpAudioPlaybackMonitor get() {
    return newInstance();
  }

  public static NoOpAudioPlaybackMonitor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NoOpAudioPlaybackMonitor newInstance() {
    return new NoOpAudioPlaybackMonitor();
  }

  private static final class InstanceHolder {
    static final NoOpAudioPlaybackMonitor_Factory INSTANCE = new NoOpAudioPlaybackMonitor_Factory();
  }
}
