export type ItemKind = 'lost' | 'found'

export type ItemStatus = 'open' | 'matched' | 'closed'

export interface LostItem {
  id: string
  kind: ItemKind
  title: string
  category: string
  location: string
  happenedAt: string
  description: string
  publisher: string
  status: ItemStatus
  contactHint: string
}

export const itemKindLabels: Record<ItemKind, string> = {
  lost: '寻物',
  found: '招领',
}

export const itemStatusLabels: Record<ItemStatus, string> = {
  open: '待认领',
  matched: '核实中',
  closed: '已归还',
}

