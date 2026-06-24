package com.storelense.c66.di;

import com.storelense.c66.data.remote.AuthInterceptor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class AppModule_ProvideOkHttpFactory implements Factory<OkHttpClient> {
  private final Provider<AuthInterceptor> authProvider;

  public AppModule_ProvideOkHttpFactory(Provider<AuthInterceptor> authProvider) {
    this.authProvider = authProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideOkHttp(authProvider.get());
  }

  public static AppModule_ProvideOkHttpFactory create(Provider<AuthInterceptor> authProvider) {
    return new AppModule_ProvideOkHttpFactory(authProvider);
  }

  public static OkHttpClient provideOkHttp(AuthInterceptor auth) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideOkHttp(auth));
  }
}
