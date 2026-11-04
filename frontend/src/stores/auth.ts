import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchCurrentUser,
  login as loginRequest,
  logoutSession,
  refreshSession,
  updateCurrentUser,
} from '@/api/auth'
import { setAccessToken } from '@/api/http'
import type { AuthSession, LoginInput, UserProfile, UserProfileUpdate } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserProfile | null>(null)
  const initialized = ref(false)
  const busy = ref(false)
  const loginOpen = ref(false)
  let bootstrapTask: Promise<void> | null = null

  const displayName = computed(() => {
    if (user.value?.realName) {
      return user.value.realName
    }
    return user.value ? `校园用户 ${user.value.publicId.slice(-4)}` : '未登录'
  })

  function applySession(session: AuthSession): void {
    setAccessToken(session.accessToken)
    user.value = session.user
  }

  function clearSession(): void {
    setAccessToken('')
    user.value = null
  }

  function openLogin(): void {
    loginOpen.value = true
  }

  function closeLogin(): void {
    loginOpen.value = false
  }

  async function bootstrap(): Promise<void> {
    if (initialized.value) {
      return
    }
    if (bootstrapTask) {
      return bootstrapTask
    }
    bootstrapTask = (async () => {
      try {
        applySession(await refreshSession())
      } catch {
        clearSession()
      } finally {
        initialized.value = true
        bootstrapTask = null
      }
    })()
    return bootstrapTask
  }

  async function login(input: LoginInput): Promise<AuthSession> {
    busy.value = true
    try {
      const session = await loginRequest(input)
      applySession(session)
      initialized.value = true
      return session
    } finally {
      busy.value = false
    }
  }

  async function refreshUser(): Promise<void> {
    user.value = await fetchCurrentUser()
  }

  async function saveProfile(input: UserProfileUpdate): Promise<UserProfile> {
    busy.value = true
    try {
      const updated = await updateCurrentUser(input)
      user.value = updated
      return updated
    } finally {
      busy.value = false
    }
  }

  async function logout(): Promise<void> {
    busy.value = true
    try {
      await logoutSession()
    } finally {
      clearSession()
      busy.value = false
    }
  }

  return {
    user,
    initialized,
    busy,
    loginOpen,
    displayName,
    bootstrap,
    openLogin,
    closeLogin,
    login,
    refreshUser,
    saveProfile,
    logout,
  }
})
