export interface AdminSummary {
  totalUsers: number
  activeUsers: number
  bannedUsers: number
  adminUsers: number
  totalPosts: number
  activePosts: number
}

export interface AdminUser {
  publicId: string
  studentNo: string | null
  realName: string | null
  phoneMasked: string | null
  dept: string | null
  role: string
  status: string
  postCount: number
  createdAt: string
  lastLoginAt: string | null
}

export interface AdminUserPage {
  list: AdminUser[]
  total: number
  page: number
  size: number
}

export const roleLabels: Record<string, string> = {
  STUDENT: '学生',
  SERVICE_ADMIN: '服务点管理员',
  ADMIN: '系统管理员',
}

export const accountStatusLabels: Record<string, string> = {
  ACTIVE: '正常',
  BANNED: '已封禁',
  DELETED: '已注销',
}
