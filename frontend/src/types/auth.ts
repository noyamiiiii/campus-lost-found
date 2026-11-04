export interface UserProfile {
  publicId: string
  studentNo: string
  realName: string | null
  phoneMasked: string
  dept: string | null
  dorm: string | null
  wechat: string | null
  role: 'STUDENT' | 'SERVICE_ADMIN' | 'ADMIN'
  status: 'ACTIVE' | 'BANNED' | 'DELETED'
  profileComplete: boolean
  phoneVerified: boolean
}

export interface AuthSession {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserProfile
  needsProfileCompletion: boolean
}

export interface SendCodeData {
  expiresInSeconds: number
  retryAfterSeconds: number
  debugCode: string | null
}

export interface SendCodeInput {
  studentNo: string
  phone: string
}

export interface LoginInput extends SendCodeInput {
  code: string
}

export interface UserProfileUpdate {
  realName: string
  dept: string
  dorm: string | null
  wechat: string | null
}
