package com.hardbasseq.eq.discovery;

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
public final class DiscoveryUpdater_Factory implements Factory<DiscoveryUpdater> {
  private final Provider<DiscoveryRepository> repositoryProvider;

  private final Provider<DiscoverySource> sourceProvider;

  private DiscoveryUpdater_Factory(Provider<DiscoveryRepository> repositoryProvider,
      Provider<DiscoverySource> sourceProvider) {
    this.repositoryProvider = repositoryProvider;
    this.sourceProvider = sourceProvider;
  }

  @Override
  public DiscoveryUpdater get() {
    return newInstance(repositoryProvider.get(), sourceProvider.get());
  }

  public static DiscoveryUpdater_Factory create(Provider<DiscoveryRepository> repositoryProvider,
      Provider<DiscoverySource> sourceProvider) {
    return new DiscoveryUpdater_Factory(repositoryProvider, sourceProvider);
  }

  public static DiscoveryUpdater newInstance(DiscoveryRepository repository,
      DiscoverySource source) {
    return new DiscoveryUpdater(repository, source);
  }
}
