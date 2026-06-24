package com.storelense.c66.di;

import com.google.gson.Gson;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class AppModule_ProvideRetrofitFactory implements Factory<Retrofit> {
  private final Provider<OkHttpClient> okHttpProvider;

  private final Provider<Gson> gsonProvider;

  public AppModule_ProvideRetrofitFactory(Provider<OkHttpClient> okHttpProvider,
      Provider<Gson> gsonProvider) {
    this.okHttpProvider = okHttpProvider;
    this.gsonProvider = gsonProvider;
  }

  @Override
  public Retrofit get() {
    return provideRetrofit(okHttpProvider.get(), gsonProvider.get());
  }

  public static AppModule_ProvideRetrofitFactory create(Provider<OkHttpClient> okHttpProvider,
      Provider<Gson> gsonProvider) {
    return new AppModule_ProvideRetrofitFactory(okHttpProvider, gsonProvider);
  }

  public static Retrofit provideRetrofit(OkHttpClient okHttp, Gson gson) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRetrofit(okHttp, gson));
  }
}
