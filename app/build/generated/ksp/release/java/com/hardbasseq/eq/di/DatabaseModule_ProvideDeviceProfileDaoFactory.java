package com.hardbasseq.eq.di;

import com.hardbasseq.eq.data.db.AppDatabase;
import com.hardbasseq.eq.data.db.DeviceProfileDao;
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
public final class DatabaseModule_ProvideDeviceProfileDaoFactory implements Factory<DeviceProfileDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvideDeviceProfileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DeviceProfileDao get() {
    return provideDeviceProfileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideDeviceProfileDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideDeviceProfileDaoFactory(dbProvider);
  }

  public static DeviceProfileDao provideDeviceProfileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDeviceProfileDao(db));
  }
}
