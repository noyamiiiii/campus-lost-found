import { http } from './http'
import type { ClaimCreateInput, ClaimView } from '@/types/claim'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '操作失败，请重试')
  }
  return envelope.data
}

export async function createClaim(postPublicId: string, input: ClaimCreateInput): Promise<ClaimView> {
  const { data } = await http.post<ApiEnvelope<ClaimView>>(`/api/v1/posts/${postPublicId}/claims`, input)
  return unwrap(data)
}

export async function fetchMyClaims(): Promise<ClaimView[]> {
  const { data } = await http.get<ApiEnvelope<ClaimView[]>>('/api/v1/claims/mine')
  return unwrap(data)
}

export async function fetchReceivedClaims(): Promise<ClaimView[]> {
  const { data } = await http.get<ApiEnvelope<ClaimView[]>>('/api/v1/claims/received')
  return unwrap(data)
}

export async function fetchClaim(publicId: string): Promise<ClaimView> {
  const { data } = await http.get<ApiEnvelope<ClaimView>>(`/api/v1/claims/${publicId}`)
  return unwrap(data)
}

type ClaimAction = 'confirm' | 'reject' | 'reopen' | 'cancel' | 'complete'

export async function actOnClaim(
  publicId: string,
  action: ClaimAction,
  message?: string,
): Promise<ClaimView> {
  const payload = action === 'cancel' ? undefined : { message: message ?? '' }
  const { data } = await http.post<ApiEnvelope<ClaimView>>(`/api/v1/claims/${publicId}/${action}`, payload)
  return unwrap(data)
}

export function toClaimRequestMessage(error: unknown): string {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.message || '操作失败，请稍后重试'
}