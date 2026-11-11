import { http } from './http'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface UploadedFile {
  id: number
  url: string
  mime: string
  size: number
  width: number | null
  height: number | null
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '上传失败，请重试')
  }
  return envelope.data
}

/**
 * 上传一张图片，返回的 id 用于发帖 / 认领时引用，url 可直接渲染。
 */
export async function uploadImage(file: File): Promise<UploadedFile> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiEnvelope<UploadedFile>>('/api/v1/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 20000,
  })
  return unwrap(data)
}