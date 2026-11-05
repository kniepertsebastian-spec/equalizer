package com.hardbasseq.eq.auto;

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
public final class AndroidCarConnectionRepository_Factory implements Factory<AndroidCarConnectionRepository> {
  private final Provider<Context> contextProvider;

  private AndroidCarConnectionRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidCarConnectionRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidCarConnectionRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidCarConnectionRepository_Factory(contextProvider);
  }

  public static AndroidCarConnectionRepository newInstance(Context context) {
    return new AndroidCarConnectionRepository(context);
  }
}
