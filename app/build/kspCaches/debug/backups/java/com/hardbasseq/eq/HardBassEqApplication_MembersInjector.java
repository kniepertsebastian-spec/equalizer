package com.hardbasseq.eq;

import com.hardbasseq.eq.auto.AppAutoCatalog;
import com.hardbasseq.eq.service.AppVisibility;
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

  private final Provider<AppVisibility> appVisibilityProvider;

  private HardBassEqApplication_MembersInjector(Provider<AppAutoCatalog> autoCatalogProvider,
      Provider<AppVisibility> appVisibilityProvider) {
    this.autoCatalogProvider = autoCatalogProvider;
    this.appVisibilityProvider = appVisibilityProvider;
  }

  @Override
  public void injectMembers(HardBassEqApplication instance) {
    injectAutoCatalog(instance, autoCatalogProvider.get());
    injectAppVisibility(instance, appVisibilityProvider.get());
  }

  public static MembersInjector<HardBassEqApplication> create(
      Provider<AppAutoCatalog> autoCatalogProvider, Provider<AppVisibility> appVisibilityProvider) {
    return new HardBassEqApplication_MembersInjector(autoCatalogProvider, appVisibilityProvider);
  }

  @InjectedFieldSignature("com.hardbasseq.eq.HardBassEqApplication.autoCatalog")
  public static void injectAutoCatalog(HardBassEqApplication instance, AppAutoCatalog autoCatalog) {
    instance.autoCatalog = autoCatalog;
  }

  @InjectedFieldSignature("com.hardbasseq.eq.HardBassEqApplication.appVisibility")
  public static void injectAppVisibility(HardBassEqApplication instance,
      AppVisibility appVisibility) {
    instance.appVisibility = appVisibility;
  }
}
