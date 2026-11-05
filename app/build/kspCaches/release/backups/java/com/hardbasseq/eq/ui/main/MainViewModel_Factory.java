package com.hardbasseq.eq.ui.main;

import com.hardbasseq.eq.audio.AudioEffectRepository;
import com.hardbasseq.eq.audio.AudioEngine;
import com.hardbasseq.eq.audio.AudioRouteRepository;
import com.hardbasseq.eq.audio.AudioSessionRepository;
import com.hardbasseq.eq.audio.SystemVolumeRepository;
import com.hardbasseq.eq.auto.CarConnectionRepository;
import com.hardbasseq.eq.correction.CorrectionProfileRepository;
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder;
import com.hardbasseq.eq.integration.PlayerBridge;
import com.hardbasseq.eq.preset.PresetRepository;
import com.hardbasseq.eq.profile.DeviceAliasStore;
import com.hardbasseq.eq.profile.DeviceProfileRepository;
import com.hardbasseq.eq.settings.AppSettingsRepository;
import com.hardbasseq.eq.text.TextProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.hardbasseq.eq.di.DefaultDispatcher")
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<AudioEffectRepository> repositoryProvider;

  private final Provider<AudioEngine> audioEngineProvider;

  private final Provider<AudioRouteRepository> routeRepositoryProvider;

  private final Provider<SystemVolumeRepository> volumeRepositoryProvider;

  private final Provider<DiagnosticsRecorder> diagnosticsRecorderProvider;

  private final Provider<PlayerBridge> playerBridgeProvider;

  private final Provider<PresetRepository> presetRepositoryProvider;

  private final Provider<AppSettingsRepository> appSettingsRepositoryProvider;

  private final Provider<CorrectionProfileRepository> correctionProfileRepositoryProvider;

  private final Provider<DeviceProfileRepository> deviceProfileRepositoryProvider;

  private final Provider<TextProvider> textsProvider;

  private final Provider<DeviceAliasStore> deviceAliasesProvider;

  private final Provider<AudioSessionRepository> sessionRepositoryProvider;

  private final Provider<CarConnectionRepository> carConnectionRepositoryProvider;

  private final Provider<CoroutineDispatcher> backgroundDispatcherProvider;

  private MainViewModel_Factory(Provider<AudioEffectRepository> repositoryProvider,
      Provider<AudioEngine> audioEngineProvider,
      Provider<AudioRouteRepository> routeRepositoryProvider,
      Provider<SystemVolumeRepository> volumeRepositoryProvider,
      Provider<DiagnosticsRecorder> diagnosticsRecorderProvider,
      Provider<PlayerBridge> playerBridgeProvider,
      Provider<PresetRepository> presetRepositoryProvider,
      Provider<AppSettingsRepository> appSettingsRepositoryProvider,
      Provider<CorrectionProfileRepository> correctionProfileRepositoryProvider,
      Provider<DeviceProfileRepository> deviceProfileRepositoryProvider,
      Provider<TextProvider> textsProvider, Provider<DeviceAliasStore> deviceAliasesProvider,
      Provider<AudioSessionRepository> sessionRepositoryProvider,
      Provider<CarConnectionRepository> carConnectionRepositoryProvider,
      Provider<CoroutineDispatcher> backgroundDispatcherProvider) {
    this.repositoryProvider = repositoryProvider;
    this.audioEngineProvider = audioEngineProvider;
    this.routeRepositoryProvider = routeRepositoryProvider;
    this.volumeRepositoryProvider = volumeRepositoryProvider;
    this.diagnosticsRecorderProvider = diagnosticsRecorderProvider;
    this.playerBridgeProvider = playerBridgeProvider;
    this.presetRepositoryProvider = presetRepositoryProvider;
    this.appSettingsRepositoryProvider = appSettingsRepositoryProvider;
    this.correctionProfileRepositoryProvider = correctionProfileRepositoryProvider;
    this.deviceProfileRepositoryProvider = deviceProfileRepositoryProvider;
    this.textsProvider = textsProvider;
    this.deviceAliasesProvider = deviceAliasesProvider;
    this.sessionRepositoryProvider = sessionRepositoryProvider;
    this.carConnectionRepositoryProvider = carConnectionRepositoryProvider;
    this.backgroundDispatcherProvider = backgroundDispatcherProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(repositoryProvider.get(), audioEngineProvider.get(), routeRepositoryProvider.get(), volumeRepositoryProvider.get(), diagnosticsRecorderProvider.get(), playerBridgeProvider.get(), presetRepositoryProvider.get(), appSettingsRepositoryProvider.get(), correctionProfileRepositoryProvider.get(), deviceProfileRepositoryProvider.get(), textsProvider.get(), deviceAliasesProvider.get(), sessionRepositoryProvider.get(), carConnectionRepositoryProvider.get(), backgroundDispatcherProvider.get());
  }

  public static MainViewModel_Factory create(Provider<AudioEffectRepository> repositoryProvider,
      Provider<AudioEngine> audioEngineProvider,
      Provider<AudioRouteRepository> routeRepositoryProvider,
      Provider<SystemVolumeRepository> volumeRepositoryProvider,
      Provider<DiagnosticsRecorder> diagnosticsRecorderProvider,
      Provider<PlayerBridge> playerBridgeProvider,
      Provider<PresetRepository> presetRepositoryProvider,
      Provider<AppSettingsRepository> appSettingsRepositoryProvider,
      Provider<CorrectionProfileRepository> correctionProfileRepositoryProvider,
      Provider<DeviceProfileRepository> deviceProfileRepositoryProvider,
      Provider<TextProvider> textsProvider, Provider<DeviceAliasStore> deviceAliasesProvider,
      Provider<AudioSessionRepository> sessionRepositoryProvider,
      Provider<CarConnectionRepository> carConnectionRepositoryProvider,
      Provider<CoroutineDispatcher> backgroundDispatcherProvider) {
    return new MainViewModel_Factory(repositoryProvider, audioEngineProvider, routeRepositoryProvider, volumeRepositoryProvider, diagnosticsRecorderProvider, playerBridgeProvider, presetRepositoryProvider, appSettingsRepositoryProvider, correctionProfileRepositoryProvider, deviceProfileRepositoryProvider, textsProvider, deviceAliasesProvider, sessionRepositoryProvider, carConnectionRepositoryProvider, backgroundDispatcherProvider);
  }

  public static MainViewModel newInstance(AudioEffectRepository repository, AudioEngine audioEngine,
      AudioRouteRepository routeRepository, SystemVolumeRepository volumeRepository,
      DiagnosticsRecorder diagnosticsRecorder, PlayerBridge playerBridge,
      PresetRepository presetRepository, AppSettingsRepository appSettingsRepository,
      CorrectionProfileRepository correctionProfileRepository,
      DeviceProfileRepository deviceProfileRepository, TextProvider texts,
      DeviceAliasStore deviceAliases, AudioSessionRepository sessionRepository,
      CarConnectionRepository carConnectionRepository, CoroutineDispatcher backgroundDispatcher) {
    return new MainViewModel(repository, audioEngine, routeRepository, volumeRepository, diagnosticsRecorder, playerBridge, presetRepository, appSettingsRepository, correctionProfileRepository, deviceProfileRepository, texts, deviceAliases, sessionRepository, carConnectionRepository, backgroundDispatcher);
  }
}
