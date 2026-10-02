import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { authGuard } from './core/services/oath/oath.guard';

export const routes: Routes = [
    {
    path: '',
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  // Catch-all route to redirect unknown paths back to root
  {
    path: '**',
    redirectTo: ''
  }
];
