package com.hardbasseq.eq;

import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<SessionAttachSpikeController> spikeControllerProvider;

  private MainActivity_MembersInjector(
      Provider<SessionAttachSpikeController> spikeControllerProvider) {
    this.spikeControllerProvider = spikeControllerProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectSpikeController(instance, spikeControllerProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<SessionAttachSpikeController> spikeControllerProvider) {
    return new MainActivity_MembersInjector(spikeControllerProvider);
  }

  @InjectedFieldSignature("com.hardbasseq.eq.MainActivity.spikeController")
  public static void injectSpikeController(MainActivity instance,
      SessionAttachSpikeController spikeController) {
    instance.spikeController = spikeController;
  }
}
