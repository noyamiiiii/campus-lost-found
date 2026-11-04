import { http } from './http'
import type {
  AuthSession,
  LoginInput,
  SendCodeData,
  SendCodeInput,
  UserProfile,
  UserProfileUpdate,
} from '@/types/auth'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '请求未成功')
  }
  return envelope.data
}

export async function sendLoginCode(input: SendCodeInput): Promise<SendCodeData> {
  const { data } = await http.post<ApiEnvelope<SendCodeData>>('/api/v1/auth/sms-code', input)
  return unwrap(data)
}

export async function login(input: LoginInput): Promise<AuthSession> {
  const { data } = await http.post<ApiEnvelope<AuthSession>>('/api/v1/auth/login', input)
  return unwrap(data)
}

export async function refreshSession(): Promise<AuthSession> {
  const { data } = await http.post<ApiEnvelope<AuthSession>>('/api/v1/auth/refresh')
  return unwrap(data)
}

export async function logoutSession(): Promise<void> {
  const { data } = await http.post<ApiEnvelope<null>>('/api/v1/auth/logout')
  unwrap(data)
}

export async function fetchCurrentUser(): Promise<UserProfile> {
  const { data } = await http.get<ApiEnvelope<UserProfile>>('/api/v1/users/me')
  return unwrap(data)
}

export async function updateCurrentUser(input: UserProfileUpdate): Promise<UserProfile> {
  const { data } = await http.put<ApiEnvelope<UserProfile>>('/api/v1/users/me', input)
  return unwrap(data)
}
