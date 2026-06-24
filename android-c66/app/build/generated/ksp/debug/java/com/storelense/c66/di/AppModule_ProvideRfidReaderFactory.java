package com.storelense.c66.di;

import com.storelense.c66.rfid.C66RfidReader;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
    "cast"
})
public final class AppModule_ProvideRfidReaderFactory implements Factory<C66RfidReader> {
  @Override
  public C66RfidReader get() {
    return provideRfidReader();
  }

  public static AppModule_ProvideRfidReaderFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static C66RfidReader provideRfidReader() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRfidReader());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideRfidReaderFactory INSTANCE = new AppModule_ProvideRfidReaderFactory();
  }
}
