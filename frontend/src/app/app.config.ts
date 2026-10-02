import { ApplicationConfig, APP_INITIALIZER, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { KeycloakService, KeycloakBearerInterceptor } from 'keycloak-angular';
import { routes } from './app.routes';
import { environment } from '../environments/environment.development';

// Factory function to initialize Keycloak before Angular app boots
function initializeKeycloak(keycloak: KeycloakService) {
  return () =>
    keycloak.init({
      config: {
        url: environment.keycloak.url,       // Your Keycloak server URL
        realm: environment.keycloak.realm,          // Your Keycloak realm
        clientId: environment.keycloak.clientId         // Your Keycloak client ID
      },
      initOptions: {
        onLoad: 'login-required'
      },
      enableBearerInterceptor: true,
      bearerPrefix: 'Bearer'
    });
}

export const appConfig: ApplicationConfig = {
  providers: [
    // 1. Angular Default Providers
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),

    // 2. HTTP Client Configuration
    provideHttpClient(withInterceptorsFromDi()),

    // 3. Keycloak Auth & Interceptor Setup
    KeycloakService,
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      multi: true,
      deps: [KeycloakService]
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: KeycloakBearerInterceptor,
      multi: true
    }
  ]
};
