package com.hardbasseq.eq.auto;

import com.hardbasseq.eq.discovery.DiscoveryRepository;
import com.hardbasseq.eq.integration.PlayerController;
import com.hardbasseq.eq.playlist.PlaylistRepository;
import com.hardbasseq.eq.text.TextProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AppAutoCatalog_Factory implements Factory<AppAutoCatalog> {
  private final Provider<PlaylistRepository> playlistRepositoryProvider;

  private final Provider<DiscoveryRepository> discoveryRepositoryProvider;

  private final Provider<PlayerController> controllerProvider;

  private final Provider<TextProvider> textsProvider;

  private AppAutoCatalog_Factory(Provider<PlaylistRepository> playlistRepositoryProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider,
      Provider<PlayerController> controllerProvider, Provider<TextProvider> textsProvider) {
    this.playlistRepositoryProvider = playlistRepositoryProvider;
    this.discoveryRepositoryProvider = discoveryRepositoryProvider;
    this.controllerProvider = controllerProvider;
    this.textsProvider = textsProvider;
  }

  @Override
  public AppAutoCatalog get() {
    return newInstance(playlistRepositoryProvider.get(), discoveryRepositoryProvider.get(), controllerProvider.get(), textsProvider.get());
  }

  public static AppAutoCatalog_Factory create(
      Provider<PlaylistRepository> playlistRepositoryProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider,
      Provider<PlayerController> controllerProvider, Provider<TextProvider> textsProvider) {
    return new AppAutoCatalog_Factory(playlistRepositoryProvider, discoveryRepositoryProvider, controllerProvider, textsProvider);
  }

  public static AppAutoCatalog newInstance(PlaylistRepository playlistRepository,
      DiscoveryRepository discoveryRepository, PlayerController controller, TextProvider texts) {
    return new AppAutoCatalog(playlistRepository, discoveryRepository, controller, texts);
  }
}
