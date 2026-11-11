export type ClaimStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELED' | 'COMPLETED'

export interface ClaimView {
  publicId: string
  postPublicId: string
  postTitle: string
  postStatus: string
  applicantPublicId: string | null
  applicantName: string | null
  ownerPublicId: string | null
  ownerName: string | null
  status: ClaimStatus
  message: string | null
  contact: string | null
  evidenceFileId: number | null
  confirmedAt: string | null
  completedAt: string | null
  canceledAt: string | null
  createdAt: string
}

export interface ClaimCreateInput {
  message: string
  contact?: string | null
  evidenceFileId?: number | null
}

export const claimStatusLabels: Record<ClaimStatus, string> = {
  PENDING: '待处理',
  CONFIRMED: '已同意，待交接',
  REJECTED: '已拒绝',
  CANCELED: '已撤回',
  COMPLETED: '已完成交接',
}

export function claimStatusTone(status: ClaimStatus): string {
  if (status === 'PENDING') {
    return 'active'
  }
  if (status === 'CONFIRMED' || status === 'COMPLETED') {
    return 'matched'
  }
  if (status === 'REJECTED') {
    return 'bad'
  }
  return 'closed'
}

export function formatClaimTime(value: string | null): string {
  if (!value) {
    return '—'
  }
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