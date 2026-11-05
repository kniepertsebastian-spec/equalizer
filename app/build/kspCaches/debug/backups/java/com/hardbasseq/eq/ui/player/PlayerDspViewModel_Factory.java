package com.hardbasseq.eq.ui.player;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class PlayerDspViewModel_Factory implements Factory<PlayerDspViewModel> {
  private final Provider<Context> contextProvider;

  private PlayerDspViewModel_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PlayerDspViewModel get() {
    return newInstance(contextProvider.get());
  }

  public static PlayerDspViewModel_Factory create(Provider<Context> contextProvider) {
    return new PlayerDspViewModel_Factory(contextProvider);
  }

  public static PlayerDspViewModel newInstance(Context context) {
    return new PlayerDspViewModel(context);
  }
}
