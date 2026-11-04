<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoaderCircle, LockKeyhole, Phone, ShieldCheck } from 'lucide-vue-next'
import { sendLoginCode } from '@/api/auth'
import { toRequestMessage } from '@/api/posts'
import { useAuthStore } from '@/stores/auth'
import type { AuthMode } from '@/types/auth'

const auth = useAuthStore()
const router = useRouter()

const DEMO_STUDENT_NO = '20260001'
const DEMO_PHONE = '13800000001'

const mode = ref<AuthMode>('login')
const studentNo = ref(DEMO_STUDENT_NO)
const phone = ref(DEMO_PHONE)
const code = ref('')
const sending = ref(false)
const codeSent = ref(false)
const countdown = ref(0)
const error = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const dialogVisible = computed({
  get: () => auth.loginOpen,
  set: (value: boolean) => {
    if (value) {
      auth.openLogin()
    } else {
      auth.closeLogin()
    }
  },
})

const codeButtonLabel = computed(() => {
  if (sending.value) {
    return '发送中'
  }
  if (countdown.value > 0) {
    return `${countdown.value}s 后重发`
  }
  return codeSent.value ? '重新获取' : '获取验证码'
})

const dialogTitle = computed(() => (mode.value === 'login' ? '登录校园失物招领' : '注册校园账号'))
const submitLabel = computed(() => {
  if (auth.busy) {
    return mode.value === 'login' ? '正在验证' : '正在注册'
  }
  return mode.value === 'login' ? '登录' : '注册并登录'
})

function switchMode(next: AuthMode): void {
  if (auth.busy || mode.value === next) {
    return
  }
  mode.value = next
  auth.setLoginMode(next)
  clearDemoAccountForRegister(next)
  error.value = ''
  code.value = ''
}

function clearDemoAccountForRegister(next: AuthMode): void {
  if (next === 'register' && studentNo.value === DEMO_STUDENT_NO && phone.value === DEMO_PHONE) {
    studentNo.value = ''
    phone.value = ''
  }
}

function startCountdown(seconds: number): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = seconds
  countdownTimer = setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1)
    if (countdown.value === 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

function validateAccount(): boolean {
  if (!/^[A-Za-z0-9]{4,24}$/.test(studentNo.value.trim())) {
    error.value = '请输入 4 到 24 位字母或数字组成的学号'
    return false
  }
  if (!/^1[3-9][0-9]{9}$/.test(phone.value.trim())) {
    error.value = '请输入有效的 11 位手机号'
    return false
  }
  return true
}

async function requestCode(): Promise<void> {
  error.value = ''
  if (!validateAccount() || countdown.value > 0) {
    return
  }
  sending.value = true
  try {
    const result = await sendLoginCode({
      studentNo: studentNo.value.trim(),
      phone: phone.value.trim(),
    })
    codeSent.value = true
    startCountdown(result.retryAfterSeconds)
    if (result.debugCode) {
      code.value = result.debugCode
    }
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  } finally {
    sending.value = false
  }
}

async function submit(): Promise<void> {
  error.value = ''
  if (!validateAccount()) {
    return
  }
  if (!/^[0-9]{6}$/.test(code.value.trim())) {
    error.value = '请输入 6 位数字验证码'
    return
  }
  try {
    const input = {
      studentNo: studentNo.value.trim(),
      phone: phone.value.trim(),
      code: code.value.trim(),
    }
    const session = mode.value === 'register' ? await auth.register(input) : await auth.login(input)
    auth.closeLogin()
    ElMessage.success(mode.value === 'register' ? '注册成功，请完善个人资料' : '登录成功')
    if (session.needsProfileCompletion) {
      await router.push('/profile')
    }
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  }
}

watch(
  () => auth.loginOpen,
  (open) => {
    if (open) {
      error.value = ''
      if (mode.value === 'login' && !studentNo.value && !phone.value) {
        studentNo.value = DEMO_STUDENT_NO
        phone.value = DEMO_PHONE
      }
    }
  },
)

watch(
  () => auth.loginMode,
  (value) => {
    mode.value = value
    clearDemoAccountForRegister(value)
    if (value === 'login' && !studentNo.value && !phone.value) {
      studentNo.value = DEMO_STUDENT_NO
      phone.value = DEMO_PHONE
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="430px" align-center>
    <div class="login-dialog">
      <div class="auth-tabs" role="tablist" aria-label="登录或注册">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ 'is-active': mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ 'is-active': mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <p class="login-dialog__lead">
        {{
          mode === 'login'
            ? '使用学号和手机号验证身份，登录已有账号。'
            : '使用学号和手机号创建账号，注册后自动登录并完善资料。'
        }}
      </p>

      <div v-if="mode === 'login'" class="demo-account">
        <div>
          <span>本地演示账号</span>
          <strong>20260001</strong>
        </div>
        <div>
          <span>手机号</span>
          <strong>13800000001</strong>
        </div>
        <div>
          <span>固定验证码</span>
          <strong>123456</strong>
        </div>
      </div>

      <div v-else class="register-hint">
        <ShieldCheck :size="15" />
        <span>本地演示阶段验证码固定为 <strong>123456</strong>，真实短信接入后会改为手机接收。</span>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label class="form-field">
          <span>学号</span>
          <el-input v-model="studentNo" maxlength="24" autocomplete="username" placeholder="请输入学号" />
        </label>

        <label class="form-field">
          <span>手机号</span>
          <el-input v-model="phone" maxlength="11" autocomplete="tel" placeholder="请输入手机号">
            <template #prefix><Phone :size="15" /></template>
          </el-input>
        </label>

        <label class="form-field">
          <span>验证码</span>
          <div class="code-row">
            <el-input v-model="code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码">
              <template #prefix><LockKeyhole :size="15" /></template>
            </el-input>
            <button type="button" class="code-button" :disabled="sending || countdown > 0" @click="requestCode">
              <LoaderCircle v-if="sending" :size="14" class="spin" />
              {{ codeButtonLabel }}
            </button>
          </div>
        </label>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <button type="submit" class="btn btn--primary login-submit" :disabled="auth.busy">
          <LoaderCircle v-if="auth.busy" :size="16" class="spin" />
          <ShieldCheck v-else :size="16" />
          {{ submitLabel }}
        </button>
      </form>

      <p class="auth-switch">
        <template v-if="mode === 'login'">
          还没有账号？
          <button type="button" @click="switchMode('register')">立即注册</button>
        </template>
        <template v-else>
          已有账号？
          <button type="button" @click="switchMode('login')">返回登录</button>
        </template>
      </p>
    </div>
  </el-dialog>
</template>
