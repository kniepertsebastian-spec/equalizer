package com.hardbasseq.eq.diagnostics;

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
public final class InMemoryDiagnosticsRecorder_Factory implements Factory<InMemoryDiagnosticsRecorder> {
  @Override
  public InMemoryDiagnosticsRecorder get() {
    return newInstance();
  }

  public static InMemoryDiagnosticsRecorder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InMemoryDiagnosticsRecorder newInstance() {
    return new InMemoryDiagnosticsRecorder();
  }

  private static final class InstanceHolder {
    static final InMemoryDiagnosticsRecorder_Factory INSTANCE = new InMemoryDiagnosticsRecorder_Factory();
  }
}
