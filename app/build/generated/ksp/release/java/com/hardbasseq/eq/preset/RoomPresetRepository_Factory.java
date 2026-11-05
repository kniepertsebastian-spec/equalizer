package com.hardbasseq.eq.preset;

import com.hardbasseq.eq.data.db.PresetDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
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
public final class RoomPresetRepository_Factory implements Factory<RoomPresetRepository> {
  private final Provider<PresetDao> daoProvider;

  private RoomPresetRepository_Factory(Provider<PresetDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomPresetRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomPresetRepository_Factory create(Provider<PresetDao> daoProvider) {
    return new RoomPresetRepository_Factory(daoProvider);
  }

  public static RoomPresetRepository newInstance(PresetDao dao) {
    return new RoomPresetRepository(dao);
  }
}
