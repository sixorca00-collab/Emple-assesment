// usuario tal cual lo devuelve GET /users (SP rw_search_users)
export interface AdminUser {
  id: string;
  displayName: string;
  jobTitle: string;
  avatarUrl: string | null;
  isActive: boolean;
  createdAt: string;
}

// cuerpo de PATCH /users/{id}; los campos nulos dejan el valor actual
export interface UpdateUserPayload {
  displayName?: string;
  jobTitle?: string;
  bio?: string;
  isActive?: boolean;
}
