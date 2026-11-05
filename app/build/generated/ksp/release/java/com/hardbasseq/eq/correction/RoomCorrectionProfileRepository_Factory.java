package com.hardbasseq.eq.correction;

import com.hardbasseq.eq.data.db.CorrectionProfileDao;
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
public final class RoomCorrectionProfileRepository_Factory implements Factory<RoomCorrectionProfileRepository> {
  private final Provider<CorrectionProfileDao> daoProvider;

  private RoomCorrectionProfileRepository_Factory(Provider<CorrectionProfileDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomCorrectionProfileRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomCorrectionProfileRepository_Factory create(
      Provider<CorrectionProfileDao> daoProvider) {
    return new RoomCorrectionProfileRepository_Factory(daoProvider);
  }

  public static RoomCorrectionProfileRepository newInstance(CorrectionProfileDao dao) {
    return new RoomCorrectionProfileRepository(dao);
  }
}
