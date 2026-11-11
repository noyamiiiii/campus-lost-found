export interface NotificationView {
  id: number
  category: 'MATCH' | 'CLAIM' | 'AUDIT' | 'SYSTEM'
  title: string
  content: string
  refType: string | null
  refId: number | null
  read: boolean
  createdAt: string
}