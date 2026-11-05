package com.hardbasseq.eq.auto;

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
public final class AutoBrowserService_MembersInjector implements MembersInjector<AutoBrowserService> {
  private final Provider<AppAutoCatalog> catalogProvider;

  private AutoBrowserService_MembersInjector(Provider<AppAutoCatalog> catalogProvider) {
    this.catalogProvider = catalogProvider;
  }

  @Override
  public void injectMembers(AutoBrowserService instance) {
    injectCatalog(instance, catalogProvider.get());
  }

  public static MembersInjector<AutoBrowserService> create(
      Provider<AppAutoCatalog> catalogProvider) {
    return new AutoBrowserService_MembersInjector(catalogProvider);
  }

  @InjectedFieldSignature("com.hardbasseq.eq.auto.AutoBrowserService.catalog")
  public static void injectCatalog(AutoBrowserService instance, AppAutoCatalog catalog) {
    instance.catalog = catalog;
  }
}
