package com.hardbasseq.eq.service;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AppVisibility_Factory implements Factory<AppVisibility> {
  @Override
  public AppVisibility get() {
    return newInstance();
  }

  public static AppVisibility_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AppVisibility newInstance() {
    return new AppVisibility();
  }

  private static final class InstanceHolder {
    static final AppVisibility_Factory INSTANCE = new AppVisibility_Factory();
  }
}
