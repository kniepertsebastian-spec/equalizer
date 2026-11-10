package com.hardbasseq.eq.di;

import com.hardbasseq.eq.audio.AndroidAudioPlaybackMonitor;
import com.hardbasseq.eq.audio.AudioPlaybackMonitor;
import com.hardbasseq.eq.audio.NoOpAudioPlaybackMonitor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
public final class AudioModule_Companion_ProvideAudioPlaybackMonitorFactory implements Factory<AudioPlaybackMonitor> {
  private final Provider<AndroidAudioPlaybackMonitor> androidMonitorProvider;

  private final Provider<NoOpAudioPlaybackMonitor> noOpMonitorProvider;

  private AudioModule_Companion_ProvideAudioPlaybackMonitorFactory(
      Provider<AndroidAudioPlaybackMonitor> androidMonitorProvider,
      Provider<NoOpAudioPlaybackMonitor> noOpMonitorProvider) {
    this.androidMonitorProvider = androidMonitorProvider;
    this.noOpMonitorProvider = noOpMonitorProvider;
  }

  @Override
  public AudioPlaybackMonitor get() {
    return provideAudioPlaybackMonitor(androidMonitorProvider.get(), noOpMonitorProvider.get());
  }

  public static AudioModule_Companion_ProvideAudioPlaybackMonitorFactory create(
      Provider<AndroidAudioPlaybackMonitor> androidMonitorProvider,
      Provider<NoOpAudioPlaybackMonitor> noOpMonitorProvider) {
    return new AudioModule_Companion_ProvideAudioPlaybackMonitorFactory(androidMonitorProvider, noOpMonitorProvider);
  }

  public static AudioPlaybackMonitor provideAudioPlaybackMonitor(
      AndroidAudioPlaybackMonitor androidMonitor, NoOpAudioPlaybackMonitor noOpMonitor) {
    return Preconditions.checkNotNullFromProvides(AudioModule.Companion.provideAudioPlaybackMonitor(androidMonitor, noOpMonitor));
  }
}
