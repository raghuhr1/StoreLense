package com.storelense.c66.ui.login;

import com.storelense.c66.data.repository.AuthRepository;
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
public final class LoginViewModel_Factory implements Factory<LoginViewModel> {
  private final Provider<AuthRepository> authProvider;

  public LoginViewModel_Factory(Provider<AuthRepository> authProvider) {
    this.authProvider = authProvider;
  }

  @Override
  public LoginViewModel get() {
    return newInstance(authProvider.get());
  }

  public static LoginViewModel_Factory create(Provider<AuthRepository> authProvider) {
    return new LoginViewModel_Factory(authProvider);
  }

  public static LoginViewModel newInstance(AuthRepository auth) {
    return new LoginViewModel(auth);
  }
}
