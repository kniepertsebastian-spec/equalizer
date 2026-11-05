package com.hardbasseq.eq.audio;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AndroidAudioEngine_Factory implements Factory<AndroidAudioEngine> {
  private final Provider<CoroutineDispatcher> dispatcherProvider;

  private AndroidAudioEngine_Factory(Provider<CoroutineDispatcher> dispatcherProvider) {
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public AndroidAudioEngine get() {
    return newInstance(dispatcherProvider.get());
  }

  public static AndroidAudioEngine_Factory create(
      Provider<CoroutineDispatcher> dispatcherProvider) {
    return new AndroidAudioEngine_Factory(dispatcherProvider);
  }

  public static AndroidAudioEngine newInstance(CoroutineDispatcher dispatcher) {
    return new AndroidAudioEngine(dispatcher);
  }
}
