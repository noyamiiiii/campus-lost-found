import axios from 'axios'

/**
 * 统一 HTTP 实例。开发期所有请求走 Vite 代理，因此这里不需要写后端域名。
 * 后续接入鉴权时，在这里统一加请求头与 401 处理。
 */
export const http = axios.create({
  baseURL: '/',
  timeout: 5000,
  headers: { Accept: 'application/json' },
})

