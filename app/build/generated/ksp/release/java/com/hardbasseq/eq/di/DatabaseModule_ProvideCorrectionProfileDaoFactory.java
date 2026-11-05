package com.hardbasseq.eq.di;

import com.hardbasseq.eq.data.db.AppDatabase;
import com.hardbasseq.eq.data.db.CorrectionProfileDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
public final class DatabaseModule_ProvideCorrectionProfileDaoFactory implements Factory<CorrectionProfileDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvideCorrectionProfileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public CorrectionProfileDao get() {
    return provideCorrectionProfileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideCorrectionProfileDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideCorrectionProfileDaoFactory(dbProvider);
  }

  public static CorrectionProfileDao provideCorrectionProfileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCorrectionProfileDao(db));
  }
}
