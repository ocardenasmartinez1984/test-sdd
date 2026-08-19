export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  enabled: boolean;
  createdAt: string;
  roles: string[];
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
  roles: string[];
}

export interface UpdateUserRequest {
  email?: string;
  fullName?: string;
  enabled?: boolean;
  password?: string;
  roles?: string[];
}
