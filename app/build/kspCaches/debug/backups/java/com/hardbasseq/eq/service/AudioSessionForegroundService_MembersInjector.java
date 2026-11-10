package com.hardbasseq.eq.service;

import com.hardbasseq.eq.audio.AudioEngine;
import com.hardbasseq.eq.audio.AudioPlaybackMonitor;
import com.hardbasseq.eq.audio.AudioRouteRepository;
import com.hardbasseq.eq.audio.AudioSessionRepository;
import com.hardbasseq.eq.audio.SystemVolumeRepository;
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class AudioSessionForegroundService_MembersInjector implements MembersInjector<AudioSessionForegroundService> {
  private final Provider<AudioSessionRepository> sessionRepositoryProvider;

  private final Provider<AudioRouteRepository> routeRepositoryProvider;

  private final Provider<SystemVolumeRepository> volumeRepositoryProvider;

  private final Provider<AudioEngine> audioEngineProvider;

  private final Provider<DiagnosticsRecorder> diagnosticsRecorderProvider;

  private final Provider<AppVisibility> appVisibilityProvider;

  private final Provider<AudioPlaybackMonitor> playbackMonitorProvider;

  private AudioSessionForegroundService_MembersInjector(
      Provider<AudioSessionRepository> sessionRepositoryProvider,
      Provider<AudioRouteRepository> routeRepositoryProvider,
      Provider<SystemVolumeRepository> volumeRepositoryProvider,
      Provider<AudioEngine> audioEngineProvider,
      Provider<DiagnosticsRecorder> diagnosticsRecorderProvider,
      Provider<AppVisibility> appVisibilityProvider,
      Provider<AudioPlaybackMonitor> playbackMonitorProvider) {
    this.sessionRepositoryProvider = sessionRepositoryProvider;
    this.routeRepositoryProvider = routeRepositoryProvider;
    this.volumeRepositoryProvider = volumeRepositoryProvider;
    this.audioEngineProvider = audioEngineProvider;
    this.diagnosticsRecorderProvider = diagnosticsRecorderProvider;
    this.appVisibilityProvider = appVisibilityProvider;
    this.playbackMonitorProvider = playbackMonitorProvider;
  }

  @Override
  public void injectMembers(AudioSessionForegroundService instance) {
    injectSessionRepository(instance, sessionRepositoryProvider.get());
    injectRouteRepository(instance, routeRepositoryProvider.get());
    injectVolumeRepository(instance, volumeRepositoryProvider.get());
    injectAudioEngine(instance, audioEngineProvider.get());
    injectDiagnosticsRecorder(instance, diagnosticsRecorderProvider.get());
    injectAppVisibility(instance, appVisibilityProvider.get());
    injectPlaybackMonitor(instance, playbackMonitorProvider.get());
  }

  public static MembersInjector<AudioSessionForegroundService> create(
      Provider<AudioSessionRepository> sessionRepositoryProvider,
      Provider<AudioRouteRepository> routeRepositoryProvider,
      Provider<SystemVolumeRepository> volumeRepositoryProvider,
      Provider<AudioEngine> audioEngineProvider,
      Provider<DiagnosticsRecorder> diagnosticsRecorderProvider,
      Provider<AppVisibility> appVisibilityProvider,
      Provider<AudioPlaybackMonitor> playbackMonitorProvider) {
    return new AudioSessionForegroundService_MembersInjector(sessionRepositoryProvider, routeRepositoryProvider, volumeRepositoryProvider, audioEngineProvider, diagnosticsRecorderProvider, appVisibilityProvider, playbackMonitorProvider);
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.sessionRepository")
  public static void injectSessionRepository(AudioSessionForegroundService instance,
      AudioSessionRepository sessionRepository) {
    instance.sessionRepository = sessionRepository;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.routeRepository")
  public static void injectRouteRepository(AudioSessionForegroundService instance,
      AudioRouteRepository routeRepository) {
    instance.routeRepository = routeRepository;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.volumeRepository")
  public static void injectVolumeRepository(AudioSessionForegroundService instance,
      SystemVolumeRepository volumeRepository) {
    instance.volumeRepository = volumeRepository;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.audioEngine")
  public static void injectAudioEngine(AudioSessionForegroundService instance,
      AudioEngine audioEngine) {
    instance.audioEngine = audioEngine;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.diagnosticsRecorder")
  public static void injectDiagnosticsRecorder(AudioSessionForegroundService instance,
      DiagnosticsRecorder diagnosticsRecorder) {
    instance.diagnosticsRecorder = diagnosticsRecorder;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.appVisibility")
  public static void injectAppVisibility(AudioSessionForegroundService instance,
      AppVisibility appVisibility) {
    instance.appVisibility = appVisibility;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.service.AudioSessionForegroundService.playbackMonitor")
  public static void injectPlaybackMonitor(AudioSessionForegroundService instance,
      AudioPlaybackMonitor playbackMonitor) {
    instance.playbackMonitor = playbackMonitor;
  }
}
