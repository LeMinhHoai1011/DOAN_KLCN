import axios from 'axios'
import api from './api'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export interface User {
  id: number
  username: string
  fullName: string
  email: string
  phone?: string | null
  avatar?: string | null
  role?: 'ADMIN' | 'ACCOUNTANT' | 'EMPLOYEE' | 'USER' | string
  roles?: string[]
  companyId?: number | null
  status?: string
  createdAt?: string
  updatedAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  user: User
}

export interface RegisterRequest {
  username: string
  password: string
  fullName: string
  email: string
  phone?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export const login = async (credentials: LoginRequest) => {
  const { data } = await api.post<LoginResponse>('/api/v1/auth/login', credentials)
  saveToken(data.token)
  const user = await getMe()
  saveUser(user)
  window.dispatchEvent(new Event('auth-changed'))
  return { ...data, user }
}

export const getMe = async () => {
  const { data } = await api.get<User>('/api/v1/users/me')
  return data
}

export const changePassword = async (request: ChangePasswordRequest) => {
  await api.put('/api/v1/users/me/password', request)
}

export const register = async (request: RegisterRequest) => {
  const { data } = await api.post<User>('/api/v1/auth/register', request)
  return data
}

export const logout = () => {
  removeToken()
  localStorage.removeItem(USER_KEY)
  window.dispatchEvent(new Event('auth-changed'))
}

export const getEffectiveRole = (user: User | null = getCurrentUser()) => {
  const normalizeRole = (value: string | undefined) => {
    return value?.trim().toUpperCase().replace(/^ROLE_/, '')
  }
  const roleCodes = [...(user?.roles || []).map(normalizeRole), normalizeRole(user?.role)]

  for (const role of ['ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER']) {
    if (roleCodes.includes(role)) return role
  }
  return null
}

export const getDashboardPath = (user: User | null = getCurrentUser()) => {
  const role = getEffectiveRole(user)
  if (role === 'ADMIN') return '/admin/dashboard'
  if (role === 'ACCOUNTANT') return '/accountant/dashboard'
  if (role === 'EMPLOYEE' || role === 'USER') return '/employee/dashboard'
  return null
}

export const getToken = () => localStorage.getItem(TOKEN_KEY)

export const saveToken = (token: string) => {
  localStorage.setItem(TOKEN_KEY, token)
}

export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY)
}

export const getCurrentUser = (): User | null => {
  const storedUser = localStorage.getItem(USER_KEY)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser) as User
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const saveUser = (user: User) => {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export const getErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    if (error.response?.status === 403) {
      return 'Bạn không có quyền thực hiện thao tác này.'
    }
    return error.response?.data?.message || fallback
  }

  return fallback
}
