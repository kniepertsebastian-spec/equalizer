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
public final class AndroidSystemVolumeRepository_Factory implements Factory<AndroidSystemVolumeRepository> {
  private final Provider<Context> contextProvider;

  private AndroidSystemVolumeRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidSystemVolumeRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidSystemVolumeRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidSystemVolumeRepository_Factory(contextProvider);
  }

  public static AndroidSystemVolumeRepository newInstance(Context context) {
    return new AndroidSystemVolumeRepository(context);
  }
}
