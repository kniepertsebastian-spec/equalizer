package com.hardbasseq.eq.ui.player;

import com.hardbasseq.eq.discovery.DiscoveryRepository;
import com.hardbasseq.eq.discovery.DiscoveryUpdater;
import com.hardbasseq.eq.integration.PlayerController;
import com.hardbasseq.eq.text.TextProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class DiscoveryViewModel_Factory implements Factory<DiscoveryViewModel> {
  private final Provider<DiscoveryRepository> repositoryProvider;

  private final Provider<DiscoveryUpdater> updaterProvider;

  private final Provider<PlayerController> controllerProvider;

  private final Provider<TextProvider> textsProvider;

  private DiscoveryViewModel_Factory(Provider<DiscoveryRepository> repositoryProvider,
      Provider<DiscoveryUpdater> updaterProvider, Provider<PlayerController> controllerProvider,
      Provider<TextProvider> textsProvider) {
    this.repositoryProvider = repositoryProvider;
    this.updaterProvider = updaterProvider;
    this.controllerProvider = controllerProvider;
    this.textsProvider = textsProvider;
  }

  @Override
  public DiscoveryViewModel get() {
    return newInstance(repositoryProvider.get(), updaterProvider.get(), controllerProvider.get(), textsProvider.get());
  }

  public static DiscoveryViewModel_Factory create(Provider<DiscoveryRepository> repositoryProvider,
      Provider<DiscoveryUpdater> updaterProvider, Provider<PlayerController> controllerProvider,
      Provider<TextProvider> textsProvider) {
    return new DiscoveryViewModel_Factory(repositoryProvider, updaterProvider, controllerProvider, textsProvider);
  }

  public static DiscoveryViewModel newInstance(DiscoveryRepository repository,
      DiscoveryUpdater updater, PlayerController controller, TextProvider texts) {
    return new DiscoveryViewModel(repository, updater, controller, texts);
  }
}
