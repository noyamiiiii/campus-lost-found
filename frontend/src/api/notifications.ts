import { http } from './http'
import type { NotificationView } from '@/types/notification'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '操作失败，请重试')
  }
  return envelope.data
}

export async function fetchNotifications(): Promise<NotificationView[]> {
  const { data } = await http.get<ApiEnvelope<NotificationView[]>>('/api/v1/notifications')
  return unwrap(data)
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await http.get<ApiEnvelope<{ count: number }>>('/api/v1/notifications/unread-count')
  return unwrap(data).count
}

export async function markNotificationRead(id: number): Promise<void> {
  await http.post(`/api/v1/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await http.post('/api/v1/notifications/read-all')
}