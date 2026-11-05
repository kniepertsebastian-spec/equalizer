package com.hardbasseq.eq;

import com.hardbasseq.eq.auto.AppAutoCatalog;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class HardBassEqApplication_MembersInjector implements MembersInjector<HardBassEqApplication> {
  private final Provider<AppAutoCatalog> autoCatalogProvider;

  private HardBassEqApplication_MembersInjector(Provider<AppAutoCatalog> autoCatalogProvider) {
    this.autoCatalogProvider = autoCatalogProvider;
  }

  @Override
  public void injectMembers(HardBassEqApplication instance) {
    injectAutoCatalog(instance, autoCatalogProvider.get());
  }

  public static MembersInjector<HardBassEqApplication> create(
      Provider<AppAutoCatalog> autoCatalogProvider) {
    return new HardBassEqApplication_MembersInjector(autoCatalogProvider);
  }

  @InjectedFieldSignature("com.hardbasseq.eq.HardBassEqApplication.autoCatalog")
  public static void injectAutoCatalog(HardBassEqApplication instance, AppAutoCatalog autoCatalog) {
    instance.autoCatalog = autoCatalog;
  }
}
