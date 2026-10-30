import { http } from './http'

export interface ProbeResult {
  ok: boolean
  detail: string
  latencyMs: number
}

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

interface BackendHealth {
  status: string
  service: string
  version: string
}

interface AiHealth {
  status: string
  model_loaded: boolean
}

function describeError(error: unknown): string {
  const candidate = error as { code?: string; response?: { status?: number } }
  if (candidate?.code === 'ECONNABORTED') {
    return '请求超时'
  }
  if (!candidate?.response) {
    return '未启动或端口不通'
  }
  return `HTTP ${candidate.response.status}`
}

async function probe<T>(url: string, describe: (body: T) => string | null): Promise<ProbeResult> {
  const started = performance.now()
  try {
    const { data } = await http.get<T>(url, { timeout: 3000 })
    const detail = describe(data)
    return {
      ok: detail !== null,
      detail: detail ?? '响应格式与契约不一致',
      latencyMs: Math.round(performance.now() - started),
    }
  } catch (error) {
    return {
      ok: false,
      detail: describeError(error),
      latencyMs: Math.round(performance.now() - started),
    }
  }
}

export function fetchBackendHealth(): Promise<ProbeResult> {
  return probe<ApiEnvelope<BackendHealth>>('/api/v1/health', (body) => {
    if (body?.code !== 0 || body?.data?.status !== 'UP') {
      return null
    }
    return `版本 ${body.data.version ?? '-'}`
  })
}

export function fetchAiHealth(): Promise<ProbeResult> {
  return probe<AiHealth>('/ai/health', (body) => {
    if (body?.status !== 'ok') {
      return null
    }
    return body.model_loaded ? '服务正常，模型已加载' : '服务正常，模型未加载'
  })
}

