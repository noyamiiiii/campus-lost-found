import { http } from './http'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 图片理解结果；status 为 DONE 才有可用的字段。 */
export interface ImageAiResult {
  status: 'PENDING' | 'DONE' | 'FAILED' | 'SKIPPED'
  categoryId: number | null
  categoryLabel: string | null
  confidence: number
  colors: { hex: string; name: string; ratio: number }[]
  labels: { name: string; score: number }[]
  sensitive: { hasFace: boolean; hasIdCard: boolean; boxes: unknown[] } | null
  description: string
  title: string
  model: string
  degraded: boolean
}

export interface UploadedFile {
  id: number
  url: string
  mime: string
  size: number
  width: number | null
  height: number | null
  ai: ImageAiResult | null
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '上传失败，请重试')
  }
  return envelope.data
}

/**
 * 上传一张图片，返回的 id 用于发帖 / 认领时引用，url 可直接渲染。
 * kind/location 只影响服务端生成的 AI 标题措辞，可省略。
 */
export async function uploadImage(
  file: File,
  options: { kind?: string; location?: string } = {},
): Promise<UploadedFile> {
  const form = new FormData()
  form.append('file', file)
  if (options.kind) {
    form.append('kind', options.kind)
  }
  if (options.location) {
    form.append('location', options.location)
  }
  const { data } = await http.post<ApiEnvelope<UploadedFile>>('/api/v1/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000,
  })
  return unwrap(data)
}