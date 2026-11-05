import { http } from './http'
import type { AdminSummary, AdminUser, AdminUserPage } from '@/types/admin'

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

export interface AdminUserQuery {
  keyword?: string
  status?: string
  page?: number
  size?: number
}

export async function fetchAdminSummary(): Promise<AdminSummary> {
  const { data } = await http.get<ApiEnvelope<AdminSummary>>('/api/v1/admin/summary')
  return unwrap(data)
}

export async function fetchAdminUsers(query: AdminUserQuery): Promise<AdminUserPage> {
  const { data } = await http.get<ApiEnvelope<AdminUserPage>>('/api/v1/admin/users', {
    params: {
      keyword: query.keyword?.trim() || undefined,
      status: query.status || undefined,
      page: query.page ?? 1,
      size: query.size ?? 10,
    },
  })
  return unwrap(data)
}

export async function updateUserStatus(
  publicId: string,
  status: 'ACTIVE' | 'BANNED',
  reason?: string,
): Promise<AdminUser> {
  const { data } = await http.patch<ApiEnvelope<AdminUser>>(
    `/api/v1/admin/users/${encodeURIComponent(publicId)}/status`,
    { status, reason },
  )
  return unwrap(data)
}
