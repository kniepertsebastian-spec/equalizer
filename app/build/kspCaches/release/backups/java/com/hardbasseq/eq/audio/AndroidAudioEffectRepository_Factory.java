package com.hardbasseq.eq.audio;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AndroidAudioEffectRepository_Factory implements Factory<AndroidAudioEffectRepository> {
  @Override
  public AndroidAudioEffectRepository get() {
    return newInstance();
  }

  public static AndroidAudioEffectRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AndroidAudioEffectRepository newInstance() {
    return new AndroidAudioEffectRepository();
  }

  private static final class InstanceHolder {
    static final AndroidAudioEffectRepository_Factory INSTANCE = new AndroidAudioEffectRepository_Factory();
  }
}
