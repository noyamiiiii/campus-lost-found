import axios from 'axios'
import { http } from './http'
import type { CampusLocation, ItemDetail, ItemKind, ItemPage, ItemSummary, PostCreateInput } from '@/types/item'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

interface CategoryListData {
  items: Category[]
}

export interface Category {
  id: number
  name: string
  slug: string
  icon: string | null
  sort: number
}

export interface PostQuery {
  type?: ItemKind
  categoryId?: number
  keyword?: string
  cursor?: string
  size?: number
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 0) {
    throw new Error(envelope.message || '请求未成功')
  }
  return envelope.data
}

export async function fetchCategories(): Promise<Category[]> {
  const { data } = await http.get<ApiEnvelope<CategoryListData>>('/api/v1/categories')
  return unwrap(data).items
}

export async function fetchPosts(query: PostQuery): Promise<ItemPage> {
  const { data } = await http.get<ApiEnvelope<ItemPage>>('/api/v1/posts', {
    params: {
      type: query.type,
      categoryId: query.categoryId,
      keyword: query.keyword?.trim() || undefined,
      cursor: query.cursor,
      size: query.size ?? 10,
    },
  })
  return unwrap(data)
}

export async function fetchPostDetail(publicId: string): Promise<ItemDetail> {
  const { data } = await http.get<ApiEnvelope<ItemDetail>>(`/api/v1/posts/${encodeURIComponent(publicId)}`)
  return unwrap(data)
}

export interface PostMatch {
  publicId: string
  title: string
  type: ItemKind
  categoryName: string
  locationName: string | null
  status: string
  score: number
  reasons: string[]
}

export async function fetchPostMatches(publicId: string): Promise<PostMatch[]> {
  const { data } = await http.get<ApiEnvelope<PostMatch[]>>(`/api/v1/posts/${encodeURIComponent(publicId)}/matches`)
  return unwrap(data)
}

/** 以图搜图的结果：score 是图片向量的余弦相似度。 */
export interface VisualMatch {
  publicId: string
  title: string
  status: string
  coverUrl: string | null
  score: number
}

export async function fetchSimilarImages(publicId: string, limit = 5): Promise<VisualMatch[]> {
  const { data } = await http.get<ApiEnvelope<VisualMatch[]>>(
    `/api/v1/posts/${encodeURIComponent(publicId)}/similar-images`,
    { params: { limit } },
  )
  return unwrap(data)
}

export async function fetchLocations(): Promise<CampusLocation[]> {
  const { data } = await http.get<ApiEnvelope<{ items: CampusLocation[] }>>('/api/v1/locations')
  return unwrap(data).items
}

export async function createPost(input: PostCreateInput): Promise<ItemDetail> {
  const { data } = await http.post<ApiEnvelope<ItemDetail>>('/api/v1/posts', input)
  return unwrap(data)
}

export async function fetchMyPosts(): Promise<ItemSummary[]> {
  const { data } = await http.get<ApiEnvelope<ItemSummary[]>>('/api/v1/posts/mine')
  return unwrap(data)
}

export function toRequestMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return '请求超时，请稍后重试'
    }
    const serverMessage = (error.response?.data as { message?: unknown } | undefined)?.message
    if (typeof serverMessage === 'string' && serverMessage.length > 0) {
      return serverMessage
    }
    if (!error.response) {
      return '无法连接后端服务，请确认服务已经启动'
    }
    if (error.response.status === 403) {
      return '请求被拒绝（HTTP 403），请确认访问地址与后端跨域配置'
    }
    return `请求失败（HTTP ${error.response.status}）`
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}
