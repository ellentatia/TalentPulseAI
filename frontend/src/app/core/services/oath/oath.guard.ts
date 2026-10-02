import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';

export const authGuard: CanActivateFn = async (route, state) => {
  const keycloak = inject(KeycloakService);
  const router = inject(Router);

  // Check if the user is authenticated via Keycloak
  const isLoggedIn = await keycloak.isLoggedIn();

  if (!isLoggedIn) {
    // Redirect to Keycloak Login page
    await keycloak.login({
      redirectUri: window.location.origin + state.url
    });
    return false;
  }

  // Optional: Role-based access control
  const requiredRoles = route.data['roles'] as Array<string>;
  if (!requiredRoles || requiredRoles.length === 0) {
    return true;
  }

  const userRoles = keycloak.getUserRoles();
  const hasRequiredRole = requiredRoles.every(role => userRoles.includes(role));

  if (!hasRequiredRole) {
    // Redirect or deny access if missing roles
    console.warn('User does not have required roles:', requiredRoles);
    return false;
  }

  return true;
};