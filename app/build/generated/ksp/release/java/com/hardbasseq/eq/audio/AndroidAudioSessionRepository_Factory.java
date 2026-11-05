package com.hardbasseq.eq.audio;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AndroidAudioSessionRepository_Factory implements Factory<AndroidAudioSessionRepository> {
  private final Provider<Context> contextProvider;

  private AndroidAudioSessionRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidAudioSessionRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidAudioSessionRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidAudioSessionRepository_Factory(contextProvider);
  }

  public static AndroidAudioSessionRepository newInstance(Context context) {
    return new AndroidAudioSessionRepository(context);
  }
}
