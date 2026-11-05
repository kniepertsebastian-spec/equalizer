package com.hardbasseq.eq.profile;

import com.hardbasseq.eq.data.db.DeviceProfileDao;
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
public final class RoomDeviceProfileRepository_Factory implements Factory<RoomDeviceProfileRepository> {
  private final Provider<DeviceProfileDao> daoProvider;

  private RoomDeviceProfileRepository_Factory(Provider<DeviceProfileDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomDeviceProfileRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomDeviceProfileRepository_Factory create(Provider<DeviceProfileDao> daoProvider) {
    return new RoomDeviceProfileRepository_Factory(daoProvider);
  }

  public static RoomDeviceProfileRepository newInstance(DeviceProfileDao dao) {
    return new RoomDeviceProfileRepository(dao);
  }
}
