package com.storelense.c66.ui.gate;

import com.google.gson.Gson;
import com.storelense.c66.data.repository.AuthRepository;
import com.storelense.c66.data.repository.GateRepository;
import com.storelense.c66.rfid.C66RfidReader;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "cast"
})
public final class GateScanViewModel_Factory implements Factory<GateScanViewModel> {
  private final Provider<GateRepository> gateRepoProvider;

  private final Provider<AuthRepository> authRepoProvider;

  private final Provider<C66RfidReader> rfidProvider;

  private final Provider<Gson> gsonProvider;

  public GateScanViewModel_Factory(Provider<GateRepository> gateRepoProvider,
      Provider<AuthRepository> authRepoProvider, Provider<C66RfidReader> rfidProvider,
      Provider<Gson> gsonProvider) {
    this.gateRepoProvider = gateRepoProvider;
    this.authRepoProvider = authRepoProvider;
    this.rfidProvider = rfidProvider;
    this.gsonProvider = gsonProvider;
  }

  @Override
  public GateScanViewModel get() {
    return newInstance(gateRepoProvider.get(), authRepoProvider.get(), rfidProvider.get(), gsonProvider.get());
  }

  public static GateScanViewModel_Factory create(Provider<GateRepository> gateRepoProvider,
      Provider<AuthRepository> authRepoProvider, Provider<C66RfidReader> rfidProvider,
      Provider<Gson> gsonProvider) {
    return new GateScanViewModel_Factory(gateRepoProvider, authRepoProvider, rfidProvider, gsonProvider);
  }

  public static GateScanViewModel newInstance(GateRepository gateRepo, AuthRepository authRepo,
      C66RfidReader rfid, Gson gson) {
    return new GateScanViewModel(gateRepo, authRepo, rfid, gson);
  }
}
