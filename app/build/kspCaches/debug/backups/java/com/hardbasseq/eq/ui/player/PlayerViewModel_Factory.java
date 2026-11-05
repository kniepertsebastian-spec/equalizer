package com.hardbasseq.eq.ui.player;

import com.hardbasseq.eq.integration.PlayerController;
import com.hardbasseq.eq.playlist.PlaylistRepository;
import com.hardbasseq.eq.playlist.SpotifyImportRepository;
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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<PlayerController> controllerProvider;

  private final Provider<PlaylistRepository> playlistRepositoryProvider;

  private final Provider<SpotifyImportRepository> spotifyImportRepositoryProvider;

  private final Provider<TextProvider> textsProvider;

  private PlayerViewModel_Factory(Provider<PlayerController> controllerProvider,
      Provider<PlaylistRepository> playlistRepositoryProvider,
      Provider<SpotifyImportRepository> spotifyImportRepositoryProvider,
      Provider<TextProvider> textsProvider) {
    this.controllerProvider = controllerProvider;
    this.playlistRepositoryProvider = playlistRepositoryProvider;
    this.spotifyImportRepositoryProvider = spotifyImportRepositoryProvider;
    this.textsProvider = textsProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(controllerProvider.get(), playlistRepositoryProvider.get(), spotifyImportRepositoryProvider.get(), textsProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<PlayerController> controllerProvider,
      Provider<PlaylistRepository> playlistRepositoryProvider,
      Provider<SpotifyImportRepository> spotifyImportRepositoryProvider,
      Provider<TextProvider> textsProvider) {
    return new PlayerViewModel_Factory(controllerProvider, playlistRepositoryProvider, spotifyImportRepositoryProvider, textsProvider);
  }

  public static PlayerViewModel newInstance(PlayerController controller,
      PlaylistRepository playlistRepository, SpotifyImportRepository spotifyImportRepository,
      TextProvider texts) {
    return new PlayerViewModel(controller, playlistRepository, spotifyImportRepository, texts);
  }
}
