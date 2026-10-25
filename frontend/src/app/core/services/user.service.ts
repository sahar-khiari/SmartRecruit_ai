import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../constants/api-endpoints.constants';
import { SUPPRESS_ERROR_NOTIFICATION, HANDLE_UNAUTHORIZED_INLINE } from '../interceptors/error-notification.context';
import { MessageResponse } from '../models/auth.model';
import { PageResponse } from '../models/page.model';
import { RoleName } from '../models/role.model';
import {
  ChangePasswordRequest,
  UpdateProfileRequest,
  UpdateUserRoleRequest,
  User,
} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  getCurrentUser(silent = true): Observable<User> {
    return this.http.get<User>(
      `${environment.apiUrl}${API_ENDPOINTS.users.me}`,
      this.requestOptions(silent),
    );
  }

  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(
      `${environment.apiUrl}${API_ENDPOINTS.users.me}`,
      request,
      { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) },
    );
  }

  changePassword(request: ChangePasswordRequest): Observable<MessageResponse> {
    const context = new HttpContext()
      .set(SUPPRESS_ERROR_NOTIFICATION, true)
      .set(HANDLE_UNAUTHORIZED_INLINE, true);
    return this.http.put<MessageResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.users.password}`,
      request,
      { context },
    );
  }

  getUsers(
    page: number,
    size: number,
    sort: string,
    silent = true,
  ): Observable<PageResponse<User>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<PageResponse<User>>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.users}`,
      { params, ...this.requestOptions(silent) },
    );
  }

  getUserById(id: number, silent = true): Observable<User> {
    return this.http.get<User>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.userById(id)}`,
      this.requestOptions(silent),
    );
  }

  updateStatus(id: number, enabled: boolean): Observable<User> {
    return this.http.put<User>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.userStatus(id)}`,
      { enabled },
    );
  }

  updateRole(id: number, role: RoleName): Observable<User> {
    return this.http.put<User>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.userRole(id)}`,
      { role } satisfies UpdateUserRoleRequest,
    );
  }

  deleteUser(id: number): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(
      `${environment.apiUrl}${API_ENDPOINTS.admin.userById(id)}`,
    );
  }

  private requestOptions(silent: boolean): { context?: HttpContext } {
    return silent
      ? { context: new HttpContext().set(SUPPRESS_ERROR_NOTIFICATION, true) }
      : {};
  }
}
