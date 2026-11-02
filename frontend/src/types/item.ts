export type ItemKind = 'LOST' | 'FOUND'

export type ItemStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'CLAIMING'
  | 'CLAIMED'
  | 'HANDOVER_FAILED'
  | 'RETURNED'
  | 'EXPIRED'
  | 'CLOSED'

export type ContactMode = 'PUBLIC' | 'AFTER_CLAIM' | 'IN_APP_ONLY'

export interface ItemSummary {
  publicId: string
  type: ItemKind
  title: string
  description: string
  categoryId: number
  categoryName: string
  locationName: string | null
  eventTime: string
  status: ItemStatus
  auditStatus: string
  matchCount: number
  publisherName: string
}

export interface ItemDetail extends ItemSummary {
  locationDetail: string | null
  brand: string | null
  color: string | null
  contactMode: ContactMode
  expireAt: string | null
}

export interface ItemPage {
  list: ItemSummary[]
  nextCursor: string | null
  hasMore: boolean
}

export const itemKindLabels: Record<ItemKind, string> = {
  LOST: '寻物',
  FOUND: '招领',
}

export const itemStatusLabels: Record<ItemStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '待认领',
  CLAIMING: '认领中',
  CLAIMED: '已认领',
  HANDOVER_FAILED: '交接异常',
  RETURNED: '已归还',
  EXPIRED: '已过期',
  CLOSED: '已关闭',
}

export const contactModeLabels: Record<ContactMode, string> = {
  PUBLIC: '公开联系',
  AFTER_CLAIM: '认领后可见',
  IN_APP_ONLY: '仅站内沟通',
}

export function itemStatusTone(status: ItemStatus): string {
  if (status === 'ACTIVE') {
    return 'active'
  }
  if (status === 'CLAIMING' || status === 'CLAIMED') {
    return 'matched'
  }
  if (status === 'HANDOVER_FAILED' || status === 'EXPIRED') {
    return 'bad'
  }
  return 'closed'
}

export function formatEventTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

