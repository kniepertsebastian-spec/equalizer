package com.hardbasseq.eq.audio.spike;

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
public final class SessionAttachSpikeController_Factory implements Factory<SessionAttachSpikeController> {
  private final Provider<Context> contextProvider;

  private SessionAttachSpikeController_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SessionAttachSpikeController get() {
    return newInstance(contextProvider.get());
  }

  public static SessionAttachSpikeController_Factory create(Provider<Context> contextProvider) {
    return new SessionAttachSpikeController_Factory(contextProvider);
  }

  public static SessionAttachSpikeController newInstance(Context context) {
    return new SessionAttachSpikeController(context);
  }
}
