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
  const roles = user?.roles || []
  if (roles.includes('ADMIN')) return 'ADMIN'
  if (roles.includes('ACCOUNTANT')) return 'ACCOUNTANT'
  if (roles.includes('EMPLOYEE')) return 'EMPLOYEE'
  if (user?.role === 'USER') return 'EMPLOYEE'
  return user?.role || null
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
    return error.response?.data?.message || fallback
  }

  return fallback
}
